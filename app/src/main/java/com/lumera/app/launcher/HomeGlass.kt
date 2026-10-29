package com.lumera.app.launcher

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * What the tiles, tabs, buttons and menus of the home screen are made of. There is no frosting on
 * the TV: the picture or film behind is shown by the video chip under the window, where nothing
 * can blur it, so the glass is smoked or clear and reads as glass by its light.
 */
enum class HomeGlass(val key: String, val title: String) {
    /** The style's own tiles, lit at their edges. */
    Off("off", "Off"),

    /** Smoked glass the picture shows through, white signs on it and a white plate behind the button in focus. */
    Frost("frost", "Frost"),

    /** Frost with a little of the liquid glass's lit rim and gloss. */
    Soft("soft", "Soft glass"),

    /** Clear glass lit at its rim with a gloss across the top, as Apple's liquid glass, with the apps' own icons on it. */
    Liquid("liquid", "Liquid glass");

    companion object {
        fun of(key: String?): HomeGlass? = entries.firstOrNull { it.key == key }
    }
}

// The rim of liquid glass as a sweep from three o'clock: bright where the light comes in at the
// bottom left and out at the top right, faint between
private val RimStops = arrayOf(
    0f to Color.White.copy(alpha = 0.2f),
    0.11f to Color.White.copy(alpha = 0.06f),
    0.28f to Color.White.copy(alpha = 0.5f),
    0.33f to Color.White.copy(alpha = 0.95f),
    0.44f to Color.White.copy(alpha = 0.35f),
    0.58f to Color.White.copy(alpha = 0.08f),
    0.75f to Color.White.copy(alpha = 0.12f),
    0.85f to Color.White.copy(alpha = 0.75f),
    0.96f to Color.White.copy(alpha = 0.25f),
    1f to Color.White.copy(alpha = 0.2f)
)

/** Behind a white button in focus on smoked glass: the ink on it. */
internal val GlassInk = Color(0xFF0B0D12)

/**
 * A soft shadow round a pane as it comes forward, [lift] from 0 to 1. It lies outside the pane
 * only: an elevation shadow would show through the glass as a dark box inside it.
 */
internal fun Modifier.glassShadow(shape: Shape, lift: () -> Float): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val rings = listOf(3.dp, 7.dp, 12.dp, 18.dp).map { Stroke(it.toPx() * 2f) }
    val drop = 4.dp.toPx()
    onDrawBehind {
        val l = lift().coerceIn(0f, 1f)
        if (l <= 0f) return@onDrawBehind
        clipPath(path, ClipOp.Difference) {
            translate(top = drop * l) {
                rings.forEach { drawPath(path, Color.Black, style = it, alpha = 0.07f * l) }
            }
        }
    }
}

/**
 * A pane of [glass] shaped [shape] behind what the modifier holds. [lit] runs from 0 at rest to 1
 * in focus: smoked glass brightens, or turns white with [white] as a button does, and liquid glass
 * lights up from inside. [panel] is the darker glass of a menu, which text sits on.
 */
