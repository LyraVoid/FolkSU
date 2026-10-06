package me.weishu.kernelsu.data.backup

import me.weishu.kernelsu.Natives
import org.json.JSONArray
import org.json.JSONObject

/** Schema identifier of an allow-list export document. */
const val ALLOWLIST_SCHEMA = "folksu.allowlist"

/** Highest supported allow-list namespace ordinal. */
private const val NAMESPACE_MAX = 2

/**
 * A portable representation of one allow-list entry.
 *
 * The field names mirror the allow-list JSON contract: [key] and [packageName] carry the kernel
 * lookup key, [uid]/[currentUid] the package identity, and the remaining fields the root and
 * non-root profile parameters.
 */
data class AllowlistEntryDto(
    val key: String,
    val packageName: String,
    val uid: Int,
    val currentUid: Int,
    val allowSu: Boolean,
    val flags: Long,
    val rootUseDefault: Boolean,
    val rootTemplate: String?,
    val rootUid: Int,
    val rootGid: Int,
    val groups: List<Int>,
    val capabilities: List<Int>,
    val context: String,
    val namespace: Int,
    val nonRootUseDefault: Boolean,
    val umountModules: Boolean,
    val rules: String,
)

/**
 * A complete allow-list export.
 *
 * Unknown fields in the source document are ignored on read, so a newer exporter stays readable.
 */
data class AllowlistDocument(
    val exportedAt: String,
    val appVersion: String,
    val kernelUapiVersion: Int,
    val entries: List<AllowlistEntryDto>,
) {
    /** Serialises this document to a 2-space indented JSON object. */
    fun toJson(): String {
        val array = JSONArray()
        for (entry in entries) {
            array.put(entry.toJson())
        }
        val json = JSONObject()
        json.put("schema", ALLOWLIST_SCHEMA)
        json.put("version", BACKUP_FORMAT_VERSION)
        json.put("exportedAt", exportedAt)
        json.put("appVersion", appVersion)
        json.put("kernelUapiVersion", kernelUapiVersion)
        json.put("entries", array)
        return json.toString(2)
    }

    /** Validates the document, returning a typed failure instead of throwing. */
    fun validate(): Result<Unit> = parseBackupDocument {
        if (entries.isEmpty()) {
            throw BackupFormatException(BackupFormatError.EMPTY, "allow-list document has no entries")
        }
        entries.forEach { it.validate() }
    }

    companion object {
        /** Parses and validates a document, returning a typed failure instead of throwing. */
        fun fromJson(json: String): Result<AllowlistDocument> = parseBackupDocument {
            val obj = parseBackupObject(json)
            obj.requireSchemaVersion(ALLOWLIST_SCHEMA)
            val array = obj.optJSONArray("entries")
                ?: throw BackupFormatException(BackupFormatError.MALFORMED, "missing entries array")
            val entries = ArrayList<AllowlistEntryDto>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw BackupFormatException(
                        BackupFormatError.MALFORMED,
                        "entry $index is not an object",
                    )
                entries += parseEntry(item)
            }
            val document = AllowlistDocument(
                exportedAt = obj.optString("exportedAt", ""),
                appVersion = obj.optString("appVersion", ""),
                kernelUapiVersion = obj.optInt("kernelUapiVersion", 0),
                entries = entries,
            )
            document.validate().getOrThrow()
            document
        }

        private fun parseEntry(item: JSONObject): AllowlistEntryDto {
            val key = item.optString("key", "")
            val uid = item.requireInt("uid")
            val currentUid = if (item.has("currentUid") && !item.isNull("currentUid")) {
                item.requireInt("currentUid")
            } else {
                uid
            }
            return AllowlistEntryDto(
                key = key,
                packageName = item.optString("packageName", key),
                uid = uid,
                currentUid = currentUid,
                allowSu = item.optBoolean("allowSu", false),
                flags = item.optLong("flags", 0L),
                rootUseDefault = item.optBoolean("rootUseDefault", true),
                rootTemplate = if (item.has("rootTemplate") && !item.isNull("rootTemplate")) {
                    item.requireString("rootTemplate")
                } else {
                    null
                },
                rootUid = item.optInt("rootUid", 0),
                rootGid = item.optInt("rootGid", 0),
                groups = item.optIntList("groups"),
                capabilities = item.optIntList("capabilities"),
                context = item.optString("context", ""),
                namespace = item.optInt("namespace", 0),
                nonRootUseDefault = item.optBoolean("nonRootUseDefault", true),
                umountModules = item.optBoolean("umountModules", true),
                rules = item.optString("rules", ""),
            )
        }
    }
}

private fun AllowlistEntryDto.toJson(): JSONObject {
    val json = JSONObject()
    json.put("key", key)
    json.put("packageName", packageName)
    json.put("uid", uid)
    json.put("currentUid", currentUid)
    json.put("allowSu", allowSu)
    json.put("flags", flags)
    json.put("rootUseDefault", rootUseDefault)
    json.put("rootTemplate", rootTemplate ?: JSONObject.NULL)
    json.put("rootUid", rootUid)
    json.put("rootGid", rootGid)
    json.put("groups", JSONArray(groups))
    json.put("capabilities", JSONArray(capabilities))
    json.put("context", context)
    json.put("namespace", namespace)
    json.put("nonRootUseDefault", nonRootUseDefault)
    json.put("umountModules", umountModules)
    json.put("rules", rules)
    return json
}

private fun AllowlistEntryDto.validate() {
    if (key.isBlank()) {
        throw BackupFormatException(BackupFormatError.INVALID_FIELD, "entry key must not be blank")
    }
    if (namespace !in 0..NAMESPACE_MAX) {
        throw BackupFormatException(BackupFormatError.INVALID_FIELD, "namespace out of range: $namespace")
    }
    if (flags < 0) {
        throw BackupFormatException(BackupFormatError.INVALID_FIELD, "flags must not be negative")
    }
}

/** Projects a kernel profile into its portable representation. */
fun Natives.Profile.toDto(key: String, packageName: String): AllowlistEntryDto = AllowlistEntryDto(
    key = key,
    packageName = packageName,
    uid = currentUid,
    currentUid = currentUid,
    allowSu = allowSu,
    flags = flags,
    rootUseDefault = rootUseDefault,
    rootTemplate = rootTemplate,
    rootUid = uid,
    rootGid = gid,
    groups = groups,
    capabilities = capabilities,
    context = context,
    namespace = namespace,
    nonRootUseDefault = nonRootUseDefault,
    umountModules = umountModules,
    rules = rules,
)

/** Rebuilds the kernel profile described by this portable entry. */
fun AllowlistEntryDto.toProfile(): Natives.Profile = Natives.Profile(
    name = key,
    currentUid = currentUid,
    allowSu = allowSu,
    rootUseDefault = rootUseDefault,
    rootTemplate = rootTemplate,
    uid = rootUid,
    gid = rootGid,
    groups = groups,
    capabilities = capabilities,
    context = context,
    namespace = namespace,
    nonRootUseDefault = nonRootUseDefault,
    umountModules = umountModules,
    rules = rules,
    flags = flags,
)
