package me.weishu.kernelsu.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutStyleTest {

    @Test
    fun `supported tokens keep the persisted spelling`() {
        // The exact bytes stored under the preference key are part of the contract.
        assertEquals("circle", HomeLayoutStyle.CIRCLE)
        assertEquals("kernelsu", HomeLayoutStyle.GRID)
        assertEquals("focus", HomeLayoutStyle.FOCUS)
        assertEquals("dashboard_ui", HomeLayoutStyle.DASHBOARD)
        assertEquals("stats", HomeLayoutStyle.STATS)
        assertEquals("circle", HomeLayoutStyle.DEFAULT)
    }

    @Test
    fun `known tokens round-trip`() {
        assertEquals(HomeLayoutStyle.CIRCLE, HomeLayoutStyle.fromValue("circle"))
        assertEquals(HomeLayoutStyle.GRID, HomeLayoutStyle.fromValue("kernelsu"))
        assertEquals(HomeLayoutStyle.FOCUS, HomeLayoutStyle.fromValue("focus"))
        assertEquals(HomeLayoutStyle.DASHBOARD, HomeLayoutStyle.fromValue("dashboard_ui"))
        assertEquals(HomeLayoutStyle.STATS, HomeLayoutStyle.fromValue("stats"))
    }

    @Test
    fun `missing or unsupported tokens fall back to the default`() {
        assertEquals(HomeLayoutStyle.DEFAULT, HomeLayoutStyle.fromValue(null))
        assertEquals(HomeLayoutStyle.DEFAULT, HomeLayoutStyle.fromValue(""))
        assertEquals(HomeLayoutStyle.DEFAULT, HomeLayoutStyle.fromValue("default"))
        assertEquals(HomeLayoutStyle.DEFAULT, HomeLayoutStyle.fromValue("unsupported"))
    }

    @Test
    fun `supported list contains only the known tokens`() {
        assertEquals(
            listOf(HomeLayoutStyle.CIRCLE, HomeLayoutStyle.GRID, HomeLayoutStyle.FOCUS, HomeLayoutStyle.DASHBOARD, HomeLayoutStyle.STATS),
            HomeLayoutStyle.supported,
        )
    }
}
