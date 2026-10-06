//! Pure, host-testable planning for Folk Mount (built-in module mounting).
//!
//! This module contains only filesystem-agnostic tree logic: module filtering,
//! deterministic first-wins merging, partition remapping, whiteout/opaque
//! detection and the tmpfs-vs-bind decision. It never performs a mount, never
//! talks to the kernel and does not depend on the Android-only `defs` module,
//! so it can be exercised by host unit tests through the injectable [`PlanFs`].
//!
//! The Android executor in `magic_mount` supplies a real [`PlanFs`] and turns
//! the resulting [`Node`] tree into mounts in Phase 2.

#![allow(dead_code)] // consumed by the Phase 2 executor

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use log::{error, warn};

/// Marker file names understood by module filtering.
///
/// These mirror the names in the Android-only `defs` module. They are repeated
/// here so the planner stays usable (and testable) on the host.
pub const DISABLE_FILE_NAME: &str = "disable";
pub const REMOVE_FILE_NAME: &str = "remove";
pub const SKIP_MOUNT_FILE_NAME: &str = "skip_mount";

/// Kind of a directory entry, reported without following symlinks.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum EntryKind {
    File,
    Directory,
    Symlink,
    CharDevice,
    Other,
}

/// Metadata for one entry, gathered with `lstat` semantics.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EntryMeta {
    pub kind: EntryKind,
    /// Device number for character/block devices; `0` identifies a whiteout.
    pub rdev: u64,
}

impl EntryMeta {
    #[must_use]
    pub const fn new(kind: EntryKind, rdev: u64) -> Self {
        Self { kind, rdev }
    }
}

/// Type of a node in a merge plan.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum NodeFileType {
    RegularFile,
    Directory,
    Symlink,
    Whiteout,
}

impl NodeFileType {
    /// Map `lstat` metadata to a plan node type. A character device with rdev
    /// `0` is a whiteout; unsupported kinds (block devices, FIFOs, sockets and
    /// devices with a non-zero rdev) are ignored.
    #[must_use]
    pub const fn from_entry_meta(meta: EntryMeta) -> Option<Self> {
        match meta.kind {
            EntryKind::File => Some(Self::RegularFile),
            EntryKind::Directory => Some(Self::Directory),
            EntryKind::Symlink => Some(Self::Symlink),
            EntryKind::CharDevice if meta.rdev == 0 => Some(Self::Whiteout),
            EntryKind::CharDevice | EntryKind::Other => None,
        }
    }

    /// Decide whether mounting this node over the real entry requires a tmpfs
    /// overlay instead of a direct bind.
    #[must_use]
    pub fn needs_tmpfs_vs_real(self, real: RealEntry) -> bool {
        match self {
            Self::Symlink => true,
            Self::Whiteout => real.exists,
            Self::RegularFile | Self::Directory => real
                .kind
                .is_none_or(|real_type| real_type != self || real_type == Self::Symlink),
        }
    }
}

/// The result of inspecting a path on the *real* filesystem, used by the tmpfs
/// decision. Both views are captured because the original algorithm follows
/// symlinks for whiteouts (`exists`) but not for type checks.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct RealEntry {
    /// `lstat` view; `None` when the path cannot be inspected (usually missing).
    pub kind: Option<NodeFileType>,
    /// `Path::exists()` view (follows symlinks).
    pub exists: bool,
}

impl RealEntry {
    /// A path that does not exist in either view.
    #[must_use]
    pub const fn missing() -> Self {
        Self {
            kind: None,
            exists: false,
        }
    }

    /// A real regular file.
    #[must_use]
    pub const fn file() -> Self {
        Self {
            kind: Some(NodeFileType::RegularFile),
            exists: true,
        }
    }
}

