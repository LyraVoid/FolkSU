use super::{
    Config, Detection, abi,
    model::Rule,
    store::{self, State},
};
use anyhow::{Result, bail};
use serde::Serialize;
use std::{collections::BTreeMap, fs, io::Write, os::unix::fs::MetadataExt};

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Stage {
    Live,
    PreMount,
    PostMount,
    Deferred,
}

#[derive(Serialize)]
pub struct Report {
    pub status: &'static str,
    pub applied: Vec<String>,
    pub failures: BTreeMap<String, String>,
    pub pending_reboot: Vec<String>,
}

pub fn operations(config: &Config) -> Result<BTreeMap<String, String>> {
    let mut ops = BTreeMap::new();
    for (key, value) in [
        ("hide_mounts", config.settings.hide_mounts),
        ("logging", config.settings.logging),
        ("avc_spoof", config.settings.avc_spoof),
    ] {
        if let Some(value) = value {
            ops.insert(key.to_owned(), value.to_string());
        }
    }
    if let Some(value) = &config.settings.uname {
        ops.insert("uname".to_owned(), serde_json::to_string(value)?);
    }
    if let Some(value) = &config.settings.cmdline {
        ops.insert("cmdline".to_owned(), value.clone());
    }
    for rule in &config.rules {
        let (kind, path) = match rule {
            Rule::Path { path, .. } => ("path", path),
            Rule::Map { path } => ("map", path),
            Rule::Redirect { path, .. } => ("redirect", path),
            Rule::Kstat { path, .. } => ("kstat", path),
        };
        ops.insert(format!("{kind}:{path}"), serde_json::to_string(rule)?);
    }
    for name in &config.lkm.hidden_modules {
        ops.insert(format!("module:{name}"), name.clone());
    }
    for path in &config.lkm.mount_prefixes {
        ops.insert(format!("prefix:{path}"), path.clone());
    }
    Ok(ops)
}

fn require(detection: &Detection, feature: &str) -> Result<()> {
    if !detection.supports(feature) {
        bail!("Capability unavailable: {feature}");
    }
    Ok(())
}

fn proc_write(detection: &Detection, node: &str, command: &str) -> Result<()> {
    if !detection.proc(node) {
        bail!("LKM proc extension unavailable: {node}");
    }
    let mut file = fs::OpenOptions::new()
        .write(true)
        .open(format!("/proc/{node}"))?;
    // One write: proc handlers parse a complete command. Never shell-evaluate rule input.
    let bytes = format!("{command}\n");
    if file.write(bytes.as_bytes())? != bytes.len() {
        bail!("Short SUSFS proc write");
    }
    Ok(())
}

fn toggle(detection: &Detection, capability: &str, command: u32, enabled: bool) -> Result<()> {
    require(detection, capability)?;
    let mut payload = abi::Toggle { enabled, err: 126 };
    abi::call(command, &mut payload);
    abi::checked(payload.err)
}

fn setting(config: &Config, detection: &Detection, key: &str) -> Result<()> {
    match key {
        "hide_mounts" => toggle(
            detection,
            "CONFIG_KSU_SUSFS_SUS_MOUNT",
            0x0005_5561,
            config.settings.hide_mounts.unwrap_or(false),
        ),
        "logging" => toggle(
            detection,
            "CONFIG_KSU_SUSFS_ENABLE_LOG",
            0x0005_55a0,
            config.settings.logging.unwrap_or(false),
        ),
        "avc_spoof" => {
            // LKM dispatches AVC but does not advertise an upstream feature for it.
            if detection.implementation == "lkm" {
                proc_write(
                    detection,
                    "susfs_avc_spoof",
                    if config.settings.avc_spoof == Some(true) {
                        "1"
                    } else {
                        "0"
                    },
                )
            } else {
                toggle(
                    detection,
                    "CONFIG_KSU_SUSFS_AVC_LOG_SPOOFING",
                    0x0006_0010,
                    config.settings.avc_spoof.unwrap_or(false),
                )
            }
        }
        "uname" => {
            require(detection, "CONFIG_KSU_SUSFS_SPOOF_UNAME")?;
            let value = config
                .settings
                .uname
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("Missing uname"))?;
            let mut payload = abi::Uname {
                release: abi::bytes(&value.release)?,
                version: abi::bytes(&value.version)?,
                err: 126,
            };
            abi::call(0x0005_5590, &mut payload);
            abi::checked(payload.err)
        }
        "cmdline" => {
            require(detection, "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG")?;
            let mut payload = abi::Text::<8192> {
                text: abi::bytes(config.settings.cmdline.as_deref().unwrap_or_default())?,
                err: 126,
            };
            abi::call(0x0005_55b0, &mut payload);
            abi::checked(payload.err)
        }
        _ if key.starts_with("module:") => proc_write(
            detection,
            "susfs_hide_modules",
            &format!("add {}", &key[7..]),
        ),
        _ if key.starts_with("prefix:") => proc_write(
            detection,
            "susfs_hide_mounts",
            &format!("add {}", &key[7..]),
        ),
        _ => bail!("Unknown SUSFS operation"),
    }
}

