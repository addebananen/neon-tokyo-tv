package com.lumera.app.projectivy

import com.lumera.app.R

/**
 * The neon skyline in six colours. In Projectivy the wallpaper takes the colour of the app or
 * input whose card has focus: red for YouTube and Netflix, green for Spotify, violet for
 * Stremio, blue for HDMI inputs, and so on.
 */
enum class NeonWallpaper(
    val drawable: Int,
    val label: String,
    /** The horizon colour of that skyline, for swatches. */
    val accent: Long,
    /** Who gets this colour, as shown on the settings page. */
    val apps: String
) {
    TOKYO(R.drawable.nt_wall_tokyo, "Tokyo pink", 0xFFFF3D9A, "Neon Tokyo, and anything pink or grey"),
    CRIMSON(R.drawable.nt_wall_crimson, "Crimson", 0xFFFF2D55, "YouTube, YouTube Music, Netflix, Switch"),
    GOLD(R.drawable.nt_wall_gold, "Gold", 0xFFFFC46B, "Plex, and warm orange or yellow apps"),
    EMERALD(R.drawable.nt_wall_emerald, "Emerald", 0xFF2EE59D, "Spotify, Xbox"),
    OCEAN(R.drawable.nt_wall_ocean, "Ocean", 0xFF3EF0FF, "HDMI inputs, Wii, PC and Steam, Disney+, Prime Video, Kodi"),
    VIOLET(R.drawable.nt_wall_violet, "Violet", 0xFFB46BFF, "Stremio, Max, Twitch");

    companion object {
        // Checked first, so a renamed input ("Switch 2") wins over the input app's own package
        private val titleWords = listOf(
            "switch" to CRIMSON, "nintendo" to CRIMSON, "wii" to OCEAN,
            "xbox" to EMERALD, "playstation" to OCEAN, "ps5" to OCEAN, "ps4" to OCEAN,
            "steam" to OCEAN
        )

        private val packages = listOf(
            "com.google.android.youtube" to CRIMSON,
            "com.netflix" to CRIMSON,
            "se.tv4" to CRIMSON,
            "com.spotify" to EMERALD,
            "com.stremio" to VIOLET,
            "com.wbd" to VIOLET,
            "com.hbo" to VIOLET,
            "tv.twitch" to VIOLET,
            "com.neontokyo" to TOKYO,
            "com.lumera" to TOKYO,
            "com.disney" to OCEAN,
            "com.amazon" to OCEAN,
            "com.apple" to OCEAN,
            "org.xbmc.kodi" to OCEAN,
            "com.plexapp" to GOLD
        )

        fun forCard(packageName: String?, title: String?, lightColor: Int): NeonWallpaper {
            val name = title?.lowercase().orEmpty()
            titleWords.firstOrNull { (word, _) -> word in name }?.let { return it.second }
            val pkg = packageName.orEmpty()
            packages.firstOrNull { (prefix, _) -> pkg.startsWith(prefix) }?.let { return it.second }
            if ("hdmi" in name || name == "pc" || name.startsWith("pc ")) return OCEAN
            return forColor(lightColor)
        }

        /** Nearest colour for a card's own colour; greys and near-blacks stay Tokyo pink. */
        fun forColor(argb: Int): NeonWallpaper {
            val r = (argb shr 16 and 0xFF) / 255f
            val g = (argb shr 8 and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            if (max < 0.15f || delta / max < 0.25f) return TOKYO
            val hue = when (max) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            return when {
                hue < 16f -> CRIMSON
                hue < 76f -> GOLD
                hue < 171f -> EMERALD
                hue < 236f -> OCEAN
                hue < 300f -> VIOLET
                hue < 339f -> TOKYO
                else -> CRIMSON
            }
        }
    }
}
