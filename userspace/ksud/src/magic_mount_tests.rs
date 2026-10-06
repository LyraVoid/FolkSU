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

fn plan_for(modules: &Path, root: &Path, opaque: impl Fn(&Path) -> Result<bool>) -> Option<Node> {
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

// ---------------------------------------------------------------------------
// Phase 2 executor tests
//
// These drive `run_mount` against a real (temporary) target/work directory with
// an instrumented `MountOps` that records mount operations and can inject
// failures. That exercises whiteout/opaque/type-conflict handling and the
// rollback ordering without root or a private mount namespace.
// ---------------------------------------------------------------------------

use std::cell::{Cell, RefCell};
use std::collections::BTreeMap;
use std::path::PathBuf;

/// Instrumented [`MountOps`]: filesystem calls go to the real temporary tree,
/// mount/metadata mutations are recorded and can be made to fail.
struct FakeOps {
    bound: RefCell<Vec<(PathBuf, PathBuf)>>,
    moved: RefCell<Vec<(PathBuf, PathBuf)>>,
    tmpfs: RefCell<Vec<(String, PathBuf)>>,
    privated: RefCell<Vec<PathBuf>>,
    detached: RefCell<Vec<PathBuf>>,
    /// Mirror of the kernel's unmount list, pre-seeded with external entries.
    kernel_registrations: RefCell<Vec<String>>,
    registered: RefCell<Vec<PathBuf>>,
    unregistered: RefCell<Vec<PathBuf>>,
    reported: Cell<bool>,
    fail_bind_at: Cell<Option<usize>>,
    fail_private_at: Cell<Option<usize>>,
    fail_move_at: Cell<Option<usize>>,
    fail_register_at: Cell<Option<usize>>,
    fail_unmount_at: Cell<Option<usize>>,
    fail_tmpfs_at: Cell<Option<usize>>,
    bind_count: Cell<usize>,
    private_count: Cell<usize>,
    move_count: Cell<usize>,
    register_count: Cell<usize>,
    unmount_count: Cell<usize>,
    tmpfs_count: Cell<usize>,
}

impl FakeOps {
    fn new() -> Self {
        Self {
            bound: RefCell::new(Vec::new()),
            moved: RefCell::new(Vec::new()),
            tmpfs: RefCell::new(Vec::new()),
            privated: RefCell::new(Vec::new()),
            detached: RefCell::new(Vec::new()),
            kernel_registrations: RefCell::new(Vec::new()),
            registered: RefCell::new(Vec::new()),
            unregistered: RefCell::new(Vec::new()),
            reported: Cell::new(false),
            fail_bind_at: Cell::new(None),
            fail_private_at: Cell::new(None),
            fail_move_at: Cell::new(None),
            fail_register_at: Cell::new(None),
            fail_unmount_at: Cell::new(None),
            fail_tmpfs_at: Cell::new(None),
            bind_count: Cell::new(0),
            private_count: Cell::new(0),
            move_count: Cell::new(0),
            register_count: Cell::new(0),
            unmount_count: Cell::new(0),
            tmpfs_count: Cell::new(0),
        }
    }

    fn bound_sources(&self) -> Vec<PathBuf> {
        self.bound
            .borrow()
            .iter()
            .map(|(src, _)| src.clone())
            .collect()
    }
}

/// Return true when the next invocation of a counter matches its failure slot.
fn should_fail(counter: &Cell<usize>, fail_at: &Cell<Option<usize>>) -> bool {
    let next = counter.get() + 1;
    counter.set(next);
    fail_at.get() == Some(next)
}

impl MountOps for FakeOps {
    fn read_dir(&self, path: &Path) -> Result<Vec<RealDirEntry>> {
        let mut out = Vec::new();
        for entry in std::fs::read_dir(path)? {
            let entry = entry?;
            let kind = entry.file_type()?;
            let file_type = if kind.is_file() {
                NodeFileType::RegularFile
            } else if kind.is_dir() {
                NodeFileType::Directory
            } else if kind.is_symlink() {
                NodeFileType::Symlink
            } else {
                continue;
            };
            out.push(RealDirEntry {
                name: entry.file_name().to_string_lossy().into_owned(),
                file_type,
            });
        }
        Ok(out)
    }

    fn symlink_metadata(&self, path: &Path) -> Result<EntryMeta> {
        Ok(entry_meta(&std::fs::symlink_metadata(path)?))
    }

    fn metadata(&self, path: &Path) -> Result<FileMeta> {
        use std::os::unix::fs::MetadataExt;
        let meta = std::fs::metadata(path)?;
        Ok(FileMeta {
            mode: meta.mode(),
            uid: meta.uid(),
            gid: meta.gid(),
        })
    }

    fn read_link(&self, path: &Path) -> Result<PathBuf> {
        Ok(std::fs::read_link(path)?)
    }

    fn exists(&self, path: &Path) -> bool {
        path.exists()
    }

    fn is_dir(&self, path: &Path) -> bool {
        path.is_dir()
    }

    fn create_dir_all(&self, path: &Path) -> Result<()> {
        std::fs::create_dir_all(path)?;
        Ok(())
    }

    fn create_file(&self, path: &Path) -> Result<()> {
        std::fs::File::create(path)?;
        Ok(())
    }

    fn symlink(&self, target: &Path, link: &Path) -> Result<()> {
        std::os::unix::fs::symlink(target, link)?;
        Ok(())
    }

    fn chmod(&self, _path: &Path, _mode: u32) -> Result<()> {
        Ok(())
    }

    fn chown(&self, _path: &Path, _uid: u32, _gid: u32) -> Result<()> {
        Ok(())
    }

    fn lgetfilecon(&self, _path: &Path) -> Result<String> {
        Ok("u:object_r:system_file:s0".to_string())
    }

    fn lsetfilecon(&self, _path: &Path, _con: &str) -> Result<()> {
        Ok(())
    }

    fn mount_tmpfs(&self, name: &str, target: &Path) -> Result<()> {
        let fail = should_fail(&self.tmpfs_count, &self.fail_tmpfs_at);
        self.tmpfs
            .borrow_mut()
            .push((name.to_string(), target.to_path_buf()));
        if fail {
            anyhow::bail!("injected tmpfs failure");
        }
        Ok(())
    }

    fn mount_bind(&self, source: &Path, target: &Path) -> Result<()> {
        let fail = should_fail(&self.bind_count, &self.fail_bind_at);
        self.bound
            .borrow_mut()
            .push((source.to_path_buf(), target.to_path_buf()));
        if fail {
            anyhow::bail!("injected bind failure");
        }
        Ok(())
    }

    fn mount_move(&self, source: &Path, target: &Path) -> Result<()> {
        let fail = should_fail(&self.move_count, &self.fail_move_at);
        self.moved
            .borrow_mut()
            .push((source.to_path_buf(), target.to_path_buf()));
        if fail {
            anyhow::bail!("injected move failure");
        }
        Ok(())
    }

    fn make_private(&self, path: &Path) -> Result<()> {
        let fail = should_fail(&self.private_count, &self.fail_private_at);
        self.privated.borrow_mut().push(path.to_path_buf());
        if fail {
            anyhow::bail!("injected private failure");
        }
        Ok(())
    }

    fn unmount_detach(&self, path: &Path) -> Result<()> {
        let fail = should_fail(&self.unmount_count, &self.fail_unmount_at);
        self.detached.borrow_mut().push(path.to_path_buf());
        if fail {
            anyhow::bail!("injected unmount failure");
        }
        Ok(())
    }

    fn register_umount(&self, path: &Path) -> Result<()> {
        let as_string = path.to_string_lossy().into_owned();
        if self
            .kernel_registrations
            .borrow()
            .iter()
            .any(|existing| existing == &as_string)
        {
            anyhow::bail!("EEXIST: {as_string} is already registered");
        }
        if should_fail(&self.register_count, &self.fail_register_at) {
            anyhow::bail!("injected register failure");
        }
        self.kernel_registrations.borrow_mut().push(as_string);
        self.registered.borrow_mut().push(path.to_path_buf());
        Ok(())
    }

    fn unregister_umount(&self, path: &Path) -> Result<()> {
        let as_string = path.to_string_lossy().into_owned();
        self.kernel_registrations
            .borrow_mut()
            .retain(|existing| existing != &as_string);
        self.unregistered.borrow_mut().push(path.to_path_buf());
        Ok(())
    }

    fn report_mounted(&self) -> Result<()> {
        self.reported.set(true);
        Ok(())
    }
}

fn node(name: &str, file_type: NodeFileType, module: Option<&Path>) -> Node {
    Node {
        name: name.to_string(),
        file_type,
        children: BTreeMap::new(),
        module_path: module.map(Path::to_path_buf),
        replace: false,
        skip: false,
    }
}

fn insert(parent: &mut Node, child: Node) {
    parent.children.insert(child.name.clone(), child);
}

/// Build `system/etc/...` module trees rooted at a synthetic root.
fn root_with(system_children: Vec<Node>) -> Node {
    let mut system = node("system", NodeFileType::Directory, None);
    for child in system_children {
        insert(&mut system, child);
    }
    let mut root = node("", NodeFileType::Directory, None);
    insert(&mut root, system);
    root
}

#[test]
fn whiteout_excludes_real_file_from_mirror() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("keep.txt"), "keep").unwrap();
    fs::write(etc_real.join("remove-me.txt"), "gone").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(
        &mut etc,
        node(
            "remove-me.txt",
            NodeFileType::Whiteout,
            Some(&module_etc.join("remove-me.txt")),
        ),
    );

    let ops = FakeOps::new();
    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_none(), "{:?}", report.error);
    assert_eq!(report.outcome.target_count, 1);
    assert!(!report.outcome.partial);

    let sources = ops.bound_sources();
    assert!(sources.contains(&etc_real.join("keep.txt")), "{sources:?}");
    assert!(
        !sources.iter().any(|path| path.ends_with("remove-me.txt")),
        "whiteout must not be mirrored: {sources:?}"
    );
    assert!(ops.moved.borrow().iter().any(|(_, to)| to == &etc_real));
    assert!(ops.reported.get());
}

