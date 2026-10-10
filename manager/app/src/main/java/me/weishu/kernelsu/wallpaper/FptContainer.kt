package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bounded, flat archive reader and writer.
 *
 * Both directions stream the ciphertext, so a tablet-sized theme (tens to hundreds of megabytes)
 * never has to fit in the heap: the payload is decrypted and compressed on the fly, entry by
 * entry. The small byte-array overload is retained for protocol tests.
 */
internal object FptContainer {
    private const val MAX_ENTRIES = 128
    private const val MAX_CONFIG = 1024L * 1024
    private const val MAX_TOTAL = 1024L * 1024 * 1024
    // ZIP headers and the central directory add overhead to the resource budget.
    private const val MAX_CONTAINER = MAX_TOTAL + 32L * 1024 * 1024

    private val key get() = SecretKeySpec(MessageDigest.getInstance("SHA-256")
        .digest("FolkPatchThemeSecretKey2025".toByteArray(Charsets.UTF_8)), "AES")

    fun read(input: InputStream, directory: File): Pair<JSONObject, Map<String, File>> {
        val bounded = BoundedInputStream(input, MAX_CONTAINER)
        val iv = readFully(bounded, 16) ?: error("Incomplete FPT container")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        check(directory.mkdirs() || directory.isDirectory)
        val entries = linkedMapOf<String, File>()
        var total = 0L
        val plain = DecryptedStream(bounded, cipher)
        ZipInputStream(BufferedInputStream(plain)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                require(!entry.isDirectory && name.isNotBlank() &&
                    name !in setOf(".", "..", "original.json", "applied.json") &&
                    '/' !in name && '\\' !in name && '\u0000' !in name) { "Unsafe ZIP entry: $name" }
                require(name !in entries && entries.size < MAX_ENTRIES) { "Duplicate or excessive ZIP entries" }
                val budget = minOf(MAX_TOTAL - total, if (name == "theme.json") MAX_CONFIG else MAX_TOTAL)
                val written = copyBounded(zip, File(directory, name), budget)
                require(written > 0) { "Empty ZIP entry: $name" }
                total += written
                entries[name] = File(directory, name)
                zip.closeEntry()
                entry = zip.nextEntry
            }
            // The zip reader stops at the central directory; pull the rest of the stream so the
            // cipher validates its final block. Invalid length or padding fails preparation;
            // the import service removes the staging directory. CBC is not authenticated.
            drain(plain)
        }
        val config = entries.remove("theme.json") ?: error("theme.json is missing")
        return JSONObject(config.readText(Charsets.UTF_8)) to entries
    }

    fun write(json: JSONObject, entries: Map<String, File>): ByteArray {
        val output = ByteArrayOutputStream()
        write(json, entries, output)
        return output.toByteArray()
    }

    fun write(json: JSONObject, entries: Map<String, File>, target: OutputStream) {
        val config = json.toString().toByteArray(Charsets.UTF_8)
        require(config.size <= MAX_CONFIG && entries.size < MAX_ENTRIES) { "Theme exceeds size limit" }
        entries.forEach { (name, file) ->
            require(name.isNotBlank() &&
                name !in setOf(".", "..", "theme.json", "original.json", "applied.json") &&
                '/' !in name && '\\' !in name && '\u0000' !in name && file.isFile && file.length() > 0L) {
                "Unsafe ZIP entry: $name"
            }
        }
        require(entries.values.sumOf { it.length() } + config.size <= MAX_TOTAL) { "Theme exceeds size limit" }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        target.write(iv)
        CipherOutputStream(target, cipher).use { encrypted ->
            ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
                zip.putNextEntry(ZipEntry("theme.json"))
                zip.write(config)
                zip.closeEntry()
                var total = config.size.toLong()
                entries.forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(name))
                    val written = file.inputStream().use { copyBounded(it, zip, MAX_TOTAL - total) }
                    require(written > 0) { "Empty ZIP entry: $name" }
                    total += written
                    zip.closeEntry()
                }
            }
        }
    }

    private fun copyBounded(input: InputStream, file: File, limit: Long): Long =
        file.outputStream().use { copyBounded(input, it, limit) }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Theme exceeds size limit" }
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun drain(input: InputStream) {
        val sink = ByteArray(64 * 1024)
        while (input.read(sink) >= 0) { /* consume the trailing blocks */ }
    }

    private fun readFully(input: InputStream, size: Int): ByteArray? {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return buffer
    }
}

/** Counts actual ciphertext reads, including the tail consumed for padding validation. */
internal class BoundedInputStream(source: InputStream, private val limit: Long) : FilterInputStream(source) {
    private var consumed = 0L

    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = `in`.read(buffer, offset, length)
        if (count > 0) account(count)
        return count
    }

    private fun account(count: Int) {
        consumed += count
        require(consumed <= limit) { "Theme exceeds size limit" }
    }
}

/**
 * Sequential AES-CBC reader that never holds the whole payload: it decrypts as the zip reader
 * pulls, and surfaces a bad final block (truncated, misaligned or tampered ciphertext) as an
 * exception. It does not own [source]; the caller closes that stream.
 */
private class DecryptedStream(source: InputStream, private val cipher: Cipher) : InputStream() {
    private val source = BufferedInputStream(source)
    private val encrypted = ByteArray(64 * 1024)
    private var plain = ByteArray(0)
    private var position = 0
    private var finished = false

    override fun read(): Int {
        if (remaining() <= 0) return -1
        return plain[position++].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val available = remaining()
        if (available <= 0) return -1
        val count = minOf(length, available)
        System.arraycopy(plain, position, buffer, offset, count)
        position += count
        return count
    }

    override fun available(): Int = plain.size - position

    private fun remaining(): Int {
        while (plain.size - position <= 0 && !finished) {
            val read = source.read(encrypted)
            val chunk = if (read < 0) {
                finished = true
                cipher.doFinal()
            } else {
                cipher.update(encrypted, 0, read)
            }
            if (chunk != null && chunk.isNotEmpty()) {
                plain = if (position >= plain.size) chunk else plain.copyOfRange(position, plain.size) + chunk
                position = 0
            }
        }
        return plain.size - position
    }
}
