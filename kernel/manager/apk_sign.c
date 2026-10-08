#include "util.h"
#include <linux/err.h>
#include <linux/fs.h>
#include <linux/gfp.h>
#include <linux/kernel.h>
#include <linux/limits.h>
#include <linux/slab.h>
#include <linux/version.h>
#ifdef CONFIG_KSU_DEBUG
#include <linux/moduleparam.h>
#endif
#include <crypto/hash.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
#include <crypto/sha2.h>
#else
#include <crypto/sha.h>
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 4, 0)
#include <linux/hex.h>
#endif

#include "manager/apk_sign.h"
#include "manager/dynamic_manager.h"
#include "uapi/app_profile.h"
#include "klog.h" // IWYU pragma: keep

typedef struct {
    unsigned size;
    const char *sha256;
    unsigned flags;
    const char *name;
} apk_sign_key_t;

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
/*
 * Only FolkSU's own release key is trusted by default. Every third-party
 * manager below is recognized but never auto-authorized: the user has to opt
 * each candidate in through the dynamic manager interface.
 */
#ifndef FOLKSU_TRUSTED_SIZE
#define FOLKSU_TRUSTED_SIZE EXPECTED_SIZE
#endif
#ifndef FOLKSU_TRUSTED_HASH
#define FOLKSU_TRUSTED_HASH EXPECTED_HASH
#endif

/* Bit i selects the i-th preset row (0xff keeps all of them). */
#ifndef KSU_PRESET_MANAGER_MASK
#define KSU_PRESET_MANAGER_MASK 0xff
#endif

static apk_sign_key_t apk_sign_keys[] = {
    { FOLKSU_TRUSTED_SIZE, FOLKSU_TRUSTED_HASH, APK_SIGN_FLAG_TRUSTED, "FolkSU" },
#if (KSU_PRESET_MANAGER_MASK & 0x01)
    { 0x033b, "c371061b19d8c7d7d6133c6a9bafe198fa944e50c1b31c9d8daa8d7f1fc2d2d6", 0, "KernelSU" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x02)
    { 0x375, "484fcba6e6c43b1fb09700633bf2fb4758f13cb0b2f4457b80d075084b26c588", 0, "KowSU" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x04)
    { 0x3e6, "79e590113c4c4c0c222978e413a5faa801666957b1212a328e46c00c69821bf7", 0, "KernelSU-Next" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x08)
    { 0x377, "d3469712b6214462764a1d8d3e5cbe1d6819a0b629791b9f4101867821f1df64", 0, "ReSukiSU" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x10)
    { 0x35c, "947ae944f3de4ed4c21a7e4f7953ecf351bfa2b36239da37a34111ad29993eef", 0, "SukiSU" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x20)
    { 0x180, "7e0c6d7278a3bb8e364e0fcba95afaf3666cf5ff3c245a3b63c8833bd0445cc4", 0, "MKSU" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x40)
    { 0x396, "f415f4ed9435427e1fdf7f1fccd4dbc07b3d6b8751e4dbcec6f19671f427870b", 0, "RKSU" },
#endif
#if (KSU_PRESET_MANAGER_MASK & 0x80)
    { 0x363, "4359c171f32543394cbc23ef908c4bb94cad7c8087002ba164c8230948c21549", 0, "xxKSU" },
#endif
};
#else
/* Dynamic manager disabled: preserve the classic two fixed trusted slots. */
static apk_sign_key_t apk_sign_keys[] = {
    { EXPECTED_SIZE, EXPECTED_HASH, APK_SIGN_FLAG_TRUSTED, "KernelSU" },
#ifdef EXPECTED_SIZE2
    { EXPECTED_SIZE2, EXPECTED_HASH2, APK_SIGN_FLAG_TRUSTED, "FolkSU" },
#endif
};
#endif

struct sdesc {
    struct shash_desc shash;
    char ctx[];
};

static struct sdesc *init_sdesc(struct crypto_shash *alg)
{
    struct sdesc *sdesc;
    int size;

