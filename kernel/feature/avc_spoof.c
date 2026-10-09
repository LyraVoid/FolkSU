// SPDX-License-Identifier: GPL-2.0-only
#include <linux/atomic.h>
#include <linux/kprobes.h>
#include <linux/security.h>
#include <linux/version.h>

#include "include/arch.h"
#include "include/klog.h"
#include "policy/feature.h"
#include "selinux/selinux.h"
#include "feature/avc_spoof.h"

static bool avc_spoof_enabled;
static bool avc_spoof_registered;
static atomic64_t avc_sid_pair = ATOMIC64_INIT(0);

static int avc_spoof_pre_handler(struct kprobe *p, struct pt_regs *regs)
{
    unsigned long tsid;
    u64 sid_pair;

    if (!smp_load_acquire(&avc_spoof_enabled))
        return 0;
    sid_pair = atomic64_read(&avc_sid_pair);

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 4, 0)
    tsid = PT_REGS_PARM3(regs);
#else
    tsid = PT_REGS_PARM2(regs);
#endif
    if ((u32)tsid != (u32)(sid_pair >> 32))
        return 0;

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 4, 0)
    PT_REGS_PARM3(regs) = (u32)sid_pair;
#else
    PT_REGS_PARM2(regs) = (u32)sid_pair;
#endif
    return 0;
}

static struct kprobe avc_spoof_probe = {
    .symbol_name = "slow_avc_audit",
    .pre_handler = avc_spoof_pre_handler,
};

static int avc_spoof_get(u64 *value)
{
    *value = READ_ONCE(avc_spoof_enabled);
    return 0;
}

static int avc_spoof_set(u64 value)
{
    const char *cover = "u:r:priv_app:s0:c512,c768";
    u32 ksu_sid, cover_sid;
    int ret;

    if (value > 1)
        return -EINVAL;
    if (!value) {
        WRITE_ONCE(avc_spoof_enabled, false);
        return 0;
    }
    if (READ_ONCE(avc_spoof_enabled))
        return 0;

    ret = security_secctx_to_secid(KERNEL_SU_CONTEXT, strlen(KERNEL_SU_CONTEXT), &ksu_sid);
    if (ret)
        return ret;
    ret = security_secctx_to_secid(cover, strlen(cover), &cover_sid);
    if (ret)
        return ret;
    if (!ksu_sid || !cover_sid || ksu_sid == cover_sid)
        return -EINVAL;

    atomic64_set(&avc_sid_pair, ((u64)ksu_sid << 32) | cover_sid);
    /* Publish both SIDs before allowing probe callbacks to use them. */
    smp_store_release(&avc_spoof_enabled, true);
    return 0;
}

static const struct ksu_feature_handler avc_spoof_handler = {
    .feature_id = KSU_FEATURE_AVC_SPOOF,
    .name = "avc_spoof",
    .get_handler = avc_spoof_get,
    .set_handler = avc_spoof_set,
};

void __init ksu_avc_spoof_init(void)
{
    int ret = register_kprobe(&avc_spoof_probe);

    if (ret) {
        pr_warn("avc_spoof: probe unavailable: %d\n", ret);
        return;
    }
    ret = ksu_register_feature_handler(&avc_spoof_handler);
    if (ret) {
        unregister_kprobe(&avc_spoof_probe);
        return;
    }
    avc_spoof_registered = true;
}

void __exit ksu_avc_spoof_exit(void)
{
    if (!avc_spoof_registered)
        return;
    ksu_unregister_feature_handler(KSU_FEATURE_AVC_SPOOF);
    WRITE_ONCE(avc_spoof_enabled, false);
    unregister_kprobe(&avc_spoof_probe);
}