fn kstat(path: &str, full_clone: bool, detection: &Detection, update: bool) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SUS_KSTAT")?;
    if update && full_clone && detection.implementation == "lkm" {
        return proc_write(
            detection,
            "susfs_kstat",
            &format!("update_sus_kstat_full_clone {path}"),
        );
    }
    if full_clone && detection.implementation == "lkm" && !detection.proc("susfs_kstat") {
        bail!("LKM full-clone requires the proc extension; supercall ignores flags");
    }
    let md = fs::metadata(path)?;
    let mut payload = abi::Kstat {
        path: abi::bytes(path)?,
        target_ino: md.ino() as libc::c_ulong,
        ino: md.ino() as libc::c_ulong,
        dev: md.dev() as libc::c_ulong,
        nlink: md.nlink() as u32,
        size: md.size() as i64,
        atime_sec: md.atime() as libc::c_long,
        atime_nsec: md.atime_nsec() as libc::c_ulong,
        mtime_sec: md.mtime() as libc::c_long,
        mtime_nsec: md.mtime_nsec() as libc::c_ulong,
        ctime_sec: md.ctime() as libc::c_long,
        ctime_nsec: md.ctime_nsec() as libc::c_ulong,
        blocks: md.blocks() as i64,
        blksize: md.blksize() as libc::c_long,
        flags: if full_clone && update { 0x0fff } else { 0x0ff3 },
        ..abi::Kstat::default()
    };
    abi::call(if update { 0x0005_5571 } else { 0x0005_5570 }, &mut payload);
    abi::checked(payload.err)
}

fn rule(rule: &Rule, detection: &Detection) -> Result<()> {
    match rule {
        Rule::Path { path, .. } | Rule::Map { path } => {
            let (feature, command) = match rule {
                Rule::Path { looping: true, .. } => ("CONFIG_KSU_SUSFS_SUS_PATH", 0x0005_5553),
                Rule::Path { .. } => ("CONFIG_KSU_SUSFS_SUS_PATH", 0x0005_5550),
                _ => ("CONFIG_KSU_SUSFS_SUS_MAP", 0x0006_0020),
            };
            require(detection, feature)?;
            let mut payload = abi::Text::<256> {
                text: abi::bytes(path)?,
                err: 126,
            };
            abi::call(command, &mut payload);
            abi::checked(payload.err)
        }
        Rule::Redirect {
            path,
            destination,
            uid_scheme,
        } => {
            require(detection, "CONFIG_KSU_SUSFS_OPEN_REDIRECT")?;
            if detection.implementation == "lkm" && *uid_scheme > 2 {
                bail!("LKM UID schemes 3/4 are not equivalent to built-in SUSFS");
            }
            let mut payload = abi::Redirect {
                target: abi::bytes(path)?,
                destination: abi::bytes(destination)?,
                uid_scheme: *uid_scheme as i32,
                err: 126,
            };
            abi::call(0x0005_55c0, &mut payload);
            abi::checked(payload.err)
        }
        Rule::Kstat { .. } => bail!("Kstat requires paired pre/post mount stages"),
    }
}

