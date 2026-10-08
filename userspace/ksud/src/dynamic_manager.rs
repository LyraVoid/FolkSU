//! Dynamic-manager signature persistence and kernel replay.
//!
//! FolkSU's dynamic-manager trust model keeps a volatile table of user-claimed
//! signatures in the kernel. This module owns the userspace half: it extracts
//! APK v2 certificate signatures, persists the user's list as JSON at
//! [`defs::DYNAMIC_MANAGER_PATH`] with mode 0600 and pushes the table back to
//! the kernel through `KSU_IOCTL_SET_DYNAMIC_MANAGERS`. The list is replayed on
//! every boot from `on_post_fs_data`.

use std::fs::{OpenOptions, Permissions, set_permissions};
use std::io::Write;
use std::os::raw::c_char;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;
use std::process::Command;

use anyhow::{Context, Result, bail, ensure};
use log::{info, warn};

use crate::{apk_sign, defs, ksu_uapi, ksucalls, utils};

/// One Android user covers 100000 UIDs; the app id is `uid % PER_USER_RANGE`.
const PER_USER_RANGE: u32 = 100_000;

/// Maximum number of signatures the kernel accepts.
const MAX_SIGNS: usize = ksu_uapi::KSU_DYNAMIC_MANAGER_MAX_SIGNS as usize;

/// A normalized `(size, sha256)` certificate signature.
#[derive(Debug, Clone, PartialEq, Eq)]
struct Sign {
    size: u32,
    hash: String,
}

impl Sign {
    /// Encode into the UAPI struct, copying the hash into its fixed 65-byte
    /// NUL-terminated buffer.
    fn to_uapi(&self) -> ksu_uapi::ksu_dynamic_manager_sign {
        let mut hash = [c_char::default(); 65];
        for (dst, byte) in hash.iter_mut().zip(self.hash.bytes()) {
            *dst = byte as c_char;
        }
        ksu_uapi::ksu_dynamic_manager_sign {
            size: self.size,
            hash,
        }
    }
}

/// Parse a certificate size, accepting decimal or `0x`-prefixed hex.
fn parse_size(value: &str) -> Result<u32> {
    let value = value.trim();
    value
        .strip_prefix("0x")
        .or_else(|| value.strip_prefix("0X"))
        .map_or_else(|| value.parse::<u32>(), |hex| u32::from_str_radix(hex, 16))
        .with_context(|| format!("invalid size: {value}"))
}

/// Validate and lowercase a 64-character hex SHA-256.
fn normalize_hash(value: &str) -> Result<String> {
    ensure!(
        value.len() == 64,
        "hash must be 64 hex characters, got {}",
        value.len()
    );
    let mut normalized = String::with_capacity(64);
    for ch in value.chars() {
        ensure!(
            ch.is_ascii_hexdigit(),
            "hash contains a non-hex character: {ch}"
        );
        normalized.push(ch.to_ascii_lowercase());
    }
    Ok(normalized)
}

fn parse_sign(size_arg: &str, hash_arg: &str) -> Result<Sign> {
    Ok(Sign {
        size: parse_size(size_arg)?,
        hash: normalize_hash(hash_arg)?,
    })
}

fn parse_size_value(value: &serde_json::Value) -> Option<u32> {
    match value {
        serde_json::Value::Number(number) => u32::try_from(number.as_u64()?).ok(),
        serde_json::Value::String(text) => parse_size(text).ok(),
        _ => None,
    }
}

fn sign_from_json(map: &serde_json::Map<String, serde_json::Value>) -> Option<Sign> {
    let size = map.get("size").and_then(parse_size_value)?;
    let hash = map
        .get("hash")
        .and_then(serde_json::Value::as_str)
        .and_then(|value| normalize_hash(value).ok())?;
    Some(Sign { size, hash })
}

/// Walk a parsed config, tolerating both a bare array and an object that nests
/// the array under one of the historical keys. Deduplicates `(size, hash)`.
fn collect_signs(value: &serde_json::Value, signs: &mut Vec<Sign>) {
    match value {
        serde_json::Value::Array(items) => {
            for item in items {
                collect_signs(item, signs);
            }
        }
        serde_json::Value::Object(map) => {
            if let Some(sign) = sign_from_json(map) {
                if !signs.contains(&sign) {
                    signs.push(sign);
                }
                return;
            }
            for key in ["managers", "dynamic_managers", "signs"] {
                if let Some(inner) = map.get(key) {
                    collect_signs(inner, signs);
                }
            }
        }
        _ => {}
    }
}

