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

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
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
    /// A path that does not exist in either view. Test helper.
    #[cfg(test)]
    #[must_use]
    pub const fn missing() -> Self {
        Self {
            kind: None,
            exists: false,
        }
    }

    /// A real regular file. Test helper.
    #[cfg(test)]
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
    ///
    /// Used by the plan tests and kept as a public helper for status/debug
    /// output; the executor walks children directly to preserve rollback order.
    #[allow(dead_code)]
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

/// Build [`EntryMeta`] from `std::fs` metadata using `lstat` semantics.
///
/// Shared by the planner's [`StdFs`] and the Android executor so both agree on
/// how character devices (whiteouts) and other entry kinds are classified.
#[must_use]
pub fn entry_meta(meta: &std::fs::Metadata) -> EntryMeta {
    EntryMeta::new(entry_kind(meta), device_number(meta))
}

fn entry_kind(meta: &std::fs::Metadata) -> EntryKind {
    let file_type = meta.file_type();
    if file_type.is_file() {
        EntryKind::File
    } else if file_type.is_dir() {
        EntryKind::Directory
    } else if file_type.is_symlink() {
        EntryKind::Symlink
    } else if is_char_device(meta) {
        EntryKind::CharDevice
    } else {
        EntryKind::Other
    }
}

#[cfg(unix)]
fn is_char_device(meta: &std::fs::Metadata) -> bool {
    use std::os::unix::fs::FileTypeExt;
    meta.file_type().is_char_device()
}

/// Platforms without character devices cannot carry a whiteout, so nothing is one.
#[cfg(not(unix))]
fn is_char_device(_meta: &std::fs::Metadata) -> bool {
    false
}

#[cfg(unix)]
fn device_number(meta: &std::fs::Metadata) -> u64 {
    use std::os::unix::fs::MetadataExt;
    meta.rdev()
}

#[cfg(not(unix))]
fn device_number(_meta: &std::fs::Metadata) -> u64 {
    0
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

// ---------------------------------------------------------------------------
// Phase 2 executor
//
// The algorithm below is host-testable: it only uses [`MountOps`], an
// abstract interface that the Android-only `magic_mount` module implements
// with rustix/extattr/ksucalls. Host tests substitute an instrumented fake and
// exercise whiteout/opaque/type-conflict handling and failure rollback without
// needing root or a private mount namespace.
// ---------------------------------------------------------------------------

/// Ownership and permission bits copied onto a tmpfs mirror.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct FileMeta {
    pub mode: u32,
    pub uid: u32,
    pub gid: u32,
}

/// One entry of a real directory, reported with `lstat` semantics.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RealDirEntry {
    pub name: String,
    pub file_type: NodeFileType,
}

/// Filesystem, metadata and mount primitives required by [`run_mount`].
///
/// The Android implementation lives in `magic_mount`; the host tests provide an
/// instrumented implementation so the executor can be exercised end to end.
pub trait MountOps {
    /// List one real directory without following symlinks.
    ///
    /// # Errors
    /// Returns an error when the directory cannot be read.
    fn read_dir(&self, path: &Path) -> Result<Vec<RealDirEntry>>;

    /// `lstat`-style metadata.
    ///
    /// # Errors
    /// Returns an error when the path cannot be inspected.
    fn symlink_metadata(&self, path: &Path) -> Result<EntryMeta>;

    /// `stat`-style metadata (follows symlinks), reduced to mode/uid/gid.
    ///
    /// # Errors
    /// Returns an error when the path cannot be inspected.
    fn metadata(&self, path: &Path) -> Result<FileMeta>;

    /// Read a symlink target.
    ///
    /// # Errors
    /// Returns an error when the link cannot be read.
    fn read_link(&self, path: &Path) -> Result<PathBuf>;

    /// True when `path` exists (follows symlinks).
    fn exists(&self, path: &Path) -> bool;

    /// True when `path` is a directory (follows symlinks).
    fn is_dir(&self, path: &Path) -> bool;

    /// Create a directory and its missing parents.
    ///
    /// # Errors
    /// Returns an error when the directory cannot be created.
    fn create_dir_all(&self, path: &Path) -> Result<()>;

