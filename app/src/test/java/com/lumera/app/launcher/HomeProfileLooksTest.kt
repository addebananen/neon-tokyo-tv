package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeProfileLooksTest {
    private val adrian = HomeProfile("a", "Adrian", HomeStyle.DeepBlue)

    @Test
    fun aStyleWornAgainBringsBackWhatWasChosenForIt() {
        val chosen = adrian.copy(glass = HomeGlass.Frost, names = true, rowsDown = true)
        val back = chosen.wearing(HomeStyle.Nature).wearing(HomeStyle.DeepBlue)
        assertEquals(HomeStyle.DeepBlue, back.style)
        assertEquals(HomeGlass.Frost, back.glassLook)
        assertTrue(back.namesAlways)
        assertTrue(back.watchDown)
    }

    @Test
    fun aStyleNeverChangedComesAsItIs() {
        val nature = adrian.copy(glass = HomeGlass.Off).wearing(HomeStyle.Nature)
        assertNull(nature.glass)
        assertEquals(HomeStyle.Nature.glass, nature.glassLook)
    }

    @Test
    fun eachStyleKeepsItsOwn() {
        val p = adrian.copy(glass = HomeGlass.Soft)
            .wearing(HomeStyle.Clean).copy(names = false, squareIcons = true)
            .wearing(HomeStyle.DeepBlue)
        assertEquals(HomeGlass.Soft, p.glass)
        assertNull(p.squareIcons)
        val clean = p.wearing(HomeStyle.Clean)
        assertEquals(false, clean.names)
        assertEquals(true, clean.squareIcons)
        assertNull(clean.glass)
        // And Deep Blue's choice is still kept while Clean shows
        assertEquals(HomeGlass.Soft, clean.looks[HomeStyle.DeepBlue]?.glass)
    }

    @Test
    fun iconsAndColoursStayWithTheProfile() {
        val p = adrian.copy(icons = IconPack.entries.last(), tint = IconTint.entries.last()).wearing(HomeStyle.Neon)
        assertEquals(IconPack.entries.last(), p.icons)
        assertEquals(IconTint.entries.last(), p.tint)
    }

    @Test
    fun theFocusMotionIsKeptPerStyleToo() {
        val p = adrian.copy(focus = GlassFocus.Tilt).wearing(HomeStyle.IceLake)
        assertEquals(GlassFocus.Glow, p.focusMotion)
        assertEquals(GlassFocus.Tilt, p.wearing(HomeStyle.DeepBlue).focusMotion)
    }

    @Test
    fun aLandingSettlesWhereItEnds() {
        assertEquals(0f to 0f, squash(1f))
        assertEquals(0f, bob(1f), 0f)
        assertEquals(1f, easeOut(1f), 1e-6f)
        assertEquals(0f, easeOut(0f), 1e-6f)
        // Squashed wide before it stretches tall
        assertTrue(squash(0.1f).first > 0f)
    }
}
