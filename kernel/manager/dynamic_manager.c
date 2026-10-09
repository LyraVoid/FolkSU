#include <linux/compiler.h>
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/fs.h>
#include <linux/hashtable.h>
#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/mutex.h>
#include <linux/pid.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/task_work.h>

#include "klog.h" // IWYU pragma: keep
#include "ksu.h"
#include "manager/apk_sign.h"
#include "manager/dynamic_manager.h"
#include "manager/manager_identity.h"
#include "policy/feature.h"
#include "uapi/supercall.h"

#define KSU_DYNAMIC_MANAGER_HASH_BITS 6

#define KERNEL_SU_DYNAMIC_MANAGER "/data/adb/ksu/dynamic_manager"
#define DYNAMIC_MANAGER_FILE_MAGIC 0x4d554b53 /* 'KSUM', u32 */
#define DYNAMIC_MANAGER_FILE_VERSION 1 /* u32 */

struct dynamic_manager_sign {
    struct hlist_node node;
    u32 size;
    u32 version_code;
    char hash[65];
    bool matched; /* a scanned app already used this sign (run-local) */
};

struct dynamic_manager_app {
    struct hlist_node node;
    uid_t appid;
    u32 size;
    u32 version_code;
    char hash[65];
    bool preset;
    bool trusted;
};

/* Fixed on-disk record shared by the persist and load paths. */
struct dynamic_manager_disk_sign {
    u32 size;
    u32 version_code;
    char hash[65];
};

static DEFINE_MUTEX(dynamic_manager_lock);
static DEFINE_HASHTABLE(dynamic_manager_signs, KSU_DYNAMIC_MANAGER_HASH_BITS);
static DEFINE_HASHTABLE(dynamic_manager_apps, KSU_DYNAMIC_MANAGER_HASH_BITS);

/*
 * Published snapshot of trusted appids. ksu_is_dynamic_manager_uid() runs from
 * the setuid/umount paths, so readers must not take dynamic_manager_lock.
 */
static uid_t trusted_dynamic_appids[KSU_DYNAMIC_MANAGER_MAX_APPS];
static u32 trusted_dynamic_count;

static u32 sign_key(u32 size, const char *hash)
{
    u32 key = size;
    int i;

    if (hash) {
        for (i = 0; i < 8 && hash[i]; i++)
            key = key * 33 + hash[i];
    }

    return key;
}

static struct dynamic_manager_sign *find_sign_locked(u32 size, const char *hash)
{
    struct dynamic_manager_sign *sign;

    hash_for_each_possible(dynamic_manager_signs, sign, node, sign_key(size, hash))
    {
        if (sign->size == size && strncmp(sign->hash, hash, sizeof(sign->hash)) == 0)
            return sign;
    }

    return NULL;
}

static struct dynamic_manager_app *find_app_locked(uid_t appid)
{
    struct dynamic_manager_app *app;

    hash_for_each_possible(dynamic_manager_apps, app, node, appid)
    {
        if (app->appid == appid)
            return app;
    }

    return NULL;
}

static bool normalize_hash(char dst[65], const char *src)
{
    static const char hex[] = "0123456789abcdef";
    int i;

    if (strnlen(src, 65) != 64)
        return false;

    for (i = 0; i < 64; i++) {
        int val = hex_to_bin(src[i]);

        if (val < 0)
            return false;
        dst[i] = hex[val];
    }
    dst[64] = '\0';
    return true;
}

static void clear_signs_locked(void)
{
    struct dynamic_manager_sign *sign;
    struct hlist_node *tmp;
    int bucket;

    hash_for_each_safe(dynamic_manager_signs, bucket, tmp, sign, node)
    {
        hash_del(&sign->node);
        kfree(sign);
    }
}

