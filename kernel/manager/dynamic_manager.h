#ifndef __KSU_H_DYNAMIC_MANAGER
#define __KSU_H_DYNAMIC_MANAGER

#include <linux/types.h>

#include "uapi/supercall.h"

struct apk_sign_match;

void ksu_dynamic_manager_init(void);
void ksu_dynamic_manager_exit(void);

/* Persist/restore the trusted signature list in the kernel, so it survives a
 * boot even when a third-party manager's ksud owns /data/adb/ksud. */
void ksu_dynamic_manager_persist(void);
void ksu_dynamic_manager_load(void);

/* Hot-path queries (must not take the dynamic-manager mutex). */
bool ksu_is_dynamic_manager_uid(uid_t uid);
bool ksu_has_dynamic_manager(void);

/* Slow-path query, takes the mutex. */
bool ksu_is_preset_manager_uid(uid_t uid);

/* Snapshot the tracked apps into apps[0..max_count); returns count written. */
u32 ksu_dynamic_manager_get_apps(struct ksu_dynamic_manager_app *apps,
                                 u32 max_count, u32 *total_count);

/* Record a scanned APK candidate. */
void ksu_dynamic_manager_note_scanned(uid_t appid,
                                      const struct apk_sign_match *match);

/* Replace the trusted signature list; sets *need_rescan when a rescan helps. */
int ksu_dynamic_manager_set(const struct ksu_dynamic_manager_sign *signs,
                            u32 count, bool *need_rescan);

/* Is (size, hash) in the user-managed trusted signature list? */
bool ksu_dynamic_manager_is_trusted_sign(u32 size, const char *hash);

/* Reported versionCode for a trusted dynamic manager appid, if known. */
bool ksu_dynamic_manager_version_code(uid_t appid, u32 *out);

#endif /* __KSU_H_DYNAMIC_MANAGER */
