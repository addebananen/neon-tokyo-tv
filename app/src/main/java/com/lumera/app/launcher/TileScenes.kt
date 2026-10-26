package com.lumera.app.launcher

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * More of the little scenes a big Home tile plays while it is in focus, for the streaming apps
 * people open most. Each is about what the app is for, drawn in its own colour on the right of
 * the tile and fading out towards its name, and none of them is anyone's logo.
 */

/** How strongly a scene shows: half lit while its tile rests, fully lit in focus. */
internal fun sceneStrength(lit: Float) = 0.55f + 0.45f * lit

/** Rings going out from a pulsing live light, as from a broadcast: TV4 Play. */
internal fun CacheDrawScope.broadcastScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.82f, h * 0.5f)
    val reach = h * 0.95f
    val glow = Brush.radialGradient(0f to color.copy(alpha = 0.34f), 1f to Color.Transparent, center = center, radius = reach)
    val ring = Stroke(1.6.dp.toPx())
    val dot = 4.5.dp.toPx()
    val core = lerp(color, Color.White, 0.55f)
    val period = 1.8f
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        drawCircle(glow, radius = reach, center = center, alpha = strength)
        // Three rings at a time, each fading as it goes out
        for (k in 0 until 3) {
            val age = ((t / period) + k / 3f) % 1f
            drawCircle(color, radius = dot + (reach - dot) * age, center = center, style = ring, alpha = (1f - age) * 0.7f * strength)
        }
        val beat = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / period)
        drawCircle(color, radius = dot * (1.9f + 0.5f * beat), center = center, alpha = 0.3f * strength)
        drawCircle(core, radius = dot, center = center, alpha = strength)
    }
}

/** Three ribbons of light flowing in long waves: SVT Play, HBO Max, Apple TV. */
internal fun CacheDrawScope.wavesScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val from = w * 0.36f
    val step = 3.dp.toPx()
    val paths = List(3) { Path() }
    val colors = listOf(color, lerp(color, Color.White, 0.35f), lerp(color, Color(0xFF3EF0FF), 0.4f))
    val brushes = colors.map { c -> Brush.horizontalGradient(0f to Color.Transparent, 0.35f to c, 1f to c.copy(alpha = 0.7f), startX = from, endX = w) }
    val widths = listOf(2.4.dp.toPx(), 1.4.dp.toPx(), 1.dp.toPx())
    val glow = Brush.radialGradient(0f to color.copy(alpha = 0.22f), 1f to Color.Transparent, center = Offset(w * 0.85f, h * 0.55f), radius = h)
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        drawRect(glow, alpha = strength)
        paths.forEachIndexed { i, path ->
            path.reset()
            val mid = h * (0.42f + i * 0.1f)
            val amp = h * (0.17f - i * 0.03f)
            val speed = 1.1f + i * 0.35f
            var x = from
            while (x <= w) {
                val u = (x - from) / (w - from)
                val y = mid + amp * sin(u * 5.2f + t * speed + i * 1.7f) * (0.35f + 0.65f * u)
                if (x == from) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            drawPath(path, brushes[i], style = Stroke(widths[i], cap = StrokeCap.Round), alpha = strength)
        }
    }
}

/** A satellite circling a planet at the edge of the tile, its trail behind it: Allente, Plex. */
internal fun CacheDrawScope.orbitScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val planet = h * 0.36f
    val center = Offset(w * 0.86f, h * 0.56f)
    val rx = h * 0.78f
    val ry = h * 0.26f
    val tilt = -14f
    val body = Brush.radialGradient(
        0f to lerp(color, Color.White, 0.25f),
        0.7f to color.copy(alpha = 0.55f),
        1f to Color(0xFF0B0516),
        center = Offset(center.x - planet * 0.35f, center.y - planet * 0.4f),
        radius = planet * 1.5f
    )
    val glow = Brush.radialGradient(0f to color.copy(alpha = 0.3f), 1f to Color.Transparent, center = center, radius = h)
    val line = Stroke(0.8.dp.toPx())
    val oval = Size(rx * 2f, ry * 2f)
    val ovalAt = Offset(center.x - rx, center.y - ry)
    val head = 3.dp.toPx()
    val core = lerp(color, Color.White, 0.6f)
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        drawCircle(glow, radius = h, center = center, alpha = strength)
        rotate(tilt, center) {
            drawOval(color, ovalAt, oval, style = line, alpha = 0.3f * strength)
        }
        // Behind the planet on the far side of its orbit, in front of it on the near side
        val angle = t * 1.3f
        val near = sin(angle) > 0f
        fun spot(a: Float): Offset {
            val x = rx * cos(a)
            val y = ry * sin(a)
            val r = tilt * PI.toFloat() / 180f
            return Offset(center.x + x * cos(r) - y * sin(r), center.y + x * sin(r) + y * cos(r))
        }
        fun satellite() {
            for (k in 8 downTo 1) drawCircle(color, radius = head * (1f - k / 10f), center = spot(angle - k * 0.07f), alpha = (1f - k / 9f) * 0.5f * strength)
            drawCircle(color, radius = head * 2.2f, center = spot(angle), alpha = 0.3f * strength)
            drawCircle(core, radius = head, center = spot(angle), alpha = strength)
        }
        if (!near) satellite()
        drawCircle(body, radius = planet, center = center, alpha = 0.6f + 0.4f * strength)
        if (near) satellite()
    }
}

