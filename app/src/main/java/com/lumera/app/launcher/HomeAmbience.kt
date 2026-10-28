package com.lumera.app.launcher

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The small things that move in a style's picture: rain running down the window, petals on the
 * wind, the lights of a city. They are drawn on one layer of their own, from the time alone, so
 * nothing is laid out or recomposed while they play; they keep the slow beat of [awaitBeat], as
 * slow things need no more, and stop whenever the home screen is not in front.
 * Places are fractions of the screen and match the pictures in res/drawable-nodpi.
 */
@Composable
fun StyleAmbience(ambience: Ambience, modifier: Modifier = Modifier, light: Int? = null) {
    if (ambience == Ambience.NeonSky || ambience == Ambience.None) return
    val time = rememberAmbientTime()
    val context = LocalContext.current
    val lightImage by produceState<ImageBitmap?>(null, light) {
        value = light?.let { id ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() }.getOrNull() }
        }
    }
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .drawBehind {
                val t = time.value
                when (ambience) {
                    Ambience.Lake -> lake(t, lightImage)
                    Ambience.Calm -> calm(t)
                    Ambience.Dusk -> dusk(t, lightImage)
                    Ambience.NeonSky, Ambience.None -> Unit
                }
            }
    )
}

/** Seconds the home screen has been in front, ticking on the [awaitBeat]. */
@Composable
private fun rememberAmbientTime(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = awaitBeat()
                // A long gap, as after the screen was away, is not a jump forward
                seconds.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
                last = now
            }
        }
    }
    return seconds
}

// The beat the slow animations keep: 30 frames a second, which they need no more than
private const val BEAT_NANOS = 1_000_000_000L / 30

/**
 * Waits for the next frame on the beat the slow animations of the home screen keep, and returns
 * its time. All of them change on the same frames, so together they redraw the screen at most 30
 * times a second, which a TV's graphics chip can keep up with, where each on its own beat would
 * add up to every frame.
 */
internal suspend fun awaitBeat(): Long {
    val beat = withFrameNanos { it } / BEAT_NANOS
    while (true) {
        val now = withFrameNanos { it }
        if (now / BEAT_NANOS != beat) return now
    }
}