    size = sizeof(struct shash_desc) + crypto_shash_descsize(alg);
    sdesc = kzalloc(size, GFP_KERNEL);
    if (!sdesc)
        return ERR_PTR(-ENOMEM);
    sdesc->shash.tfm = alg;
    return sdesc;
}

static int calc_hash(struct crypto_shash *alg, const unsigned char *data, unsigned int datalen, unsigned char *digest)
{
    struct sdesc *sdesc;
    int ret;

    sdesc = init_sdesc(alg);
    if (IS_ERR(sdesc)) {
        pr_info("can't alloc sdesc\n");
        return PTR_ERR(sdesc);
    }

    ret = crypto_shash_digest(&sdesc->shash, data, datalen, digest);
    kfree(sdesc);
    return ret;
}

static int ksu_sha256(const unsigned char *data, unsigned int datalen, unsigned char *digest)
{
    struct crypto_shash *alg;
    char *hash_alg_name = "sha256";
    int ret;

    alg = crypto_alloc_shash(hash_alg_name, 0, 0);
    if (IS_ERR(alg)) {
        pr_info("can't alloc alg %s\n", hash_alg_name);
        return PTR_ERR(alg);
    }
    ret = calc_hash(alg, data, datalen, digest);
    crypto_free_shash(alg);
    return ret;
}

static bool read_exact(struct file *fp, void *buffer, size_t size, loff_t *pos, loff_t end)
{
    if (*pos < 0 || *pos > end || size > (size_t)(end - *pos))
        return false;

    return kernel_read(fp, buffer, size, pos) == (ssize_t)size;
}

static bool read_length_prefixed_end(struct file *fp, loff_t *pos, loff_t container_end, loff_t *value_end)
{
    u32 length;

    if (!read_exact(fp, &length, sizeof(length), pos, container_end))
        return false;
    if (length > INT_MAX || length > (u64)(container_end - *pos))
        return false;

    *value_end = *pos + length;
    return true;
}

static bool check_block(struct file *fp, loff_t *pos, loff_t block_end, struct apk_sign_match *match)
{
    loff_t signers_end, signer_end, signed_data_end, digests_end, certificates_end;
    u32 certificate_size;

    // v2 block: signers sequence -> first signer -> signed data -> digests
    if (!read_length_prefixed_end(fp, pos, block_end, &signers_end) ||
        !read_length_prefixed_end(fp, pos, signers_end, &signer_end) ||
        !read_length_prefixed_end(fp, pos, signer_end, &signed_data_end) ||
        !read_length_prefixed_end(fp, pos, signed_data_end, &digests_end))
        return false;

    *pos = digests_end;
    if (!read_length_prefixed_end(fp, pos, signed_data_end, &certificates_end) ||
        !read_exact(fp, &certificate_size, sizeof(certificate_size), pos, certificates_end))
        return false;

    if (certificate_size > INT_MAX || certificate_size > (u64)(certificates_end - *pos))
        return false;

#define CERT_MAX_LENGTH 1024
    if (certificate_size > CERT_MAX_LENGTH) {
        pr_info("cert length overlimit\n");
        return false;
    }

    char cert[CERT_MAX_LENGTH];
    if (!read_exact(fp, cert, certificate_size, pos, certificates_end))
        return false;

    unsigned char digest[SHA256_DIGEST_SIZE];
    if (ksu_sha256(cert, certificate_size, digest)) {
        pr_info("sha256 error\n");
        return false;
    }

    char hash_str[SHA256_DIGEST_SIZE * 2 + 1];
    hash_str[SHA256_DIGEST_SIZE * 2] = '\0';

    bin2hex(hash_str, digest, SHA256_DIGEST_SIZE);
    pr_info("sha256: %s\n", hash_str);

    int i;
    for (i = 0; i < ARRAY_SIZE(apk_sign_keys); i++) {
        apk_sign_key_t key = apk_sign_keys[i];
        if (certificate_size != key.size)
            continue;
        if (strcmp(key.sha256, hash_str) != 0)
            continue;
        if (match) {
            match->index = i;
            match->trusted = (key.flags & APK_SIGN_FLAG_TRUSTED) != 0;
            match->preset = !match->trusted;
            match->dynamic = false;
            match->size = certificate_size;
            match->name = key.name;
            strscpy(match->hash, hash_str, sizeof(match->hash));
        }
        return true;
    }

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
    if (ksu_dynamic_manager_is_trusted_sign(certificate_size, hash_str)) {
        if (match) {
            match->index = -1;
            match->trusted = true;
            match->preset = false;
            match->dynamic = true;
            match->size = certificate_size;
            match->name = "dynamic";
            strscpy(match->hash, hash_str, sizeof(match->hash));
        }
        return true;
    }
#endif

    return false;
}