/// A node in the merged mount plan.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Node {
    pub name: String,
    pub file_type: NodeFileType,
    pub children: BTreeMap<String, Self>,
    /// The module path this node was collected from; `None` for synthetic roots.
    pub module_path: Option<PathBuf>,
    /// True when the module directory carries `trusted.overlay.opaque=y`.
    pub replace: bool,
    /// Set by [`should_create_tmpfs`] for root-level children that cannot be
    /// overlaid because the real root is not a module directory.
    pub skip: bool,
}

impl Node {
    /// Create a synthetic (non-module) root node named `name`.
    #[must_use]
    pub fn new_root(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            file_type: NodeFileType::Directory,
            children: BTreeMap::new(),
            module_path: None,
            replace: false,
            skip: false,
        }
    }

    /// Build a node from one entry of a module directory.
    fn from_entry(fs: &impl PlanFs, name: &str, path: &Path) -> Result<Option<Self>> {
        let Ok(meta) = fs.symlink_metadata(path) else {
            return Ok(None);
        };
        let Some(file_type) = NodeFileType::from_entry_meta(meta) else {
            return Ok(None);
        };
        let replace = file_type == NodeFileType::Directory && fs.read_opaque(path)?;
        Ok(Some(Self {
            name: name.to_string(),
            file_type,
            children: BTreeMap::new(),
            module_path: Some(path.to_path_buf()),
            replace,
            skip: false,
        }))
    }

    /// Recursively merge one module's directory (normally its `system/`) into
    /// `self`. Returns whether any file-like leaf was collected. Entries are
    /// visited in stable byte order and symlinks are never followed.
    ///
    /// The caller guarantees `dir` is not a symlink (module root and `system`
    /// root are validated during enumeration).
    ///
    /// # Errors
    /// Propagates filesystem read errors, including a failed opaque xattr read.
    pub fn collect_module_files(&mut self, fs: &impl PlanFs, dir: &Path) -> Result<bool> {
        let mut names = fs
            .read_dir(dir)
            .with_context(|| format!("read module dir {}", dir.display()))?;
        names.sort_unstable();

        let mut has_file = false;
        for name in names {
            let path = dir.join(&name);
            if let Some(mut node) = Self::from_entry(fs, &name, &path)? {
                if node.file_type == NodeFileType::Directory {
                    // An opaque directory still contributes its own children;
                    // `replace` only decides whether the real directory is
                    // replaced wholesale.
                    has_file |= node.collect_module_files(fs, &path)? || node.replace;
                } else {
                    has_file = true;
                }
                self.children.entry(name).or_insert(node);
            }
        }
        Ok(has_file)
    }

    /// Merge `other` into `self` with first-wins semantics: existing files are
    /// kept, directories are merged recursively, and a type conflict or an
    /// opaque winning directory stops later content from being merged.
    pub fn merge_from(&mut self, other: Self) {
        for (name, node) in other.children {
            match self.children.entry(name) {
                std::collections::btree_map::Entry::Vacant(slot) => {
                    slot.insert(node);
                }
                std::collections::btree_map::Entry::Occupied(mut slot) => {
                    let existing = slot.get_mut();
                    if existing.file_type == NodeFileType::Directory
                        && node.file_type == NodeFileType::Directory
                        && !existing.replace
                    {
                        existing.merge_from(node);
                    }
                }
            }
        }
    }

    /// Walk the tree, yielding every node with the absolute path it would
    /// occupy under `base`. The synthetic root (empty name) maps to `base`.
    pub fn visit_targets(&self, base: &Path, f: &mut impl FnMut(&Self, &Path)) {
        let target = base.join(&self.name);
        f(self, &target);
        for child in self.children.values() {
            child.visit_targets(&target, f);
        }
    }
}

/// Filesystem access needed by the planner, injected so host tests do not need
/// Android APIs.
pub trait PlanFs {
    /// List the immediate child names of `dir`; order does not matter.
    ///
    /// # Errors
    /// Returns an error when the directory cannot be read.
    fn read_dir(&self, dir: &Path) -> Result<Vec<String>>;