#[test]
fn dir_over_file_conflict_uses_tmpfs_without_error() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("conflict"), "real-file").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    let module_conflict = module_etc.join("conflict");
    fs::create_dir_all(&module_conflict).unwrap();
    fs::write(module_conflict.join("child"), "module").unwrap();

    let mut conflict = node("conflict", NodeFileType::Directory, Some(&module_conflict));
    insert(
        &mut conflict,
        node(
            "child",
            NodeFileType::RegularFile,
            Some(&module_conflict.join("child")),
        ),
    );
    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, conflict);

    let ops = FakeOps::new();
    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_none(), "{:?}", report.error);
    assert!(!report.outcome.partial);
    assert_eq!(report.outcome.target_count, 1);
    assert!(
        ops.bound_sources().contains(&module_conflict.join("child")),
        "{:?}",
        ops.bound_sources()
    );
    assert!(ops.moved.borrow().iter().any(|(_, to)| to == &etc_real));
}

#[test]
fn file_over_dir_conflict_uses_tmpfs_without_error() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    let conflict_real = etc_real.join("conflict");
    fs::create_dir_all(&conflict_real).unwrap();
    fs::write(conflict_real.join("old.txt"), "old").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let module_conflict = module_etc.join("conflict");
    fs::write(&module_conflict, "module-file").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(
        &mut etc,
        node(
            "conflict",
            NodeFileType::RegularFile,
            Some(&module_conflict),
        ),
    );

    let ops = FakeOps::new();
    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_none(), "{:?}", report.error);
    assert!(
        ops.bound_sources().contains(&module_conflict),
        "{:?}",
        ops.bound_sources()
    );
    assert!(
        !ops.bound_sources()
            .iter()
            .any(|path| path.ends_with("old.txt")),
        "the replaced real directory must not be mirrored"
    );
    assert!(ops.moved.borrow().iter().any(|(_, to)| to == &etc_real));
}

