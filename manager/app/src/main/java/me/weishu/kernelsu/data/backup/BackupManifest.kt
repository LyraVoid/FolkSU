package me.weishu.kernelsu.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Version shared by every backup document written by this build. */
const val BACKUP_FORMAT_VERSION = 1

/** Schema identifier of a module backup sidecar. */
const val MODULE_BACKUP_SCHEMA = "folksu.module.backup"

/** Schema identifier of a boot image backup sidecar. */
const val BOOT_BACKUP_SCHEMA = "folksu.boot.backup"

/** Schema identifier of the optional remote index document. */
const val BACKUP_MANIFEST_SCHEMA = "folksu.backup.manifest"

/**
 * Sidecar metadata for a module archive.
 *
 * The document is written next to the archive as `<name>.meta.json` and records everything needed
 * to display or restore the archive without opening the ZIP.
 */
data class ModuleBackupMeta(
    val moduleId: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val author: String,
    val metamodule: Boolean,
    val originalFileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val source: BackupOrigin,
    val createdAt: String,
) {
    /** Serialises this metadata to a 2-space indented JSON object. */
    fun toJson(): String {
        val json = JSONObject()
        json.put("schema", MODULE_BACKUP_SCHEMA)
        json.put("version", BACKUP_FORMAT_VERSION)
        json.put("moduleId", moduleId)
        json.put("name", name)
        json.put("versionName", versionName)
        json.put("versionCode", versionCode)
        json.put("author", author)
        json.put("metamodule", metamodule)
        json.put("originalFileName", originalFileName)
        json.put("sizeBytes", sizeBytes)
        json.put("sha256", sha256)
        json.put("source", source.value)
        json.put("createdAt", createdAt)
        return json.toString(2)
    }

    companion object {
        /** Parses a sidecar document, returning a typed failure instead of throwing. */
        fun fromJson(json: String): Result<ModuleBackupMeta> = parseBackupDocument {
            val obj = parseBackupObject(json)
            obj.requireSchemaVersion(MODULE_BACKUP_SCHEMA)
            ModuleBackupMeta(
                moduleId = obj.requireString("moduleId"),
                name = obj.optString("name", ""),
                versionName = obj.optString("versionName", ""),
                versionCode = obj.optInt("versionCode", 0),
                author = obj.optString("author", ""),
                metamodule = obj.optBoolean("metamodule", false),
                originalFileName = obj.optString("originalFileName", ""),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                sha256 = obj.optString("sha256", ""),
                source = obj.requireSource(),
                createdAt = obj.optString("createdAt", ""),
            )
        }
    }
}

/** Sidecar metadata for an exported stock boot image. */
data class BootBackupMeta(
    val partition: String,
    val sha1: String,
    val sizeBytes: Long,
    val device: String,
    val fingerprint: String,
    val createdAt: String,
) {
    /** Serialises this metadata to a 2-space indented JSON object. */
    fun toJson(): String {
        val json = JSONObject()
        json.put("schema", BOOT_BACKUP_SCHEMA)
        json.put("version", BACKUP_FORMAT_VERSION)
        json.put("partition", partition)
        json.put("sha1", sha1)
        json.put("sizeBytes", sizeBytes)
        json.put("device", device)
        json.put("fingerprint", fingerprint)
        json.put("createdAt", createdAt)
        return json.toString(2)
    }

    companion object {
        /** Parses a sidecar document, returning a typed failure instead of throwing. */
        fun fromJson(json: String): Result<BootBackupMeta> = parseBackupDocument {
            val obj = parseBackupObject(json)
            obj.requireSchemaVersion(BOOT_BACKUP_SCHEMA)
            BootBackupMeta(
                partition = obj.requireString("partition"),
                sha1 = obj.requireString("sha1"),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                device = obj.optString("device", ""),
                fingerprint = obj.optString("fingerprint", ""),
                createdAt = obj.optString("createdAt", ""),
            )
        }
    }
}

/** One entry of the optional remote [BackupManifest] index. */
data class BackupManifestEntry(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
    val kind: BackupKind,
    val createdAt: String,
)

/**
 * Optional index of the artifacts present in a backup backend.
 *
 * The index is a convenience only: every path is self-describing, so a missing or stale manifest
 * still allows recovery.
 */