    /// Create (or truncate) an empty regular file.
    ///
    /// # Errors
    /// Returns an error when the file cannot be created.
    fn create_file(&self, path: &Path) -> Result<()>;

    /// Create a symlink at `link` pointing to `target`.
    ///
    /// # Errors
    /// Returns an error when the link cannot be created.
    fn symlink(&self, target: &Path, link: &Path) -> Result<()>;

    /// Change the permission bits of `path`.
    ///
    /// # Errors
    /// Returns an error when the mode cannot be changed.
    fn chmod(&self, path: &Path, mode: u32) -> Result<()>;

    /// Change the owner/group of `path`.
    ///
    /// # Errors
    /// Returns an error when the ownership cannot be changed.
    fn chown(&self, path: &Path, uid: u32, gid: u32) -> Result<()>;

    /// Read the SELinux label of `path` without following symlinks.
    ///
    /// # Errors
    /// Returns an error when the label cannot be read.
    fn lgetfilecon(&self, path: &Path) -> Result<String>;

    /// Set the SELinux label of `path` without following symlinks.
    ///
    /// # Errors
    /// Returns an error when the label cannot be set.
    fn lsetfilecon(&self, path: &Path, con: &str) -> Result<()>;

    /// Mount a tmpfs named `name` on `target`.
    ///
    /// # Errors
    /// Returns an error when the mount fails.
    fn mount_tmpfs(&self, name: &str, target: &Path) -> Result<()>;

    /// Bind-mount `source` onto `target`.
    ///
    /// # Errors
    /// Returns an error when the mount fails.
    fn mount_bind(&self, source: &Path, target: &Path) -> Result<()>;

    /// Move the mount at `source` to `target`.
    ///
    /// # Errors
    /// Returns an error when the move fails.
    fn mount_move(&self, source: &Path, target: &Path) -> Result<()>;

    /// Make the mount at `path`, and every mount below it, private.
    ///
    /// Deliberately recursive (`MS_PRIVATE | MS_REC`). A bind clone inherits the
    /// propagation of its source, so a module file sourced from `/data` would
    /// otherwise remain a member of that mount's peer group: creating it, and
    /// unmounting it later, would then propagate to every namespace sharing the
    /// group.
    ///
    /// # Errors
    /// Returns an error when the propagation change fails.
    fn make_private(&self, path: &Path) -> Result<()>;

    /// Detach the mount at `path`.
    ///
    /// # Errors
    /// Returns an error when the unmount fails.
    fn unmount_detach(&self, path: &Path) -> Result<()>;

    /// Register a final target for kernel-side per-app unmounting.
    ///
    /// # Errors
    /// Returns an error when the kernel rejects the registration (for example
    /// `EEXIST` for a path registered by someone else).
    fn register_umount(&self, path: &Path) -> Result<()>;

    /// Remove one of our own kernel unmount registrations.
    ///
    /// # Errors
    /// Returns an error when the registration cannot be removed.
    fn unregister_umount(&self, path: &Path) -> Result<()>;

    /// Notify the kernel that modules are mounted.
    ///
    /// # Errors
    /// Returns an error when the notification cannot be sent.
    fn report_mounted(&self) -> Result<()>;
}

/// Final result of one executor round.
#[derive(Debug, Default, Clone)]
pub struct ExecutionOutcome {
    /// Number of final, published targets still mounted after the round.
    pub target_count: usize,
    /// Nodes the planner marked as unoverlayable and the executor skipped.
    pub skipped_count: usize,
    /// True when a rollback or cleanup could not undo every mount.
    pub partial: bool,
    /// Paths that could not be undone (mounts that remain).
    pub residue: Vec<PathBuf>,
}

/// Outcome plus the error that aborted the round, if any.
#[derive(Debug)]
pub struct ExecutionReport {
    pub outcome: ExecutionOutcome,
    pub error: Option<anyhow::Error>,
}

/// Bookkeeping for one round: published mounts and kernel registrations are
/// recorded as they are created so a failure can be undone in reverse order.
struct MountTransaction<'a, O: MountOps> {
    ops: &'a O,
    /// Final targets published onto the real filesystem, in creation order.
    mounts: Vec<PathBuf>,
    /// Kernel unmount registrations added by this round, in insertion order.
    registrations: Vec<PathBuf>,
    skipped: usize,
}

