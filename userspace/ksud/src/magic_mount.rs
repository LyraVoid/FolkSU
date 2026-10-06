//! Folk Mount: built-in module mounting.
//!
//! Phase 1 provided the configuration file, the `auto | builtin | metamodule`
//! provider decision and the `ksud mount status` / `set-mode` protocol. Phase 2
//! adds the tmpfs+bind executor (delegated to the host-testable planner in
//! `mount_plan`), metadata/SELinux cloning, transaction rollback, unmount
//! registration and the per-boot runtime record.

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use log::{info, warn};
use rustix::fs::{FlockOperation, Gid, MetadataExt, Mode, Uid, chmod, chown, flock};
use rustix::mount::{
    MountFlags, MountPropagationFlags, UnmountFlags, mount, mount_bind, mount_change, mount_move,
    unmount,
};
use serde_json::json;

use crate::defs;
use crate::mount_plan::{self, FileMeta, MountOps, Node, NodeFileType, RealDirEntry};

/// User-visible mount mode stored in [`defs::FOLK_MOUNT_CONFIG`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MountMode {
    Auto,
    Builtin,
    Metamodule,
}

impl MountMode {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::Builtin => "builtin",
            Self::Metamodule => "metamodule",
        }
    }

    /// Parse a config/CLI token; surrounding whitespace is ignored.
    #[must_use]
    pub fn parse(value: &str) -> Option<Self> {
        match value.trim() {
            "auto" => Some(Self::Auto),
            "builtin" => Some(Self::Builtin),
            "metamodule" => Some(Self::Metamodule),
            _ => None,
        }
    }
}

/// The provider that performs the next boot's module mounting.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Provider {
    Builtin,
    Metamodule,
}

impl Provider {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Builtin => "builtin",
            Self::Metamodule => "metamodule",
        }
    }
}

/// Result of a mount attempt.
#[must_use]
#[derive(Debug, Clone)]
pub struct MountOutcome {
    pub provider: Provider,
    /// Final, published targets (direct binds and moved tmpfs roots).
    pub target_count: usize,
    /// Nodes the planner marked unoverlayable and the executor skipped.
    pub skipped_count: usize,
    /// True when rollback or staging cleanup could not undo every mount.
    pub partial: bool,
    /// Mounts that remain because they could not be undone.
    pub residue: Vec<PathBuf>,
    /// True when the provider ran but published nothing (or had no script).
    pub noop: bool,
}

impl MountOutcome {
    pub const fn empty(provider: Provider) -> Self {
        Self {
            provider,
            target_count: 0,
            skipped_count: 0,
            partial: false,
            residue: Vec::new(),
            noop: true,
        }
    }

    fn failed(provider: Provider) -> Self {
        Self {
            noop: false,
            ..Self::empty(provider)
        }
    }
}

/// Error carrying the partial outcome of a built-in mount that failed.
#[derive(Debug)]
struct MountFailure {
    outcome: MountOutcome,
    source: anyhow::Error,
}

impl std::fmt::Display for MountFailure {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:#}", self.source)
    }
}

impl std::error::Error for MountFailure {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        Some(self.source.root_cause())
    }
}

/// Per-boot runtime record used by `ksud mount status` and reentrancy checks.
#[derive(Debug, Clone, Default)]
struct RuntimeRecord {
    boot_id: String,
    provider: Option<String>,
    result: String,
    target_count: Option<usize>,
    error: Option<String>,
}

/// Read the configured mount mode from [`defs::FOLK_MOUNT_CONFIG`].
///
/// A missing file or an unrecognised value falls back to [`MountMode::Auto`]
/// (the latter with a warning). Other I/O errors are returned so an unreadable
/// configuration never silently selects a provider.
///
/// # Errors
/// Returns an error when the configuration file exists but cannot be read.
pub fn read_mount_mode() -> Result<MountMode> {
    let path = Path::new(defs::FOLK_MOUNT_CONFIG);
    match fs::read_to_string(path) {
        Ok(content) => Ok(MountMode::parse(&content).unwrap_or_else(|| {
            warn!(
                "invalid mount mode {:?} in {}, falling back to auto",
                content.trim(),
                defs::FOLK_MOUNT_CONFIG
            );
            MountMode::Auto
        })),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(MountMode::Auto),
        Err(e) => Err(e).with_context(|| format!("failed to read {}", defs::FOLK_MOUNT_CONFIG)),
    }
}

