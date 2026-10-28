package com.lumera.app.launcher

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The colours an app's own icon or banner is shown in: as the app has it, or remixed. A remix is
 * one colour matrix on the drawing of the icon, so it costs the TV nothing more than the icon
 * itself, and the icon is still read from the app on the TV, never kept in Neon Tokyo.
 */
enum class IconTint(val key: String, val title: String, private val dark: Color?, private val light: Color?) {
    /** As the app has it. */
    Own("own", "Own", null, null),

    /** Deep violet shadows, hot pink lights, as on an 80s record sleeve. */
    Synthwave("synthwave", "Synthwave", Color(0xFF1E0440), Color(0xFFFF6AD5)),

    /** Midnight blue to ice cyan. */
    Ice("ice", "Ice", Color(0xFF031A33), Color(0xFF8CF6FF)),

    /** Wine red to warm amber, as the sun goes down over the bay. */
    Sunset("sunset", "Sunset", Color(0xFF3A0820), Color(0xFFFFC46B)),

    /** Green phosphor on black, as an arcade screen. */
    Arcade("arcade", "Arcade", Color(0xFF001808), Color(0xFF6BFF9E)),

    /** Black and white, clean. */
    Mono("mono", "Mono", Color(0xFF0E0E12), Color(0xFFF4F4F8)),

    /** Each app in its own neon colour, the one its tile glows in. */
    Tone("tone", "Own colour", null, null),

    /** The app's own colours, turned up. */
    Vivid("vivid", "Vivid", null, null),

    /** Dimmed and cooled, a little less colourful: quiet for a dark room. */
    Night("night", "Night", null, null),

    /** Light and dark swapped, the colours kept, as on a photo negative. */
    Negative("negative", "Negative", null, null);

    /** The filter for an app whose tile glows in [app], or null to draw the icon as it is. */
    fun filter(app: Color): ColorFilter? = when (this) {
        Own -> null
        Tone -> ColorFilter.colorMatrix(duotone(lerp(app, Color.Black, 0.82f), lerp(app, Color.White, 0.3f)))
        Vivid -> ColorFilter.colorMatrix(VIVID)
        Night -> ColorFilter.colorMatrix(NIGHT)
        Negative -> ColorFilter.colorMatrix(NEGATIVE)
        else -> ColorFilter.colorMatrix(duotone(dark!!, light!!))
    }

    /** The colour an icon in this remix glows in, close to its lights so the two go together. */
    fun glow(app: Color): Color = if (light != null && this != Mono) lerp(light, app, 0.25f) else app

    companion object {
        fun of(key: String?): IconTint = values().firstOrNull { it.key == key } ?: Own

        /** Every pixel's brightness put on the way from [dark] to [light], with a touch more contrast. */
        internal fun duotone(dark: Color, light: Color): ColorMatrix {
            val contrast = 1.15f
            val lift = (0.5f - 0.5f * contrast) * 255f
            fun row(d: Float, l: Float): List<Float> {
                val k = (l - d) * contrast
                return listOf(k * 0.299f, k * 0.587f, k * 0.114f, 0f, d * 255f + (l - d) * lift)
            }
            return ColorMatrix(
                (row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) + listOf(0f, 0f, 0f, 1f, 0f)).toFloatArray()
            )
        }

        private val VIVID = ColorMatrix().apply { setToSaturation(1.7f) }

