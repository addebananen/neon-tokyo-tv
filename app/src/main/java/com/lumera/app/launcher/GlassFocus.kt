package com.lumera.app.launcher

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sin

/**
 * How a liquid glass tile comes forward as the focus lands on it, chosen per profile and style in
 * its Customize menu. Every one grows the glass and lights it from inside; they differ in what
 * plays once on landing. Only the tile in focus moves, and only in drawing and its own layer.
 */
enum class GlassFocus(val key: String, val title: String) {
    /** Grows 10 % and a light sweeps across once. */
    Glow("glow", "Calm glow"),

    /** As Calm glow, and the light inside breathes slowly while the tile is in focus. */
    Breathe("breathe", "Breathe"),

    /** Leans towards where the focus came from and straightens, a spot of light sliding across. */
    Tilt("tilt", "Tilt"),

    /** Lands like a drop: squashed, stretched, settled; the icon bobs with it. */
    Liquid("liquid", "Liquid"),

    /** A light runs once round the rim. */
    Rim("rim", "Rim light"),

    /** A ring spreads from the middle inside the glass, as a drop in water. */
    Drop("drop", "Drop"),

    /** The glass grows a little less and the icon lifts off it, a shadow under it. */
    Layers("layers", "Layers");

    /** The light that sweeps across glass as the focus lands. */
    val sweeps: Boolean get() = this == Glow || this == Breathe || this == Layers

    companion object {
        fun of(key: String?): GlassFocus? = entries.firstOrNull { it.key == key }

        /** Where on the screen the tile last in focus sat, so the next one knows which way the focus came. */
        @Volatile
        var lastX = Float.NaN
    }
}

/** 0 to 1 eased out, for a light or a lean settling. */
internal fun easeOut(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

/**
 * How much wider and taller the glass is than its focus size [land] of the way through landing as a
 * drop: squashed wide, stretched tall, a last small swing, still by 0.78 of the way.
 */
internal fun squash(land: Float): Pair<Float, Float> {
    val t = (land / 0.78f).coerceIn(0f, 1f)
    if (t >= 1f) return 0f to 0f
    val swing = sin(t * 3f * Math.PI).toFloat() * (1f - t) * (1f - t)
    return 0.05f * swing to -0.05f * swing
}

/** How far the icon bobs down and back as the glass lands as a drop, as a share of the tile's height. */
internal fun bob(land: Float): Float {
    val t = (land / 0.7f).coerceIn(0f, 1f)
    if (t >= 1f) return 0f
    return sin(t * 2f * Math.PI).toFloat() * (1f - t) * 0.04f
}

/**
 * What [motion] plays on liquid glass shaped [shape] as the focus lands: [land] runs 0 to 1 once
 * each landing, [lit] is the focus light and [from] which way the focus came (-1 from the right,
 * 1 from the left, 0 not known). Drawn over the pane, under what is on the glass.
 */
internal fun Modifier.glassFocusFx(
    motion: GlassFocus,
    shape: Shape,
    land: () -> Float,
    lit: () -> Float,
    from: () -> Float
): Modifier = if (motion == GlassFocus.Glow || motion == GlassFocus.Breathe || motion == GlassFocus.Liquid) this else drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val center = Offset(size.width / 2f, size.height / 2f)
    val rimStroke = Stroke(2.2.dp.toPx())
    val ringStroke = Stroke(2.dp.toPx())
    val haloStroke = Stroke(9.dp.toPx())
    // The rim's runner: a bright arc that the matrix turns round the middle
    val turn = Matrix()
    val runner = SweepGradient(
        center.x, center.y,
        intArrayOf(0, 0, Color.White.copy(alpha = 0.95f).toArgb(), 0),
        floatArrayOf(0f, 0.7f, 0.85f, 1f)
    )
    val runnerBrush = ShaderBrush(runner)
    val reach = max(size.width, size.height) * 0.62f
    onDrawBehind {
        val t = land()
        val l = lit()
        when (motion) {
            GlassFocus.Tilt -> if (l > 0f) {
                // The spot slides from the side the focus came from towards the middle
                val dir = from().let { if (it == 0f) 1f else it }
                val x = size.width * (0.5f - 0.25f * dir + 0.4f * dir * easeOut(t))
                drawPath(
                    path,
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.36f), 1f to Color.Transparent,
                        center = Offset(x, size.height * 0.2f), radius = size.width * 0.32f
                    ),
                    alpha = l
                )
            }
            GlassFocus.Rim -> if (t > 0f && t < 1f) {
                turn.setRotate(-40f + 360f * easeOut(t), center.x, center.y)
                runner.setLocalMatrix(turn)
                val fade = (t / 0.15f).coerceAtMost(1f) * ((1f - t) / 0.15f).coerceAtMost(1f)
                clipPath(path) { drawPath(path, runnerBrush, style = rimStroke, alpha = fade) }
            }
            GlassFocus.Drop -> if (t > 0f && t < 1f) {
                val r = reach * (0.08f + 0.92f * easeOut(t))
                val a = 0.8f * (1f - t)
                clipPath(path) {
                    drawCircle(Color.White, r, center, alpha = a * 0.2f, style = haloStroke)
                    drawCircle(Color.White, r, center, alpha = a, style = ringStroke)
                }
            }
            GlassFocus.Layers -> if (l > 0f) {
                // The lifted icon's shadow on the glass under it
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x59001423), 1f to Color.Transparent,
                        center = Offset(center.x, center.y + size.height * 0.08f), radius = size.height * 0.32f
                    ),
                    radius = size.height * 0.32f,
                    center = Offset(center.x, center.y + size.height * 0.08f),
                    alpha = l
                )
            }
            else -> Unit
        }
    }
}