internal fun Modifier.glassPane(
    glass: HomeGlass,
    shape: Shape,
    lit: () -> Float,
    white: Boolean = false,
    panel: Boolean = false,
    dim: Boolean = false
): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val hair = Stroke(2.dp.toPx())
    val rim = Stroke(1.8.dp.toPx())
    val rimBrush = Brush.sweepGradient(*RimStops, center = Offset(size.width / 2f, size.height / 2f))
    val gloss = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.24f),
        1f to Color.Transparent,
        center = Offset(size.width * 0.32f, -size.height * 0.06f),
        radius = maxOf(size.width * 0.62f, size.height * 0.9f)
    )
    // Smoked glass: lit along its top edge, faint round the rest
    val smoke = when {
        panel && glass == HomeGlass.Liquid -> Color(0xB3080A10)
        panel -> Color(0xCC0A0C12)
        glass == HomeGlass.Liquid -> Color.Transparent
        else -> Color(0x4D0C0E14)
    }
    val frost = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = if (panel) 0.03f else 0.04f)))
    val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.25f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.06f))
    val edgeLit = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.25f to Color.White.copy(alpha = 0.1f), 1f to Color.White.copy(alpha = 0.06f))
    val focused = if (white) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = if (dim) 0.16f else 0.22f)
    // Liquid glass: clearer in the middle than at its ends, darker at its foot
    val start = Offset(size.width * 0.45f, 0f)
    val end = Offset(size.width * 0.55f, size.height)
    val body = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (panel) 0.16f else 0.22f), 0.42f to Color.White.copy(alpha = if (panel) 0.05f else 0.06f),
        0.7f to Color.White.copy(alpha = 0.03f), 1f to Color.White.copy(alpha = if (panel) 0.07f else 0.10f),
        start = start, end = end
    )
    val bodyLit = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.27f), 0.42f to Color.White.copy(alpha = 0.08f),
        0.7f to Color.White.copy(alpha = 0.05f), 1f to Color.White.copy(alpha = 0.16f),
        start = start, end = end
    )
    val foot = Brush.verticalGradient(0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.14f))
    // Liquid glass in focus glows from inside, brightest a little above its middle, not at its rim
    val shine = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.22f),
        0.55f to Color.White.copy(alpha = 0.08f),
        1f to Color.Transparent,
        center = Offset(size.width * 0.5f, size.height * 0.38f),
        radius = maxOf(size.width, size.height) * 0.62f
    )
    val wide = Stroke(16.dp.toPx())
    val mid = Stroke(9.dp.toPx())
    val near = Stroke(4.dp.toPx())
    onDrawBehind {
        val l = lit().coerceIn(0f, 1f)
        when (glass) {
            HomeGlass.Off -> Unit
            HomeGlass.Frost, HomeGlass.Soft -> {
                drawPath(path, smoke, alpha = 1f - l)
                drawPath(path, frost, alpha = 1f - l)
                if (l > 0f) drawPath(path, focused, alpha = l)
                clipPath(path) {
                    drawPath(path, edge, style = hair, alpha = 1f - l)
                    if (l > 0f && !white) drawPath(path, edgeLit, style = hair, alpha = l)
                    if (glass == HomeGlass.Soft) drawPath(path, rimBrush, style = rim, alpha = 0.3f + 0.15f * l)
                }
                if (glass == HomeGlass.Soft) drawPath(path, gloss, alpha = 0.5f * (1f - if (white) l else 0f))
            }
            HomeGlass.Liquid -> {
                if (smoke.alpha > 0f) drawPath(path, smoke)
                drawPath(path, body, alpha = 1f - l)
                if (l > 0f) drawPath(path, bodyLit, alpha = l)
                clipPath(path) {
                    // The glass's thickness glowing at its edges, the same in focus as at rest
                    drawPath(path, Color.White, style = wide, alpha = 0.07f / 3f)
                    drawPath(path, Color.White, style = mid, alpha = 0.07f / 3f)
                    drawPath(path, Color.White, style = near, alpha = 0.07f / 3f)
                    drawRect(foot)
                    if (l > 0f) drawRect(shine, alpha = l)
                    drawPath(path, Color.White, style = hair, alpha = 0.10f)
                    drawPath(path, rimBrush, style = rim, alpha = 0.55f)
                }
                drawPath(path, gloss)
            }
        }
    }
}

/**
 * A button of the style's own when the glass is off: smoked in its colours with a rim running from
 * [rim1] to [rim2], white in focus.
 */
internal fun Modifier.flairPane(smoke: Color, rim1: Color, rim2: Color, shape: Shape, lit: () -> Float): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val frost = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
    val rim = Brush.linearGradient(
        0f to rim1.copy(alpha = 0.9f), 0.45f to Color.White.copy(alpha = 0.08f), 1f to rim2.copy(alpha = 0.7f),
        start = Offset.Zero, end = Offset(size.width, size.height * 0.6f)
    )
    val gloss = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.16f),
        1f to Color.Transparent,
        center = Offset(size.width * 0.3f, -size.height * 0.1f),
        radius = maxOf(size.width * 0.6f, size.height)
    )
    val line = Stroke(2.dp.toPx())
    onDrawBehind {
        val l = lit().coerceIn(0f, 1f)
        drawPath(path, smoke, alpha = 1f - l)
        drawPath(path, frost, alpha = 1f - l)
        drawPath(path, gloss, alpha = 1f - l)
        clipPath(path) { drawPath(path, rim, style = line, alpha = 1f - l) }
        if (l > 0f) drawPath(path, Color.White.copy(alpha = 0.95f), alpha = l)
    }
}

/** The light that sweeps once across glass as the focus lands on it; [run] goes from 0 to 1. */
internal fun Modifier.glassSheen(shape: Shape, run: () -> Float): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val band = size.width * 0.45f
    onDrawWithContent {
        drawContent()
        val r = run()
        if (r <= 0f || r >= 1f) return@onDrawWithContent
        val x = -size.width * 0.6f + size.width * 1.8f * r
        clipPath(path) {
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    start = Offset(x, 0f),
                    end = Offset(x + band, size.height * 0.25f)
                )
            )
        }
    }
}
