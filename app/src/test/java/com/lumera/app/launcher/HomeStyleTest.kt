package com.lumera.app.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStyleTest {

    @Test
    fun everyStyleHasItsOwnKey() {
        val keys = HomeStyle.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        HomeStyle.entries.forEach { assertEquals(it, HomeStyle.of(it.key)) }
    }

    @Test
    fun anUnknownOrMissingStyleIsNeon() {
        assertEquals(HomeStyle.Neon, HomeStyle.of(null))
        assertEquals(HomeStyle.Neon, HomeStyle.of("vaporwave-from-the-future"))
    }

    @Test
    fun onlyNeonDrawsTheSkylineInsteadOfAPainting() {
        assertNull(HomeStyle.Neon.backdrop)
        HomeStyle.entries.filter { it != HomeStyle.Neon }.forEach { assertNotNull(it.name, it.backdrop) }
    }

    @Test
    fun neonKeepsTheAppsOwnColoursAndCleanTurnsThemWhite() {
        val red = Color(0xFFFF2D55)
        assertEquals(red, HomeStyle.Neon.look.tint(red))
        val clean = HomeStyle.Clean.look.tint(red)
        // Nearly white: every channel high
        assert(clean.red > 0.9f && clean.green > 0.8f && clean.blue > 0.8f) { "Clean tinted red to $clean" }
    }
}
