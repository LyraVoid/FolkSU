#!/usr/bin/env bash
#
# FolkSU build driver - a local mirror of the GitHub Actions pipeline.
#
#   .github/workflows/build-lkm.yml       build-lkm       -> ./build.sh lkm
#   .github/workflows/ksud.yml            build-ksud      -> ./build.sh ksud
#   .github/workflows/build-manager.yml   build-manager   -> ./build.sh apk
#   .github/workflows/build-manager.yml   repack-manager  -> ./build.sh repack
#
# Typical full run:
#   ./build.sh all
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# --------------------------------------------------------------------------- #
# Defaults (every one of these can be overridden from the environment)
# --------------------------------------------------------------------------- #
# A single-KMI local build serves only that KMI; late-load/boot-patch will fail
# on other KMIs. Use a CI release package (pack_lkm: true, full KMI matrix) for
# all-KMI support, or widen KMIS here.
KMIS="${KMIS:-android15-6.6}"
ABIS="${ABIS:-arm64-v8a}"
DDK_RELEASE="${DDK_RELEASE:-20260828}"       # .github/workflows/ddk-lkm.yml default
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-26}" # same as .github/scripts/setup-rust-build.sh
KSUD_BUILD_TYPE="${KSUD_BUILD_TYPE:-release}"
APP_BUILD_TYPE="${APP_BUILD_TYPE:-release}"
OUT_DIR="${OUT_DIR:-dist}"
KEYSTORE_PROPS="${KEYSTORE_PROPS:-$REPO_ROOT/manager/keystore.properties}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
# CI builds against NDK r29. Newer NDKs break the static ksuinit link
# (duplicate symbol rust_eh_personality: their libc.a already provides it).
NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    if [[ -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]]; then
        ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
    else
        printf '\033[1;33m[!]\033[0m NDK %s not found under %s/ndk, falling back to the newest one\n' \
            "$NDK_VERSION" "$ANDROID_HOME" >&2
        ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
    fi
    export ANDROID_NDK_HOME
fi

# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

read_keystore_cfg() {
    [[ -f "$KEYSTORE_PROPS" ]] || die "missing $KEYSTORE_PROPS (copy sign.example.properties and fill it in)"
    # shellcheck disable=SC1090
    set -a; source "$KEYSTORE_PROPS"; set +a
    : "${KEYSTORE_FILE:?KEYSTORE_FILE not set in $KEYSTORE_PROPS}"
    : "${KEYSTORE_PASSWORD:?KEYSTORE_PASSWORD not set in $KEYSTORE_PROPS}"
    : "${KEY_ALIAS:?KEY_ALIAS not set in $KEYSTORE_PROPS}"
    : "${KEY_PASSWORD:=$KEYSTORE_PASSWORD}"
    KEYSTORE_PATH="$REPO_ROOT/manager/$KEYSTORE_FILE"
    [[ -f "$KEYSTORE_PATH" ]] || die "keystore not found: $KEYSTORE_PATH"
}

# Exports the values manager/app/build.gradle.kts reads through the apksign plugin.
export_signing_env() {
    read_keystore_cfg
    export ORG_GRADLE_PROJECT_KEYSTORE_FILE="$KEYSTORE_FILE"
    export ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
    export ORG_GRADLE_PROJECT_KEY_ALIAS="$KEY_ALIAS"
    export ORG_GRADLE_PROJECT_KEY_PASSWORD="$KEY_PASSWORD"
}

# Derives the pair the kernel expects for the *second* trusted manager signature.
cert_size_and_hash() {
    read_keystore_cfg
    local der; der="$(mktemp)"
    # shellcheck disable=SC2064
    trap "rm -f '$der'" RETURN
    keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE_PATH" \
        -storepass "$KEYSTORE_PASSWORD" -file "$der" >/dev/null
    CERT_SIZE_HEX="$(printf '0x%04x' "$(stat -c%s "$der")")"
    CERT_HASH="$(sha256sum "$der" | awk '{print $1}')"
    log "manager cert: size=$CERT_SIZE_HEX sha256=$CERT_HASH"
}

