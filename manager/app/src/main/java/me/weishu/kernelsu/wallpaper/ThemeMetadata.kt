package me.weishu.kernelsu.wallpaper

import android.content.Context
import org.json.JSONObject

data class ThemeMetadata(
    val name: String = "",
    val type: String = "phone",
    val version: String = "1",
    val author: String = "",
    val description: String = "",
) {
    fun write(json: JSONObject) {
        json.put("meta_name", name)
        json.put("meta_type", type)
        json.put("meta_version", version)
        json.put("meta_author", author)
        json.put("meta_description", description)
    }

    companion object {
        fun read(json: JSONObject) = ThemeMetadata(
            json.optString("meta_name", ""), json.optString("meta_type", "phone"),
            json.optString("meta_version", "1"), json.optString("meta_author", ""),
            json.optString("meta_description", ""),
        )

        fun current(context: Context): ThemeMetadata = read(JSONObject(
            context.getSharedPreferences("theme_metadata", Context.MODE_PRIVATE)
                .getString("json", "{}") ?: "{}"
        ))
    }

    fun save(context: Context) {
        val json = JSONObject().also(::write)
        check(context.getSharedPreferences("theme_metadata", Context.MODE_PRIVATE)
            .edit().putString("json", json.toString()).commit())
    }
}
