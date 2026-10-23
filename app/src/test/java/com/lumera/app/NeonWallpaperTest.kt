package com.lumera.app

import com.lumera.app.projectivy.NeonWallpaper
import com.lumera.app.projectivy.NeonWallpaper.CRIMSON
import com.lumera.app.projectivy.NeonWallpaper.EMERALD
import com.lumera.app.projectivy.NeonWallpaper.GOLD
import com.lumera.app.projectivy.NeonWallpaper.OCEAN
import com.lumera.app.projectivy.NeonWallpaper.TOKYO
import com.lumera.app.projectivy.NeonWallpaper.VIOLET
import org.junit.Assert.assertEquals
import org.junit.Test

class NeonWallpaperTest {

    @Test
    fun adriansAppsGetTheirColours() {
        assertEquals(CRIMSON, NeonWallpaper.forCard("com.google.android.youtube.tv", "YouTube", 0))
        assertEquals(CRIMSON, NeonWallpaper.forCard("com.google.android.youtube.tvmusic", "YouTube Music", 0))
        assertEquals(EMERALD, NeonWallpaper.forCard("com.spotify.tv.android", "Spotify", 0))
        assertEquals(VIOLET, NeonWallpaper.forCard("com.stremio.one", "Stremio", 0))
        assertEquals(TOKYO, NeonWallpaper.forCard("com.neontokyo.tv", "Neon Tokyo", 0))
        assertEquals(CRIMSON, NeonWallpaper.forCard("com.netflix.ninja", "Netflix", 0))
    }

    @Test
    fun renamedInputsWinOverTheInputApp() {
        assertEquals(CRIMSON, NeonWallpaper.forCard("com.tcl.tv", "Switch 2", 0))
        assertEquals(EMERALD, NeonWallpaper.forCard("com.tcl.tv", "Xbox 360", 0))
        assertEquals(EMERALD, NeonWallpaper.forCard("com.tcl.tv", "Xbox", 0))
        assertEquals(OCEAN, NeonWallpaper.forCard("com.tcl.tv", "Wii U", 0))
        assertEquals(OCEAN, NeonWallpaper.forCard("com.tcl.tv", "Wii", 0))
        assertEquals(OCEAN, NeonWallpaper.forCard("com.tcl.tv", "Steam PC", 0))
        assertEquals(OCEAN, NeonWallpaper.forCard(null, "PC", 0))
        assertEquals(OCEAN, NeonWallpaper.forCard(null, "HDMI 3", 0xFFFF0000.toInt()))
    }

    @Test
    fun otherAppsFollowTheirCardColour() {
        assertEquals(OCEAN, NeonWallpaper.forCard("com.example.weather", "Weather", 0xFF00A0FF.toInt()))
        assertEquals(GOLD, NeonWallpaper.forCard("com.example.radio", "Radio", 0xFFFF9900.toInt()))
        assertEquals(TOKYO, NeonWallpaper.forCard(null, null, 0))
    }

    @Test
    fun coloursMapToTheNearestSkyline() {
        assertEquals(TOKYO, NeonWallpaper.forColor(0xFF808080.toInt())) // grey
        assertEquals(TOKYO, NeonWallpaper.forColor(0xFF101010.toInt())) // near black
        assertEquals(CRIMSON, NeonWallpaper.forColor(0xFFFF0000.toInt()))
        assertEquals(CRIMSON, NeonWallpaper.forColor(0xFFFF2050.toInt()))
        assertEquals(GOLD, NeonWallpaper.forColor(0xFFFFC46B.toInt()))
        assertEquals(EMERALD, NeonWallpaper.forColor(0xFF1ED760.toInt())) // Spotify green
        assertEquals(OCEAN, NeonWallpaper.forColor(0xFF0066FF.toInt()))
        assertEquals(VIOLET, NeonWallpaper.forColor(0xFF8B4DFF.toInt()))
        assertEquals(TOKYO, NeonWallpaper.forColor(0xFFFF3D9A.toInt())) // the Neon Tokyo magenta
    }
}
