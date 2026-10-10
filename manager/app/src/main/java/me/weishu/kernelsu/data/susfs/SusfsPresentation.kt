package me.weishu.kernelsu.data.susfs

import org.json.JSONObject

/** A receipt from FolkSU, not an inventory of kernel or third-party rules. */
data class SusfsApplyReport(
    val status: String,
    val applied: List<String>,
    val failures: Map<String, String>,
    val pendingReboot: List<String>,
) {
    companion object {
        fun parse(text: String): SusfsApplyReport {
            val json = JSONObject(text)
            fun strings(key: String): List<String> = json.getJSONArray(key).let { array ->
                List(array.length()) { array.getString(it) }
            }
            val failures = json.getJSONObject("failures")
            return SusfsApplyReport(json.getString("status"), strings("applied"),
                failures.keys().asSequence().associateWith { failures.getString(it) }, strings("pending_reboot"))
        }
    }
}

object SusfsInputs {
    fun validPath(value: String): Boolean = value.startsWith("/") && value != "/" &&
        value.toByteArray(Charsets.UTF_8).size < 256 && !value.contains("//") &&
        value.none { it.isWhitespace() || it.isISOControl() } &&
        value.split('/').none { it == "." || it == ".." }

    fun validText(value: String, capacity: Int): Boolean = value.isNotBlank() &&
        value.toByteArray(Charsets.UTF_8).size < capacity && value.none(Char::isISOControl)

    /** Match the entry shown to the user, never an index from an obsolete screen snapshot. */
    fun replaceRule(config: JSONObject, original: String?, replacement: JSONObject?) {
        val rules = config.getJSONArray("rules")
        if (original == null) {
            requireNotNull(replacement)
            rules.put(replacement)
            return
        }
        val expected = JSONObject(original)
        val index = (0 until rules.length()).firstOrNull {
            val current = rules.getJSONObject(it)
            current.getString("kind") == expected.getString("kind") &&
                current.getString("path") == expected.getString("path")
        } ?: error("Rule changed elsewhere; refresh before editing")
        val current = rules.getJSONObject(index)
        require(current.length() == expected.length() && expected.keys().asSequence().all {
            current.has(it) && current.get(it) == expected.get(it)
        }) { "Rule changed elsewhere; refresh before editing" }
        if (replacement == null) rules.remove(index) else rules.put(index, replacement)
    }

    /** Preview only. The daemon remains the authoritative schema validator before any write. */
    fun preview(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= 128 * 1024) { "Configuration exceeds 128 KiB" }
        val json = JSONObject(text)
        require(json.getInt("schema") == 1) { "Unsupported schema" }
        json.getBoolean("automatic")
        json.getJSONObject("settings")
        require(json.getJSONArray("rules").length() <= 128) { "Too many rules" }
        val lkm = json.getJSONObject("lkm")
        lkm.getJSONArray("hidden_modules")
        lkm.getJSONArray("mount_prefixes")
        return json
    }
}
