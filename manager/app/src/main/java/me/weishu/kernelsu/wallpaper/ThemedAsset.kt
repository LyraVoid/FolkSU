package me.weishu.kernelsu.wallpaper

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A registered payload and its configuration; the container does not know asset implementations. */
internal interface ThemedAsset {
    val base: String
    fun currentFile(context: Context): File?
    fun extension(file: File): String = WallpaperManager.resolveFileExtension(file).removePrefix(".")
    fun writeConfig(json: JSONObject)
    suspend fun apply(context: Context, json: JSONObject, file: File?)
    suspend fun reset(context: Context)
}

/** Exact archive entry names are owned by the group, for example musicFilename. */
internal interface ThemedAssetGroup {
    fun writeConfig(json: JSONObject)
    fun currentFiles(context: Context): Map<String, File>
    suspend fun apply(context: Context, json: JSONObject, imported: Map<String, File>)
    suspend fun reset(context: Context)
}