pub fn apply(config: &Config, detection: &Detection, phase: Stage) -> Result<Report> {
    config.validate()?;
    if super::safe_mode() {
        bail!("SUSFS apply is disabled in safe mode");
    }
    if detection.status != "compatible" {
        bail!("SUSFS is unavailable or protocol is read-only");
    }
    #[cfg(target_os = "android")]
    crate::utils::switch_mnt_ns(1)?;
    let desired = operations(config)?;
    let mut state = store::read_state()?;
    let mut report = Report {
        status: "saved",
        applied: Vec::new(),
        failures: BTreeMap::new(),
        pending_reboot: Vec::new(),
    };
    state.failures.retain(|key, _| desired.contains_key(key));
    for key in state
        .applied
        .keys()
        .filter(|key| !desired.contains_key(*key))
    {
        report.pending_reboot.push(key.clone());
    }
    for (key, value) in &desired {
        if state.applied.get(key) == Some(value) {
            continue;
        }
        if state.applied.contains_key(key) && key.contains(':') {
            report.pending_reboot.push(key.clone());
            continue;
        }
        if state
            .snapshots
            .get(key)
            .is_some_and(|snapshot| snapshot != value)
        {
            report.pending_reboot.push(key.clone());
            continue;
        }
        let result = if key.starts_with("kstat:") {
            let parsed: Rule = serde_json::from_str(value)?;
            let Rule::Kstat { path, full_clone } = parsed else {
                bail!("Invalid kstat rule");
            };
            apply_kstat(
                key,
                &path,
                full_clone,
                detection,
                phase,
                &mut state,
                &mut report,
            )
        } else if phase == Stage::PreMount || phase == Stage::PostMount {
            continue;
        } else if key.starts_with("path:")
            || key.starts_with("map:")
            || key.starts_with("redirect:")
        {
            rule(&serde_json::from_str(value)?, detection).map(|()| true)
        } else {
            setting(config, detection, key).map(|()| true)
        };
        match result {
            Ok(true) => {
                state.applied.insert(key.clone(), value.clone());
                state.failures.remove(key);
                report.applied.push(key.clone());
            }
            Ok(false) => {}
            Err(error) => {
                state.failures.insert(key.clone(), error.to_string());
            }
        }
        // Persist after each successful mutation: replay does not silently duplicate rules.
        store::write_state(&state)?;
    }
    report.failures.clone_from(&state.failures);
    state.pending_reboot.clone_from(&report.pending_reboot);
    report.status = if !report.failures.is_empty() {
        "failed"
    } else if !report.pending_reboot.is_empty() {
        "pending_reboot"
    } else {
        store::summarize(&state, &desired)
    };
    store::write_state(&state)?;
    Ok(report)
}

fn apply_kstat(
    key: &str,
    path: &str,
    full_clone: bool,
    detection: &Detection,
    phase: Stage,
    state: &mut State,
    report: &mut Report,
) -> Result<bool> {
    #[cfg(target_os = "android")]
    let late = crate::ksucalls::is_late_load();
    #[cfg(not(target_os = "android"))]
    let late = false;
    let captured = state.kstat_pending.iter().any(|k| k == key);
    if kstat_phase(phase, captured, late) == Some(false) {
        kstat(path, full_clone, detection, false)?;
        state.snapshots.insert(
            key.to_owned(),
            serde_json::to_string(&Rule::Kstat {
                path: path.to_owned(),
                full_clone,
            })?,
        );
        state.kstat_pending.push(key.to_owned());
        Ok(false)
    } else if kstat_phase(phase, captured, late) == Some(true) {
        kstat(path, full_clone, detection, true)?;
        state.kstat_pending.retain(|k| k != key);
        Ok(true)
    } else {
        report.pending_reboot.push(key.to_owned());
        Ok(false)
    }
}

// Some(false): capture original; Some(true): retarget; None: cannot safely apply.
const fn kstat_phase(phase: Stage, captured: bool, late: bool) -> Option<bool> {
    match (phase, captured, late) {
        (Stage::PreMount, false, false) => Some(false),
        (Stage::PostMount, true, false) => Some(true),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dynamic_kstat_requires_the_real_pre_post_pair() {
        assert_eq!(kstat_phase(Stage::PreMount, false, false), Some(false));
        assert_eq!(kstat_phase(Stage::PostMount, true, false), Some(true));
        assert_eq!(kstat_phase(Stage::PostMount, false, false), None);
        assert_eq!(kstat_phase(Stage::PreMount, true, false), None);
        for phase in [Stage::Live, Stage::Deferred] {
            assert_eq!(kstat_phase(phase, false, false), None);
            assert_eq!(kstat_phase(phase, true, false), None);
        }
        for phase in [
            Stage::PreMount,
            Stage::PostMount,
            Stage::Deferred,
            Stage::Live,
        ] {
            assert_eq!(kstat_phase(phase, false, true), None);
            assert_eq!(kstat_phase(phase, true, true), None);
        }
    }

    #[test]
    fn extension_controls_need_the_explicit_marker_and_proc_node() {
        let mut detection = Detection {
            status: "compatible",
            version: "v2.3.0".to_owned(),
            variant: "GKI".to_owned(),
            implementation: "builtin",
            features: vec!["CONFIG_KSU_SUSFS_SUS_KSTAT".to_owned()],
            proc_nodes: vec!["susfs_kstat".to_owned()],
        };
        assert!(!detection.proc("susfs_kstat"));
        detection.implementation = "lkm";
        assert!(detection.proc("susfs_kstat"));
        detection.proc_nodes.clear();
        assert!(!detection.proc("susfs_kstat"));
        detection.status = "unknown_protocol";
        assert!(!detection.supports("CONFIG_KSU_SUSFS_SUS_KSTAT"));
    }
}
