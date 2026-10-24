package com.lumera.app.launcher

import androidx.compose.ui.graphics.Color
import java.text.Collator
import java.util.Locale

/** An app the home screen knows, with its neon icon and colour. */
data class KnownApp(val name: String, val glyph: Glyph, val color: Color, val packages: List<String>)

/** A console on an HDMI input. [port] is the number on its tile, in the order Adrian plugs them in. */
data class Console(val id: String, val name: String, val glyph: Glyph, val color: Color, val port: Int)

/** An app installed on the TV that the home screen can open. */
data class InstalledApp(val packageName: String, val label: String, val known: KnownApp?) {
    val name: String get() = known?.name ?: label
}

object HomeCatalog {
    val YouTube = KnownApp("YouTube", Glyph.YouTube, Color(0xFFFF4D6D), listOf("com.google.android.youtube.tv"))
    val YtMusic = KnownApp("YT Music", Glyph.YtMusic, Color(0xFFFF6FB5), listOf("com.google.android.youtube.tvmusic"))
    val Spotify = KnownApp("Spotify", Glyph.Spotify, Color(0xFF4DFFA6), listOf("com.spotify.tv.android"))
    val Stremio = KnownApp("Stremio", Glyph.Stremio, Color(0xFFB58CFF), listOf("com.stremio.one"))
    val NeonTokyo = KnownApp("Neon Tokyo", Glyph.NeonTokyo, Color(0xFFFF3D9A), listOf("com.neontokyo.tv", "com.neontokyo.tv.test"))

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

    /** Everything with an icon of its own, in the order of the All apps tab. */
    private val withIcons = listOf(
        NeonTokyo, Stremio, YouTube, YtMusic, Spotify,
        KnownApp("Netflix", Glyph.Tv, Color(0xFFFF2B3A), listOf("com.netflix.ninja", "com.netflix.mediaclient")),
        KnownApp("Prime Video", Glyph.Parcel, Color(0xFF2FC4FF), listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")),
        KnownApp("Disney+", Glyph.Castle, Color(0xFF3D8BFF), listOf("com.disney.disneyplus")),
        KnownApp("HBO Max", Glyph.Moon, Color(0xFF9D6BFF), listOf("com.wbd.stream", "com.hbo.hbonow")),
        KnownApp("Play Store", Glyph.Bag, Color(0xFF86FF6A), listOf("com.android.vending")),
        KnownApp("Media Player", Glyph.Folder, Color(0xFFFFD36B), emptyList())
    )
    private val mediaPlayer = withIcons.last()

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
