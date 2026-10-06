package me.weishu.kernelsu.data.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Timestamp layout used for locally stored backup file names. */
const val BACKUP_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern(BACKUP_TIMESTAMP_PATTERN, Locale.ROOT)

private val UNSAFE_SEGMENT_CHARS = Regex("[^A-Za-z0-9._-]")

private val REPEATED_DOTS = Regex("\\.{2,}")

/** Formats [epochMs] as a compact backup timestamp in [zone] (UTC by default). */
fun backupTimestamp(epochMs: Long, zone: ZoneId = ZoneOffset.UTC): String =
    TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** Parses a timestamp produced by [backupTimestamp], returning `null` when malformed. */
fun parseBackupTimestamp(value: String, zone: ZoneId = ZoneOffset.UTC): Long? = try {
    LocalDateTime.parse(value, TIMESTAMP_FORMATTER).atZone(zone).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    null
}

/** Formats [epochMs] as an ISO-8601 UTC instant, e.g. `2026-10-06T12:00:00Z`. */
fun backupIsoTimestamp(epochMs: Long): String =
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

/**
 * Reduces [raw] to a single safe path segment.
 *
 * Characters outside `[A-Za-z0-9._-]` become `_`, leading and trailing dots are removed so a
 * segment can never be `.` or `..`, and an empty result becomes `_`. As a result the returned
 * value never contains a path separator or a traversal sequence.
 */
fun sanitizeSegment(raw: String): String {
    val cleaned = raw.trim()
        .replace(UNSAFE_SEGMENT_CHARS, "_")
        .replace(REPEATED_DOTS, ".")
        .trim('.')
    return cleaned.ifEmpty { "_" }
}

/** Builds a module archive file name: `<moduleId>-<versionName>-<versionCode>-<timestamp>.zip`. */
fun moduleArchiveName(moduleId: String, versionName: String, versionCode: Int, epochMs: Long): String =
    "${sanitizeSegment(moduleId)}-${sanitizeSegment(versionName)}-$versionCode-${backupTimestamp(epochMs)}.zip"

/** Derives the sidecar file name for a module archive. */
fun moduleMetaFileName(zipFileName: String): String =
    zipFileName.removeSuffix(".zip") + ".meta.json"

/** Builds an allow-list export file name: `allowlist-<timestamp>.json`. */
fun allowlistFileName(epochMs: Long): String =
    "allowlist-${backupTimestamp(epochMs)}.json"

/** Builds a boot image file name: `<partition>-stock-<sha1>.img`. */
fun bootBackupName(partition: String, sha1: String): String =
    "${sanitizeSegment(partition)}-stock-${sanitizeSegment(sha1)}.img"

/** Derives the sidecar file name for a boot image. */
fun bootMetaFileName(imgFileName: String): String =
    imgFileName.removeSuffix(".img") + ".meta.json"

/** Builds the storage-relative path of a module archive. */
fun moduleRelativePath(moduleId: String, fileName: String): String =
    "modules/${sanitizeSegment(moduleId)}/${sanitizeSegment(fileName)}"

/**
 * Maps an artifact to its storage-relative path.
 *
 * Module archives are grouped per module; allow-list and boot artifacts are grouped per kind. The
 * returned path is always relative and never contains a traversal segment.
 */
fun remotePathFor(kind: BackupKind, moduleId: String?, fileName: String): String = when (kind) {
    BackupKind.MODULE -> "modules/${sanitizeSegment(moduleId.orEmpty())}/${sanitizeSegment(fileName)}"
    BackupKind.ALLOWLIST -> "allowlist/${sanitizeSegment(fileName)}"
    BackupKind.BOOT -> "boot/${sanitizeSegment(fileName)}"
}