/// Write `contents` to `path` atomically: temp file in the same directory,
/// fsync, rename, then fsync the directory.
fn write_atomic(path: &Path, contents: &[u8]) -> Result<()> {
    let dir = path
        .parent()
        .context("atomic write target has no parent directory")?;
    fs::create_dir_all(dir).with_context(|| format!("failed to create {}", dir.display()))?;

    let mut tmp = tempfile::Builder::new()
        .prefix(".folk_mount.")
        .tempfile_in(dir)
        .with_context(|| format!("failed to create temp file in {}", dir.display()))?;
    tmp.write_all(contents)
        .context("failed to write temporary file")?;
    tmp.as_file()
        .sync_all()
        .context("failed to sync temporary file")?;
    tmp.persist(path)
        .with_context(|| format!("failed to persist {}", path.display()))?;

    fs::File::open(dir)
        .and_then(|handle| handle.sync_all())
        .with_context(|| format!("failed to sync {}", dir.display()))?;
    Ok(())
}

/// Atomically persist `mode` to [`defs::FOLK_MOUNT_CONFIG`].
///
/// This only affects the next boot; it never triggers a mount or unmount.
///
/// # Errors
/// Returns an error when the directory cannot be created or the file cannot be
/// written, synced and renamed.
pub fn write_mount_mode(mode: MountMode) -> Result<()> {
    write_atomic(
        Path::new(defs::FOLK_MOUNT_CONFIG),
        format!("{}\n", mode.as_str()).as_bytes(),
    )
}

/// Return `(id, enabled)` for the installed metamodule, if any.
///
/// A metamodule marked with `disable` or `remove` is reported but disabled, so
/// `auto` does not select a provider that would be a no-op.
fn metamodule_state() -> (Option<String>, bool) {
    let Some(path) = crate::metamodule::get_metamodule_path() else {
        return (None, false);
    };
    let enabled =
        !path.join(defs::DISABLE_FILE_NAME).exists() && !path.join(defs::REMOVE_FILE_NAME).exists();
    let id = path
        .file_name()
        .and_then(|name| name.to_str())
        .map(ToString::to_string);
    (id, enabled)
}

const fn provider_for(mode: MountMode, metamodule_enabled: bool) -> Provider {
    match mode {
        MountMode::Metamodule => Provider::Metamodule,
        MountMode::Auto if metamodule_enabled => Provider::Metamodule,
        MountMode::Builtin | MountMode::Auto => Provider::Builtin,
    }
}

/// Resolve which provider the next boot should use.
///
/// In `auto` mode an enabled metamodule wins; a disabled or removal-marked
/// metamodule is ignored so Folk Mount can take over. An enabled metamodule
/// without a `metamount.sh` is still selected (the script is a no-op) so the
/// provider never silently switches away from the user's metamodule. `builtin`
/// and `metamodule` force their provider.
///
/// # Errors
/// Returns an error when the configuration file exists but cannot be read.
pub fn resolve_provider() -> Result<Provider> {
    let mode = read_mount_mode()?;
    let (_, metamodule_enabled) = metamodule_state();
    Ok(provider_for(mode, metamodule_enabled))
}

// ---------------------------------------------------------------------------
// Runtime record
// ---------------------------------------------------------------------------

fn current_boot_id() -> Option<String> {
    fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .ok()
        .map(|value| value.trim().to_string())
}

fn sanitize(value: &str) -> String {
    value.replace(['\n', '\r'], " ")
}

fn write_runtime(record: &RuntimeRecord) -> Result<()> {
    let contents = format!(
        "boot_id={}\nprovider={}\nresult={}\ntarget_count={}\nerror={}\n",
        record.boot_id,
        record.provider.as_deref().unwrap_or(""),
        record.result,
        record
            .target_count
            .map_or_else(String::new, |count| count.to_string()),
        record.error.as_deref().map_or_else(String::new, sanitize),
    );
    write_atomic(Path::new(defs::FOLK_MOUNT_RUNTIME), contents.as_bytes())
}