        // Three quarters of the colour, then dimmed to about 60 %, the blue a little less
        private val NIGHT = ColorMatrix(
            floatArrayOf(
                0.482f, 0.107f, 0.011f, 0f, 0f,
                0.033f, 0.576f, 0.011f, 0f, 0f,
                0.040f, 0.134f, 0.576f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        // Inverted, then turned half way round the colour wheel so the hues come back (feHueRotate 180)
        private val NEGATIVE = ColorMatrix(
            floatArrayOf(
                0.574f, -1.43f, -0.144f, 0f, 255f,
                -0.426f, -0.43f, -0.144f, 0f, 255f,
                -0.426f, -1.43f, 0.856f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
}

internal val LocalIconTint = compositionLocalOf { IconTint.Own }

/**
 * What an app's own icon does when the focus lands on it, each about the app: TV4 beats like a
 * live signal, the music apps groove, Netflix gives its ta-dum, Neon Tokyo lights up like a neon
 * sign. Every one only moves the icon's layer, so the TV redraws, never lays anything out again.
 */
enum class IconMotion {
    /** It rises a touch towards the viewer. */
    Rise,

    /** A heartbeat, two quick beats and a rest, with rings going out as from a broadcast. */
    Beat,

    /** Bobbing and rocking to a 120 bpm beat. */
    Groove,

    /** One turn round on landing, as a coin, then a slow sway. */
    Spin,

    /** Squeezed in, then a pop out that settles: ta-dum. */
    Tadum,

    /** It swings from its top like a sign that was just hung, and settles. */
    Swing,

    /** It floats, light as in a dream, with stars twinkling round it. */
    Drift,

    /** Pressed in like a play button, then it bounces out. */
    Press,

    /** Flickering on as a neon tube does, with an RGB glitch now and then. */
    Neon;

    companion object {
        fun of(name: String): IconMotion = when (name) {
            "Neon Tokyo" -> Neon
            "TV4 Play", "TV4" -> Beat
            "Spotify", "YT Music", "YouTube Music" -> Groove
            "Netflix" -> Tadum
            "YouTube", "SmartTube", "YouTube Kids", "Twitch" -> Press
            "Viaplay", "Prime Video", "Rakuten TV" -> Swing
            "Disney+", "SkyShowtime", "Apple TV", "HBO Max", "Angel", "The Chosen" -> Drift
            "Allente", "Plex", "SVT Play", "Stremio", "Kodi", "Crunchyroll", "iQIYI" -> Spin
            else -> Rise
        }
    }
}

/** How an icon stands at [t] seconds after the focus landed on it. */
internal class Pose {
    var scale = 1f
    var dx = 0f
    var dy = 0f
    var turn = 0f
    var flip = 0f
    var alpha = 1f
    var glitch = 0f
}

/** A soft bump from 0 up to 1 and back over [width] seconds starting at [at]. */
private fun bump(t: Float, at: Float, width: Float): Float {
    val u = (t - at) / width
    return if (u <= 0f || u >= 1f) 0f else sin(u * PI.toFloat())
}

/**
 * The pose of an icon doing [motion], [t] seconds after landing and [lit] from 0 to 1 as the
 * focus light comes up. [unit] is one dp in pixels.
 */
internal fun IconMotion.pose(t: Float, lit: Float, unit: Float, into: Pose): Pose {
    into.scale = 1f + 0.08f * lit
    into.dx = 0f
    into.dy = 0f
    into.turn = 0f
    into.flip = 0f
    into.alpha = 1f
    into.glitch = 0f
    if (lit <= 0f) return into
    when (this) {
        IconMotion.Rise -> {}
        IconMotion.Beat -> {
            val p = t % 1.2f
            into.scale += (0.09f * bump(p, 0f, 0.16f) + 0.05f * bump(p, 0.22f, 0.16f)) * lit
        }
        IconMotion.Groove -> {
            val beat = t * 2f * PI.toFloat()
            into.dy = -3.5f * unit * kotlin.math.abs(sin(beat / 2f)) * lit
            into.turn = 5f * sin(beat / 2f) * lit
        }
        IconMotion.Spin -> {
            val u = (t / 0.75f).coerceAtMost(1f)
            // Out-back, so the turn goes a little past and comes back
            val e = 1f + 2.2f * (u - 1f) * (u - 1f) * (u - 1f) + 1.2f * (u - 1f) * (u - 1f)
            // Once round is the same as none, so it rests at 0 and only sways
            into.flip = ((if (u < 1f) 360f * e else 0f) + 9f * sin(t * 1.6f) * u) * lit
        }
        IconMotion.Tadum -> {
            into.scale += -0.16f * exp(-5f * t) * cos(13f * t) * lit
        }
        IconMotion.Swing -> {
            into.turn = (11f * exp(-2.4f * t) * sin(9f * t) + 2f * sin(t * 1.9f)) * lit
        }
        IconMotion.Drift -> {
            into.dy = -4f * unit * sin(t * 1.8f) * lit
            into.turn = 2.5f * sin(t * 1.3f) * lit
        }
        IconMotion.Press -> {
            into.scale += (-0.14f * exp(-7f * t) * cos(12f * t) + 0.035f * bump(t % 2.4f, 1.2f, 0.3f)) * lit
        }
        IconMotion.Neon -> {
            into.scale = 1f + 0.1f * lit
            into.alpha = neonFlicker(t)
            into.glitch = neonGlitch(t) * lit
        }
    }
    return into
}

/** How lit a neon tube is [t] seconds after it was switched on: a stutter, then a steady glow with a buzz now and then. */
internal fun neonFlicker(t: Float): Float = when {
    t < 0.06f -> 0.85f
    t < 0.13f -> 0.2f
    t < 0.2f -> 1f
    t < 0.27f -> 0.35f
    t < 0.5f -> 0.55f + 0.45f * (t - 0.27f) / 0.23f
    else -> {
        val p = (t - 0.5f) % 5.3f
        if (p > 5.1f) 0.55f else 1f
    }
}

/** How far the colours of a glitch split apart, from 0 to 1: on landing, then a short one every four seconds. */
internal fun neonGlitch(t: Float): Float {
    val p = if (t < 0.6f) t else 0.6f + (t - 0.6f) % 4f
    val g = when {
        p in 0.12f..0.4f -> bump(p, 0.12f, 0.28f)
        p in 3.6f..3.78f -> bump(p, 3.6f, 0.18f)
        else -> 0f
    }
    // It jitters while it lasts, as a bad signal does
    return g * (0.6f + 0.4f * sin(t * 90f))
}

/** The pink, violet and cyan a neon sign runs through. */
internal val NeonTubes = listOf(Color(0xFFFF3D9A), Color(0xFFB04DFF), Color(0xFF3EF0FF))

/** The neon colour at [t] seconds, sliding from tube to tube. */
internal fun neonAt(t: Float): Color {
    val u = (t / 2.2f) % NeonTubes.size
    val i = u.toInt()
    return lerp(NeonTubes[i], NeonTubes[(i + 1) % NeonTubes.size], u - i)
}