#[test]
fn opaque_directory_replaces_real_contents() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("old.txt"), "old").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let module_new = module_etc.join("new.txt");
    fs::write(&module_new, "new").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    etc.replace = true;
    insert(
        &mut etc,
        node("new.txt", NodeFileType::RegularFile, Some(&module_new)),
    );

    let ops = FakeOps::new();
    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_none(), "{:?}", report.error);
    assert_eq!(report.outcome.target_count, 1);
    let sources = ops.bound_sources();
    assert!(sources.contains(&module_new), "{sources:?}");
    assert!(
        !sources.iter().any(|path| path.ends_with("old.txt")),
        "opaque directory must drop the real contents: {sources:?}"
    );
    assert!(ops.moved.borrow().iter().any(|(_, to)| to == &etc_real));
}

#[test]
fn registration_failure_rolls_back_in_reverse_and_keeps_external() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("a"), "real-a").unwrap();
    fs::write(etc_real.join("b"), "real-b").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let mod_a = module_etc.join("a");
    let mod_b = module_etc.join("b");
    fs::write(&mod_a, "mod-a").unwrap();
    fs::write(&mod_b, "mod-b").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, node("a", NodeFileType::RegularFile, Some(&mod_a)));
    insert(&mut etc, node("b", NodeFileType::RegularFile, Some(&mod_b)));

    let ops = FakeOps::new();
    ops.kernel_registrations
        .borrow_mut()
        .push(etc_real.to_string_lossy().into_owned());
    ops.fail_register_at.set(Some(2));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_some());
    assert_eq!(report.outcome.target_count, 0);
    assert!(!report.outcome.partial);
    assert!(!ops.reported.get());

    let detached = ops.detached.borrow().clone();
    assert_eq!(
        detached,
        vec![
            etc_real.join("b"),
            etc_real.join("a"),
            work.path().to_path_buf()
        ],
        "mounts must be detached in reverse creation order"
    );
    assert_eq!(ops.registered.borrow().clone(), vec![etc_real.join("a")]);
    assert_eq!(ops.unregistered.borrow().clone(), vec![etc_real.join("a")]);
    // Our own successful registration was removed; the external one survived.
    assert_eq!(
        ops.kernel_registrations.borrow().clone(),
        vec![etc_real.to_string_lossy().into_owned()]
    );
}

