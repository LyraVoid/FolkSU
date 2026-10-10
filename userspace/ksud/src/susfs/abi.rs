// SPDX-License-Identifier: GPL-2.0-only
// Wire definitions independently implemented from susfs4ksu-lkm's GPL-2.0
// kernel/susfs_abi.h (inforcqb, 8f6776e7). BakaSU was consulted for command usage,
// not copied. Keep native C alignment, including the trailing err sentinel.
use anyhow::{Result, bail};

#[repr(C)]
pub struct Text<const N: usize> {
    pub text: [u8; N],
    pub err: i32,
}

impl<const N: usize> Text<N> {
    pub const fn empty() -> Self {
        Self {
            text: [0; N],
            err: 126,
        }
    }
}

#[repr(C)]
pub struct Toggle {
    pub enabled: bool,
    pub err: i32,
}

#[repr(C)]
pub struct Redirect {
    pub target: [u8; 256],
    pub destination: [u8; 256],
    pub uid_scheme: i32,
    pub err: i32,
}

#[repr(C)]
pub struct Uname {
    pub release: [u8; 65],
    pub version: [u8; 65],
    pub err: i32,
}

#[repr(C)]
#[derive(Clone)]
pub struct Kstat {
    pub is_static: i32,
    pub target_ino: libc::c_ulong,
    pub path: [u8; 256],
    pub ino: libc::c_ulong,
    pub dev: libc::c_ulong,
    pub nlink: u32,
    pub size: i64,
    pub atime_sec: libc::c_long,
    pub atime_nsec: libc::c_ulong,
    pub mtime_sec: libc::c_long,
    pub mtime_nsec: libc::c_ulong,
    pub ctime_sec: libc::c_long,
    pub ctime_nsec: libc::c_ulong,
    pub blocks: i64,
    pub blksize: libc::c_long,
    pub flags: i32,
    pub err: i32,
}

impl Default for Kstat {
    fn default() -> Self {
        Self {
            is_static: 0,
            target_ino: 0,
            path: [0; 256],
            ino: 0,
            dev: 0,
            nlink: 0,
            size: 0,
            atime_sec: 0,
            atime_nsec: 0,
            mtime_sec: 0,
            mtime_nsec: 0,
            ctime_sec: 0,
            ctime_nsec: 0,
            blocks: 0,
            blksize: 0,
            flags: 0,
            err: 126,
        }
    }
}

pub fn call<T>(command: u32, payload: &mut T) {
    // Invalid reboot magics are rejected by stock kernels; never use real reboot magics.
    // Payload remains owned and live until the syscall/task_work has returned.
    unsafe {
        libc::syscall(
            libc::SYS_reboot,
            0xdead_beef_u32,
            0xfafa_fafa_u32,
            command,
            std::ptr::from_mut(payload),
        );
    }
}

pub fn checked(err: i32) -> Result<()> {
    if err != 0 {
        bail!("SUSFS returned error {err}");
    }
    Ok(())
}

pub fn bytes<const N: usize>(value: &str) -> Result<[u8; N]> {
    if value.len() >= N || value.as_bytes().contains(&0) {
        bail!("SUSFS field exceeds ABI length");
    }
    let mut result = [0; N];
    result[..value.len()].copy_from_slice(value.as_bytes());
    Ok(result)
}

pub fn string(value: &[u8]) -> Option<String> {
    let end = value.iter().position(|c| *c == 0)?;
    std::str::from_utf8(&value[..end]).ok().map(str::to_owned)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn arm64_layouts() {
        assert_eq!(size_of::<Text<16>>(), 20);
        assert_eq!(std::mem::offset_of!(Text<8192>, err), 8192);
        assert_eq!(size_of::<Toggle>(), 8);
        assert_eq!(size_of::<Redirect>(), 520);
        assert_eq!(std::mem::offset_of!(Uname, err), 132);
        if size_of::<libc::c_ulong>() == 8 {
            assert_eq!(std::mem::offset_of!(Kstat, target_ino), 8);
            assert_eq!(std::mem::offset_of!(Kstat, flags), 368);
            assert_eq!(std::mem::offset_of!(Kstat, err), 372);
            assert_eq!(size_of::<Kstat>(), 376);
        }
    }
    #[test]
    fn strings_are_bounded_and_terminated() {
        assert!(bytes::<16>(&"x".repeat(16)).is_err());
        assert!(bytes::<16>("a\0b").is_err());
        assert!(string(&[b'x'; 16]).is_none());
    }
}