    /// `lstat`-style metadata.
    ///
    /// # Errors
    /// Returns an error when the path cannot be inspected.
    fn symlink_metadata(&self, path: &Path) -> Result<EntryMeta>;

    /// True when `path` resolves to a directory (follows symlinks).
    fn is_dir(&self, path: &Path) -> bool;

    /// True when `path` itself is a symlink (does not follow).
    fn is_symlink(&self, path: &Path) -> bool;

    /// True when `path` exists (follows symlinks).
    fn exists(&self, path: &Path) -> bool;

    /// Read the `trusted.overlay.opaque` attribute of a directory.
    /// `Ok(true)` only for the exact value `y`; `Ok(false)` when absent.
    ///
    /// # Errors
    /// Returns an error for read failures other than a missing attribute.
    fn read_opaque(&self, path: &Path) -> Result<bool>;
}

/// A [`PlanFs`] backed by `std::fs`.
///
/// Opaque xattr reads are injected because reading `trusted.overlay.opaque`
/// needs the Android-only `extattr` crate on device; host tests can supply a
/// stub.
pub struct StdFs<F> {
    opaque: F,
}

impl<F> StdFs<F> {
    #[must_use]
    pub const fn new(opaque: F) -> Self {
        Self { opaque }
    }
}

impl<F> PlanFs for StdFs<F>
where
    F: Fn(&Path) -> Result<bool>,
{
    fn read_dir(&self, dir: &Path) -> Result<Vec<String>> {
        let mut names = Vec::new();
        for entry in
            std::fs::read_dir(dir).with_context(|| format!("read dir {}", dir.display()))?
        {
            let entry = entry.with_context(|| format!("read entry in {}", dir.display()))?;
            names.push(entry.file_name().to_string_lossy().into_owned());
        }
        Ok(names)
    }

    fn symlink_metadata(&self, path: &Path) -> Result<EntryMeta> {
        let meta =
            std::fs::symlink_metadata(path).with_context(|| format!("stat {}", path.display()))?;
        Ok(EntryMeta::new(entry_kind(&meta), device_number(&meta)))
    }

    fn is_dir(&self, path: &Path) -> bool {
        path.is_dir()
    }

    fn is_symlink(&self, path: &Path) -> bool {
        path.is_symlink()
    }

    fn exists(&self, path: &Path) -> bool {
        path.exists()
    }

    fn read_opaque(&self, path: &Path) -> Result<bool> {
        (self.opaque)(path)
    }
}

fn entry_kind(meta: &std::fs::Metadata) -> EntryKind {
    use std::os::unix::fs::FileTypeExt;
    let file_type = meta.file_type();
    if file_type.is_file() {
        EntryKind::File
    } else if file_type.is_dir() {
        EntryKind::Directory
    } else if file_type.is_symlink() {
        EntryKind::Symlink
    } else if file_type.is_char_device() {
        EntryKind::CharDevice
    } else {
        EntryKind::Other
    }
}

fn device_number(meta: &std::fs::Metadata) -> u64 {
    use std::os::unix::fs::MetadataExt;
    meta.rdev()
}

/// A built-in partition and whether its `/system/<name>` entry must be a
/// symlink before it is remapped to `/<name>`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PartitionSpec {
    pub name: &'static str,
    pub require_system_symlink: bool,
}

/// Partitions mirrored from the device layout, in reference order.
pub const BUILTIN_PARTITIONS: [PartitionSpec; 5] = [
    PartitionSpec {
        name: "vendor",
        require_system_symlink: true,
    },
    PartitionSpec {
        name: "system_ext",
        require_system_symlink: true,
    },
    PartitionSpec {
        name: "product",
        require_system_symlink: true,
    },
    PartitionSpec {
        name: "odm",
        require_system_symlink: false,
    },
    PartitionSpec {
        name: "oem",
        require_system_symlink: false,
    },
];