fn read_runtime() -> Option<RuntimeRecord> {
    let content = fs::read_to_string(defs::FOLK_MOUNT_RUNTIME).ok()?;
    let mut record = RuntimeRecord::default();
    for line in content.lines() {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        match key {
            "boot_id" => record.boot_id = value.to_string(),
            "provider" => record.provider = (!value.is_empty()).then(|| value.to_string()),
            "result" => record.result = value.to_string(),
            "target_count" => record.target_count = value.parse().ok(),
            "error" => record.error = (!value.is_empty()).then(|| value.to_string()),
            _ => {}
        }
    }
    Some(record)
}

/// Best-effort record that this boot did not mount modules.
fn record_skipped() {
    let boot_id = current_boot_id().unwrap_or_default();
    let record = RuntimeRecord {
        boot_id,
        provider: None,
        result: "skipped".to_string(),
        target_count: None,
        error: None,
    };
    if let Err(e) = write_runtime(&record) {
        warn!("folk mount: failed to record skipped state: {e:#}");
    }
}

/// Refuse a second run in the same boot and diagnose a `mounting` residue
/// without trying to clean unknown old mounts.
fn guard_reentrancy(boot_id: &str, provider: Provider) -> Result<()> {
    let Some(record) = read_runtime() else {
        return Ok(());
    };
    if boot_id.is_empty() || record.boot_id != boot_id {
        return Ok(());
    }
    if record.result == "mounting" {
        bail!(
            "Folk Mount left a 'mounting' record for boot {boot_id}; refusing to mount again. \
             Diagnose the residue manually (work dir / umount list) before rebooting"
        );
    }
    bail!(
        "Folk Mount already ran this boot (provider={provider}); refusing to mount again",
        provider = provider.as_str()
    );
}

/// Hold an exclusive lock on [`defs::FOLK_MOUNT_LOCK`] for the duration of one
/// mount attempt, preventing concurrent runs.
struct MountLock {
    _file: fs::File,
}

impl MountLock {
    fn acquire() -> Result<Self> {
        fs::create_dir_all(defs::WORKING_DIR)
            .with_context(|| format!("failed to create {}", defs::WORKING_DIR))?;
        let file = fs::OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .open(defs::FOLK_MOUNT_LOCK)
            .with_context(|| format!("failed to open {}", defs::FOLK_MOUNT_LOCK))?;
        flock(&file, FlockOperation::NonBlockingLockExclusive)
            .context("another Folk Mount attempt is already running")?;
        Ok(Self { _file: file })
    }
}

// ---------------------------------------------------------------------------
// Android executor
// ---------------------------------------------------------------------------

/// Executor primitives backed by std/std::fs, rustix, extattr and ksucalls.
struct AndroidOps;

fn read_opaque(path: &Path) -> Result<bool> {
    const REPLACE_DIR_XATTR: &str = "trusted.overlay.opaque";
    match extattr::lgetxattr(path, REPLACE_DIR_XATTR) {
        Ok(value) => Ok(String::from_utf8_lossy(&value) == "y"),
        // `extattr` reports `errno::Errno`; ENODATA/ENOATTR means "absent".
        Err(e) if e.0 == libc::ENODATA => Ok(false),
        Err(e) => Err(e).with_context(|| format!("read {REPLACE_DIR_XATTR} of {}", path.display())),
    }
}

impl MountOps for AndroidOps {
    fn read_dir(&self, path: &Path) -> Result<Vec<RealDirEntry>> {
        let mut entries = Vec::new();
        for entry in fs::read_dir(path).with_context(|| format!("read dir {}", path.display()))? {
            let entry = entry.with_context(|| format!("read entry in {}", path.display()))?;
            let meta = entry
                .metadata()
                .with_context(|| format!("stat {}", entry.path().display()))?;
            if let Some(file_type) = NodeFileType::from_entry_meta(mount_plan::entry_meta(&meta)) {
                entries.push(RealDirEntry {
                    name: entry.file_name().to_string_lossy().into_owned(),
                    file_type,
                });
            } else {
                warn!(
                    "folk mount: ignoring unsupported entry {}",
                    entry.path().display()
                );
            }
        }
        Ok(entries)
    }