fn parse_signs(content: &str) -> Result<Vec<Sign>> {
    let value: serde_json::Value =
        serde_json::from_str(content).context("failed to parse dynamic manager config")?;
    let mut signs = Vec::new();
    collect_signs(&value, &mut signs);
    if signs.len() > MAX_SIGNS {
        warn!(
            "dynamic manager: truncating {} signature(s) to {MAX_SIGNS}",
            signs.len()
        );
        signs.truncate(MAX_SIGNS);
    }
    Ok(signs)
}

fn signs_to_json(signs: &[Sign]) -> String {
    let entries = signs
        .iter()
        .map(|sign| {
            serde_json::json!({
                "size": sign.size,
                "hash": sign.hash,
            })
        })
        .collect::<Vec<_>>();
    let value = serde_json::json!({ "signs": entries });
    let mut text =
        serde_json::to_string_pretty(&value).unwrap_or_else(|_| String::from("{\"signs\":[]}"));
    text.push('\n');
    text
}

fn load_signs() -> Result<Vec<Sign>> {
    let path = Path::new(defs::DYNAMIC_MANAGER_PATH);
    match std::fs::read_to_string(path) {
        Ok(content) => parse_signs(&content),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Vec::new()),
        Err(e) => Err(e).with_context(|| format!("failed to read {}", path.display())),
    }
}

fn save_signs(signs: &[Sign]) -> Result<()> {
    let path = Path::new(defs::DYNAMIC_MANAGER_PATH);
    if let Some(parent) = path.parent() {
        utils::ensure_dir_exists(parent)?;
    }
    let mut file = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(path)
        .with_context(|| format!("failed to open {}", path.display()))?;
    file.write_all(signs_to_json(signs).as_bytes())
        .with_context(|| format!("failed to write {}", path.display()))?;
    // `OpenOptionsExt::mode` only applies on creation, so enforce it here too.
    set_permissions(path, Permissions::from_mode(0o600))
        .with_context(|| format!("failed to chmod {}", path.display()))?;
    Ok(())
}

/// Push `signs` to the kernel, replacing whatever table is there.
fn apply(signs: &[Sign]) -> Result<()> {
    ensure!(
        signs.len() <= MAX_SIGNS,
        "too many dynamic manager signatures: {} > {MAX_SIGNS}",
        signs.len()
    );
    let raw = signs.iter().map(Sign::to_uapi).collect::<Vec<_>>();
    ksucalls::set_dynamic_managers(&raw)
}

fn add_sign(sign: Sign) -> Result<()> {
    let mut list = load_signs()?;
    let size = sign.size;
    let hash = sign.hash.clone();
    if !list.contains(&sign) {
        ensure!(
            list.len() < MAX_SIGNS,
            "too many dynamic manager signatures: {} >= {MAX_SIGNS}",
            list.len()
        );
        list.push(sign);
    }
    apply(&list)?;
    save_signs(&list)?;
    println!("size: {size:#x}");
    println!("hash: {hash}");
    Ok(())
}

fn sign_from_apk(apk: &str) -> Result<Sign> {
    let (size, hash) = apk_sign::get_apk_signature(apk)
        .with_context(|| format!("failed to get APK signature: {apk}"))?;
    Ok(Sign {
        size,
        hash: normalize_hash(&hash)?,
    })
}

/// Run a program and return its stdout when it exits successfully.
fn run_command(program: &str, args: &[&str]) -> Option<String> {
    let output = Command::new(program).args(args).output().ok()?;
    if output.status.success() {
        Some(String::from_utf8_lossy(&output.stdout).into_owned())
    } else {
        None
    }
}

/// Map a UID to package names using `cmd package` / `pm`.
fn packages_for_uid(uid: u32) -> Vec<String> {
    let user = (uid / PER_USER_RANGE).to_string();
    let variants: [(&str, Vec<&str>); 4] = [
        (
            "cmd",
            vec!["package", "list", "packages", "--user", &user, "-U"],
        ),
        ("pm", vec!["list", "packages", "--user", &user, "-U"]),
        ("cmd", vec!["package", "list", "packages", "-U"]),
        ("pm", vec!["list", "packages", "-U"]),
    ];
    let listing = variants.into_iter().find_map(|(program, args)| {
        let output = run_command(program, &args)?;
        if output.trim().is_empty() {
            None
        } else {
            Some(output)
        }
    });
    let Some(listing) = listing else {
        return Vec::new();
    };
    listing
        .lines()
        .filter_map(|line| package_from_line(line.trim(), uid))
        .collect()
}

fn package_from_line(line: &str, uid: u32) -> Option<String> {
    let package_pos = line.find("package:")?;
    let uid_pos = line.find("uid:")?;
    let package = line[package_pos + "package:".len()..]
        .split_whitespace()
        .next()?;
    let parsed_uid = line[uid_pos + "uid:".len()..]
        .split_whitespace()
        .next()?
        .parse::<u32>()
        .ok()?;
    if parsed_uid == uid && !package.is_empty() {
        Some(package.to_string())
    } else {
        None
    }
}

