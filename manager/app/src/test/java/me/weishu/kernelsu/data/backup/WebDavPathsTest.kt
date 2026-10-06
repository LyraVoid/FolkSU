package me.weishu.kernelsu.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPathsTest {

    @Test
    fun `parent dirs returns every ancestor`() {
        assertEquals(listOf("a", "a/b", "a/b/c"), WebDavPaths.parentDirs("/a/b/c"))
        assertEquals(listOf("a", "a/b", "a/b/c"), WebDavPaths.parentDirs("a/b/c"))
        assertEquals(listOf("a", "a/b", "a/b/c"), WebDavPaths.parentDirs("/a/b/c/"))
    }

    @Test
    fun `parent dirs of a bare segment is itself`() {
        assertEquals(listOf("backup"), WebDavPaths.parentDirs("backup"))
    }

    @Test
    fun `parent dirs of an empty path is empty`() {
        assertTrue(WebDavPaths.parentDirs("").isEmpty())
        assertTrue(WebDavPaths.parentDirs("/").isEmpty())
    }

    @Test
    fun `join base uses exactly one separator`() {
        assertEquals("https://host/dav/modules/a.zip", WebDavPaths.joinBase("https://host/dav/", "modules/a.zip"))
        assertEquals("https://host/dav/modules/a.zip", WebDavPaths.joinBase("https://host/dav", "/modules/a.zip"))
        assertEquals("modules/a.zip", WebDavPaths.joinBase("", "/modules/a.zip"))
    }

    @Test
    fun `join base of the root keeps the caller prefix`() {
        assertEquals("/modules/a.zip", WebDavPaths.joinBase("/", "modules/a.zip"))
    }
}
