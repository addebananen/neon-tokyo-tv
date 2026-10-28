package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIconsTest {
    @Test
    fun aPackIsReadBackFromItsKey() {
        IconPack.values().forEach { assertEquals(it, IconPack.of(it.key)) }
    }

    @Test
    fun aProfileFromBeforeThePacksKeepsTheNeonIcons() {
        assertEquals(IconPack.Neon, IconPack.of(null))
        assertEquals(IconPack.Neon, IconPack.of(""))
        assertEquals(IconPack.Neon, IconPack.of("chrome"))
    }

    @Test
    fun aRemixIsReadBackFromItsKeyAndOldProfilesKeepTheirOwnColours() {
        IconTint.values().forEach { assertEquals(it, IconTint.of(it.key)) }
        assertEquals(IconTint.Own, IconTint.of(null))
        assertEquals(IconTint.Own, IconTint.of(""))
    }

    @Test
    fun aDuotoneTakesBlackToItsDarkAndWhiteToItsLight() {
        val dark = androidx.compose.ui.graphics.Color(0xFF200040)
        val light = androidx.compose.ui.graphics.Color(0xFFFF60D0)
        val m = IconTint.duotone(dark, light)
        fun out(row: Int, v: Float) = m[row, 0] * v + m[row, 1] * v + m[row, 2] * v + m[row, 4]
        // With the extra contrast both ends go a touch past, and are clamped by the drawing
        assertTrue(out(0, 0f) <= dark.red * 255f + 1f)
        assertTrue(out(0, 255f) >= light.red * 255f - 1f)
        assertEquals(1f, m[3, 3], 0f)
    }

    @Test
    fun everyAppTheTvMayHaveGetsAMotionAndNeonTokyoLightsUp() {
        assertEquals(IconMotion.Neon, IconMotion.of("Neon Tokyo"))
        assertEquals(IconMotion.Beat, IconMotion.of("TV4 Play"))
        assertEquals(IconMotion.Tadum, IconMotion.of("Netflix"))
        assertEquals(IconMotion.Rise, IconMotion.of("Settings"))
    }

    @Test
    fun aMotionAtRestLeavesTheIconAsItIs() {
        IconMotion.values().forEach { motion ->
            val pose = motion.pose(t = 2.3f, lit = 0f, unit = 2f, into = Pose())
            assertEquals(1f, pose.scale, 0f)
            assertEquals(0f, pose.flip, 0f)
            assertEquals(0f, pose.glitch, 0f)
        }
    }
}
