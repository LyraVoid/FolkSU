//! FolkSU-owned SUSFS management. Never installs ksu_susfs or edits module configs.
#![cfg_attr(not(target_os = "android"), allow(dead_code))]
mod abi;
mod apply;
mod model;
mod store;

use anyhow::{Result, bail};
use serde::Serialize;
use std::io::Read;

pub use model::Config;

#[derive(Debug, Clone, Copy, clap::Subcommand)]
pub enum Command {
    /// Query the root-only control ABI and the FolkSU management state
    Status,
    /// Export the independent, versioned FolkSU configuration
    Export,
    /// Validate and atomically save JSON from stdin; does not apply it
    Import,
    /// Apply supported live operations; never fabricates pre-mount kstat
    Apply,
}

#[derive(Debug, Clone, Serialize)]
pub struct Detection {
    pub status: &'static str,
    pub version: String,
    pub variant: String,
    pub implementation: &'static str,
    pub features: Vec<String>,
    pub proc_nodes: Vec<String>,
}

impl Detection {
    pub fn supports(&self, feature: &str) -> bool {
        self.status == "compatible" && self.features.iter().any(|f| f == feature)
    }

    pub fn proc(&self, node: &str) -> bool {
        self.status == "compatible"
            && self.implementation == "lkm"
            && self.proc_nodes.iter().any(|n| n == node)
    }
}

pub const PROC_NODES: [&str; 7] = [
    "susfs_kstat",
    "susfs_open_redirect",
    "susfs_enable_log",
    "susfs_avc_spoof",
    "susfs_hide_modules",
    "susfs_hide_mounts",
    "susfs_path",
];

fn query<const N: usize>(command: u32) -> (i32, Option<String>) {
    let mut payload = abi::Text::<N>::empty();
    abi::call(command, &mut payload);
    (payload.err, abi::string(&payload.text))
}

fn classify(
    version: &(i32, Option<String>),
    features: &(i32, Option<String>),
    variant: &(i32, Option<String>),
) -> Detection {
    let detected = version.0 != 126 || features.0 != 126 || variant.0 != 126;
    let tokens: Vec<String> = features
        .1
        .as_deref()
        .unwrap_or_default()
        .lines()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .collect();
    // Only the audited 2.3 wire layout is writable. Other revisions remain visible/read-only.
    let compatible = version.0 == 0
        && features.0 == 0
        && variant.0 == 0
        && version.1.as_deref() == Some("v2.3.0")
        && matches!(variant.1.as_deref(), Some("GKI" | "NON-GKI"))
        && !tokens.is_empty()
        && tokens
            .iter()
            .all(|s| s == "SUSFS_LKM_MODULES" || s.starts_with("CONFIG_KSU_SUSFS_"));
    let lkm = tokens.iter().any(|s| s == "SUSFS_LKM_MODULES");
    Detection {
        status: if !detected {
            "not_detected"
        } else if compatible {
            "compatible"
        } else {
            "unknown_protocol"
        },
        version: version.1.clone().unwrap_or_default(),
        variant: variant.1.clone().unwrap_or_default(),
        implementation: if lkm {
            "lkm"
        } else if compatible {
            "builtin"
        } else {
            "unknown"
        },
        features: tokens,
        proc_nodes: Vec::new(),
    }
}

pub fn detect() -> Detection {
    if unsafe { libc::geteuid() } != 0 {
        return Detection {
            status: "root_unavailable",
            version: String::new(),
            variant: String::new(),
            implementation: "unknown",
            features: Vec::new(),
            proc_nodes: Vec::new(),
        };
    }
    let mut detection = classify(
        &query::<16>(0x0005_55e1),
        &query::<8192>(0x0005_55e2),
        &query::<16>(0x0005_55e3),
    );
    if detection.status == "compatible" && detection.implementation == "lkm" {
        detection.proc_nodes = PROC_NODES
            .iter()
            .filter(|node| {
                std::fs::OpenOptions::new()
                    .write(true)
                    .open(format!("/proc/{node}"))
                    .is_ok()
            })
            .map(|node| (*node).to_owned())
            .collect();
    }
    detection
}

pub fn run(command: Command) -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        bail!("Root is required for SUSFS management");
    }
    match command {
        Command::Status => {
            let detection = detect();
            let config = store::read_config().and_then(|config| {
                let summary = store::summary(&config)?;
                Ok((config, summary))
            });
            let status = match config {
                Ok((config, summary)) => serde_json::json!({
                    "detection": detection, "automatic": config.automatic,
                    "management": summary, "config_error": null,
                }),
                Err(error) => serde_json::json!({
                    "detection": detection, "automatic": false, "management": "failed",
                    "config_error": error.to_string(),
                }),
            };
            println!("{status}");
        }
        Command::Export => println!("{}", serde_json::to_string_pretty(&store::read_config()?)?),
        Command::Import => {
            let mut input = String::new();
            std::io::stdin()
                .take((model::MAX_CONFIG + 1) as u64)
                .read_to_string(&mut input)?;
            let config = Config::parse(&input)?;
            let _lock = store::lock()?;
            store::write_config(&config)?;
            println!(
                "{}",
                serde_json::json!({"status": "saved", "management": store::summary(&config)?})
            );
        }
        Command::Apply => {
            let _lock = store::lock()?;
            let result = apply::apply(&store::read_config()?, &detect(), apply::Stage::Live)?;
            println!("{}", serde_json::to_string(&result)?);
        }
    }
    Ok(())
}

pub fn boot(stage: &str) {
    let stage = match stage {
        "pre-mount" => apply::Stage::PreMount,
        "post-mount" => apply::Stage::PostMount,
        "service" | "boot-completed" => apply::Stage::Deferred,
        _ => return,
    };
    let result = (|| -> Result<()> {
        let config = store::read_config()?;
        if !config.automatic || safe_mode() {
            return Ok(());
        }
        let _lock = store::lock()?;
        let report = apply::apply(&config, &detect(), stage)?;
        log::info!("SUSFS: {}", serde_json::to_string(&report)?);
        Ok(())
    })();
    if let Err(error) = result {
        log::warn!("SUSFS boot apply: {error:#}");
    }
}

fn safe_mode() -> bool {
    #[cfg(target_os = "android")]
    {
        crate::utils::is_safe_mode()
    }
    #[cfg(not(target_os = "android"))]
    {
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn response(value: &str) -> (i32, Option<String>) {
        (0, Some(value.to_owned()))
    }
    #[test]
    fn detection_requires_all_queries_and_audited_protocol() {
        let absent = (126, Some(String::new()));
        assert_eq!(classify(&absent, &absent, &absent).status, "not_detected");
        assert_eq!(
            classify(&response("v2.3.0"), &absent, &absent).status,
            "unknown_protocol"
        );
        assert_eq!(
            classify(
                &response("v9.0.0"),
                &response("CONFIG_KSU_SUSFS_SUS_PATH"),
                &response("GKI")
            )
            .status,
            "unknown_protocol"
        );
        let builtin = classify(
            &response("v2.3.0"),
            &response("CONFIG_KSU_SUSFS_SUS_PATH"),
            &response("GKI"),
        );
        assert_eq!(builtin.implementation, "builtin");
        let lkm = classify(
            &response("v2.3.0"),
            &response("SUSFS_LKM_MODULES\nCONFIG_KSU_SUSFS_SUS_PATH"),
            &response("GKI"),
        );
        assert_eq!(lkm.implementation, "lkm");
        assert!(!lkm.proc("susfs_kstat"));
    }
}
