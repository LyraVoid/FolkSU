package me.weishu.kernelsu.wallpaper

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SurfaceImageFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `replacement publishes all new bytes at the same path`() {
        val directory = temporaryFolder.root
        val target = File(directory, "card.jpg").apply { writeText("old") }
        assertEquals(target, replaceSurfaceImage(directory, "card", ".jpg") {
            assertEquals("old", target.readText())
            it.writeText("new image")
            true
        })
        assertEquals("new image", target.readText())
        assertEquals(listOf("card.jpg"), directory.list()!!.toList())
    }

    @Test
    fun `partial failed copy preserves the existing image and removes staging`() {
        val directory = temporaryFolder.root
        val target = File(directory, "card.jpg").apply { writeText("old") }
        assertThrows(IOException::class.java) {
            replaceSurfaceImage(directory, "card", ".jpg") {
                it.writeText("partial")
                false
            }
        }
        assertEquals("old", target.readText())
        assertEquals(listOf("card.jpg"), directory.list()!!.toList())
    }

    @Test
    fun `empty input cannot replace an existing image`() {
        val directory = temporaryFolder.root
        val target = File(directory, "card.jpg").apply { writeText("old") }
        assertThrows(IOException::class.java) { replaceSurfaceImage(directory, "card", ".jpg") { true } }
        assertEquals("old", target.readText())
    }

    @Test
    fun `exceptions preserve the old image`() {
        val directory = temporaryFolder.root
        val target = File(directory, "card.jpg").apply { writeText("old") }
        assertThrows(IOException::class.java) {
            replaceSurfaceImage(directory, "card", ".jpg") { throw IOException("read failed") }
        }
        assertEquals("old", target.readText())
        assertEquals(1, directory.list()!!.size)
    }

    @Test
    fun `the current image can itself be imported safely`() {
        val directory = temporaryFolder.root
        val target = File(directory, "card.jpg").apply { writeText("image") }
        replaceSurfaceImage(directory, "card", ".jpg") {
            target.inputStream().use { input -> it.outputStream().use { output -> input.copyTo(output) } }
            true
        }
        assertEquals("image", target.readText())
    }

    @Test
    fun `failed first upload leaves no image or temporary file`() {
        val directory = temporaryFolder.root
        assertThrows(IOException::class.java) { replaceSurfaceImage(directory, "card", ".png") { false } }
        assertFalse(File(directory, "card.png").exists())
        assertEquals(0, directory.list()!!.size)
    }
}
