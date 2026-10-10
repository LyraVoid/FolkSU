#!/usr/bin/env python3
"""Host regression tests that compile the actual throne tracker reader."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "kernel/manager/throne_tracker.c"

STUBS = r"""
#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#define KSU_MAX_PACKAGE_NAME 256
#define GFP_KERNEL 0
#define SYSTEM_PACKAGES_LIST_PATH "unused"
#define O_RDONLY 0
#define min_t(type, a, b) ((type)(a) < (type)(b) ? (type)(a) : (type)(b))
#define IS_ERR(fp) false
#define PTR_ERR(fp) 0L
#define pr_err(...) ((void)0)
typedef uint32_t u32;
struct cred {};
static const struct cred *ksu_cred;
struct list_head { struct list_head *next, *prev; };
struct uid_data {
    struct list_head list;
    u32 uid;
    char package[KSU_MAX_PACKAGE_NAME];
};
struct file { const char *data; size_t len; };
static struct file input;
static long fail_offset = -1;
static long short_offset = -1;
static int fail_alloc = -1;
static void *override_creds(const struct cred *cred) { return (void *)cred; }
static struct file *filp_open(const char *p, int flags, int mode) { return &input; }
static void filp_close(struct file *fp, int unused) {}
static ssize_t kernel_read(struct file *fp, void *buf, size_t len, loff_t *pos)
{
    if (*pos == fail_offset)
        return -EIO;
    size_t count = fp->len - *pos;
    if (count > len)
        count = len;
    if (*pos == short_offset && len > 1 && count)
        count--;
    memcpy(buf, fp->data + *pos, count);
    *pos += count;
    return count;
}
static void *kzalloc(size_t size, int flags)
{
    if (fail_alloc == 0)
        return NULL;
    if (fail_alloc > 0)
        fail_alloc--;
    return calloc(1, size);
}
static int kstrtou32(const char *s, int base, u32 *out)
{
    char *end;
    if (*s == '+')
        s++;
    if (*s < '0' || *s > '9')
        return -EINVAL;
    errno = 0;
    unsigned long long value = strtoull(s, &end, base);
    if (*end == '\n' && !end[1])
        end++;
    if (*end || errno || value > UINT32_MAX)
        return -EINVAL;
    *out = value;
    return 0;
}
static void strscpy(char *dst, const char *src, size_t size)
{
    snprintf(dst, size, "%s", src);
}
#define INIT_LIST_HEAD(h) ((h)->next = (h)->prev = (h))
static void list_add_tail(struct list_head *node, struct list_head *head)
{
    node->prev = head->prev;
    node->next = head;
    head->prev->next = node;
    head->prev = node;
}
"""

EPILOGUE = r"""
    while (uid_list.next != &uid_list) {
        struct uid_data *entry = (struct uid_data *)uid_list.next;
        printf("%s %u\n", entry->package, entry->uid);
        uid_list.next = entry->list.next;
        free(entry);
    }
    return parse_ok ? 0 : 2;
out_revert_cred:
    return 3;
}
int main(void)
{
    size_t size = 0, capacity = 4096;
    char *data = malloc(capacity);
    int c;
    while ((c = getchar()) != EOF) {
        if (size == capacity) {
            capacity *= 2;
            data = realloc(data, capacity);
        }
        data[size++] = c;
    }
    input = (struct file){data, size};
    if (getenv("FAIL_OFFSET")) fail_offset = atol(getenv("FAIL_OFFSET"));
    if (getenv("SHORT_OFFSET")) short_offset = atol(getenv("SHORT_OFFSET"));
    if (getenv("FAIL_ALLOC")) fail_alloc = atoi(getenv("FAIL_ALLOC"));
    int rc = scan_packages();
    free(data);
    return rc;
}
"""


class PackagesListTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix="packages-list-")
        cls.addClassCleanup(cls.tmp.cleanup)
        cls.binary = Path(cls.tmp.name) / "reader"
        source = SOURCE.read_text()
        start = source.index("static int read_package_uid(")
        end = source.index("\n    /*\n     * A truncated parse", start)
        reader = source[start:end].replace(
            "static void do_track_throne(bool prune_only)",
            "static int scan_packages(void)",
        )
        harness = Path(cls.tmp.name) / "reader.c"
        harness.write_text(STUBS + reader + EPILOGUE)
        subprocess.run(
            [os.environ.get("CC", "cc"), "-std=gnu11", "-Wall", "-Wextra",
             "-Werror", "-Wno-unused-parameter", "-Wno-unused-variable",
             str(harness), "-o", str(cls.binary)],
            check=True,
        )

    def scan(self, data, expected, **faults):
        env = os.environ.copy()
        env.update({key: str(value) for key, value in faults.items()})
        result = subprocess.run(
            [str(self.binary)], input=data, capture_output=True, env=env,
        )
        self.assertEqual(result.returncode, 0 if expected is not None else 2,
                         result.stderr.decode())
        if expected is not None:
            self.assertEqual(result.stdout.decode().splitlines(), expected)

    def test_shared_uid_does_not_consume_next_record(self):
        self.scan(b"android.media 10061\nandroid.uid.networkstack 1073\n",
                  ["android.media 10061", "android.uid.networkstack 1073"])

    def test_optional_fields_and_long_records(self):
        self.scan(b"example.app 10450 " + b"optional " * 300 + b"\n",
                  ["example.app 10450"])

    def test_final_record_without_newline(self):
        self.scan(b"first.app 1\nlast.app 4294967295",
                  ["first.app 1", "last.app 4294967295"])

    def test_whitespace(self):
        self.scan(b"one.app\t10001\r\ntwo.app   10002 optional\n",
                  ["one.app 10001", "two.app 10002"])

    def test_maximum_package_and_uid(self):
        package = b"a" * 255
        self.scan(package + b" 4294967295\n",
                  [package.decode() + " 4294967295"])
        self.scan(package + b" 4294967295 " + b"x" * 300 + b"\n",
                  [package.decode() + " 4294967295"])

    def test_invalid_records_fail_closed(self):
        for record in (b"missing.uid", b"bad.uid text", b"bad.uid 4294967296",
                       b"bad.uid -1", b"bad.uid 12x", b"bad.uid ", b" 123",
                       b"bad.uid 12\0junk", b"a" * 256 + b" 123",
                       b"a" * 255 + b" " + b"0" * 20, b""):
            with self.subTest(record=record[:60]):
                self.scan(b"good.app 10001\n" + record + b"\n", None)

    def test_io_and_allocation_failure(self):
        data = b"first.app 1\nsecond.app 2\n"
        self.scan(data, None, FAIL_OFFSET=5)
        self.scan(data, None, SHORT_OFFSET=0)
        self.scan(data, None, FAIL_ALLOC=1)

    def test_empty_file(self):
        self.scan(b"", [])

    @unittest.skipUnless(os.environ.get("PACKAGES_LIST_FIXTURE"),
                         "set PACKAGES_LIST_FIXTURE to validate a device export")
    def test_device_fixture(self):
        data = Path(os.environ["PACKAGES_LIST_FIXTURE"]).read_bytes()
        expected = [b" ".join(line.split()[:2]).decode()
                    for line in data.splitlines()]
        self.scan(data, expected)


if __name__ == "__main__":
    unittest.main()