/** A steady random number from 0 to 1 for particle [i] and property [k]. */
private fun rnd(i: Int, k: Int): Float {
    val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun fract(x: Float) = x - floor(x)

private fun DrawScope.at(x: Float, y: Float) = Offset(x * size.width, y * size.height)

private fun DrawScope.softLight(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0f || radius <= 0f) return
    drawCircle(
        Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

/**
 * Nature, a photo of Moraine Lake: the first sun breathing on the peaks and in their reflection,
 * now and then a brighter light running along the ridge, mist rising out of the valley, the lake
 * shimmering below the tiles and once in a while two birds crossing the sky.
 */
private fun DrawScope.lake(t: Float, light: ImageBitmap?) {
    val w = size.width
    val h = size.height
    if (light != null) {
        val dst = IntSize(w.roundToInt(), h.roundToInt())
        drawImage(light, dstSize = dst, alpha = 0.14f + 0.12f * sin(t * 0.33f), blendMode = BlendMode.Plus)
        // The ridge catches the sun: a soft band of light crossing the peaks every 18 seconds
        val run = fract(t / 18f) * 1.8f - 0.2f
        if (run in 0.25f..1.05f) {
            val half = 0.07f
            val strips = 16
            var edge = ((run - half).coerceIn(0f, 1f) * light.width).roundToInt()
            for (k in 1..strips) {
                val next = ((run - half + 2 * half * k / strips).coerceIn(0f, 1f) * light.width).roundToInt()
                if (next > edge) {
                    val mid = (edge + next) / 2f / light.width
                    val strength = exp(-((mid - run) / (half * 0.5f)).pow(2))
                    val x0 = (edge.toFloat() / light.width * w).roundToInt()
                    val x1 = (next.toFloat() / light.width * w).roundToInt()
                    drawImage(
                        light,
                        srcOffset = IntOffset(edge, 0),
                        srcSize = IntSize(next - edge, light.height),
                        dstOffset = IntOffset(x0, 0),
                        dstSize = IntSize(x1 - x0, dst.height),
                        alpha = 0.5f * strength,
                        blendMode = BlendMode.Plus
                    )
                }
                edge = next
            }
        }
    }
    // Mist rising out of the valley where it meets the lake, and lying low on the water
    for (i in 0 until 6) {
        val p = fract(rnd(i, 1) + t * (0.007f + 0.005f * rnd(i, 2)))
        val low = i >= 4
        val x = if (low) p * 1.3f - 0.15f else 0.16f + p * 0.56f
        val y = if (low) 0.715f + 0.02f * rnd(i, 3) else 0.455f + 0.055f * rnd(i, 3) - p * 0.025f
        val c = at(x, y)
        val fade = sin(p * PI.toFloat())
        scale(scaleX = 1f, scaleY = if (low) 0.1f else 0.2f, pivot = c) {
            softLight(c, w * (0.13f + 0.08f * rnd(i, 4)), Color(0xFFE2ECF2), (if (low) 0.11f else 0.17f) * fade)
        }
    }
    // Slow swells coming in across the lake
    for (i in 0 until 3) {
        val y = 0.69f + fract(t / 15f + i / 3f) * 0.33f
        val band = h * 0.035f
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color(0x10D8F6FF), 1f to Color.Transparent, startY = y * h - band, endY = y * h + band),
            topLeft = Offset(0f, y * h - band),
            size = Size(w, band * 2)
        )
    }
    // Glints on the water: cool on the open lake, warm in the reflection of the peaks
    val unit = 1.dp.toPx()
    for (i in 0 until 44) {
        val depth = rnd(i, 5).pow(1.3f)
        val y = 0.705f + depth * 0.29f
        val x = fract(rnd(i, 6) + t * 0.003f * (0.5f + depth))
        val flash = sin(t * (0.6f + 0.8f * rnd(i, 7)) + rnd(i, 8) * 6.28f).coerceAtLeast(0f).pow(4)
        if (flash < 0.03f) continue
        val len = w * (0.008f + 0.026f * rnd(i, 9)) * (0.6f + depth * 1.6f)
        val warm = x in 0.45f..0.78f && y < 0.87f
        val c = at(x, y)
        drawLine(
            if (warm) Color(0xFFFFCFA6) else Color(0xFFD4F6FF),
            c - Offset(len / 2, 0f), c + Offset(len / 2, 0f),
            strokeWidth = unit * (0.8f + depth), cap = StrokeCap.Round, alpha = 0.38f * flash
        )
    }
    // Two birds, every 40 seconds, gliding from the right towards the far peaks
    val flight = fract(t / 40f)
    if (flight < 0.45f) {
        val q = flight / 0.45f
        for (b in 0 until 2) {
            val x = 1.04f - q * 0.78f - b * 0.03f
            val y = 0.085f + b * 0.018f + 0.012f * sin(q * 7f + b)
            val span = unit * (13f - 6f * q) * (1f - 0.12f * b)
            val flap = sin(t * 8.5f + b * 1.9f)
            val c = at(x, y)
            val alpha = 0.85f * sin(q * PI.toFloat()).coerceIn(0f, 1f).pow(0.4f)
            val wing = Path().apply {
                moveTo(c.x - span, c.y - flap * span * 0.45f)
                quadraticTo(c.x - span * 0.45f, c.y - span * 0.35f, c.x, c.y)
                quadraticTo(c.x + span * 0.45f, c.y - span * 0.35f, c.x + span, c.y - flap * span * 0.45f)
            }
            drawPath(wing, Color(0xFF141C26), alpha = alpha, style = Stroke(width = unit * 1.6f, cap = StrokeCap.Round))
        }
    }
}

/** Clean: a faint light travels round the ring and along the hairline horizon, and nothing else. */
private fun DrawScope.calm(t: Float) {
    val w = size.width
    val h = size.height
    val ring = at(0.79f, 0.39f)
    val r = w * 0.135f
    val angle = (t / 26f) * 360f
    rotate(angle, pivot = ring) {
        drawArc(
            Brush.sweepGradient(0f to Color.Transparent, 0.12f to Color.White.copy(alpha = 0.32f), 0.14f to Color.Transparent, center = ring),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = ring - Offset(r, r), size = Size(r * 2f, r * 2f),
            style = Stroke(1.2.dp.toPx())
        )
    }
    val run = fract(t / 14f)
    val x = (0.1f + run * 0.85f) * w
    val y = 0.703f * h
    val fade = sin(run * PI.toFloat())
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.4f * fade), Color.Transparent), startX = x - w * 0.08f, endX = x + w * 0.08f),
        Offset(x - w * 0.08f, y), Offset(x + w * 0.08f, y), strokeWidth = 1.dp.toPx()
    )
}

/** Golden Dusk: the violet bulbs of the street lamps in the painting, near to far, and their size. */
private val DuskLamps = floatArrayOf(0.594f, 0.287f, 1f, 0.752f, 0.491f, 0.55f, 0.844f, 0.606f, 0.35f)

/**
 * Golden Dusk, Angel Ganev's painting of a path under burning evening clouds: a slow wave of light
 * rolls across the cloud tops, the violet street lamps hum and flare, stars twinkle in the dark
 * corner of the sky, now and then one falls, and fireflies drift over the field.
 */
