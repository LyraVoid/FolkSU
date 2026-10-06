package me.weishu.kernelsu.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatePolicyTest {

    @Test
    fun `no existing entries is never a duplicate`() {
        assertFalse(DuplicatePolicy.isDuplicate(emptyMap(), "modules/m/a.zip", "abc"))
    }

    @Test
    fun `same path and same hash is a duplicate`() {
        val existing = mapOf("modules/m/a.zip" to "abc")
        assertTrue(DuplicatePolicy.isDuplicate(existing, "modules/m/a.zip", "abc"))
    }

    @Test
    fun `same path with a different hash is not a duplicate`() {
        val existing = mapOf("modules/m/a.zip" to "abc")
        assertFalse(DuplicatePolicy.isDuplicate(existing, "modules/m/a.zip", "def"))
    }

    @Test
    fun `identical content at another path is a duplicate`() {
        val existing = mapOf("modules/m/old.zip" to "abc")
        assertTrue(DuplicatePolicy.isDuplicate(existing, "modules/m/new.zip", "abc"))
    }

    @Test
    fun `an unknown stored hash does not match a hash`() {
        val existing = mapOf("modules/m/old.zip" to null)
        assertFalse(DuplicatePolicy.isDuplicate(existing, "modules/m/new.zip", "abc"))
    }

    @Test
    fun `a hash is compared case insensitively`() {
        val existing = mapOf("modules/m/a.zip" to "ABC")
        assertTrue(DuplicatePolicy.isDuplicate(existing, "modules/m/a.zip", "abc"))
    }

    @Test
    fun `a blank candidate hash is never a duplicate`() {
        val existing = mapOf("modules/m/a.zip" to "")
        assertFalse(DuplicatePolicy.isDuplicate(existing, "modules/m/a.zip", ""))
    }
}
