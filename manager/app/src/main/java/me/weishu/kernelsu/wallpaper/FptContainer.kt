package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Bounded, flat archive reader. Extraction never mutates live theme state. */
internal object FptContainer {
    private const val LIMIT = 256L * 1024 * 1024
    private val key get() = SecretKeySpec(MessageDigest.getInstance("SHA-256")
        .digest("FolkPatchThemeSecretKey2025".toByteArray(Charsets.UTF_8)), "AES")

    fun read(input: InputStream, directory: File): Pair<JSONObject, Map<String, File>> {
        val bytes = bounded(input, LIMIT)
        require(bytes.size > 16 && (bytes.size - 16) % 16 == 0) { "Incomplete FPT container" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(bytes.copyOfRange(0, 16)))
        val plain = cipher.doFinal(bytes, 16, bytes.size - 16)
        require(plain.size >= 4 && plain[0] == 0x50.toByte() && plain[1] == 0x4b.toByte()) { "Invalid ZIP" }
        check(directory.mkdirs() || directory.isDirectory)
        val entries = linkedMapOf<String, File>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                require(!entry.isDirectory && name.isNotBlank() && name !in setOf(".", "..", "original.json", "applied.json") &&
                    '/' !in name && '\\' !in name && '\u0000' !in name) { "Unsafe ZIP entry: $name" }
                require(name !in entries && entries.size < 128) { "Duplicate or excessive ZIP entries" }
                val data = bounded(zip, minOf(LIMIT - total, if (name == "theme.json") 1024L * 1024 else LIMIT))
                total += data.size
                require(data.isNotEmpty()) { "Empty ZIP entry: $name" }
                val file = File(directory, name)
                file.writeBytes(data)
                entries[name] = file
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val config = entries.remove("theme.json") ?: error("theme.json is missing")
        return JSONObject(config.readText(Charsets.UTF_8)) to entries
    }

    fun write(json: JSONObject, entries: Map<String, File>): ByteArray {
        val config = json.toString().toByteArray(Charsets.UTF_8)
        require(config.size <= 1024 * 1024 && entries.size < 128)
        require(entries.values.sumOf { it.length() } + config.size <= LIMIT) { "Theme exceeds size limit" }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write(config)
            zip.closeEntry()
            entries.forEach { (name, file) ->
                require(name.isNotBlank() && name !in setOf(".", "..", "theme.json", "original.json", "applied.json") &&
                    '/' !in name && '\\' !in name && '\u0000' !in name && file.isFile && file.length() > 0)
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return iv + cipher.doFinal(bytes.toByteArray())
    }

    private fun bounded(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= limit) { "Theme exceeds size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