/// Paths and partition rules used to build a plan.
#[derive(Debug, Clone, Copy)]
pub struct PlanConfig<'a> {
    pub modules_dir: &'a Path,
    /// The real root (normally `/`), used to test partition presence.
    pub root_dir: &'a Path,
    /// The real system root (normally `/system`), used for fallback targets.
    pub system_dir: &'a Path,
    pub partitions: &'a [PartitionSpec],
}

impl PlanConfig<'_> {
    #[must_use]
    pub const fn new<'a>(
        modules_dir: &'a Path,
        root_dir: &'a Path,
        system_dir: &'a Path,
    ) -> PlanConfig<'a> {
        PlanConfig {
            modules_dir,
            root_dir,
            system_dir,
            partitions: &BUILTIN_PARTITIONS,
        }
    }
}

/// A module directory discovered under the modules root.
#[derive(Debug, Clone, PartialEq, Eq)]
#[allow(clippy::struct_excessive_bools)]
pub struct ModuleCandidate {
    pub id: String,
    pub path: PathBuf,
    pub is_metamodule: bool,
    pub disabled: bool,
    pub removed: bool,
    pub skip_mount: bool,
    pub has_system: bool,
    pub system_is_symlink: bool,
}

impl ModuleCandidate {
    /// A module participates in the merge only when it is not disabled, not
    /// marked for removal, not skipping mount, not the metamodule itself, has a
    /// `system/` directory, and that directory is not a symlink.
    #[must_use]
    pub const fn is_mountable(&self) -> bool {
        !self.disabled
            && !self.removed
            && !self.skip_mount
            && !self.is_metamodule
            && self.has_system
            && !self.system_is_symlink
    }
}

/// Enumerate the direct module directories under `modules_dir` in stable byte
/// order. `is_metamodule` decides whether a directory carries the metamodule
/// marker (normally parsed from `module.prop`).
///
/// # Errors
/// Returns an error when the modules directory itself cannot be read.
pub fn enumerate_modules<F>(
    fs: &impl PlanFs,
    modules_dir: &Path,
    is_metamodule: F,
) -> Result<Vec<ModuleCandidate>>
where
    F: Fn(&Path) -> bool,
{
    let mut names = fs
        .read_dir(modules_dir)
        .with_context(|| format!("read modules dir {}", modules_dir.display()))?;
    names.sort_unstable();

    let mut modules = Vec::new();
    for id in names {
        let path = modules_dir.join(&id);
        if fs.is_symlink(&path) || !fs.is_dir(&path) {
            continue;
        }
        let system = path.join("system");
        modules.push(ModuleCandidate {
            id,
            is_metamodule: is_metamodule(&path),
            disabled: fs.exists(&path.join(DISABLE_FILE_NAME)),
            removed: fs.exists(&path.join(REMOVE_FILE_NAME)),
            skip_mount: fs.exists(&path.join(SKIP_MOUNT_FILE_NAME)),
            has_system: fs.is_dir(&system),
            system_is_symlink: fs.is_symlink(&system),
            path,
        });
    }
    Ok(modules)
}

/// Build the merged, remapped mount plan from every mountable module.
///
/// Returns `None` when no module contributes any file, matching the reference
/// behaviour of not mounting an empty tree.
///
/// # Errors
/// Returns an error when the modules directory cannot be listed. Per-module
/// read failures (including opaque xattr errors) exclude that module and are
/// logged instead of aborting the whole plan.
pub fn collect_module_files<F>(
    fs: &impl PlanFs,
    cfg: &PlanConfig<'_>,
    is_metamodule: F,
) -> Result<Option<Node>>
where
    F: Fn(&Path) -> bool,
{
    let modules = enumerate_modules(fs, cfg.modules_dir, is_metamodule)?;
    let mut system = Node::new_root("system");
    let mut has_file = false;

    for module in modules {
        if !module.is_mountable() {
            continue;
        }
        let mod_system = module.path.join("system");
        // Build each module into its own tree so an opaque xattr failure can
        // discard it without leaving partial content in the merged tree.
        let mut tree = Node::new_root("system");
        match tree.collect_module_files(fs, &mod_system) {
            Ok(true) => {
                has_file = true;
                system.merge_from(tree);
            }
            Ok(false) => {}
            Err(e) => warn!("folk mount: skipping module {}: {e:#}", module.id),
        }
    }

    if !has_file {
        return Ok(None);
    }

    let mut root = Node::new_root("");
    for spec in cfg.partitions {
        let root_partition = cfg.root_dir.join(spec.name);
        let system_partition = cfg.system_dir.join(spec.name);
        if fs.is_dir(&root_partition)
            && (!spec.require_system_symlink || fs.is_symlink(&system_partition))
            && let Some(node) = system.children.remove(spec.name)
        {
            root.children.insert(spec.name.to_string(), node);
        }
    }
    root.children.insert("system".to_string(), system);
    Ok(Some(root))
}

