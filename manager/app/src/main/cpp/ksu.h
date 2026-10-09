//
// Created by weishu on 2022/12/9.
//

#ifndef KERNELSU_KSU_H
#define KERNELSU_KSU_H

#include <cstdint>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <utility>

#include "uapi/ksu.h"

uint32_t get_kernel_uapi_version();

uint32_t get_manager_uapi_version();

uint32_t get_version();

// Re-reads the kernel info explicitly and returns the fresh snapshot, bypassing the TTL cache.
// Used when the caller must observe a just-changed identity (dynamic-manager grant/revoke).
struct ksu_get_info_cmd refresh_info();

bool uid_should_umount(int uid);

bool is_safe_mode();

bool is_lkm_mode();

bool is_lkm_bundled();

bool is_late_load_mode();

bool is_manager();

bool is_pr_build();

using p_key_t = char[KSU_MAX_PACKAGE_NAME];

bool set_app_profile(const app_profile *profile);

int get_app_profile(app_profile *profile);

// Su compat
bool set_su_enabled(bool enabled);

bool is_su_enabled();

// Kernel umount
bool set_kernel_umount_enabled(bool enabled);

bool is_kernel_umount_enabled();

// SELinux hide
int set_selinux_hide_enabled(bool enabled);

bool is_selinux_hide_enabled();

bool get_allow_list(struct ksu_new_get_allow_list_cmd *);

// Fetch the kernel-tracked dynamic-manager candidates (preset + user-claimed).
// Writes at most max_count entries into apps, returns the number written and
// stores the total tracked count in *total_count (may be null).
uint32_t get_dynamic_managers(struct ksu_dynamic_manager_app *apps, uint32_t max_count,
                              uint32_t *total_count);

// §6.1 compatibility probe: dynamic-manager ioctls are only guaranteed when the
// kernel UAPI matches the manager's; on a mismatch, probe KSU_FEATURE_DYNAMIC_MANAGER.
bool is_dynamic_manager_enabled();

inline std::pair<int, int> legacy_get_info() {
    int32_t version = -1;
    int32_t flags = 0;
    int32_t result = 0;
    prctl(0xDEADBEEF, 2, &version, &flags, &result);
    return {version, flags};
}

#endif //KERNELSU_KSU_H