static void rebuild_trusted_cache_locked(void)
{
    struct dynamic_manager_app *app;
    uid_t appids[KSU_DYNAMIC_MANAGER_MAX_APPS];
    u32 count = 0;
    int bucket;
    u32 i;

    smp_store_release(&trusted_dynamic_count, 0);

    hash_for_each(dynamic_manager_apps, bucket, app, node)
    {
        if (!app->trusted)
            continue;
        if (count >= KSU_DYNAMIC_MANAGER_MAX_APPS)
            break;
        appids[count++] = app->appid;
    }

    for (i = 0; i < count; i++)
        WRITE_ONCE(trusted_dynamic_appids[i], appids[i]);

    smp_store_release(&trusted_dynamic_count, count);
}

static int dynamic_manager_feature_get(u64 *value)
{
    *value = 1;
    return 0;
}

static const struct ksu_feature_handler dynamic_manager_feature_handler = {
    .feature_id = KSU_FEATURE_DYNAMIC_MANAGER,
    .name = "dynamic_manager",
    .get_handler = dynamic_manager_feature_get,
    .set_handler = NULL,
};

void __init ksu_dynamic_manager_init(void)
{
    hash_init(dynamic_manager_signs);
    hash_init(dynamic_manager_apps);
    smp_store_release(&trusted_dynamic_count, 0);

    if (ksu_register_feature_handler(&dynamic_manager_feature_handler))
        pr_warn("dynamic_manager: failed to register feature handler\n");
}

void __exit ksu_dynamic_manager_exit(void)
{
    struct dynamic_manager_app *app;
    struct hlist_node *tmp;
    int bucket;

    mutex_lock(&dynamic_manager_lock);
    smp_store_release(&trusted_dynamic_count, 0);
    clear_signs_locked();
    hash_for_each_safe(dynamic_manager_apps, bucket, tmp, app, node)
    {
        hash_del(&app->node);
        kfree(app);
    }
    mutex_unlock(&dynamic_manager_lock);

    ksu_unregister_feature_handler(KSU_FEATURE_DYNAMIC_MANAGER);
}

bool ksu_is_dynamic_manager_uid(uid_t uid)
{
    uid_t appid = uid % KSU_PER_USER_RANGE;
    u32 count = smp_load_acquire(&trusted_dynamic_count);
    u32 i;

    for (i = 0; i < count; i++) {
        if (READ_ONCE(trusted_dynamic_appids[i]) == appid)
            return true;
    }

    return false;
}

bool ksu_is_preset_manager_uid(uid_t uid)
{
    struct dynamic_manager_app *app;
    uid_t appid = uid % KSU_PER_USER_RANGE;
    bool result = false;

    mutex_lock(&dynamic_manager_lock);
    app = find_app_locked(appid);
    result = app && app->preset;
    mutex_unlock(&dynamic_manager_lock);

    return result;
}

bool ksu_has_dynamic_manager(void)
{
    return smp_load_acquire(&trusted_dynamic_count) > 0;
}

u32 ksu_dynamic_manager_get_apps(struct ksu_dynamic_manager_app *apps,
                                 u32 max_count, u32 *total_count)
{
    struct dynamic_manager_app *app;
    u32 count = 0;
    u32 total = 0;
    int bucket;

    mutex_lock(&dynamic_manager_lock);
    hash_for_each(dynamic_manager_apps, bucket, app, node)
    {
        u32 flags = 0;

        if (app->preset)
            flags |= KSU_DYNAMIC_MANAGER_FLAG_PRESET;
        if (app->trusted)
            flags |= KSU_DYNAMIC_MANAGER_FLAG_TRUSTED;
        if (!flags)
            continue;

        if (apps && count < max_count) {
            apps[count].appid = app->appid;
            apps[count].flags = flags;
            count++;
        }
        total++;
    }
    mutex_unlock(&dynamic_manager_lock);

    if (total_count)
        *total_count = total;

    return count;
}