# Runs a command inside the DDK image as root, then gives the artifacts back to us.
# (CI runs as root too; this just avoids leaving root-owned files in the tree.)
ddk_run() {
    local image="$1"; shift
    docker run --rm --privileged \
        -v "$REPO_ROOT":/build -w /build/kernel \
        -e CONFIG_KSU=m -e CC=clang \
        -e GIT_CONFIG_COUNT=1 \
        -e GIT_CONFIG_KEY_0=safe.directory \
        -e GIT_CONFIG_VALUE_0=/build \
        "$image" "$@"
    docker run --rm \
        -v "$REPO_ROOT/kernel":/k \
        --entrypoint /bin/sh "$image" \
        -c "chown -R $(id -u):$(id -g) /k" >/dev/null 2>&1 || \
        warn "could not restore ownership of kernel/ (artifacts may be root-owned)"
}

# --------------------------------------------------------------------------- #
# Steps
# --------------------------------------------------------------------------- #
build_lkm() {
    have docker || die "docker is required to build the LKM"
    cert_size_and_hash
    mkdir -p userspace/ksud/bin/aarch64

    local kmi image
    for kmi in $KMIS; do
        image="ghcr.io/ylarod/ddk-min:${kmi}-${DDK_RELEASE}"
        log "building LKM for $kmi ($image)"
        docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image"

        rm -f kernel/kernelsu.ko
        ddk_run "$image" make \
            "KSU_EXPECTED_SIZE2=$CERT_SIZE_HEX" \
            "KSU_EXPECTED_HASH2=$CERT_HASH" \
            "KSU_SECOND_SIGNATURE_RELEASE=1"

        [[ -f kernel/kernelsu.ko ]] || die "kernel/kernelsu.ko was not produced"

        local dest="kernel/${kmi}_kernelsu.ko"
        mv kernel/kernelsu.ko "$dest"
        if have llvm-strip; then
            llvm-strip -d "$dest"
        else
            warn "llvm-strip not found, skipping symbol strip"
        fi
        cp "$dest" userspace/ksud/bin/aarch64/
        log "staged userspace/ksud/bin/aarch64/${kmi}_kernelsu.ko"
    done
}

abi_to_triple() {
    case "$1" in
        arm64-v8a)   echo aarch64-linux-android ;;
        armeabi-v7a) echo armv7-linux-androideabi ;;
        x86)         echo i686-linux-android ;;
        x86_64)      echo x86_64-linux-android ;;
        riscv64)     echo riscv64-linux-android ;;
        *) die "unknown abi: $1" ;;
    esac
}

abi_to_bindir() {
    case "$1" in
        arm64-v8a)   echo aarch64 ;;
        armeabi-v7a) echo armv7 ;;
        x86)         echo i686 ;;
        x86_64)      echo x86_64 ;;
        riscv64)     echo riscv64 ;;
        *) die "unknown abi: $1" ;;
    esac
}

# ksuinit is a separate binary that ksud embeds; boot-patch fails with
# "asset not found: ksuinit" if the built ksud does not carry it.
#
# ksuinit runs as the ramdisk `init`, before /system is mounted, so it MUST be
# statically linked: the dynamic linker (/system/bin/linker64) does not exist at
# that point, and a PIE build makes the device bootloop forever. This mirrors
# the RUSTFLAGS used by .github/workflows/ksuinit.yml.
build_ksuinit() {
    have cargo || die "cargo is required"
    local ndk="${ANDROID_NDK_HOME:-}"
    [[ -n "$ndk" ]] || die "ANDROID_NDK_HOME is not set"

    local abi triple arch clang builtins bindir src
    for abi in $ABIS; do
        triple="$(abi_to_triple "$abi")"
        arch="${triple%%-*}"
        case "$arch" in armv7*) arch=arm ;; esac
        clang="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/${triple}${ANDROID_API_LEVEL}-clang"
        [[ -x "$clang" ]] || die "NDK clang not found: $clang"
        builtins="$("$clang" --print-resource-dir)/lib/linux/libclang_rt.builtins-${arch}-android.a"
        [[ -f "$builtins" ]] || die "libclang_rt.builtins not found: $builtins"

        log "building ksuinit for $abi (static, api $ANDROID_API_LEVEL)"
        (
            export ANDROID_NDK_HOME="$ndk"
            # shellcheck disable=SC1091
            source .github/scripts/setup-rust-build.sh "$triple" "$ANDROID_API_LEVEL"
            export RUSTFLAGS="-C target-feature=+crt-static -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wno-unused-command-line-argument -C link-arg=$builtins"
            cargo build --package ksuinit --target="$triple" "--$KSUD_BUILD_TYPE"
        )

        src="target/$triple/$KSUD_BUILD_TYPE/ksuinit"
        [[ -f "$src" ]] || die "ksuinit not found at $src"
        file "$src" | grep -q "statically linked" || die "ksuinit is not statically linked: $(file -b "$src")"
        bindir="$(abi_to_bindir "$abi")"
        mkdir -p "userspace/ksud/bin/$bindir"
        cp "$src" "userspace/ksud/bin/$bindir/ksuinit"
        log "staged userspace/ksud/bin/$bindir/ksuinit"
    done
}