    fn symlink_metadata(&self, path: &Path) -> Result<mount_plan::EntryMeta> {
        let meta =
            fs::symlink_metadata(path).with_context(|| format!("stat {}", path.display()))?;
        Ok(mount_plan::entry_meta(&meta))
    }

    fn metadata(&self, path: &Path) -> Result<FileMeta> {
        let meta = fs::metadata(path).with_context(|| format!("stat {}", path.display()))?;
        Ok(FileMeta {
            mode: meta.mode(),
            uid: meta.uid(),
            gid: meta.gid(),
        })
    }

    fn read_link(&self, path: &Path) -> Result<PathBuf> {
        fs::read_link(path).with_context(|| format!("read link {}", path.display()))
    }

    fn exists(&self, path: &Path) -> bool {
        path.exists()
    }

    fn is_dir(&self, path: &Path) -> bool {
        path.is_dir()
    }

    fn create_dir_all(&self, path: &Path) -> Result<()> {
        fs::create_dir_all(path).with_context(|| format!("create dir {}", path.display()))
    }

    fn create_file(&self, path: &Path) -> Result<()> {
        fs::File::create(path).with_context(|| format!("create file {}", path.display()))?;
        Ok(())
    }

    fn symlink(&self, target: &Path, link: &Path) -> Result<()> {
        std::os::unix::fs::symlink(target, link)
            .with_context(|| format!("symlink {} -> {}", link.display(), target.display()))
    }

    fn chmod(&self, path: &Path, mode: u32) -> Result<()> {
        chmod(path, Mode::from_raw_mode(mode)).with_context(|| format!("chmod {}", path.display()))
    }

    fn chown(&self, path: &Path, uid: u32, gid: u32) -> Result<()> {
        chown(path, Some(Uid::from_raw(uid)), Some(Gid::from_raw(gid)))
            .with_context(|| format!("chown {}", path.display()))
    }

    fn lgetfilecon(&self, path: &Path) -> Result<String> {
        crate::restorecon::lgetfilecon(path)
    }

    fn lsetfilecon(&self, path: &Path, con: &str) -> Result<()> {
        crate::restorecon::lsetfilecon(path, con)
    }

    fn mount_tmpfs(&self, name: &str, target: &Path) -> Result<()> {
        mount(name, target, "tmpfs", MountFlags::empty(), None)
            .with_context(|| format!("mount tmpfs on {}", target.display()))
    }

    fn mount_bind(&self, source: &Path, target: &Path) -> Result<()> {
        mount_bind(source, target)
            .with_context(|| format!("bind {} -> {}", source.display(), target.display()))
    }

    fn mount_move(&self, source: &Path, target: &Path) -> Result<()> {
        mount_move(source, target)
            .with_context(|| format!("move {} -> {}", source.display(), target.display()))
    }

    fn make_private(&self, path: &Path) -> Result<()> {
        // Recursive on purpose: privatise the whole subtree so every bind clone
        // we created below `path` leaves its source mount's peer group as well.
        mount_change(
            path,
            MountPropagationFlags::PRIVATE | MountPropagationFlags::REC,
        )
        .with_context(|| format!("make {} private", path.display()))
    }

    fn unmount_detach(&self, path: &Path) -> Result<()> {
        unmount(path, UnmountFlags::DETACH).with_context(|| format!("detach {}", path.display()))
    }

    fn register_umount(&self, path: &Path) -> Result<()> {
        let path = path
            .to_str()
            .with_context(|| format!("umount target {} is not valid UTF-8", path.display()))?;
        crate::ksucalls::umount_list_add(path, libc::MNT_DETACH as u32)
    }

    fn unregister_umount(&self, path: &Path) -> Result<()> {
        let path = path
            .to_str()
            .with_context(|| format!("umount target {} is not valid UTF-8", path.display()))?;
        crate::ksucalls::umount_list_del(path)
    }

