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
}