struct RollbackOutcome {
    remaining_mounts: usize,
    residue: Vec<PathBuf>,
}

impl<'a, O: MountOps> MountTransaction<'a, O> {
    const fn new(ops: &'a O) -> Self {
        Self {
            ops,
            mounts: Vec::new(),
            registrations: Vec::new(),
            skipped: 0,
        }
    }

    /// Record one published real-path mount. Publication happens as soon as the
    /// mount succeeds, before PRIVATE, so a later failure can still undo it.
    fn publish(&mut self, target: &Path) {
        if !self.mounts.iter().any(|path| path == target) {
            self.mounts.push(target.to_path_buf());
        }
    }

    fn mount_node(
        &mut self,
        parent: &Path,
        work_parent: &Path,
        mut current: Node,
        has_tmpfs: bool,
    ) -> Result<()> {
        let ops = self.ops;
        let path = parent.join(&current.name);
        let work = work_parent.join(&current.name);
        if current.skip {
            self.skipped += 1;
            return Ok(());
        }
        match current.file_type {
            NodeFileType::RegularFile => {
                let target = if has_tmpfs {
                    ops.create_file(&work)?;
                    work.clone()
                } else {
                    path.clone()
                };
                let Some(module_path) = current.module_path.clone() else {
                    bail!("cannot mount root file {}", path.display());
                };
                ops.mount_bind(&module_path, &target)?;
                if !has_tmpfs {
                    // Publish first so a PRIVATE failure can still undo it.
                    self.publish(&target);
                }
                // The clone inherits the source mount's peer group whether or not
                // it has been published yet; detach it so it can neither leak nor
                // receive unmounts.
                ops.make_private(&target)?;
            }
            NodeFileType::Symlink => {
                let Some(module_path) = current.module_path.clone() else {
                    bail!("cannot mount root symlink {}", path.display());
                };
                self.clone_symlink(&module_path, &work)?;
            }
            NodeFileType::Directory => {
                let create = should_create_tmpfs(&path, &mut current, has_tmpfs, |probe| {
                    real_entry(ops, probe)
                });
                let has_tmpfs = has_tmpfs || create;
                if has_tmpfs {
                    self.prepare_tmpfs_skeleton(&path, &work, current.module_path.as_deref())?;
                }
                if create {
                    ops.mount_bind(&work, &work)?;
                }
                if current.replace && current.module_path.is_none() {
                    bail!(
                        "dir {} is marked opaque but has no module source",
                        path.display()
                    );
                }
                // Only recurse into a real directory. When the module replaces
                // a real file with a directory the skeleton comes from the
                // module and the real entry must not be listed.
                if ops.is_dir(&path) && !current.replace {
                    self.process_existing_entries(&path, &work, &mut current.children, has_tmpfs)?;
                }
                self.process_remaining_children(&path, &work, current.children, has_tmpfs)?;
                if create {
                    self.move_tmpfs_to_target(&work, &path)?;
                }
            }
            NodeFileType::Whiteout => {
                log::debug!("folk mount: {} is whited out", path.display());
            }
        }
        Ok(())
    }

    /// Mirror one unmapped real entry into the work-dir tmpfs.
    fn mount_mirror(&self, parent: &Path, work_parent: &Path, entry: &RealDirEntry) -> Result<()> {
        let path = parent.join(&entry.name);
        let work = work_parent.join(&entry.name);
        match entry.file_type {
            NodeFileType::RegularFile => {
                self.ops.create_file(&work)?;
                self.ops.mount_bind(&path, &work)?;
            }
            NodeFileType::Directory => {
                self.ops.create_dir_all(&work)?;
                let meta = self.ops.metadata(&path)?;
                // chown before chmod: chown clears setuid/setgid bits.
                self.ops.chown(&work, meta.uid, meta.gid)?;
                self.ops.chmod(&work, meta.mode)?;
                self.ops.lsetfilecon(&work, &self.ops.lgetfilecon(&path)?)?;
                let mut entries = self.ops.read_dir(&path)?;
                entries.sort_by(|a, b| a.name.cmp(&b.name));
                for child in entries {
                    self.mount_mirror(&path, &work, &child)?;
                }
            }
            NodeFileType::Symlink => self.clone_symlink(&path, &work)?,
            NodeFileType::Whiteout => {}
        }
        Ok(())
    }