build_ksud() {
    have cargo || die "cargo is required"
    local ndk="${ANDROID_NDK_HOME:-}"
    [[ -n "$ndk" ]] || die "ANDROID_NDK_HOME is not set"

    local abi triple
    for abi in $ABIS; do
        triple="$(abi_to_triple "$abi")"
        log "building ksud for $abi (api $ANDROID_API_LEVEL, $KSUD_BUILD_TYPE)"
        (
            export ANDROID_NDK_HOME="$ndk"
            # shellcheck disable=SC1091
            source .github/scripts/setup-rust-build.sh "$triple" "$ANDROID_API_LEVEL"
            cargo build --target="$triple" "--$KSUD_BUILD_TYPE" --manifest-path ./userspace/ksud/Cargo.toml
        )
        log "ksud[$abi] -> target/$triple/$KSUD_BUILD_TYPE/ksud"
    done
}

build_apk() {
    export_signing_env
    log "gradle :app:assemble$([[ $APP_BUILD_TYPE == release ]] && echo Release || echo Debug)"
    ( cd manager && "./gradlew" ":app:assemble${APP_BUILD_TYPE^}" )
    log "APK: $(find "manager/app/build/outputs/apk/$APP_BUILD_TYPE" -name '*.apk' -printf '%p\n' 2>/dev/null | tail -1)"
}

build_repack() {
    export_signing_env
    have python3 || die "python3 is required"

    local args=() abi
    for abi in $ABIS; do args+=(-a "$abi"); done

    log "repacking + resigning APK with ksud"
    python3 repack_apk.py repack \
        -b "$APP_BUILD_TYPE" -t "$KSUD_BUILD_TYPE" \
        "${args[@]}" \
        -K "$KEYSTORE_PATH" -A "$KEY_ALIAS" \
        -P "$KEYSTORE_PASSWORD" -S "$KEY_PASSWORD" \
        --strip -o "$OUT_DIR"
    log "done - see $OUT_DIR/"
}

usage() {
    cat <<'EOF'
FolkSU build driver

usage: ./build.sh <step> [step...]

steps:
  lkm      build the kernel module(s) via the DDK docker image, stage them
           into userspace/ksud/bin/aarch64/ so ksud embeds them
  ksuinit  cross-compile the ksuinit helper ksud embeds (needed by boot-patch)
  ksud     cross-compile the ksud daemon with cargo-ndk
  apk      gradle assembleRelease (signed with manager/keystore.properties)
  repack   inject the freshly built ksud into the APK and re-sign it
  all      lkm -> ksuinit -> ksud -> apk -> repack

environment overrides:
  KMIS=android15-6.6        kernel modules to build
  ABIS=arm64-v8a            ABIs for ksud + repack
  DDK_RELEASE=20260828      DDK image release tag
  ANDROID_API_LEVEL=26      cargo-ndk api level
  KSUD_BUILD_TYPE=release   cargo profile (release/debug)
  APP_BUILD_TYPE=release    gradle build type (release/debug)
  OUT_DIR=dist              repack output directory
  KEYSTORE_PROPS=...        path to the signing properties file
EOF
}

main() {
    [[ $# -gt 0 ]] || { usage; exit 1; }
    local step
    for step in "$@"; do
        case "$step" in
            lkm)     build_lkm ;;
            ksuinit) build_ksuinit ;;
            ksud)    build_ksud ;;
            apk)     build_apk ;;
            repack)  build_repack ;;
            all)     build_lkm; build_ksuinit; build_ksud; build_apk; build_repack ;;
            -h|--help|help) usage ;;
            *) die "unknown step: $step (try --help)" ;;
        esac
    done
}

main "$@"
