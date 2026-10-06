//! Folk Mount configuration, provider selection and status reporting.
//!
//! Phase 1 implements the configuration file, the `auto | builtin | metamodule`
//! provider decision and the `ksud mount status` / `set-mode` protocol. The
//! actual tmpfs/bind executor lands in Phase 2 and is intentionally absent
//! here.

use std::fs;
use std::io::Write;
use std::path::Path;

use anyhow::{Context, Result, bail};
use log::warn;
use serde_json::json;

use crate::defs;

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

/// Result of a mount attempt. Unused until the Phase 2 executor lands.
#[allow(dead_code)]
#[must_use]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MountOutcome {
    pub provider: Provider,
    pub target_count: usize,
    pub skipped_count: usize,
}

#[allow(dead_code)]
impl MountOutcome {
    pub const fn empty(provider: Provider) -> Self {
        Self {
            provider,
            target_count: 0,
            skipped_count: 0,
        }
    }
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

/// Atomically persist `mode` to [`defs::FOLK_MOUNT_CONFIG`].
///
/// The value is written to a mode-0600 temporary file in the same directory,
/// synced, renamed over the target and the parent directory is then synced.
/// This only affects the next boot; it never triggers a mount or unmount.
///
/// # Errors
/// Returns an error when the directory cannot be created or the file cannot be
/// written, synced and renamed.
pub fn write_mount_mode(mode: MountMode) -> Result<()> {
    let path = Path::new(defs::FOLK_MOUNT_CONFIG);
    let dir = path
        .parent()
        .context("mount config path has no parent directory")?;
    fs::create_dir_all(dir).with_context(|| format!("failed to create {}", dir.display()))?;

    let mut tmp = tempfile::Builder::new()
        .prefix(".mount_mode.")
        .tempfile_in(dir)
        .with_context(|| format!("failed to create temp file in {}", dir.display()))?;
    tmp.write_all(mode.as_str().as_bytes())
        .context("failed to write mount mode")?;
    tmp.write_all(b"\n")
        .context("failed to terminate mount mode line")?;
    tmp.as_file()
        .sync_all()
        .context("failed to sync mount mode")?;
    tmp.persist(path)
        .with_context(|| format!("failed to persist {}", defs::FOLK_MOUNT_CONFIG))?;

    fs::File::open(dir)
        .and_then(|handle| handle.sync_all())
        .with_context(|| format!("failed to sync {}", dir.display()))?;
    Ok(())
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
/// metamodule is ignored so Folk Mount can take over. `builtin` and
/// `metamodule` force their provider.
///
/// # Errors
/// Returns an error when the configuration file exists but cannot be read.
#[allow(dead_code)]
pub fn resolve_provider() -> Result<Provider> {
    let mode = read_mount_mode()?;
    let (_, metamodule_enabled) = metamodule_state();
    Ok(provider_for(mode, metamodule_enabled))
}

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
    let (configured_mode, error) = match read_mount_mode() {
        Ok(mode) => (Some(mode), None),
        Err(e) => (None, Some(format!("{e:#}"))),
    };
    let next_provider = configured_mode.map(|mode| provider_for(mode, metamodule_enabled));

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
                "boot_id": serde_json::Value::Null,
                "boot_provider": serde_json::Value::Null,
                "boot_result": "unknown",
                "target_count": serde_json::Value::Null,
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
        writeln!(out, "boot_id=").context("failed to write mount status")?;
        writeln!(out, "boot_provider=").context("failed to write mount status")?;
        writeln!(out, "boot_result=unknown").context("failed to write mount status")?;
        writeln!(out, "target_count=").context("failed to write mount status")?;
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