static __always_inline bool check_v2_signature(char *path, struct apk_sign_match *match)
{
    unsigned char buffer[0x10] = { 0 };
    u32 cd_offset, cd_size;
    u32 zip64_locator_magic;
    u64 size_of_block, size_of_block_at_head;

    loff_t pos, pairs_end, file_size, eocd_offset;

    bool v2_signing_valid = false;
    int v2_signing_blocks = 0;

    int i;
    struct file *fp = ksu_filp_open_nonotify(path, O_RDONLY | O_NOATIME);
    if (IS_ERR(fp)) {
        pr_err("open %s error.\n", path);
        return false;
    }

    file_size = generic_file_llseek(fp, 0, SEEK_END);
    if (file_size < 0)
        goto clean;

    // https://en.wikipedia.org/wiki/Zip_(file_format)#End_of_central_directory_record_(EOCD)
    for (i = 0;; ++i) {
        unsigned short comment_size;
        u32 magic;
        pos = file_size - i - 2;
        if (!read_exact(fp, &comment_size, sizeof(comment_size), &pos, file_size))
            goto clean;
        if (comment_size == i) {
            pos -= 22;
            if (!read_exact(fp, &magic, sizeof(magic), &pos, file_size))
                goto clean;
            if (magic == 0x06054b50) {
                eocd_offset = pos - sizeof(magic);
                break;
            }
        }
        if (i == 0xffff) {
            pr_info("error: cannot find eocd\n");
            goto clean;
        }
    }

    // reject ZIP64 before looking for a signing block
    if (eocd_offset >= 20) {
        pos = eocd_offset - 20;
        if (!read_exact(fp, &zip64_locator_magic, sizeof(zip64_locator_magic), &pos, file_size))
            goto clean;
        if (zip64_locator_magic == 0x07064b50)
            goto clean;
    }

    pos = eocd_offset + 12;
    // size of central directory
    if (!read_exact(fp, &cd_size, sizeof(cd_size), &pos, file_size))
        goto clean;
    // offset of central directory
    if (!read_exact(fp, &cd_offset, sizeof(cd_offset), &pos, file_size))
        goto clean;
    if ((u64)cd_offset > (u64)eocd_offset || (u64)cd_size != (u64)eocd_offset - cd_offset)
        goto clean;
    if (cd_offset < 0x20)
        goto clean;

    pairs_end = (loff_t)cd_offset - 0x18;
    pos = pairs_end;

    if (!read_exact(fp, &size_of_block, sizeof(size_of_block), &pos, cd_offset))
        goto clean;
    if (!read_exact(fp, buffer, sizeof(buffer), &pos, cd_offset))
        goto clean;
    if (memcmp((char *)buffer, "APK Sig Block 42", sizeof(buffer)))
        goto clean;

    if (size_of_block < 0x18 || size_of_block > INT_MAX - 0x8 || size_of_block > (u64)cd_offset - 0x8)
        goto clean;

    pos = (loff_t)cd_offset - (loff_t)size_of_block - 0x8;
    if (!read_exact(fp, &size_of_block_at_head, sizeof(size_of_block_at_head), &pos, pairs_end))
        goto clean;
    if (size_of_block_at_head != size_of_block)
        goto clean;

    // Scan every length-prefixed pair, matching AOSP's signing block parser
    // Each valid pair consumes an 8-byte length plus at least a 4-byte ID, so
    // malformed entries fail below instead of spinning in place.
    while (pos < pairs_end) {
        uint32_t id;
        u64 size_of_pair;
        loff_t pair_end;

        if (!read_exact(fp, &size_of_pair, sizeof(size_of_pair), &pos, pairs_end))
            goto invalid;
        if (size_of_pair < sizeof(id) || size_of_pair > INT_MAX || size_of_pair > (u64)(pairs_end - pos))
            goto invalid;

        pair_end = pos + (loff_t)size_of_pair;
        if (!read_exact(fp, &id, sizeof(id), &pos, pair_end))
            goto invalid;

        if (id == 0x7109871au) {
            v2_signing_blocks++;
            v2_signing_valid = check_block(fp, &pos, pair_end, match);
        } else if (id != 0x42726577u) { // APK verity padding
            // https://cs.android.com/android/platform/superproject/+/android-latest-release:tools/apksig/src/main/java/com/android/apksig/internal/apk/ApkSigningBlockUtils.java;l=102;drc=ebe4dfd4fd6550c949a6c7c2427484bf5e96500b
#ifdef CONFIG_KSU_DEBUG
            pr_info("Unexpected signature block id: 0x%08x\n", id);
#endif
            goto invalid;
        }
        pos = pair_end;
    }

    if (v2_signing_blocks != 1) {
#ifdef CONFIG_KSU_DEBUG
        pr_err("Unexpected v2 signature count: %d\n", v2_signing_blocks);
#endif
        v2_signing_valid = false;
    }

    goto clean;

invalid:
    v2_signing_valid = false;
clean:
    filp_close(fp, 0);

    return v2_signing_valid;
}

