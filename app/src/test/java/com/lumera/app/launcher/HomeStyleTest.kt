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
    fun aProfileOnAStyleThatWasTakenOutWakesUpInNeon() {
        listOf("google_tv", "anime", "city", "lofi", "vaporwave").forEach { assertEquals(HomeStyle.Neon, HomeStyle.of(it)) }
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

    @Test
    fun eachStyleComesInTheGlassAdrianChose() {
        assertEquals(HomeGlass.Off, HomeStyle.Neon.glass)
        assertEquals(HomeGlass.Off, HomeStyle.Dusk.glass)
        assertEquals(HomeGlass.Frost, HomeStyle.Nature.glass)
        assertEquals(HomeGlass.Frost, HomeStyle.Clean.glass)
        assertEquals(HomeGlass.Liquid, HomeStyle.DeepBlue.glass)
        assertEquals(HomeGlass.Liquid, HomeStyle.IceLake.glass)
    }

    @Test
    fun natureKeepsItsSignsWhiteInFocusAndCleanColoursThem() {
        assertEquals(false, HomeStyle.Nature.look.brandInFocus)
        assertEquals(true, HomeStyle.Clean.look.brandInFocus)
    }

    @Test
    fun neonKeepsItsConsolesOnKallorAndIceLakeSitsLow() {
        assertEquals(false, HomeStyle.Neon.consolesOnHome)
        assertEquals(true, HomeStyle.Nature.consolesOnHome)
        assertEquals(true, HomeStyle.IceLake.rowsDown)
        assertEquals(false, HomeStyle.DeepBlue.namesAlways)
    }

    @Test
    fun aProfileFollowsItsStyleUntilItChoosesOtherwise() {
        val plain = HomeProfile("a", "Adrian", HomeStyle.IceLake)
        assertEquals(HomeGlass.Liquid, plain.glassLook)
        assertEquals(true, plain.watchDown)
        assertEquals(false, plain.showConsoles)
        val own = plain.copy(glass = HomeGlass.Frost, rowsDown = false, consolesOnHome = true, squareIcons = true, names = false)
        assertEquals(HomeGlass.Frost, own.glassLook)
        assertEquals(false, own.watchDown)
        assertEquals(true, own.showConsoles)
        assertEquals(true, own.square)
        assertEquals(false, own.namesAlways)
    }

    @Test
    fun glassOffLeavesTheStylesOwnLook() {
        val neon = HomeStyle.Neon.look
        assertEquals(neon.copy(glassMode = HomeGlass.Off), neon.inGlass(HomeGlass.Off))
        val liquid = neon.inGlass(HomeGlass.Liquid)
        assertEquals(false, liquid.glowingText)
        assertEquals(false, liquid.upperTitles)
        assertEquals(neon.accent, liquid.accent)
    }
}