data class BackupManifest(
    val updatedAt: String,
    val entries: List<BackupManifestEntry>,
) {
    /** Serialises this index to a 2-space indented JSON object. */
    fun toJson(): String {
        val array = JSONArray()
        for (entry in entries) {
            val item = JSONObject()
            item.put("path", entry.path)
            item.put("sha256", entry.sha256)
            item.put("sizeBytes", entry.sizeBytes)
            item.put("kind", entry.kind.value)
            item.put("createdAt", entry.createdAt)
            array.put(item)
        }
        val json = JSONObject()
        json.put("schema", BACKUP_MANIFEST_SCHEMA)
        json.put("version", BACKUP_FORMAT_VERSION)
        json.put("updatedAt", updatedAt)
        json.put("entries", array)
        return json.toString(2)
    }

    companion object {
        /** Parses an index document, returning a typed failure instead of throwing. */
        fun fromJson(json: String): Result<BackupManifest> = parseBackupDocument {
            val obj = parseBackupObject(json)
            obj.requireSchemaVersion(BACKUP_MANIFEST_SCHEMA)
            val array = obj.optJSONArray("entries") ?: JSONArray()
            val entries = ArrayList<BackupManifestEntry>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw BackupFormatException(
                        BackupFormatError.MALFORMED,
                        "manifest entry $index is not an object",
                    )
                val kindValue = item.optString("kind", "")
                val kind = BackupKind.fromValue(kindValue)
                    ?: throw BackupFormatException(
                        BackupFormatError.INVALID_FIELD,
                        "unknown backup kind: $kindValue",
                    )
                entries += BackupManifestEntry(
                    path = item.requireString("path"),
                    sha256 = item.optString("sha256", ""),
                    sizeBytes = item.optLong("sizeBytes", 0L),
                    kind = kind,
                    createdAt = item.optString("createdAt", ""),
                )
            }
            BackupManifest(
                updatedAt = obj.optString("updatedAt", ""),
                entries = entries,
            )
        }
    }
}

/** Parses a JSON object, converting a syntax error into a typed failure. */
internal fun parseBackupObject(json: String): JSONObject = try {
    JSONObject(json)
} catch (e: JSONException) {
    throw BackupFormatException(BackupFormatError.MALFORMED, "invalid JSON document", e)
}

/** Rejects documents whose `schema`/`version` pair is not [expectedSchema]/[BACKUP_FORMAT_VERSION]. */
internal fun JSONObject.requireSchemaVersion(expectedSchema: String) {
    val schema = optString("schema", "")
    if (schema != expectedSchema) {
        throw BackupFormatException(
            BackupFormatError.UNSUPPORTED_SCHEMA,
            "expected schema '$expectedSchema' but found '$schema'",
        )
    }
    val version = optInt("version", -1)
    if (version != BACKUP_FORMAT_VERSION) {
        throw BackupFormatException(
            BackupFormatError.UNSUPPORTED_VERSION,
            "unsupported document version $version",
        )
    }
}

/** Reads a required string field, returning a typed failure when missing or of the wrong type. */
internal fun JSONObject.requireString(field: String): String {
    if (!has(field) || isNull(field)) throw missingField(field)
    return try {
        getString(field)
    } catch (e: JSONException) {
        throw wrongFieldType(field, "string", e)
    }
}

/** Reads a required integer field, returning a typed failure when missing or of the wrong type. */
internal fun JSONObject.requireInt(field: String): Int {
    if (!has(field) || isNull(field)) throw missingField(field)
    return try {
        getInt(field)
    } catch (e: JSONException) {
        throw wrongFieldType(field, "integer", e)
    }
}

/** Reads the required `source` field as a [BackupOrigin]. */
internal fun JSONObject.requireSource(): BackupOrigin {
    val value = optString("source", "")
    return BackupOrigin.fromValue(value)
        ?: throw BackupFormatException(BackupFormatError.INVALID_FIELD, "unknown backup source: $value")
}

/** Reads an optional array of integers, tolerating a missing or non-array field. */
internal fun JSONObject.optIntList(field: String): List<Int> {
    val array = optJSONArray(field) ?: return emptyList()
    val values = ArrayList<Int>(array.length())
    for (index in 0 until array.length()) {
        values += array.optInt(index, 0)
    }
    return values
}

private fun missingField(field: String) = BackupFormatException(
    BackupFormatError.INVALID_FIELD,
    "missing field: $field",
)

private fun wrongFieldType(field: String, expected: String, cause: Throwable) = BackupFormatException(
    BackupFormatError.INVALID_FIELD,
    "field $field is not a $expected",
    cause,
)
