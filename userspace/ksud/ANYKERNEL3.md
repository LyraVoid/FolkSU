# AnyKernel3 flashing

The Manager install screen can run a trusted AnyKernel3 ZIP using
`ksud anykernel3 <zip>`. Root is required. The existing flash screen displays
installer output and offers reboot only after successful completion.

## Scope and compatibility

- No slot override is applied. The ZIP's scripts determine the partitions and
  slots actually written; this is not a sandbox or a current-slot guarantee.
- FolkSU does not install or update ksud after flashing.
- Packages must contain root `anykernel.sh`, `tools/` entries and exactly one
  `META-INF/com/google/android/update-binary`. Module ZIPs, duplicate entries
  and absolute or parent-traversing entry names are rejected.
- The installer script is limited to 4 MiB and must contain exactly one
  `chmod -R 755 tools bin;` marker. Customized installers without this marker
  are not supported.
- A bundled AOSP-derived mkbootfs is injected into the installer tools. It is
  built with the Android target compiler and extracted only to the private
  temporary installation directory, without refreshing existing daemon tools.
- Concurrent AnyKernel3 commands are rejected with a nonblocking file lock.
  Temporary files are cleaned on normal completion and failure, but abrupt
  termination or power loss can leave them behind.

## Safety

ZIP scripts execute with root privileges. Structural validation does not prove
that a package is safe or device-compatible. A failed installer may already
have written partitions, and exit status zero does not guarantee bootability.
FolkSU's existing boot-image backup may not cover all modified partitions.
Prepare device-specific recovery images before use.

## Build

`mkbootfs.cpp` retains its AOSP Apache-2.0 notice. The installer implementation
is adapted from BakaSU's `userspace/ksud/src/anykernel3.rs` at commit
`c9246641b9eee8b9986050e8bf1832f88da880c2`.

The build script generates ignored `bin/<arch>/mkbootfs` assets. A release
build on this project requires an adequate Android API level, for example:

```sh
cargo ndk -t arm64-v8a --platform 31 build --release
```

Do not use a ZIP that writes real partitions merely to test the UI. Host unit
tests cover marker validation, script argument construction, output routing
and exit-status handling; on-device flashing remains a separate validation.