/// Decide whether the children of `current` require a tmpfs overlay at `path`.
///
/// When a child of the synthetic root (a node without a `module_path`) needs an
/// overlay, it is marked `skip` instead, because the real root cannot be
/// overlaid. `real_entry` resolves the current view of the real filesystem.
#[must_use]
pub fn should_create_tmpfs(
    path: &Path,
    current: &mut Node,
    has_tmpfs: bool,
    mut real_entry: impl FnMut(&Path) -> RealEntry,
) -> bool {
    if has_tmpfs {
        return false;
    }
    if current.replace && current.module_path.is_some() {
        return true;
    }
    for (name, node) in &mut current.children {
        if node
            .file_type
            .needs_tmpfs_vs_real(real_entry(&path.join(name)))
        {
            if current.module_path.is_none() {
                error!("cannot create tmpfs on {}, ignore: {name}", path.display());
                node.skip = true;
                continue;
            }
            return true;
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::os::unix::fs::symlink;

    use tempfile::tempdir;

    use super::*;

    fn plain_fs() -> StdFs<fn(&Path) -> Result<bool>> {
        fn no_opaque(_: &Path) -> Result<bool> {
            Ok(false)
        }
        StdFs::new(no_opaque as fn(&Path) -> Result<bool>)
    }

    fn plan_for(
        modules: &Path,
        root: &Path,
        opaque: impl Fn(&Path) -> Result<bool>,
    ) -> Option<Node> {
        let system = root.join("system");
        let cfg = PlanConfig::new(modules, root, &system);
        collect_module_files(&StdFs::new(opaque), &cfg, |_| false).unwrap()
    }

    fn targets(node: &Node, base: &Path) -> Vec<PathBuf> {
        let mut out = Vec::new();
        node.visit_targets(base, &mut |_, path| out.push(path.to_path_buf()));
        out
    }

    #[test]
    fn filtering_skips_markers_metamodule_symlinks_and_missing_system() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        fs::create_dir(&modules).unwrap();

        for name in ["good", "disabled", "removed", "skip", "meta"] {
            let dir = modules.join(name).join("system/etc");
            fs::create_dir_all(&dir).unwrap();
            fs::write(dir.join("hosts"), name).unwrap();
        }
        fs::write(modules.join("disabled/disable"), "").unwrap();
        fs::write(modules.join("removed/remove"), "").unwrap();
        fs::write(modules.join("skip/skip_mount"), "").unwrap();
        fs::create_dir(modules.join("nosys")).unwrap();
        fs::create_dir(modules.join("linky")).unwrap();
        symlink(modules.join("good/system"), modules.join("linky/system")).unwrap();
        fs::write(modules.join("stray"), "").unwrap();

        let fs_ = plain_fs();
        let is_meta = |path: &Path| path.file_name().is_some_and(|name| name == "meta");
        let found = enumerate_modules(&fs_, &modules, is_meta).unwrap();
        let ids: Vec<&str> = found.iter().map(|m| m.id.as_str()).collect();
        assert_eq!(
            ids,
            [
                "disabled", "good", "linky", "meta", "nosys", "removed", "skip"
            ]
        );

        let mountable: Vec<&str> = found
            .iter()
            .filter(|m| m.is_mountable())
            .map(|m| m.id.as_str())
            .collect();
        assert_eq!(mountable, ["good"]);

        let system = tmp.path().join("system");
        let cfg = PlanConfig::new(&modules, tmp.path(), &system);
        let plan = collect_module_files(&fs_, &cfg, is_meta).unwrap().unwrap();
        let etc = &plan.children["system"].children["etc"];
        assert_eq!(etc.children.len(), 1);
        assert_eq!(
            etc.children["hosts"].module_path.as_deref(),
            Some(modules.join("good/system/etc/hosts").as_path())
        );
    }

    #[test]
    fn merge_is_deterministic_first_module_wins_regardless_of_order() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");

        for name in ["z-mod", "a-mod", "b-mod"] {
            let dir = modules.join(name).join("system/etc");
            fs::create_dir_all(&dir).unwrap();
            fs::write(dir.join("shared"), name).unwrap();
        }
        fs::write(modules.join("b-mod/system/etc/only_b"), "new").unwrap();

        let plan = plan_for(&modules, tmp.path(), |_| Ok(false)).unwrap();
        let etc = &plan.children["system"].children["etc"];
        assert_eq!(
            etc.children["shared"].module_path.as_deref(),
            Some(modules.join("a-mod/system/etc/shared").as_path())
        );
        assert_eq!(
            etc.children["only_b"].module_path.as_deref(),
            Some(modules.join("b-mod/system/etc/only_b").as_path())
        );
    }

    #[test]
    fn directory_directories_merge_recursively() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        fs::create_dir_all(modules.join("a/system/etc")).unwrap();
        fs::write(modules.join("a/system/etc/a"), "a").unwrap();
        fs::create_dir_all(modules.join("b/system/etc/sub")).unwrap();
        fs::write(modules.join("b/system/etc/sub/b"), "b").unwrap();

        let plan = plan_for(&modules, tmp.path(), |_| Ok(false)).unwrap();
        let etc = &plan.children["system"].children["etc"];
        assert!(etc.children.contains_key("a"));
        assert!(etc.children["sub"].children.contains_key("b"));
    }

    #[test]
    fn type_conflict_stops_merge_and_keeps_first() {
        // "0file" sorts before "a-dir", so the file wins and the directory is
        // not merged into it.
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        fs::create_dir_all(modules.join("0file/system")).unwrap();
        fs::write(modules.join("0file/system/etc"), "file").unwrap();
        fs::create_dir_all(modules.join("a-dir/system/etc")).unwrap();
        fs::write(modules.join("a-dir/system/etc/a"), "a").unwrap();

        let plan = plan_for(&modules, tmp.path(), |_| Ok(false)).unwrap();
        let etc = &plan.children["system"].children["etc"];
        assert_eq!(etc.file_type, NodeFileType::RegularFile);
        assert!(etc.children.is_empty());
    }

    #[test]
    fn opaque_winning_directory_stops_later_merge() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        fs::create_dir_all(modules.join("a-mod/system/etc")).unwrap();
        fs::write(modules.join("a-mod/system/etc/a"), "a").unwrap();
        fs::create_dir_all(modules.join("b-mod/system/etc")).unwrap();
        fs::write(modules.join("b-mod/system/etc/b"), "b").unwrap();

        let opaque_dir = modules.join("a-mod/system/etc");
        let plan = plan_for(&modules, tmp.path(), move |path| Ok(path == opaque_dir)).unwrap();
        let etc = &plan.children["system"].children["etc"];
        assert!(etc.replace);
        assert!(etc.children.contains_key("a"));
        assert!(!etc.children.contains_key("b"));
    }

    #[test]
    fn broken_symlink_is_collected_as_leaf_without_following() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        fs::create_dir_all(modules.join("mod/system")).unwrap();
        symlink("does-not-exist", modules.join("mod/system/link")).unwrap();

        let plan = plan_for(&modules, tmp.path(), |_| Ok(false)).unwrap();
        let link = &plan.children["system"].children["link"];
        assert_eq!(link.file_type, NodeFileType::Symlink);
        assert_eq!(
            link.module_path.as_deref(),
            Some(modules.join("mod/system/link").as_path())
        );
    }

    #[test]
    fn partition_remapping_follows_require_symlink_rule() {
        let tmp = tempdir().unwrap();
        let root = tmp.path().join("root");
        let system = root.join("system");
        let modules = tmp.path().join("modules");

        fs::create_dir_all(&system).unwrap();
        fs::create_dir_all(root.join("vendor")).unwrap();
        fs::create_dir_all(root.join("odm")).unwrap();
        fs::create_dir_all(root.join("product")).unwrap();
        // /system/vendor is a symlink -> remap; /system/product is a real dir
        // -> do not remap even though /product exists.
        symlink(root.join("vendor"), system.join("vendor")).unwrap();
        fs::create_dir(system.join("product")).unwrap();

        let dir = modules.join("mod/system");
        fs::create_dir_all(dir.join("vendor")).unwrap();
        fs::create_dir_all(dir.join("odm")).unwrap();
        fs::create_dir_all(dir.join("product")).unwrap();
        fs::create_dir_all(dir.join("system_ext")).unwrap();
        fs::create_dir_all(dir.join("etc")).unwrap();
        for (sub, file) in [
            ("vendor", "v"),
            ("odm", "o"),
            ("product", "p"),
            ("system_ext", "s"),
            ("etc", "h"),
        ] {
            fs::write(dir.join(sub).join(file), "x").unwrap();
        }

        let cfg = PlanConfig::new(&modules, &root, &system);
        let plan = collect_module_files(&plain_fs(), &cfg, |_| false)
            .unwrap()
            .unwrap();

        assert!(plan.children.contains_key("vendor"));
        assert!(plan.children.contains_key("odm"));
        assert!(!plan.children.contains_key("product"));
        assert!(!plan.children.contains_key("system_ext"));
        assert!(plan.children["system"].children.contains_key("product"));
        assert!(plan.children["system"].children.contains_key("system_ext"));

        let all = targets(&plan, &root);
        assert!(all.contains(&root.join("vendor/v")));
        assert!(all.contains(&root.join("odm/o")));
        assert!(all.contains(&root.join("system/product/p")));
        assert!(all.contains(&root.join("system/system_ext/s")));
    }

    #[test]
    fn missing_partition_never_resolves_to_root() {
        let tmp = tempdir().unwrap();
        let root = tmp.path().join("root");
        let system = root.join("system");
        let modules = tmp.path().join("modules");
        fs::create_dir_all(&system).unwrap();
        // No /vendor on this device, but the module ships system/vendor/foo.
        fs::create_dir_all(modules.join("mod/system/vendor")).unwrap();
        fs::write(modules.join("mod/system/vendor/foo"), "x").unwrap();

        let cfg = PlanConfig::new(&modules, &root, &system);
        let plan = collect_module_files(&plain_fs(), &cfg, |_| false)
            .unwrap()
            .unwrap();

        assert!(!plan.children.contains_key("vendor"));
        assert!(plan.children["system"].children.contains_key("vendor"));

        let all = targets(&plan, &root);
        assert!(all.contains(&root.join("system/vendor/foo")));
        // A missing partition must stay under /system and must never be
        // remapped to /vendor (or, worse, to the root itself).
        assert!(all.contains(&root.join("system/vendor")));
        assert!(!all.contains(&root.join("vendor")));
        assert!(!all.contains(&root.join("vendor/foo")));
    }

    #[test]
    fn whiteout_detection_requires_zero_rdev_char_device() {
        let whiteout = EntryMeta::new(EntryKind::CharDevice, 0);
        assert_eq!(
            NodeFileType::from_entry_meta(whiteout),
            Some(NodeFileType::Whiteout)
        );
        let device = EntryMeta::new(EntryKind::CharDevice, 1);
        assert_eq!(NodeFileType::from_entry_meta(device), None);
        assert_eq!(
            NodeFileType::from_entry_meta(EntryMeta::new(EntryKind::File, 0)),
            Some(NodeFileType::RegularFile)
        );
        assert_eq!(
            NodeFileType::from_entry_meta(EntryMeta::new(EntryKind::Symlink, 0)),
            Some(NodeFileType::Symlink)
        );
        assert_eq!(
            NodeFileType::from_entry_meta(EntryMeta::new(EntryKind::Other, 0)),
            None
        );
    }

    #[test]
    fn needs_tmpfs_vs_real_matches_reference_matrix() {
        let file = RealEntry::file();
        assert!(!NodeFileType::RegularFile.needs_tmpfs_vs_real(file));
        assert!(NodeFileType::Directory.needs_tmpfs_vs_real(file));
        assert!(NodeFileType::RegularFile.needs_tmpfs_vs_real(RealEntry::missing()));
        assert!(NodeFileType::Symlink.needs_tmpfs_vs_real(file));
        assert!(NodeFileType::Whiteout.needs_tmpfs_vs_real(file));
        assert!(!NodeFileType::Whiteout.needs_tmpfs_vs_real(RealEntry::missing()));
        let symlink = RealEntry {
            kind: Some(NodeFileType::Symlink),
            exists: true,
        };
        assert!(NodeFileType::RegularFile.needs_tmpfs_vs_real(symlink));
    }

    #[test]
    fn opaque_directory_is_flagged_and_requests_tmpfs() {
        let tmp = tempdir().unwrap();
        let module = tmp.path().join("system/etc");

        let mut node = Node::new_root("etc");
        node.module_path = Some(module);
        node.replace = true;
        assert!(should_create_tmpfs(
            Path::new("/"),
            &mut node,
            false,
            |_| RealEntry::missing()
        ));
        assert!(!should_create_tmpfs(
            Path::new("/"),
            &mut node,
            true,
            |_| RealEntry::missing()
        ));
    }

    #[test]
    fn unoverlayable_root_child_is_skipped() {
        let tmp = tempdir().unwrap();
        let mut root = Node::new_root("");
        root.children
            .insert("missing".to_string(), Node::new_root("missing"));

        let created = should_create_tmpfs(tmp.path(), &mut root, false, |_| RealEntry::missing());
        assert!(!created);
        assert!(root.children["missing"].skip);
    }

    #[test]
    fn opaque_read_error_excludes_module_without_partial_content() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        let bad = modules.join("bad-mod/system/etc");
        fs::create_dir_all(&bad).unwrap();
        fs::write(bad.join("secret"), "bad").unwrap();
        let good = modules.join("good-mod/system/etc");
        fs::create_dir_all(&good).unwrap();
        fs::write(good.join("hosts"), "good").unwrap();

        let failing = modules.join("bad-mod/system/etc");
        let plan = plan_for(&modules, tmp.path(), move |path| {
            if path == failing {
                anyhow::bail!("opaque read denied");
            }
            Ok(false)
        })
        .unwrap();

        let etc = &plan.children["system"].children["etc"];
        assert!(etc.children.contains_key("hosts"));
        assert!(!etc.children.contains_key("secret"));
    }

    #[test]
    fn empty_module_tree_does_not_request_a_mount() {
        let tmp = tempdir().unwrap();
        let modules = tmp.path().join("modules");
        fs::create_dir_all(modules.join("empty/system/etc")).unwrap();
        fs::create_dir_all(modules.join("nested/system/a/b")).unwrap();

        assert!(plan_for(&modules, tmp.path(), |_| Ok(false)).is_none());
    }
}