#[test]
fn bind_failure_rolls_back_published_mounts() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("a"), "real-a").unwrap();
    fs::write(etc_real.join("b"), "real-b").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let mod_a = module_etc.join("a");
    let mod_b = module_etc.join("b");
    fs::write(&mod_a, "mod-a").unwrap();
    fs::write(&mod_b, "mod-b").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, node("a", NodeFileType::RegularFile, Some(&mod_a)));
    insert(&mut etc, node("b", NodeFileType::RegularFile, Some(&mod_b)));

    let ops = FakeOps::new();
    ops.fail_bind_at.set(Some(2));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_some());
    assert_eq!(report.outcome.target_count, 0);
    assert_eq!(
        ops.detached.borrow().clone(),
        vec![etc_real.join("a"), work.path().to_path_buf()]
    );
}

#[test]
fn private_failure_rolls_back_published_mount() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("a"), "real-a").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let mod_a = module_etc.join("a");
    fs::write(&mod_a, "mod-a").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, node("a", NodeFileType::RegularFile, Some(&mod_a)));

    let ops = FakeOps::new();
    // 1 = work dir PRIVATE, 2 = the direct file bind's PRIVATE.
    ops.fail_private_at.set(Some(2));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_some());
    assert_eq!(report.outcome.target_count, 0);
    assert_eq!(
        ops.detached.borrow().clone(),
        vec![etc_real.join("a"), work.path().to_path_buf()]
    );
}

#[test]
fn move_failure_rolls_back_staging_without_publication() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("old.txt"), "old").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let module_new = module_etc.join("new.txt");
    fs::write(&module_new, "new").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    etc.replace = true;
    insert(
        &mut etc,
        node("new.txt", NodeFileType::RegularFile, Some(&module_new)),
    );

    let ops = FakeOps::new();
    ops.fail_move_at.set(Some(1));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_some());
    assert_eq!(report.outcome.target_count, 0);
    assert_eq!(
        ops.detached.borrow().clone(),
        vec![work.path().to_path_buf()],
        "only the staging tmpfs is detached; nothing was published"
    );
}