    fn clone_symlink(&self, source: &Path, link: &Path) -> Result<()> {
        let target = self.ops.read_link(source)?;
        self.ops.symlink(&target, link)?;
        self.ops.lsetfilecon(link, &self.ops.lgetfilecon(source)?)?;
        Ok(())
    }

    fn prepare_tmpfs_skeleton(
        &self,
        path: &Path,
        work: &Path,
        module_path: Option<&Path>,
    ) -> Result<()> {
        self.ops.create_dir_all(work)?;
        let source = if self.ops.is_dir(path) {
            path
        } else if let Some(module_path) = module_path {
            module_path
        } else {
            bail!(
                "cannot prepare mount skeleton for {} without a module source",
                path.display()
            );
        };
        let meta = self.ops.metadata(source)?;
        // chown before chmod: chown clears setuid/setgid bits.
        self.ops.chown(work, meta.uid, meta.gid)?;
        self.ops.chmod(work, meta.mode)?;
        self.ops.lsetfilecon(work, &self.ops.lgetfilecon(source)?)?;
        Ok(())
    }

    fn process_existing_entries(
        &mut self,
        path: &Path,
        work: &Path,
        children: &mut BTreeMap<String, Node>,
        has_tmpfs: bool,
    ) -> Result<()> {
        // Sort for deterministic first-wins behaviour and testability; the
        // reference implementation relies on readdir order here.
        let mut entries = self.ops.read_dir(path)?;
        entries.sort_by(|a, b| a.name.cmp(&b.name));
        for entry in entries {
            if let Some(node) = children.remove(&entry.name) {
                if node.skip {
                    self.skipped += 1;
                    continue;
                }
                self.mount_node(path, work, node, has_tmpfs)
                    .with_context(|| format!("folk mount {}/{}", path.display(), entry.name))?;
            } else if has_tmpfs {
                self.mount_mirror(path, work, &entry)
                    .with_context(|| format!("folk mirror {}/{}", path.display(), entry.name))?;
            }
        }
        Ok(())
    }

    fn process_remaining_children(
        &mut self,
        path: &Path,
        work: &Path,
        children: BTreeMap<String, Node>,
        has_tmpfs: bool,
    ) -> Result<()> {
        for (name, node) in children {
            if node.skip {
                self.skipped += 1;
                continue;
            }
            self.mount_node(path, work, node, has_tmpfs)
                .with_context(|| format!("folk mount {}/{}", path.display(), name))?;
        }
        Ok(())
    }

    fn move_tmpfs_to_target(&mut self, work: &Path, target: &Path) -> Result<()> {
        self.ops.mount_move(work, target)?;
        // Publish before PRIVATE so a PRIVATE failure can still undo the move.
        self.publish(target);
        self.ops.make_private(target)?;
        Ok(())
    }

    /// Register the final targets with the kernel, parents before children so
    /// the kernel unmounts children first (its list is LIFO).
    fn register_targets(&mut self) -> Result<()> {
        let mut targets = self.mounts.clone();
        targets.sort_by(|a, b| {
            component_count(a)
                .cmp(&component_count(b))
                .then_with(|| a.cmp(b))
        });
        targets.dedup();
        for target in targets {
            self.ops
                .register_umount(&target)
                .with_context(|| format!("register umount target {}", target.display()))?;
            self.registrations.push(target);
        }
        Ok(())
    }

    /// Undo this round in reverse order: our registrations, then the published
    /// mounts, then the staging tmpfs. Earlier/external rounds are untouched.
    fn rollback(&mut self, work_dir: &Path) -> RollbackOutcome {
        let mut residue = Vec::new();
        let mut remaining_mounts = 0;
        for path in std::mem::take(&mut self.registrations).into_iter().rev() {
            if self.ops.unregister_umount(&path).is_err() {
                residue.push(path);
            }
        }
        for path in std::mem::take(&mut self.mounts).into_iter().rev() {
            if self.ops.unmount_detach(&path).is_err() {
                residue.push(path);
                remaining_mounts += 1;
            }
        }
        if self.ops.unmount_detach(work_dir).is_err() {
            residue.push(work_dir.to_path_buf());
        }
        RollbackOutcome {
            remaining_mounts,
            residue,
        }
    }

