package com.lumera.app.launcher

import androidx.compose.ui.graphics.Color
import java.text.Collator
import java.util.Locale

/**
 * An app the home screen knows, with its neon icon and colour. With [useLabel] it goes by the
 * TV's own name for it, in the TV's language, which suits the TV's own tools.
 */
data class KnownApp(
    val name: String,
    val glyph: Glyph,
    val color: Color,
    val packages: List<String>,
    val useLabel: Boolean = false
)

/** A console on an HDMI input. [port] is the number on its tile, in the order Adrian plugs them in. */
data class Console(val id: String, val name: String, val glyph: Glyph, val color: Color, val port: Int)

/** An app installed on the TV that the home screen can open. */
data class InstalledApp(val packageName: String, val label: String, val known: KnownApp?) {
    val name: String get() = known?.takeUnless { it.useLabel }?.name ?: label
}

object HomeCatalog {
    val YouTube = KnownApp("YouTube", Glyph.YouTube, Color(0xFFFF4D6D), listOf("com.google.android.youtube.tv"))
    val YtMusic = KnownApp("YT Music", Glyph.YtMusic, Color(0xFFFF6FB5), listOf("com.google.android.youtube.tvmusic"))
    val Spotify = KnownApp("Spotify", Glyph.Spotify, Color(0xFF4DFFA6), listOf("com.spotify.tv.android"))
    val Stremio = KnownApp("Stremio", Glyph.Stremio, Color(0xFFB58CFF), listOf("com.stremio.one"))
    val NeonTokyo = KnownApp("Neon Tokyo", Glyph.NeonTokyo, Color(0xFFFF3D9A), listOf("com.neontokyo.tv", "com.neontokyo.tv.test"))

    /**
     * What the AirPlay button at the top opens. Google TV cannot take AirPlay itself, so a
     * receiver app does it: AirScreen first (free, the one the Play Store offers when none is
     * installed), then AirPin and AirReceiver if one of those is on the TV instead.
     */
    val AirPlay = KnownApp(
        "AirScreen", Glyph.AirPlay, Color(0xFF3EF0FF),
        listOf("com.ionitech.airscreen", "com.waxrain.airplaydmr", "com.softmedia.receiver.lite", "com.softmedia.receiver"),
        useLabel = true
    )

    /** The big tiles on the Home tab. Neon Tokyo has Stremio's place; Stremio stays under All apps. */
    val favourites = listOf(YouTube, YtMusic, Spotify, NeonTokyo)

    val consoles = listOf(
        Console("pc", "PC", Glyph.Pc, Color(0xFFFFD36B), 1),
        Console("switch2", "Switch 2", Glyph.Switch2, Color(0xFFFF5A6E), 2),
        Console("xbox360", "Xbox 360", Glyph.Xbox360, Color(0xFF86FF6A), 3),
        Console("wiiu", "Wii U", Glyph.WiiU, Color(0xFF3EF0FF), 4),
        Console("wii", "Wii", Glyph.Wii, Color(0xFFE8F6FF), 5),
        Console("xbox", "Xbox", Glyph.Xbox, Color(0xFFB8FF3E), 6)
    )

    /** The TV's file and USB player, found by package or, on other models, by name. */
    private val mediaPlayer = KnownApp("Media Player", Glyph.Folder, Color(0xFFFFD36B), listOf("com.tcl.ui_mediaCenter"), useLabel = true)

