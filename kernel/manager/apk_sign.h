#ifndef __KSU_H_APK_V2_SIGN
#define __KSU_H_APK_V2_SIGN

#include <linux/types.h>

#define APK_SIGN_FLAG_TRUSTED 0x1

struct apk_sign_match {
    int index; /* matched table index, -1 for a dynamic/unmatched signature */
    bool trusted; /* the signature is authorized to be a manager */
    bool preset; /* a recognized known-manager signature that is not trusted */
    bool dynamic; /* matched a user-added dynamic manager signature */
    u32 size; /* certificate size */
    char hash[65]; /* lowercase hex SHA-256 of the certificate */
    const char *name; /* human readable manager name, may be NULL */
};

bool is_manager_apk(char *path);
bool match_apk_signature(char *path, struct apk_sign_match *match);
int get_pkg_from_apk_path(char *pkg, const char *path);

#endif
