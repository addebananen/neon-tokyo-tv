package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Kodi has its own icon now, so an app the catalog has never heard of stands in
        assertEquals(HomeCatalog.monogramColor("Zebra TV"), HomeCatalog.monogramColor("zebra tv"))
        assertNull(HomeCatalog.known("com.example.zebra", "Zebra TV"))
    }

    @Test
    fun theGearIconIsAClosedOutlineOfThirtyTwoCorners() {
        val outline = gear(50f, 50f, 38f, 28f, 8)
        assertTrue(outline.startsWith("M") && outline.trimEnd().endsWith("Z"))
        // Dots, never commas, whatever language the TV is set to
        val numbers = Regex("-?\\d+\\.\\d+").findAll(outline).map { it.value.toFloat() }.toList()
        assertEquals(64, numbers.size)
        assertTrue(numbers.all { it in 11f..89f })
    }

    @Test
    fun everyAppOnAdriansTvsHasANeonIconOfItsOwn() {
        // Read off the new TV on 2026-10-24, plus the apps on the first one
        val packages = listOf(
            "allente.b2c.tvclient", "com.amazon.amazonvideo.livingroom", "com.angel.tv", "com.disney.disneyplus",
            "com.google.android.videos", "com.google.android.youtube.tv", "com.google.android.youtube.tvmusic",
            "com.iqiyi.i18n.tv", "com.netflix.ninja", "com.spotify.tv.android", "com.stremio.one",
            "com.teamsmart.videomanager.tv", "com.viaplay.android", "com.wbd.stream", "net.comeandsee.ott.thechosen",
            "se.svt.android.svtplay", "se.tv4.tv4playtab", "tv.twitch.android.app", "tv.wuaki.apptv",
            "com.android.vending", "com.google.android.play.games", "com.FDGEntertainment.redball4.gp",
            "com.esaba.downloader", "com.android.tv.settings", "com.neontokyo.tv", "com.tcl.tv", "com.tcl.browser",
            "com.tcl.dashboard", "com.tcl.exhibit", "com.tcl.gallery", "com.tcl.guard", "com.tcl.magiconnectfree",
            "com.tcl.ocean.instructions", "com.tcl.partnercustomizer", "com.tcl.suspension", "com.tcl.t_solo",
            "com.tcl.ui_mediaCenter", "com.tcl.waterfall.overseas",
            "org.xbmc.kodi", "com.apple.atve.androidtv.appletv", "com.nordvpn.android"
        )
        val missing = packages.filter { HomeCatalog.known(it, "") == null }
        assertTrue("no icon for $missing", missing.isEmpty())
    }

    @Test
    fun theTvsOwnAppsKeepTheTvsNameForThem() {
        val settings = InstalledApp("com.android.tv.settings", "Inställningar", HomeCatalog.known("com.android.tv.settings", "Inställningar"))
        assertEquals("Inställningar", settings.name)
        val svt = InstalledApp("se.svt.android.svtplay", "SVT Play TV", HomeCatalog.known("se.svt.android.svtplay", "SVT Play TV"))
        assertEquals("SVT Play", svt.name)
    }
}