void ksu_dynamic_manager_note_scanned(uid_t appid, const struct apk_sign_match *match)
{
    struct dynamic_manager_app *app;
    bool trusted;

    if (!match || !match->hash[0])
        return;

    mutex_lock(&dynamic_manager_lock);
    app = find_app_locked(appid);
    if (!app) {
        app = kzalloc(sizeof(*app), GFP_KERNEL);
        if (!app)
            goto out;
        app->appid = appid;
        hash_add(dynamic_manager_apps, &app->node, appid);
    }

    app->size = match->size;
    strscpy(app->hash, match->hash, sizeof(app->hash));
    app->preset = app->preset || match->preset;

    {
        struct dynamic_manager_sign *sign = find_sign_locked(app->size, app->hash);

        trusted = match->trusted || sign != NULL;
        app->trusted = app->trusted || trusted;
        if (sign)
            app->version_code = sign->version_code;
    }

    rebuild_trusted_cache_locked();
out:
    mutex_unlock(&dynamic_manager_lock);
}

int ksu_dynamic_manager_set(const struct ksu_dynamic_manager_sign *signs, u32 count,
                            bool *need_rescan)
{
    u32 i;
    bool unmatched = false;
    u32 valid_count = 0;

    if (need_rescan)
        *need_rescan = false;

    if (count > KSU_DYNAMIC_MANAGER_MAX_SIGNS)
        return -EINVAL;

    mutex_lock(&dynamic_manager_lock);
    clear_signs_locked();

    for (i = 0; i < count; i++) {
        struct dynamic_manager_sign *sign;
        char normalized_hash[65];

        if (!signs[i].size || !normalize_hash(normalized_hash, signs[i].hash))
            continue;

        sign = kzalloc(sizeof(*sign), GFP_KERNEL);
        if (!sign) {
            mutex_unlock(&dynamic_manager_lock);
            return -ENOMEM;
        }

        sign->size = signs[i].size;
        sign->version_code = signs[i].version_code;
        strscpy(sign->hash, normalized_hash, sizeof(sign->hash));
        hash_add(dynamic_manager_signs, &sign->node, sign_key(sign->size, sign->hash));
        valid_count++;
    }

    {
        int bucket;
        struct dynamic_manager_app *app;

        hash_for_each(dynamic_manager_apps, bucket, app, node)
        {
            struct dynamic_manager_sign *sign =
                find_sign_locked(app->size, app->hash);

            app->trusted = false;
            if (sign) {
                app->trusted = true;
                app->version_code = sign->version_code;
                sign->matched = true;
            }
        }
    }

    /*
     * Trigger a scan when any configured sign has no matching app yet, so a
     * manager added after another one is still discovered. A single already
     * matched sign must not suppress scanning for the new ones.
     */
    {
        int bucket;
        struct dynamic_manager_sign *sign;

        hash_for_each(dynamic_manager_signs, bucket, sign, node)
        {
            if (!sign->matched) {
                unmatched = true;
                break;
            }
        }
    }

    rebuild_trusted_cache_locked();

    if (valid_count && unmatched && need_rescan)
        *need_rescan = true;

    mutex_unlock(&dynamic_manager_lock);
    return 0;
}

bool ksu_dynamic_manager_is_trusted_sign(u32 size, const char *hash)
{
    bool result;

    if (!size || !hash)
        return false;

    mutex_lock(&dynamic_manager_lock);
    result = find_sign_locked(size, hash) != NULL;
    mutex_unlock(&dynamic_manager_lock);

    return result;
}

bool ksu_dynamic_manager_version_code(uid_t appid, u32 *out)
{
    struct dynamic_manager_app *app;
    bool found = false;

    if (!out)
        return false;

    mutex_lock(&dynamic_manager_lock);
    app = find_app_locked(appid % KSU_PER_USER_RANGE);
    if (app && app->trusted && app->version_code) {
        *out = app->version_code;
        found = true;
    }
    mutex_unlock(&dynamic_manager_lock);

    return found;
}