    /// Detach the staging tmpfs after a successful publish. A failure here is
    /// reported as residue but does not undo the committed targets.
    fn cleanup_staging(&self, work_dir: &Path) -> Vec<PathBuf> {
        if self.ops.unmount_detach(work_dir).is_err() {
            vec![work_dir.to_path_buf()]
        } else {
            Vec::new()
        }
    }
}

fn real_entry<O: MountOps>(ops: &O, path: &Path) -> RealEntry {
    let kind = ops
        .symlink_metadata(path)
        .ok()
        .and_then(NodeFileType::from_entry_meta);
    RealEntry {
        kind,
        exists: ops.exists(path),
    }
}

fn component_count(path: &Path) -> usize {
    path.components().count()
}

/// Execute a mount plan, publishing final targets and registering them with the
/// kernel.
///
/// `plan` of `None` means there is nothing to mount and no work-dir mount is
/// created. On failure the round is rolled back in reverse; any mount that
/// could not be undone is reported in [`ExecutionOutcome::residue`] with
/// `partial` set.
pub fn run_mount<O: MountOps>(
    plan: Option<Node>,
    target_root: &Path,
    work_dir: &Path,
    fs_name: &str,
    ops: &O,
) -> ExecutionReport {
    let Some(root) = plan else {
        return ExecutionReport {
            outcome: ExecutionOutcome::default(),
            error: None,
        };
    };

    if let Err(e) = ops
        .create_dir_all(work_dir)
        .with_context(|| format!("create mount work dir {}", work_dir.display()))
    {
        return ExecutionReport {
            outcome: ExecutionOutcome::default(),
            error: Some(e),
        };
    }
    if let Err(e) = ops
        .mount_tmpfs(fs_name, work_dir)
        .with_context(|| format!("mount tmpfs on {}", work_dir.display()))
    {
        // The tmpfs never appeared; there is nothing to undo.
        return ExecutionReport {
            outcome: ExecutionOutcome::default(),
            error: Some(e),
        };
    }
    if let Err(e) = ops
        .make_private(work_dir)
        .with_context(|| format!("make {} private", work_dir.display()))
    {
        // The tmpfs is mounted but everything else was skipped; detach it.
        let residue = if ops.unmount_detach(work_dir).is_err() {
            vec![work_dir.to_path_buf()]
        } else {
            Vec::new()
        };
        return ExecutionReport {
            outcome: ExecutionOutcome {
                partial: !residue.is_empty(),
                residue,
                ..ExecutionOutcome::default()
            },
            error: Some(e),
        };
    }

    let mut tx = MountTransaction::new(ops);
    if let Err(e) = tx.mount_node(target_root, work_dir, root, false) {
        let rollback = tx.rollback(work_dir);
        return ExecutionReport {
            outcome: ExecutionOutcome {
                target_count: rollback.remaining_mounts,
                skipped_count: tx.skipped,
                partial: !rollback.residue.is_empty(),
                residue: rollback.residue,
            },
            error: Some(e),
        };
    }

    if let Err(e) = tx.register_targets() {
        let rollback = tx.rollback(work_dir);
        return ExecutionReport {
            outcome: ExecutionOutcome {
                target_count: rollback.remaining_mounts,
                skipped_count: tx.skipped,
                partial: !rollback.residue.is_empty(),
                residue: rollback.residue,
            },
            error: Some(e),
        };
    }

    let target_count = tx.mounts.len();
    let skipped_count = tx.skipped;
    let residue = tx.cleanup_staging(work_dir);
    let partial = !residue.is_empty();
    if target_count > 0
        && let Err(e) = ops.report_mounted()
    {
        warn!("folk mount: failed to notify module mount: {e:#}");
    }
    ExecutionReport {
        outcome: ExecutionOutcome {
            target_count,
            skipped_count,
            partial,
            residue,
        },
        error: None,
    }
}

#[cfg(test)]
#[path = "magic_mount_tests.rs"]
mod tests;