/// Resolve the APK paths of a package, preferring `base.apk`.
fn apk_paths_for_package(package: &str, uid: u32) -> Vec<String> {
    let user = (uid / PER_USER_RANGE).to_string();
    let variants: [(&str, Vec<&str>); 4] = [
        ("cmd", vec!["package", "path", "--user", &user, package]),
        ("pm", vec!["path", "--user", &user, package]),
        ("cmd", vec!["package", "path", package]),
        ("pm", vec!["path", package]),
    ];
    for (program, args) in variants {
        if let Some(output) = run_command(program, &args) {
            let paths = parse_package_paths(&output);
            if !paths.is_empty() {
                return paths;
            }
        }
    }
    Vec::new()
}

fn parse_package_paths(output: &str) -> Vec<String> {
    let mut paths = output
        .lines()
        .filter_map(|line| line.trim().strip_prefix("package:"))
        .map(str::trim)
        .filter(|path| !path.is_empty())
        .map(str::to_string)
        .collect::<Vec<_>>();
    // Stable sort keeps `base.apk` first without disturbing the rest.
    paths.sort_by_key(|path| !path.ends_with("/base.apk"));
    paths
}

/// Find the first installed APK with a usable v2 signature for `uid`.
fn apk_from_uid(uid: u32) -> Result<(String, String)> {
    let packages = packages_for_uid(uid);
    ensure!(!packages.is_empty(), "no package found for uid {uid}");
    for package in &packages {
        for path in apk_paths_for_package(package, uid) {
            if apk_sign::get_apk_signature(&path).is_ok() {
                return Ok((package.clone(), path));
            }
        }
    }
    bail!("no APK with v2 signature found for uid {uid}")
}

fn print_sign_json(size: u32, hash: &str) {
    let value = serde_json::json!({
        "v2": {
            "has": true,
            "hash": hash,
            "size": format!("{size:#x}"),
        }
    });
    println!("{value}");
}

/// `ksud dynamic get-sign [--json] <APK> | --uid <UID>`
pub fn get_sign(apk: Option<&str>, uid: Option<u32>, json: bool) -> Result<()> {
    let (package, apk_path) = if let Some(uid) = uid {
        let (package, path) = apk_from_uid(uid)?;
        (Some(package), path)
    } else if let Some(apk) = apk {
        (None, apk.to_string())
    } else {
        bail!("usage: ksud dynamic get-sign [--json] <APK> | --uid <UID>");
    };

    let (size, hash) = apk_sign::get_apk_signature(&apk_path)
        .with_context(|| format!("failed to get APK signature: {apk_path}"))?;
    let hash = normalize_hash(&hash)?;

    if json {
        print_sign_json(size, &hash);
    } else {
        if let Some(package) = &package {
            println!("package: {package}");
            println!("apk: {apk_path}");
        }
        println!("size: {size:#x}");
        println!("hash: {hash}");
    }
    Ok(())
}

/// `ksud dynamic set-hash <size> <hash>`
pub fn set_hash(size_arg: &str, hash_arg: &str) -> Result<()> {
    add_sign(parse_sign(size_arg, hash_arg)?)
}

/// `ksud dynamic set-apk <APK>`
pub fn set_apk(apk: &str) -> Result<()> {
    add_sign(sign_from_apk(apk)?)
}

/// `ksud dynamic set-uid <UID>`
pub fn set_uid(uid: u32) -> Result<()> {
    let (package, apk) = apk_from_uid(uid)?;
    let sign = sign_from_apk(&apk)?;
    println!("package: {package}");
    println!("apk: {apk}");
    add_sign(sign)
}

/// `ksud dynamic list`
pub fn list() -> Result<()> {
    for sign in load_signs()? {
        println!("size: {:#x}", sign.size);
        println!("hash: {}", sign.hash);
    }
    Ok(())
}

/// `ksud dynamic del <size> <hash>`
pub fn del(size_arg: &str, hash_arg: &str) -> Result<()> {
    let target = parse_sign(size_arg, hash_arg)?;
    let mut signs = load_signs()?;
    let before = signs.len();
    signs.retain(|sign| sign != &target);
    ensure!(signs.len() < before, "signature not found");
    apply(&signs)?;
    save_signs(&signs)
}

/// `ksud dynamic clear`
pub fn clear() -> Result<()> {
    apply(&[])?;
    save_signs(&[])
}

/// Boot-time replay: load the persisted signatures and restore kernel state.
///
/// The kernel table is volatile, so this runs on every boot. It is idempotent
/// and callers are expected to log-and-continue on error.
pub fn load_and_apply() -> Result<()> {
    let signs = load_signs()?;
    apply(&signs)?;
    info!("dynamic manager: applied {} signature(s)", signs.len());
    Ok(())
}
