package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeonHomeTest {

    @Test
    fun consolesAreNumberedOneToSixInAdriansOrder() {
        assertEquals(listOf("PC", "Switch 2", "Xbox 360", "Wii U", "Wii", "Xbox"), HomeCatalog.consoles.map { it.name })
        assertEquals((1..6).toList(), HomeCatalog.consoles.map { it.port })
    }

    @Test
    fun hdmiPortsAreReadFromTheTvsOwnNames() {
        assertEquals(2, TvInputs.portNumber("HDMI 2"))
        assertEquals(3, TvInputs.portNumber("HDMI3 (eARC)"))
        assertEquals(1, TvInputs.portNumber("hdmi 1"))
        assertNull(TvInputs.portNumber("Composite"))
        assertNull(TvInputs.portNumber(""))
    }

    @Test
    fun devicesOnAPortAreListedRightAfterIt() {
        val inputs = listOf(
            TvInput("hw3", "HDMI 3", 3, null),
            TvInput("hw1", "Steam PC", 1, null),
            TvInput("cec", "Nintendo Switch", null, "hw2"),
            TvInput("hw2", "HDMI 2", 2, null),
            TvInput("av", "Composite", null, null)
        )
        assertEquals(listOf("hw1", "hw2", "cec", "hw3", "av"), TvInputs.sortInputs(inputs).map { it.id })
    }

    @Test
    fun withFourPortsTheFirstFourConsolesFindTheirInputByThemselves() {
        val ports = (1..4).map { TvInput("hw$it", "HDMI $it", it, null) } + TvInput("cec", "Nintendo Switch", null, "hw2")
        val linked = HomeCatalog.consoles.map { ConsoleLinks.defaultInput(it, ports)?.id }
        assertEquals(listOf("hw1", "hw2", "hw3", "hw4", null, null), linked)
    }

    @Test
    fun knownAppsComeFirstInTheirOwnOrderThenTheRestByName() {
        fun app(pkg: String, label: String) = InstalledApp(pkg, label, HomeCatalog.known(pkg, label))
        val sorted = HomeCatalog.sortForAllApps(
            listOf(
                app("com.example.zebra", "Zebra TV"),
                app("com.google.android.youtube.tv", "YouTube"),
                app("com.example.arcade", "arcade"),
                app("com.android.vending", "Google Play Store"),
                app("com.neontokyo.tv", "Neon Tokyo"),
                app("com.tcl.mediacenter", "Media Player")
            )
        )
        assertEquals(listOf("Neon Tokyo", "YouTube", "Play Store", "Media Player", "arcade", "Zebra TV"), sorted.map { it.name })
    }

    @Test
    fun appsWithoutAnIconKeepTheirColour() {
        assertEquals(HomeCatalog.monogramColor("Kodi"), HomeCatalog.monogramColor("kodi"))
        assertNull(HomeCatalog.known("org.xbmc.kodi", "Kodi"))
    }
}