private fun DrawScope.dusk(t: Float, light: ImageBitmap?) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    if (light != null) {
        val dst = IntSize(w.roundToInt(), h.roundToInt())
        drawImage(light, dstSize = dst, alpha = 0.06f + 0.05f * sin(t * 0.3f), blendMode = BlendMode.Plus)
        // The last of the sun rolling across the cloud tops every 20 seconds
        val run = fract(t / 20f) * 1.6f - 0.3f
        val half = 0.12f
        val strips = 16
        var edge = ((run - half).coerceIn(0f, 1f) * light.width).roundToInt()
        for (k in 1..strips) {
            val next = ((run - half + 2 * half * k / strips).coerceIn(0f, 1f) * light.width).roundToInt()
            if (next > edge) {
                val mid = (edge + next) / 2f / light.width
                val strength = exp(-((mid - run) / (half * 0.5f)).pow(2))
                val x0 = (edge.toFloat() / light.width * w).roundToInt()
                val x1 = (next.toFloat() / light.width * w).roundToInt()
                drawImage(
                    light, srcOffset = IntOffset(edge, 0), srcSize = IntSize(next - edge, light.height),
                    dstOffset = IntOffset(x0, 0), dstSize = IntSize(x1 - x0, dst.height),
                    alpha = 0.3f * strength, blendMode = BlendMode.Plus
                )
            }
            edge = next
        }
    }
    // Stars twinkling in the dark sky at the top left and along the top
    for (i in 0 until 30) {
        val left = i < 18
        val x = if (left) rnd(i, 1) * 0.3f else 0.3f + rnd(i, 1) * 0.45f
        val y = if (left) rnd(i, 2) * 0.3f else rnd(i, 2) * 0.1f
        val tw = sin(t * (0.8f + 1.4f * rnd(i, 3)) + rnd(i, 4) * 6.28f).coerceAtLeast(0f).pow(3)
        if (tw < 0.05f) continue
        val c = at(x, y)
        val s = unit * (1f + 2.5f * rnd(i, 5)) * tw
        drawLine(Color(0xFFFFE8D8), c - Offset(s * 2f, 0f), c + Offset(s * 2f, 0f), strokeWidth = unit * 0.8f, alpha = 0.7f * tw)
        drawLine(Color(0xFFFFE8D8), c - Offset(0f, s * 2f), c + Offset(0f, s * 2f), strokeWidth = unit * 0.8f, alpha = 0.7f * tw)
        softLight(c, s * 3f, Color(0xFFFFD8C0), 0.5f * tw)
    }
    // Now and then a star falls across the dark corner
    val fall = fract(t / 23f)
    if (fall < 0.06f) {
        val q = fall / 0.06f
        val head = at(0.08f + q * 0.18f, 0.04f + q * 0.1f)
        val tail = head - Offset(w * 0.06f, h * 0.035f) * (0.4f + q * 0.6f)
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFF0E0)), start = tail, end = head),
            tail, head, strokeWidth = unit * 1.6f, cap = StrokeCap.Round, alpha = sin(q * PI.toFloat())
        )
    }
    // The violet lamps humming, with a slow flare breathing out of the nearest one
    for (k in 0 until DuskLamps.size / 3) {
        val c = at(DuskLamps[k * 3], DuskLamps[k * 3 + 1])
        val s = DuskLamps[k * 3 + 2]
        val hum = 0.75f + 0.25f * sin(t * (1.1f + k * 0.4f) + k) + 0.06f * sin(t * 13f + k * 2f)
        softLight(c, w * 0.05f * s, Color(0xFFB48CFF), 0.35f * hum)
        softLight(c, w * 0.012f * s, Color(0xFFF4EEFF), 0.55f * hum)
    }
    val lamp = at(DuskLamps[0], DuskLamps[1])
    val flare = 0.5f + 0.5f * sin(t * 0.4f)
    drawLine(Color(0xFFD8C4FF), lamp - Offset(w * 0.07f, 0f), lamp + Offset(w * 0.07f, 0f), strokeWidth = unit * 1.2f, cap = StrokeCap.Round, alpha = 0.18f * flare)
    for (k in 1..3) {
        val c = lamp + (at(0.5f, 0.5f) - lamp) * (k * 0.45f)
        drawCircle(Color(0xFFC8A8FF), radius = w * (0.012f + 0.01f * k), center = c, alpha = 0.05f * flare, style = Stroke(unit * 1.4f))
    }
    // Fireflies drifting low over the field
    for (i in 0 until 16) {
        val p = fract(t / (14f + 10f * rnd(i, 6)) + rnd(i, 7))
        val x = fract(rnd(i, 8) + p * 0.08f) * 0.9f + 0.02f + 0.01f * sin(t * 0.9f + i)
        val y = 0.72f + 0.24f * rnd(i, 9) + 0.015f * sin(t * 1.3f + i * 2f)
        val blink = sin(t * (1.2f + rnd(i, 10)) + i * 1.7f).coerceAtLeast(0f).pow(2)
        if (blink < 0.05f) continue
        val c = at(x, y)
        softLight(c, unit * 10f, Color(0xFFFFD27A), 0.45f * blink)
        drawCircle(Color(0xFFFFF2C8), unit * 1.6f, c, alpha = 0.85f * blink)
    }
}