    /**
     * Everything with an icon of its own, in the order of the All apps tab: films and series,
     * music and live TV, games, tools, then the TV's own apps.
     */
    private val withIcons = listOf(
        NeonTokyo, Stremio, YouTube, YtMusic, Spotify,
        KnownApp("Netflix", Glyph.Tv, Color(0xFFFF2B3A), listOf("com.netflix.ninja", "com.netflix.mediaclient")),
        KnownApp("Prime Video", Glyph.Parcel, Color(0xFF2FC4FF), listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")),
        KnownApp("Disney+", Glyph.Castle, Color(0xFF3D8BFF), listOf("com.disney.disneyplus")),
        KnownApp("HBO Max", Glyph.Moon, Color(0xFF9D6BFF), listOf("com.wbd.stream", "com.hbo.hbonow")),
        KnownApp("SmartTube", Glyph.Tube, Color(0xFFFF5A5A), listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "org.smartteam.smarttube.tv")),
        KnownApp("Viaplay", Glyph.Ticket, Color(0xFFFF7AC8), listOf("com.viaplay.android")),
        KnownApp("SVT Play", Glyph.Tower, Color(0xFF4DFFB8), listOf("se.svt.android.svtplay", "se.svt.svtplay.tv")),
        KnownApp("TV4 Play", Glyph.Mic, Color(0xFFFF4D88), listOf("se.tv4.tv4playtab", "se.tv4.tv4play")),
        KnownApp("Allente", Glyph.Satellite, Color(0xFF6A8BFF), listOf("allente.b2c.tvclient")),
        KnownApp("Apple TV", Glyph.Projector, Color(0xFFE8E8F0), listOf("com.apple.atve.androidtv.appletv")),
        KnownApp("SkyShowtime", Glyph.Star, Color(0xFF9DB4FF), listOf("com.skyshowtime.skyshowtime.google")),
        KnownApp("Rakuten TV", Glyph.Popcorn, Color(0xFFFF6B6B), listOf("tv.wuaki.apptv")),
        KnownApp("Google TV", Glyph.Clapper, Color(0xFF4DD8FF), listOf("com.google.android.videos")),
        KnownApp("Angel", Glyph.Angel, Color(0xFFFFE08A), listOf("com.angel.tv")),
        KnownApp("The Chosen", Glyph.Fish, Color(0xFFFFC46B), listOf("net.comeandsee.ott.thechosen")),
        KnownApp("iQIYI", Glyph.Lantern, Color(0xFF66FF8A), listOf("com.iqiyi.i18n.tv")),
        KnownApp("Crunchyroll", Glyph.Onigiri, Color(0xFFFFA040), listOf("com.crunchyroll.crunchyroid")),
        KnownApp("YouTube Kids", Glyph.Kite, Color(0xFFFF6F6F), listOf("com.google.android.youtube.tvkids")),
        KnownApp("Twitch", Glyph.Arcade, Color(0xFFB58CFF), listOf("tv.twitch.android.app", "tv.twitch.android.viewer")),
        KnownApp("Kodi", Glyph.Speaker, Color(0xFF3EB8FF), listOf("org.xbmc.kodi")),
        KnownApp("Plex", Glyph.Server, Color(0xFFFFB84D), listOf("com.plexapp.android")),
        KnownApp("VLC", Glyph.Cone, Color(0xFFFF8A3D), listOf("org.videolan.vlc")),
        KnownApp("Play Games", Glyph.Gamepad, Color(0xFF86FF6A), listOf("com.google.android.play.games")),
        KnownApp("Red Ball 4", Glyph.Ball, Color(0xFFFF4D4D), listOf("com.FDGEntertainment.redball4.gp")),
        AirPlay,
        KnownApp("Play Store", Glyph.Bag, Color(0xFF86FF6A), listOf("com.android.vending")),
        KnownApp("Downloader", Glyph.Download, Color(0xFFFFA64D), listOf("com.esaba.downloader")),
        KnownApp("NordVPN", Glyph.Lock, Color(0xFF4D9DFF), listOf("com.nordvpn.android")),
        KnownApp("Projectivy", Glyph.Grid, Color(0xFF3EF0FF), listOf("com.spocky.projengmenu")),
        KnownApp("Live TV", Glyph.LiveTv, Color(0xFF3EF0FF), listOf("com.tcl.tv", "com.google.android.tv"), useLabel = true),
        mediaPlayer,
        KnownApp("Settings", Glyph.Gear, Color(0xFFB9A6D6), listOf("com.android.tv.settings"), useLabel = true),
        KnownApp("Gallery", Glyph.Picture, Color(0xFF6AFFD8), listOf("com.tcl.gallery"), useLabel = true),
        KnownApp("T-Cast", Glyph.Cast, Color(0xFF3ED8FF), listOf("com.tcl.magiconnectfree", "com.tcl.magiconnect"), useLabel = true),
        KnownApp("T-Solo", Glyph.Headphones, Color(0xFF86E0FF), listOf("com.tcl.t_solo"), useLabel = true),
        KnownApp("Browser", Glyph.Globe, Color(0xFF4DA6FF), listOf("com.tcl.browser"), useLabel = true),
        KnownApp("Channel", Glyph.Channel, Color(0xFFFF9A4D), listOf("com.tcl.waterfall.overseas"), useLabel = true),
        KnownApp("Dashboard", Glyph.Sliders, Color(0xFFFFD36B), listOf("com.tcl.dashboard"), useLabel = true),
        KnownApp("Guard", Glyph.Shield, Color(0xFF4DFFA6), listOf("com.tcl.guard"), useLabel = true),
        KnownApp("T-Key", Glyph.Key, Color(0xFFFFC46B), listOf("com.tcl.partnercustomizer"), useLabel = true),
        KnownApp("Menu", Glyph.Layers, Color(0xFFB58CFF), listOf("com.tcl.suspension"), useLabel = true),
        KnownApp("E-manual", Glyph.Book, Color(0xFFE8F6FF), listOf("com.tcl.ocean.instructions"), useLabel = true),
        KnownApp("Exhibit", Glyph.Sparkle, Color(0xFFFF8AD8), listOf("com.tcl.exhibit"), useLabel = true)
    )

    /** Colours for apps without an icon of their own, picked by name so an app keeps its colour. */
    private val monogramColors = listOf(
        Color(0xFFFF3D9A), Color(0xFF3EF0FF), Color(0xFFFFD36B), Color(0xFFB58CFF), Color(0xFF86FF6A), Color(0xFFFF8A3D)
    )

    fun known(packageName: String, label: String): KnownApp? {
        withIcons.firstOrNull { packageName in it.packages }?.let { return it }
        // The TV's own media player has a different package on every model
        val name = label.lowercase(Locale.ROOT).replace(" ", "")
        return if (name == "mediaplayer" || name == "media") mediaPlayer else null
    }

    fun monogramColor(label: String): Color =
        monogramColors[(label.lowercase(Locale.ROOT).hashCode() and Int.MAX_VALUE) % monogramColors.size]

    /** The All apps order: the known apps first, in their own order, then the rest by name. */
    fun sortForAllApps(apps: Collection<InstalledApp>): List<InstalledApp> {
        val collator = Collator.getInstance()
        return apps.sortedWith(
            compareBy<InstalledApp> { app -> app.known?.let { withIcons.indexOf(it) } ?: Int.MAX_VALUE }
                .thenComparator { a, b -> collator.compare(a.name, b.name) }
        )
    }
}
