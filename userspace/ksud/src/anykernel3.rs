use std::{
    ffi::OsString,
    fmt::Display,
    io::{self, BufRead, Write},
    path::Path,
};

use anyhow::{Result, bail};

const UPDATE_BINARY_ENTRY: &str = "META-INF/com/google/android/update-binary";
const PATCH_MARKER: &[u8] = b"chmod -R 755 tools bin;";

fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', "'\"'\"'"))
}

enum InstallerOutputLine<'a> {
    UserInterface(&'a [u8]),
    Console(&'a [u8]),
}

fn classify_installer_output(line: &[u8]) -> Option<InstallerOutputLine<'_>> {
    let line = line.strip_suffix(b"\n").unwrap_or(line);
    let line = line.strip_suffix(b"\r").unwrap_or(line);
    let first_non_whitespace = line
        .iter()
        .position(|byte| !byte.is_ascii_whitespace())
        .unwrap_or(line.len());
    let command = &line[first_non_whitespace..];

    if command == b"ui_print" {
        return None;
    }
    if let Some(message) = command.strip_prefix(b"ui_print ") {
        return Some(InstallerOutputLine::UserInterface(message));
    }
    Some(InstallerOutputLine::Console(line))
}

fn write_output_line<W: Write>(writer: &mut W, line: &[u8]) -> io::Result<()> {
    writer.write_all(line)?;
    writer.write_all(b"\n")?;
    writer.flush()
}

fn forward_installer_output<R: BufRead, U: Write, C: Write>(
    mut reader: R,
    mut user_interface: U,
    mut console: C,
) -> io::Result<()> {
    let mut line = Vec::new();
    let mut user_interface_error = None;
    let mut console_error = None;
    loop {
        line.clear();
        if reader.read_until(b'\n', &mut line)? == 0 {
            break;
        }
        let Some(output) = classify_installer_output(&line) else {
            continue;
        };
        match output {
            InstallerOutputLine::UserInterface(message) if user_interface_error.is_none() => {
                user_interface_error = write_output_line(&mut user_interface, message).err();
            }
            InstallerOutputLine::Console(message) if console_error.is_none() => {
                console_error = write_output_line(&mut console, message).err();
            }
            _ => {}
        }
    }
    user_interface_error.or(console_error).map_or(Ok(()), Err)
}

fn patch_update_binary(script: &[u8], mkbootfs: &Path) -> Result<Vec<u8>> {
    let matches = script
        .windows(PATCH_MARKER.len())
        .enumerate()
        .filter_map(|(index, candidate)| (candidate == PATCH_MARKER).then_some(index))
        .collect::<Vec<_>>();

    if matches.is_empty() {
        bail!("AnyKernel3 update-binary does not contain the mkbootfs injection marker");
    }
    if matches.len() != 1 {
        bail!("AnyKernel3 update-binary contains multiple mkbootfs injection markers");
    }

    let mkbootfs = mkbootfs
        .to_str()
        .ok_or_else(|| anyhow::anyhow!("mkbootfs path is not valid UTF-8"))?;
    if mkbootfs.contains('\0') {
        bail!("mkbootfs path contains a NUL byte");
    }
    let injection = format!(
        "cp -f {} \"$AKHOME/tools/mkbootfs\" || exit 1; ",
        shell_quote(mkbootfs)
    );

    let marker = matches[0];
    let mut patched = Vec::with_capacity(script.len() + injection.len());
    patched.extend_from_slice(&script[..marker]);
    patched.extend_from_slice(injection.as_bytes());
    patched.extend_from_slice(&script[marker..]);
    Ok(patched)
}

fn select_update_binary<I, S>(entries: I) -> Result<usize>
where
    I: IntoIterator<Item = (usize, S)>,
    S: AsRef<str>,
{
    let matches = entries
        .into_iter()
        .filter_map(|(index, name)| (name.as_ref() == UPDATE_BINARY_ENTRY).then_some(index))
        .collect::<Vec<_>>();
    match matches.as_slice() {
        [index] => Ok(*index),
        [] => bail!("ZIP does not contain {UPDATE_BINARY_ENTRY}"),
        _ => bail!("ZIP contains multiple {UPDATE_BINARY_ENTRY} entries"),
    }
}

fn combine_results(
    primary: Result<()>,
    secondary: Result<()>,
    secondary_label: &str,
) -> Result<()> {
    match (primary, secondary) {
        (Ok(()), Ok(())) => Ok(()),
        (Err(error), Ok(())) => Err(error),
        (Ok(()), Err(error)) => Err(anyhow::anyhow!("{secondary_label}: {error:#}")),
        (Err(primary), Err(secondary)) => Err(anyhow::anyhow!(
            "{primary:#}; additionally, {secondary_label}: {secondary:#}"
        )),
    }
}

