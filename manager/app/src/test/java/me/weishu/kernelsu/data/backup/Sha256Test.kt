package me.weishu.kernelsu.data.backup

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {

    private val emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val abcHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `empty byte array hash matches the known vector`() {
        assertEquals(emptyHash, ByteArray(0).sha256())
    }

    @Test
    fun `abc matches the known vector`() {
        assertEquals(abcHash, "abc".toByteArray().sha256())
    }

    @Test
    fun `streaming a file matches the byte array hash`() {
        val file = File.createTempFile("sha256", ".bin")
        try {
            file.writeBytes("abc".toByteArray())
            assertEquals(abcHash, runBlocking { file.sha256() })
            file.writeBytes(ByteArray(0))
            assertEquals(emptyHash, runBlocking { file.sha256() })
        } finally {
            file.delete()
        }
    }
}
