package com.lumera.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class CinemaThemeTest {
    @Test fun cinemaIsSelectableWithoutReplacingExistingThemes() {
        val cinema = DefaultThemes.AURORA_CINEMA
        assertTrue(cinema.isBuiltIn)
        assertEquals("dark", cinema.category)
        assertEquals(cinema, DefaultThemes.getById("aurora_cinema"))
        assertTrue(DefaultThemes.ALL.contains(DefaultThemes.NEON_TOKYO))
        assertEquals(DefaultThemes.ALL.size, DefaultThemes.ALL.map { it.id }.toSet().size)
        assertEquals(DefaultThemes.NEON_TOKYO, DefaultThemes.getById("missing"))
    }

    @Test fun cinemaTextAndFocusStayLegibleOnBothSurfaces() {
        val theme = DefaultThemes.AURORA_CINEMA
        for (surface in listOf(theme.backgroundColor, theme.surfaceColor)) {
            assertTrue("Main text contrast", contrast(theme.textColor, surface) >= 4.5f)
            assertTrue("Secondary text contrast", contrast(theme.textMutedColor, surface) >= 4.5f)
            assertTrue("Focus outline contrast", contrast(theme.primaryColor, surface) >= 3f)
        }
        assertTrue("Focused control text", contrast(0xFF000000, theme.primaryColor) >= 4.5f)
    }

    private fun contrast(a: Long, b: Long): Float {
        val first = Color(a.toInt()).luminance()
        val second = Color(b.toInt()).luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