fn installer_arguments(zip_path: &Path) -> Vec<OsString> {
    vec![
        OsString::from("3"),
        OsString::from("1"),
        zip_path.as_os_str().to_owned(),
    ]
}

fn ensure_installer_success(success: bool, status: impl Display) -> Result<()> {
    if !success {
        bail!("AnyKernel3 update-binary exited with status {status}");
    }
    Ok(())
}

#[cfg(target_os = "android")]
mod android {
    use std::{
        fs::{self, File},
        io::{BufReader, Read},
        path::{Path, PathBuf},
        process::{Command, Stdio},
    };

    use anyhow::{Context, Result, ensure};
    use tempfile::{Builder, TempDir};

    use crate::{
        anykernel3::{
            UPDATE_BINARY_ENTRY, combine_results, ensure_installer_success,
            forward_installer_output, installer_arguments, patch_update_binary,
            select_update_binary,
        },
        assets, defs, utils,
    };

    const MAX_UPDATE_BINARY_SIZE: u64 = 4 * 1024 * 1024;

    fn read_update_binary(zip_path: &Path) -> Result<Vec<u8>> {
        let file = File::open(zip_path)
            .with_context(|| format!("failed to open {}", zip_path.display()))?;
        let mut archive = zip::ZipArchive::new(file)
            .with_context(|| format!("invalid ZIP archive {}", zip_path.display()))?;

        let mut names = Vec::with_capacity(archive.len());
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .with_context(|| format!("failed to inspect ZIP entry {index}"))?;
            names.push((index, entry.name().to_owned()));
        }
        let mut unique = std::collections::HashSet::new();
        for (_, name) in &names {
            ensure!(unique.insert(name), "duplicate ZIP entry: {name}");
            ensure!(
                !name.starts_with('/') && !name.split('/').any(|part| part == ".."),
                "unsafe ZIP entry: {name}"
            );
        }
        ensure!(
            unique.contains(&"anykernel.sh".to_owned()),
            "ZIP is missing root anykernel.sh"
        );
        ensure!(
            names.iter().any(|(_, name)| name.starts_with("tools/")),
            "ZIP is missing tools"
        );
        ensure!(
            !names
                .iter()
                .any(|(_, name)| name == "module.prop" || name.ends_with("/module.prop")),
            "module ZIPs are not AnyKernel3 packages"
        );
        let index = select_update_binary(names.iter().map(|(index, name)| (*index, name)))?;

        let mut entry = archive
            .by_index(index)
            .context("failed to open AnyKernel3 update-binary")?;
        ensure!(
            !entry.is_dir(),
            "{UPDATE_BINARY_ENTRY} is a directory instead of a script"
        );
        ensure!(
            entry.size() <= MAX_UPDATE_BINARY_SIZE,
            "{UPDATE_BINARY_ENTRY} exceeds the {MAX_UPDATE_BINARY_SIZE} byte safety limit"
        );