#[test]
fn cleanup_failure_is_partial_but_keeps_targets() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("a"), "real-a").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let mod_a = module_etc.join("a");
    fs::write(&mod_a, "mod-a").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, node("a", NodeFileType::RegularFile, Some(&mod_a)));

    let ops = FakeOps::new();
    ops.fail_unmount_at.set(Some(1));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_none());
    assert!(report.outcome.partial);
    assert_eq!(report.outcome.target_count, 1);
    assert_eq!(report.outcome.residue, vec![work.path().to_path_buf()]);
    assert!(ops.reported.get());
    assert_eq!(ops.registered.borrow().clone(), vec![etc_real.join("a")]);
}

#[test]
fn empty_plan_does_not_mount_or_notify() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let ops = FakeOps::new();

    let report = run_mount(None, target.path(), work.path(), "FolkMount", &ops);
    assert!(report.error.is_none());
    assert_eq!(report.outcome.target_count, 0);
    assert!(!report.outcome.partial);
    assert!(ops.tmpfs.borrow().is_empty());
    assert!(ops.bound.borrow().is_empty());
    assert!(!ops.reported.get());
}

#[test]
fn unoverlayable_root_child_is_skipped_and_not_reported() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    // The real /vendor is a symlink, so a directory node cannot be overlaid at
    // the synthetic root (which has no module source) and must be skipped.
    let elsewhere = tempdir().unwrap();
    symlink(elsewhere.path(), target.path().join("vendor")).unwrap();

    let module = tempdir().unwrap();
    let module_vendor = module.path().join("vendor");
    fs::create_dir_all(&module_vendor).unwrap();
    fs::write(module_vendor.join("v"), "v").unwrap();

    let mut vendor = node("vendor", NodeFileType::Directory, Some(&module_vendor));
    insert(
        &mut vendor,
        node(
            "v",
            NodeFileType::RegularFile,
            Some(&module_vendor.join("v")),
        ),
    );
    let mut root = node("", NodeFileType::Directory, None);
    insert(&mut root, vendor);

    let ops = FakeOps::new();
    let report = run_mount(Some(root), target.path(), work.path(), "FolkMount", &ops);
    assert!(report.error.is_none());
    assert_eq!(report.outcome.target_count, 0);
    assert_eq!(report.outcome.skipped_count, 1);
    assert!(!ops.reported.get());
    assert!(ops.bound.borrow().is_empty());
}

#[test]
fn work_dir_private_failure_detaches_staging() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("a"), "real-a").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let mod_a = module_etc.join("a");
    fs::write(&mod_a, "mod-a").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, node("a", NodeFileType::RegularFile, Some(&mod_a)));

    let ops = FakeOps::new();
    // The very first PRIVATE is the work-dir mount, before any binding.
    ops.fail_private_at.set(Some(1));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_some());
    assert_eq!(report.outcome.target_count, 0);
    assert!(!report.outcome.partial);
    assert_eq!(
        ops.detached.borrow().clone(),
        vec![work.path().to_path_buf()]
    );
}

#[test]
fn tmpfs_mount_failure_reports_no_residue() {
    let target = tempdir().unwrap();
    let work = tempdir().unwrap();
    let etc_real = target.path().join("system/etc");
    fs::create_dir_all(&etc_real).unwrap();
    fs::write(etc_real.join("a"), "real-a").unwrap();

    let module = tempdir().unwrap();
    let module_etc = module.path().join("etc");
    fs::create_dir_all(&module_etc).unwrap();
    let mod_a = module_etc.join("a");
    fs::write(&mod_a, "mod-a").unwrap();

    let mut etc = node("etc", NodeFileType::Directory, Some(&module_etc));
    insert(&mut etc, node("a", NodeFileType::RegularFile, Some(&mod_a)));

    let ops = FakeOps::new();
    ops.fail_tmpfs_at.set(Some(1));

    let report = run_mount(
        Some(root_with(vec![etc])),
        target.path(),
        work.path(),
        "FolkMount",
        &ops,
    );
    assert!(report.error.is_some());
    assert_eq!(report.outcome.target_count, 0);
    assert!(!report.outcome.partial);
    assert!(report.outcome.residue.is_empty());
    assert!(
        ops.detached.borrow().is_empty(),
        "a tmpfs that never mounted must not be detached"
    );
}