    fn report_mounted(&self) -> Result<()> {
        crate::ksucalls::report_module_mounted();
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Orchestration
// ---------------------------------------------------------------------------

/// Build the merged plan from the real module directory, or `None` when no
/// module contributes any file.
fn collect_plan() -> Result<Option<Node>> {
    let cfg = mount_plan::PlanConfig::new(
        Path::new(defs::MODULE_DIR),
        Path::new("/"),
        Path::new("/system"),
    );
    let fs = mount_plan::StdFs::new(read_opaque as fn(&Path) -> Result<bool>);
    mount_plan::collect_module_files(&fs, &cfg, |path| {
        crate::module::read_module_prop(path)
            .is_ok_and(|props| crate::metamodule::is_metamodule(&props))
    })
}

/// Reject a symlinked or already-mounted work dir and make it root-only.
fn prepare_work_dir(work_dir: &Path) -> Result<()> {
    if work_dir.is_symlink() {
        bail!(
            "Folk Mount work dir {} is a symlink, refusing",
            work_dir.display()
        );
    }
    if is_mount_point(work_dir) {
        bail!(
            "Folk Mount work dir {} is already a mount point, refusing",
            work_dir.display()
        );
    }
    fs::create_dir_all(work_dir)
        .with_context(|| format!("failed to create {}", work_dir.display()))?;
    chmod(work_dir, Mode::from_raw_mode(0o700))
        .with_context(|| format!("chmod {}", work_dir.display()))?;
    chown(work_dir, Some(Uid::from_raw(0)), Some(Gid::from_raw(0)))
        .with_context(|| format!("chown {}", work_dir.display()))?;
    Ok(())
}

fn is_mount_point(path: &Path) -> bool {
    let Ok(canonical) = path.canonicalize() else {
        return false;
    };
    let Ok(mounts) = fs::read_to_string("/proc/self/mounts") else {
        return false;
    };
    mounts.lines().any(|line| {
        let mut fields = line.split_whitespace();
        let _source = fields.next();
        fields
            .next()
            .is_some_and(|mount_point| Path::new(mount_point) == canonical)
    })
}

/// Ensure the process shares PID 1's mount namespace so mounts are visible to
/// the rest of the system. Failing to compare namespaces only warns; failing to
/// switch aborts the mount.
fn ensure_init_mnt_ns() -> Result<()> {
    let self_ns = fs::read_link("/proc/self/ns/mnt");
    let init_ns = fs::read_link("/proc/1/ns/mnt");
    match (self_ns, init_ns) {
        (Ok(current), Ok(init)) if current == init => Ok(()),
        (Ok(_), Ok(_)) => {
            info!("folk mount: switching to PID 1 mount namespace");
            crate::utils::switch_mnt_ns(1)
        }
        _ => {
            warn!("folk mount: cannot compare mount namespaces, continuing");
            Ok(())
        }
    }
}

/// Run the built-in executor and preserve the partial outcome on error.
fn run_builtin() -> (MountOutcome, Option<anyhow::Error>) {
    let plan = match collect_plan() {
        Ok(plan) => plan,
        Err(e) => return (MountOutcome::failed(Provider::Builtin), Some(e)),
    };
    if plan.is_none() {
        info!("folk mount: no modules to mount, skipping");
        return (MountOutcome::empty(Provider::Builtin), None);
    }

    let work_dir = Path::new(defs::FOLK_MOUNT_WORK_DIR);
    if let Err(e) = prepare_work_dir(work_dir) {
        return (MountOutcome::failed(Provider::Builtin), Some(e));
    }

    let report = mount_plan::run_mount(
        plan,
        Path::new("/"),
        work_dir,
        defs::FOLK_MOUNT_FS_NAME,
        &AndroidOps,
    );
    let outcome = MountOutcome {
        provider: Provider::Builtin,
        target_count: report.outcome.target_count,
        skipped_count: report.outcome.skipped_count,
        partial: report.outcome.partial,
        residue: report.outcome.residue,
        noop: report.error.is_none() && report.outcome.target_count == 0,
    };
    (outcome, report.error)
}

/// Run the selected metamodule's mount script. A disabled metamodule or a
/// missing script is a no-op; the provider is never switched to builtin.
fn run_metamodule() -> Result<MountOutcome> {
    let has_script = crate::metamodule::get_metamodule_path()
        .is_some_and(|path| path.join(defs::METAMODULE_MOUNT_SCRIPT).exists());
    crate::metamodule::exec_mount_script(defs::MODULE_DIR)?;
    let mut outcome = MountOutcome::empty(Provider::Metamodule);
    outcome.noop = !has_script;
    Ok(outcome)
}

/// Android built-in executor entry point.
///
/// # Errors
/// Returns an error carrying the partial outcome when any final target bind,
/// move, PRIVATE or unmount registration fails.
pub fn mount_modules() -> Result<MountOutcome> {
    let (outcome, error) = run_builtin();
    match error {
        Some(source) => Err(MountFailure { outcome, source }.into()),
        None => Ok(outcome),
    }
}

enum ProviderRun {
    Ok(MountOutcome),
    Failed {
        outcome: MountOutcome,
        error: anyhow::Error,
    },
}

fn run_provider(provider: Provider) -> ProviderRun {
    match provider {
        Provider::Metamodule => match run_metamodule() {
            Ok(outcome) => ProviderRun::Ok(outcome),
            Err(error) => ProviderRun::Failed {
                outcome: MountOutcome::failed(Provider::Metamodule),
                error,
            },
        },
        Provider::Builtin => match mount_modules() {
            Ok(outcome) => ProviderRun::Ok(outcome),
            Err(error) => {
                let outcome = error.downcast_ref::<MountFailure>().map_or_else(
                    || MountOutcome::failed(Provider::Builtin),
                    |failure| failure.outcome.clone(),
                );
                ProviderRun::Failed { outcome, error }
            }
        },
    }
}

/// Execute the provider selected by [`resolve_provider`].
///
/// Used by both cold boot (`on_post_fs_data`) and late-load. Guards against
/// concurrent runs and a second run in the same boot, validates the UAPI,
/// rejects Magisk/safe mode, and switches to PID 1's mount namespace.
///
/// # Errors
/// Returns an error when the configuration cannot be read, the environment
/// checks fail, or the selected provider fails.
pub fn mount_selected_provider() -> Result<MountOutcome> {
    if crate::utils::has_magisk() {
        warn!("folk mount: Magisk detected, skip module mount");
        record_skipped();
        return Ok(MountOutcome::empty(Provider::Builtin));
    }
    if crate::utils::is_safe_mode() {
        warn!("folk mount: safe mode, skip module mount");
        record_skipped();
        return Ok(MountOutcome::empty(Provider::Builtin));
    }
    if let Err(e) = crate::ksucalls::ensure_uapi_version_matched() {
        warn!("folk mount: {e:#}, skip module mount");
        record_skipped();
        return Ok(MountOutcome::empty(Provider::Builtin));
    }

    let provider = resolve_provider()?;
    ensure_init_mnt_ns()?;

    let boot_id = current_boot_id().unwrap_or_default();
    let _lock = MountLock::acquire()?;
    guard_reentrancy(&boot_id, provider)?;

    write_runtime(&RuntimeRecord {
        boot_id: boot_id.clone(),
        provider: Some(provider.as_str().to_string()),
        result: "mounting".to_string(),
        target_count: None,
        error: None,
    })?;

    match run_provider(provider) {
        ProviderRun::Ok(outcome) => {
            let result = if outcome.noop { "noop" } else { "mounted" };
            log::debug!(
                "folk mount: provider={} targets={} skipped={}",
                outcome.provider.as_str(),
                outcome.target_count,
                outcome.skipped_count
            );
            finish_runtime(
                &boot_id,
                provider,
                result,
                provider == Provider::Builtin,
                outcome.target_count,
                None,
            );
            Ok(outcome)
        }
        ProviderRun::Failed { outcome, error } => {
            let result = if outcome.partial { "partial" } else { "failed" };
            warn!(
                "folk mount: provider={} {} (targets={}, skipped={}, residue={:?}): {error:#}",
                outcome.provider.as_str(),
                result,
                outcome.target_count,
                outcome.skipped_count,
                outcome.residue
            );
            finish_runtime(
                &boot_id,
                provider,
                result,
                provider == Provider::Builtin,
                outcome.target_count,
                Some(format!("{error:#}")),
            );
            Err(error)
        }
    }
}

fn finish_runtime(
    boot_id: &str,
    provider: Provider,
    result: &str,
    known_count: bool,
    target_count: usize,
    error: Option<String>,
) {
    let record = RuntimeRecord {
        boot_id: boot_id.to_string(),
        provider: Some(provider.as_str().to_string()),
        result: result.to_string(),
        target_count: known_count.then_some(target_count),
        error,
    };
    if let Err(e) = write_runtime(&record) {
        warn!("folk mount: failed to record runtime state: {e:#}");
    }
}

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

/// Print the current Folk Mount status.
///
/// By default this emits stable `key=value` lines; with `json` it emits a single
/// JSON object whose `schema_version` is `1`. Only protocol data is written to
/// stdout; diagnostics go to stderr/log.
///
/// # Errors
/// Returns an error when writing to stdout fails.
pub fn print_status(json: bool) -> Result<()> {
    let (metamodule_id, metamodule_enabled) = metamodule_state();
    let (configured_mode, config_error) = match read_mount_mode() {
        Ok(mode) => (Some(mode), None),
        Err(e) => (None, Some(format!("{e:#}"))),
    };
    let next_provider = configured_mode.map(|mode| provider_for(mode, metamodule_enabled));

    let boot_id = current_boot_id();
    let runtime = read_runtime();
    let (boot_id, boot_provider, boot_result, target_count, runtime_error) =
        match (&runtime, &boot_id) {
            (Some(record), Some(current)) if &record.boot_id == current => (
                Some(record.boot_id.clone()),
                record.provider.clone(),
                if record.result.is_empty() {
                    "unknown".to_string()
                } else {
                    record.result.clone()
                },
                record.target_count,
                record.error.clone(),
            ),
            _ => (None, None, "unknown".to_string(), None, None),
        };
    let error = config_error.or(runtime_error);

    let stdout = std::io::stdout();
    let mut out = stdout.lock();
    if json {
        writeln!(
            out,
            "{}",
            json!({
                "schema_version": 1,
                "configured_mode": configured_mode.map(MountMode::as_str),
                "next_provider": next_provider.map(Provider::as_str),
                "metamodule_id": metamodule_id,
                "metamodule_enabled": metamodule_enabled,
                "boot_id": boot_id,
                "boot_provider": boot_provider,
                "boot_result": boot_result,
                "target_count": target_count,
                "error": error,
            })
        )
        .context("failed to write mount status")?;
    } else {
        writeln!(out, "schema_version=1").context("failed to write mount status")?;
        writeln!(
            out,
            "configured_mode={}",
            configured_mode.map_or("", MountMode::as_str)
        )
        .context("failed to write mount status")?;
        writeln!(
            out,
            "next_provider={}",
            next_provider.map_or("", Provider::as_str)
        )
        .context("failed to write mount status")?;
        writeln!(
            out,
            "metamodule_id={}",
            metamodule_id.as_deref().unwrap_or("")
        )
        .context("failed to write mount status")?;
        writeln!(out, "metamodule_enabled={metamodule_enabled}")
            .context("failed to write mount status")?;
        writeln!(out, "boot_id={}", boot_id.as_deref().unwrap_or(""))
            .context("failed to write mount status")?;
        writeln!(
            out,
            "boot_provider={}",
            boot_provider.as_deref().unwrap_or("")
        )
        .context("failed to write mount status")?;
        writeln!(out, "boot_result={boot_result}").context("failed to write mount status")?;
        writeln!(
            out,
            "target_count={}",
            target_count.map_or_else(String::new, |count| count.to_string())
        )
        .context("failed to write mount status")?;
        writeln!(out, "error={}", error.as_deref().unwrap_or(""))
            .context("failed to write mount status")?;
    }
    out.flush().context("failed to flush mount status")?;
    Ok(())
}

/// Persist a new mount mode from a CLI token.
///
/// # Errors
/// Returns an error for an unknown mode token or a failed atomic write.
pub fn set_mode(mode: &str) -> Result<()> {
    let Some(parsed) = MountMode::parse(mode) else {
        bail!("invalid mount mode {mode:?}; expected one of: auto, builtin, metamodule");
    };
    write_mount_mode(parsed)
}