        let expected_size = entry.size();
        let mut script = Vec::with_capacity(expected_size as usize);
        (&mut entry)
            .take(MAX_UPDATE_BINARY_SIZE + 1)
            .read_to_end(&mut script)
            .context("failed to read AnyKernel3 update-binary")?;
        ensure!(
            script.len() as u64 <= MAX_UPDATE_BINARY_SIZE,
            "{UPDATE_BINARY_ENTRY} exceeds the {MAX_UPDATE_BINARY_SIZE} byte safety limit"
        );
        ensure!(
            script.len() as u64 == expected_size,
            "AnyKernel3 update-binary was truncated while reading"
        );
        ensure!(
            !script.contains(&0),
            "AnyKernel3 update-binary contains a NUL byte"
        );
        Ok(script)
    }

    fn prepare(temp_dir: &TempDir, zip_path: &Path) -> Result<PathBuf> {
        eprintln!("- Preparing AnyKernel3 package");
        let script = read_update_binary(zip_path)?;
        let mkbootfs = temp_dir.path().join("mkbootfs");
        let binary =
            assets::get_asset_data("mkbootfs").context("embedded mkbootfs is unavailable")?;
        utils::ensure_binary(&mkbootfs, &binary, false).context("failed to extract mkbootfs")?;
        let patched = patch_update_binary(&script, &mkbootfs)?;
        fs::create_dir_all(temp_dir.path().join("tmp"))
            .context("failed to create the AnyKernel3 POSTINSTALL tmp directory")?;

        let update_binary = temp_dir
            .path()
            .join("META-INF/com/google/android/update-binary");
        fs::create_dir_all(
            update_binary
                .parent()
                .context("update-binary path has no parent")?,
        )
        .context("failed to create AnyKernel3 script directory")?;
        fs::write(&update_binary, patched).context("failed to write patched update-binary")?;
        Ok(update_binary)
    }

    fn run_installer(temp_dir: &TempDir, update_binary: &Path, zip_path: &Path) -> Result<()> {
        eprintln!("- Running AnyKernel3 installer");
        let mut command = Command::new("/system/bin/sh");
        command
            .arg(update_binary)
            .args(installer_arguments(zip_path))
            .env("POSTINSTALL", temp_dir.path())
            .env_remove("AKHOME")
            .current_dir(temp_dir.path())
            .stdin(Stdio::inherit())
            .stdout(Stdio::piped())
            .stderr(Stdio::inherit());

        let mut child = command
            .spawn()
            .context("failed to start AnyKernel3 update-binary")?;
        let stdout = child
            .stdout
            .take()
            .context("failed to capture AnyKernel3 update-binary output")?;
        let output_result = forward_installer_output(
            BufReader::new(stdout),
            std::io::stdout().lock(),
            std::io::stderr().lock(),
        )
        .context("failed to forward AnyKernel3 update-binary output");
        let installer_result = child
            .wait()
            .context("failed to wait for AnyKernel3 update-binary")
            .and_then(|status| ensure_installer_success(status.success(), status));
        combine_results(
            installer_result,
            output_result,
            "failed to process AnyKernel3 update-binary output",
        )
    }

    fn flash_inner(temp_dir: &TempDir, zip_path: &Path) -> Result<()> {
        let update_binary = prepare(temp_dir, zip_path)?;
        run_installer(temp_dir, &update_binary, zip_path)
    }

    pub fn flash(zip_path: &Path) -> Result<()> {
        ensure!(
            unsafe { libc::geteuid() } == 0,
            "root is required to flash AnyKernel3"
        );
        let metadata = fs::metadata(zip_path)
            .with_context(|| format!("failed to stat {}", zip_path.display()))?;
        ensure!(
            metadata.is_file(),
            "{} is not a regular file",
            zip_path.display()
        );
        let zip_path = fs::canonicalize(zip_path)
            .with_context(|| format!("failed to resolve {}", zip_path.display()))?;

        utils::ensure_dir_exists(defs::WORKING_DIR)
            .context("failed to create the KernelSU working directory")?;
        let lock = File::create(Path::new(defs::WORKING_DIR).join(".anykernel3.lock"))?;
        rustix::fs::flock(&lock, rustix::fs::FlockOperation::NonBlockingLockExclusive)
            .context("another AnyKernel3 installation is running")?;
        let temp_dir = Builder::new()
            .prefix("anykernel3-")
            .tempdir_in(defs::WORKING_DIR)
            .context("failed to create the AnyKernel3 working directory")?;

        let flash_result = flash_inner(&temp_dir, &zip_path);
        let cleanup_result = temp_dir
            .close()
            .context("failed to remove the AnyKernel3 working directory");
        combine_results(
            flash_result,
            cleanup_result,
            "failed to clean the AnyKernel3 working directory",
        )?;
        eprintln!("- AnyKernel3 installation completed");
        Ok(())
    }
}

#[cfg(target_os = "android")]
pub use android::flash;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn patch_requires_exactly_one_marker() {
        assert!(patch_update_binary(b"echo no marker", Path::new("/mkbootfs")).is_err());
        let duplicate = [PATCH_MARKER, PATCH_MARKER].concat();
        assert!(patch_update_binary(&duplicate, Path::new("/mkbootfs")).is_err());
        let patched =
            patch_update_binary(PATCH_MARKER, Path::new("/path with 'quote/mkbootfs")).unwrap();
        assert!(patched.ends_with(PATCH_MARKER));
        assert!(String::from_utf8(patched).unwrap().contains("'\"'\"'"));
    }

    #[test]
    fn reject_missing_and_duplicate_installers() {
        assert!(select_update_binary([(0, "anykernel.sh")]).is_err());
        assert!(
            select_update_binary([(0, UPDATE_BINARY_ENTRY), (1, UPDATE_BINARY_ENTRY)]).is_err()
        );
        assert_eq!(select_update_binary([(7, UPDATE_BINARY_ENTRY)]).unwrap(), 7);
    }

    #[test]
    fn output_protocol_and_failure_status() {
        let mut ui = Vec::new();
        let mut console = Vec::new();
        forward_installer_output(
            &b"ui_print Installing\r\n  ui_print\nconsole\n"[..],
            &mut ui,
            &mut console,
        )
        .unwrap();
        assert_eq!(ui, b"Installing\n");
        assert_eq!(console, b"console\n");
        assert!(ensure_installer_success(false, "1").is_err());
        assert!(ensure_installer_success(true, "0").is_ok());
        assert!(combine_results(Ok(()), Err(anyhow::anyhow!("cleanup")), "cleanup").is_err());
        assert_eq!(installer_arguments(Path::new("/kernel.zip")).len(), 3);
    }
}