static void do_persist_dynamic_manager(struct callback_head *_cb)
{
    u32 magic = DYNAMIC_MANAGER_FILE_MAGIC;
    u32 version = DYNAMIC_MANAGER_FILE_VERSION;
    struct dynamic_manager_sign *sign;
    loff_t off = 0;
    int bucket;

    const struct cred *saved = override_creds(ksu_cred);
    struct file *fp = filp_open(KERNEL_SU_DYNAMIC_MANAGER,
                                O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (IS_ERR(fp)) {
        pr_err("dynamic_manager: create file failed: %ld\n", PTR_ERR(fp));
        goto out;
    }

    if (kernel_write(fp, &magic, sizeof(magic), &off) != sizeof(magic)) {
        pr_err("dynamic_manager: write magic failed\n");
        goto close_file;
    }
    if (kernel_write(fp, &version, sizeof(version), &off) != sizeof(version)) {
        pr_err("dynamic_manager: write version failed\n");
        goto close_file;
    }

    mutex_lock(&dynamic_manager_lock);
    hash_for_each (dynamic_manager_signs, bucket, sign, node) {
        struct dynamic_manager_disk_sign disk = {
            .size = sign->size,
            .version_code = sign->version_code,
        };

        strscpy(disk.hash, sign->hash, sizeof(disk.hash));
        kernel_write(fp, &disk, sizeof(disk), &off);
    }
    mutex_unlock(&dynamic_manager_lock);

close_file:
    filp_close(fp, 0);
out:
    revert_creds(saved);
    kfree(_cb);
}

void ksu_dynamic_manager_persist(void)
{
    struct task_struct *tsk;
    struct callback_head *cb;

    rcu_read_lock();
    tsk = get_pid_task(find_vpid(1), PIDTYPE_PID);
    if (!tsk) {
        rcu_read_unlock();
        pr_err("dynamic_manager: find init task err\n");
        return;
    }
    rcu_read_unlock();

    cb = kzalloc(sizeof(*cb), GFP_KERNEL);
    if (!cb) {
        pr_err("dynamic_manager: alloc cb err\n");
        goto put_task;
    }
    cb->func = do_persist_dynamic_manager;
    if (task_work_add(tsk, cb, TWA_RESUME)) {
        kfree(cb);
        pr_warn("dynamic_manager: add task_work failed\n");
    }

put_task:
    put_task_struct(tsk);
}

void ksu_dynamic_manager_load(void)
{
    struct dynamic_manager_disk_sign disk;
    struct file *fp;
    loff_t off = 0;
    u32 magic;
    u32 version;

    fp = filp_open(KERNEL_SU_DYNAMIC_MANAGER, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        pr_info("dynamic_manager: no persisted list: %ld\n", PTR_ERR(fp));
        return;
    }

    if (kernel_read(fp, &magic, sizeof(magic), &off) != sizeof(magic) ||
        magic != DYNAMIC_MANAGER_FILE_MAGIC) {
        pr_err("dynamic_manager: invalid file magic\n");
        goto exit;
    }

    if (kernel_read(fp, &version, sizeof(version), &off) != sizeof(version) ||
        version != DYNAMIC_MANAGER_FILE_VERSION) {
        pr_err("dynamic_manager: invalid file version\n");
        goto exit;
    }

    mutex_lock(&dynamic_manager_lock);
    clear_signs_locked();

    while (true) {
        struct dynamic_manager_sign *sign;
        char normalized_hash[65];

        if (kernel_read(fp, &disk, sizeof(disk), &off) != sizeof(disk))
            break;

        if (!disk.size || !normalize_hash(normalized_hash, disk.hash))
            continue;

        sign = kzalloc(sizeof(*sign), GFP_KERNEL);
        if (!sign)
            break;

        sign->size = disk.size;
        sign->version_code = disk.version_code;
        strscpy(sign->hash, normalized_hash, sizeof(sign->hash));
        hash_add(dynamic_manager_signs, &sign->node,
                 sign_key(sign->size, sign->hash));
    }

    {
        int bucket;
        struct dynamic_manager_app *app;

        hash_for_each(dynamic_manager_apps, bucket, app, node)
        {
            struct dynamic_manager_sign *sign =
                find_sign_locked(app->size, app->hash);

            app->trusted = sign != NULL;
            if (sign)
                app->version_code = sign->version_code;
        }
    }
    rebuild_trusted_cache_locked();
    mutex_unlock(&dynamic_manager_lock);

    pr_info("dynamic_manager: restored persisted list\n");

exit:
    filp_close(fp, 0);
}
