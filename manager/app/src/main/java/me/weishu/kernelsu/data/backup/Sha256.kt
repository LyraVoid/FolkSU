package me.weishu.kernelsu.data.backup

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HASH_BUFFER_SIZE = 8 * 1024

private const val HEX_DIGITS = "0123456789abcdef"

/** Returns the lowercase hex SHA-256 of these bytes. */
fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()

/** Streams this input to completion and returns its lowercase hex SHA-256. */
fun InputStream.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(HASH_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().toHexString()
}

/** Streams the file and returns its lowercase hex SHA-256. */
suspend fun File.sha256(): String = withContext(Dispatchers.IO) {
    inputStream().use { it.sha256() }
}

private fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xff
        chars[index * 2] = HEX_DIGITS[value ushr 4]
        chars[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
    }
    return String(chars)
}