/** Two searchlights sweeping the sky, as over a premiere: Viaplay, Prime Video. */
internal fun CacheDrawScope.spotlightScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val length = h * 1.6f
    val spread = 7f
    val beam = Path()
    val bases = listOf(Offset(w * 0.7f, h + 4f), Offset(w * 0.93f, h + 4f))
    val brushes = bases.map { base -> Brush.radialGradient(0f to color.copy(alpha = 0.55f), 1f to Color.Transparent, center = base, radius = length) }
    val floor = Brush.verticalGradient(0f to Color.Transparent, 1f to color.copy(alpha = 0.25f), startY = h * 0.6f, endY = h)
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        drawRect(floor, topLeft = Offset(w * 0.45f, h * 0.6f), size = Size(w * 0.55f, h * 0.4f), alpha = strength)
        bases.forEachIndexed { i, base ->
            // Each sways on its own, so they cross now and then
            val lean = (if (i == 0) -18f else 14f) + 24f * sin(t * (0.9f + i * 0.4f) + i * 2f)
            val a = (lean - 90f) * PI.toFloat() / 180f
            val s = spread * PI.toFloat() / 180f
            beam.reset()
            beam.moveTo(base.x, base.y)
            beam.lineTo(base.x + length * cos(a - s), base.y + length * sin(a - s))
            beam.lineTo(base.x + length * cos(a + s), base.y + length * sin(a + s))
            beam.close()
            drawPath(beam, brushes[i], alpha = strength)
        }
    }
}

/** Upright ribbons of light that brighten in a wave running across them: Netflix. */
internal fun CacheDrawScope.ribbonsScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val count = 7
    val ribbon = 5.dp.toPx()
    val gap = 4.dp.toPx()
    val right = w - 12.dp.toPx()
    val brush = Brush.verticalGradient(0f to Color.Transparent, 0.5f to color, 1f to Color.Transparent, startY = 0f, endY = h)
    val bright = Brush.verticalGradient(0f to Color.Transparent, 0.5f to lerp(color, Color.White, 0.45f), 1f to Color.Transparent, startY = 0f, endY = h)
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        for (i in 0 until count) {
            val x = right - ribbon - i * (ribbon + gap)
            // The ones further left, towards the name, are fainter
            val fade = 1f - i / count.toFloat()
            val wave = 0.5f + 0.5f * sin(t * 2.4f - i * 0.8f)
            val tall = h * (0.55f + 0.3f * wave)
            val top = (h - tall) / 2f
            drawRect(brush, Offset(x, top), Size(ribbon, tall), alpha = (0.25f + 0.35f * wave) * fade * strength)
            drawRect(bright, Offset(x + ribbon * 0.35f, top), Size(ribbon * 0.3f, tall), alpha = wave * fade * strength)
        }
    }
}

/** Stars twinkling, and now and then one shooting over them in an arc: Disney+, SkyShowtime. */
internal fun CacheDrawScope.stardustScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    // The same sky every time, scattered over the right of the tile
    val stars = List(16) { k ->
        val u = ((k * 0.618034f) % 1f)
        val v = ((k * 0.7548777f + 0.21f) % 1f)
        Triple(Offset(w * (0.45f + 0.53f * u), h * (0.12f + 0.76f * v)), 0.7f + (k % 3) * 0.45f, k * 1.37f)
    }
    val dot = 1.4.dp.toPx()
    val glow = Brush.radialGradient(0f to color.copy(alpha = 0.26f), 1f to Color.Transparent, center = Offset(w * 0.8f, h * 0.35f), radius = h)
    val star = lerp(color, Color.White, 0.6f)
    val arcCenter = Offset(w * 0.78f, h * 1.25f)
    val arcRadius = h * 1.05f
    val period = 3.6f
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        drawRect(glow, alpha = strength)
        stars.forEach { (at, size, phase) ->
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 2.6f + phase))
            drawCircle(star, radius = dot * size, center = at, alpha = twinkle * strength)
            if (size > 1.5f) drawCircle(color, radius = dot * size * 3f, center = at, alpha = 0.18f * twinkle * strength)
        }
        // A shooting star across the top of the arc, then a pause
        val run = (t % period) / period
        if (run < 0.55f) {
            val p = run / 0.55f
            val a = (200f + 140f * p) * PI.toFloat() / 180f
            for (k in 10 downTo 0) {
                val b = a - k * 0.028f
                val at = Offset(arcCenter.x + arcRadius * cos(b), arcCenter.y + arcRadius * sin(b))
                val fade = (1f - k / 11f) * sin(p * PI.toFloat())
                drawCircle(if (k == 0) Color.White else color, radius = dot * (2.2f - k * 0.15f), center = at, alpha = fade * strength)
            }
        }
    }
}
