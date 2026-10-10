#!/usr/bin/env python3
"""Run non-flashing AnyKernel3 fixtures against the installed Manager daemon."""
import argparse
import shlex
import subprocess
import tempfile
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()

    def adb(*command):
        if len(command) == 4 and command[:3] == ("shell", "su", "-c"):
            command = ("shell", "su -c " + shlex.quote(command[3]))
        return subprocess.run(["adb", "-s", args.serial, *command],
                              text=True, capture_output=True, timeout=60)

    package = adb("shell", "pm", "path", "me.yuki.folksu")
    package.check_returncode()
    base = package.stdout.strip().splitlines()[0].removeprefix("package:")
    daemon = str(Path(base).parent / "lib/arm64/libksud.so")
    remote = "/data/local/tmp/folksu-anykernel-safe-test.zip"
    # Only creates files inside POSTINSTALL. No block devices, mounts, boot
    # images, property changes, reboot commands or downloaded code.
    script = '''#!/system/bin/sh
set -eu
test "$1" = 3
test "$2" = 1
test -f "$3"
test -z "${AKHOME:-}"
AKHOME="$POSTINSTALL/fixture"
mkdir -p "$AKHOME/tools" "$AKHOME/bin" "$AKHOME/empty"
cd "$AKHOME"
chmod -R 755 tools bin;
test -x tools/mkbootfs
tools/mkbootfs empty > "$AKHOME/empty.cpio"
test -s "$AKHOME/empty.cpio"
echo "ui_print SAFE_FIXTURE_OK"
echo "SAFE_CONSOLE_OK"
exit EXIT_CODE
'''
    try:
        with tempfile.TemporaryDirectory(prefix="anykernel-safe-") as directory:
            for name, code in [("success", 0), ("failure", 7), ("invalid", None)]:
                file = Path(directory) / f"{name}.zip"
                with zipfile.ZipFile(file, "w") as archive:
                    archive.writestr("anykernel.sh", "# no partition writes\n")
                    archive.writestr("tools/", "")
                    archive.writestr("META-INF/com/google/android/update-binary",
                                     script.replace("EXIT_CODE", str(code)) if code is not None
                                     else "#!/system/bin/sh\nexit 0\n")
                adb("push", str(file), remote).check_returncode()
                result = adb("shell", "su", "-c", f"'{daemon}' anykernel3 {remote}")
                assert (result.returncode == 0) == (code == 0), result
                if code is not None:
                    assert "SAFE_FIXTURE_OK" in result.stdout, result
                    assert "SAFE_CONSOLE_OK" in result.stderr, result
                else:
                    assert "injection marker" in result.stderr, result
                cleanup = adb("shell", "su", "-c",
                              "find /data/adb/ksu -maxdepth 1 -type d -name 'anykernel3-*'")
                cleanup.check_returncode()
                assert not cleanup.stdout.strip(), cleanup.stdout
                print(f"PASS {name}: exit={result.returncode}; temporary directory cleaned")
            file = Path(directory) / "concurrent.zip"
            with zipfile.ZipFile(file, "w") as archive:
                archive.writestr("anykernel.sh", "# no partition writes\n")
                archive.writestr("tools/", "")
                archive.writestr("META-INF/com/google/android/update-binary",
                                 script.replace("EXIT_CODE", "0").replace(
                                     'echo "ui_print SAFE_FIXTURE_OK"',
                                     'echo "ui_print SAFE_FIXTURE_OK"\nsleep 4'))
            adb("push", str(file), remote).check_returncode()
            result = adb("shell", "su", "-c",
                         f"'{daemon}' anykernel3 {remote} >/dev/null 2>&1 & "
                         "first=$!; sleep 1; "
                         f"'{daemon}' anykernel3 {remote}; second=$?; "
                         "wait $first; first_status=$?; "
                         'test "$first_status" = 0 && test "$second" != 0')
            result.check_returncode()
            assert "another AnyKernel3 installation is running" in result.stderr, result
            cleanup = adb("shell", "su", "-c",
                          "find /data/adb/ksu -maxdepth 1 -type d -name 'anykernel3-*'")
            cleanup.check_returncode()
            assert not cleanup.stdout.strip(), cleanup.stdout
            print("PASS concurrent: second installer rejected; first succeeded; cleaned")
    finally:
        adb("shell", "rm", "-f", remote)


if __name__ == "__main__":
    main()
