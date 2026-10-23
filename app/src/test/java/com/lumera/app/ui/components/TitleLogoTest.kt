package com.lumera.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleLogoTest {

    private val transparent = 0x00000000

    /** A 48x16 logo: [ink] covers [coverage] of it, [outline] another slice, the rest is clear. */
    private fun logo(ink: Int, coverage: Float, outline: Int? = null, outlineCoverage: Float = 0f): IntArray {
        val pixels = IntArray(48 * 16) { transparent }
        val inked = (pixels.size * coverage).toInt()
        val outlined = (pixels.size * outlineCoverage).toInt()
        for (i in 0 until inked) pixels[i] = ink
        if (outline != null) for (i in inked until inked + outlined) pixels[i] = outline
        return pixels
    }

    @Test
    fun blackLetteringIsDark() {
        assertTrue(isDarkLogo(logo(0xFF000000.toInt(), 0.4f)))
    }

    @Test
    fun navyLetteringIsDark() {
        assertTrue(isDarkLogo(logo(0xFF1B1F5E.toInt(), 0.35f)))
    }

    @Test
    fun whiteLetteringIsNot() {
        assertFalse(isDarkLogo(logo(0xFFFFFFFF.toInt(), 0.4f)))
    }

    @Test
    fun darkLetteringWithALightOutlineStaysAsItIs() {
        assertFalse(isDarkLogo(logo(0xFF000000.toInt(), 0.3f, 0xFFF0F0F0.toInt(), 0.15f)))
    }

    @Test
    fun aLogoOnItsOwnSolidBackgroundIsLeftAlone() {
        assertFalse(isDarkLogo(logo(0xFF050505.toInt(), 1f)))
    }

    @Test
    fun anEmptyImageIsLeftAlone() {
        assertFalse(isDarkLogo(IntArray(48 * 16) { transparent }))
    }

    @Test
    fun boundsHugTheLettering() {
        val pixels = IntArray(10 * 6) { transparent }
        pixels[2 * 10 + 3] = 0xFFFFFFFF.toInt()
        pixels[4 * 10 + 7] = 0x80FFFFFF.toInt()
        assertEquals(PixelBounds(3, 2, 8, 5), visibleBounds(pixels, 10, 6))
    }

    @Test
    fun aFaintHazeAroundTheLogoIsTrimmed() {
        val pixels = IntArray(10 * 6) { 0x08FFFFFF }
        pixels[3 * 10 + 5] = 0xFF000000.toInt()
        assertEquals(PixelBounds(5, 3, 6, 4), visibleBounds(pixels, 10, 6))
    }

    @Test
    fun aLogoFillingTheImageKeepsItAll() {
        assertEquals(PixelBounds(0, 0, 4, 3), visibleBounds(IntArray(4 * 3) { 0xFF336699.toInt() }, 4, 3))
    }

    @Test
    fun anEmptyImageHasNoBounds() {
        assertNull(visibleBounds(IntArray(16) { transparent }, 4, 4))
    }
}