#ifdef CONFIG_KSU_DEBUG

int ksu_debug_manager_appid = -1;

#include "manager/manager_identity.h"

static int set_expected_size(const char *val, const struct kernel_param *kp)
{
    int rv = param_set_uint(val, kp);
    ksu_set_manager_appid(ksu_debug_manager_appid);
    pr_info("ksu_manager_appid set to %d\n", ksu_debug_manager_appid);
    return rv;
}

static struct kernel_param_ops expected_size_ops = {
    .set = set_expected_size,
    .get = param_get_uint,
};

module_param_cb(ksu_debug_manager_appid, &expected_size_ops, &ksu_debug_manager_appid, S_IRUSR | S_IWUSR);

#endif

int get_pkg_from_apk_path(char *pkg, const char *path)
{
    int len = strlen(path);
    if (len >= KSU_MAX_PACKAGE_NAME || len < 1)
        return -1;

    const char *last_slash = NULL;
    const char *second_last_slash = NULL;

    int i;
    for (i = len - 1; i >= 0; i--) {
        if (path[i] == '/') {
            if (!last_slash) {
                last_slash = &path[i];
            } else {
                second_last_slash = &path[i];
                break;
            }
        }
    }

    if (!last_slash || !second_last_slash)
        return -1;

    const char *last_hyphen = strchr(second_last_slash, '-');
    if (!last_hyphen || last_hyphen > last_slash)
        return -1;

    int pkg_len = last_hyphen - second_last_slash - 1;
    if (pkg_len >= KSU_MAX_PACKAGE_NAME || pkg_len <= 0)
        return -1;

    // Copying the package name
    memcpy(pkg, second_last_slash + 1, pkg_len);
    pkg[pkg_len] = '\0';

    return 0;
}

bool match_apk_signature(char *path, struct apk_sign_match *match)
{
    if (match) {
        match->index = -1;
        match->trusted = false;
        match->preset = false;
        match->dynamic = false;
        match->size = 0;
        match->name = NULL;
        match->hash[0] = '\0';
    }

    return check_v2_signature(path, match);
}

bool is_manager_apk(char *path)
{
#ifdef KSU_MANAGER_PACKAGE
    char pkg[KSU_MAX_PACKAGE_NAME];
    if (get_pkg_from_apk_path(pkg, path) < 0) {
        pr_err("Failed to get package name from apk path: %s\n", path);
        return false;
    }

    // pkg is `<real package>`
    if (strncmp(pkg, KSU_MANAGER_PACKAGE, sizeof(KSU_MANAGER_PACKAGE))) {
        return false;
    }
#endif

    struct apk_sign_match match = { .index = -1 };
    return match_apk_signature(path, &match) && match.trusted;
}
