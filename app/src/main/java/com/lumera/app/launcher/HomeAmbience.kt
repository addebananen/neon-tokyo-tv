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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The small things that move in a style's picture: rain running down the window, petals on the
 * wind, the lights of a city. They are drawn on one layer of their own, from the time alone, so
 * nothing is laid out or recomposed while they play; they run at a few frames a second, as
 * slow things need no more, and stop whenever the home screen is not in front.
 * Places are fractions of the screen and match the pictures in res/drawable-nodpi.
 */
@Composable
fun StyleAmbience(ambience: Ambience, modifier: Modifier = Modifier, light: Int? = null) {
    val fps = when (ambience) {
        Ambience.NeonSky -> return
        Ambience.GoogleGlow, Ambience.CityLights, Ambience.Calm -> 20
        Ambience.Lake, Ambience.Glory, Ambience.LoFi, Ambience.Meadow -> 24
        Ambience.Petals, Ambience.Speed, Ambience.NightRain -> 30
    }
    val time = rememberAmbientTime(fps)
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
                    Ambience.GoogleGlow -> googleGlow(t)
                    Ambience.Lake -> lake(t, lightImage)
                    Ambience.Petals -> petals(t)
                    Ambience.Calm -> calm(t)
                    Ambience.CityLights -> cityLights(t, lightImage)
                    Ambience.Glory -> glory(t, lightImage)
                    Ambience.Speed -> speed(t)
                    Ambience.NightRain -> nightRain(t)
                    Ambience.LoFi -> loFi(t)
                    Ambience.Meadow -> meadow(t)
                    Ambience.NeonSky -> Unit
                }
            }
    )
}

/** Seconds the home screen has been in front, ticking [fps] times a second. */
@Composable
private fun rememberAmbientTime(fps: Int): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, fps) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val step = 1000L / fps
            var last = withFrameNanos { it }
            while (true) {
                delay(step)
                withFrameNanos { now ->
                    // A long gap, as after the screen was away, is not a jump forward
                    seconds.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
                    last = now
                }
            }
        }
    }
    return seconds
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

/** Google TV: soft blooms of the four colours drifting slowly behind the right of the screen. */
private fun DrawScope.googleGlow(t: Float) {
    val blooms = listOf(
        Triple(0.80f, 0.22f, Color(0xFF8AB4F8)),
        Triple(0.52f, 0.36f, Color(0xFFF28B82)),
        Triple(0.66f, 0.56f, Color(0xFFFDD663)),
        Triple(0.90f, 0.50f, Color(0xFF81C995))
    )
    blooms.forEachIndexed { i, (bx, by, color) ->
        val x = bx + 0.05f * sin(t * 0.05f * (i + 1) + i * 1.7f)
        val y = by + 0.04f * cos(t * 0.04f * (i + 2) + i * 2.3f)
        val breath = 0.09f + 0.04f * sin(t * 0.11f + i * 1.3f)
        softLight(at(x, y), size.width * (0.22f + 0.03f * sin(t * 0.07f + i)), color, breath)
    }
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

/** Anime: cherry petals carried across the summer sky, turning as they fall. */
private fun DrawScope.petals(t: Float) {
    val unit = 1.dp.toPx()
    for (i in 0 until 18) {
        val speed = 0.035f + 0.03f * rnd(i, 1)
        val p = fract(rnd(i, 2) + t * speed)
        val x = fract(rnd(i, 3) + p * 0.55f) * 1.1f - 0.05f + 0.015f * sin(t * 1.4f + i)
        val y = -0.05f + p * 1.1f
        val s = (4f + 4f * rnd(i, 4)) * unit
        val flip = abs(cos(t * (1.2f + rnd(i, 5)) + i))
        val c = at(x, y)
        rotate(t * 40f * (rnd(i, 6) - 0.5f) + i * 37f, pivot = c) {
            scale(scaleX = 1f, scaleY = 0.35f + 0.65f * flip, pivot = c) {
                drawOval(
                    Color(0xFFFFC7DD),
                    topLeft = c - Offset(s, s * 0.6f),
                    size = Size(s * 2f, s * 1.2f),
                    alpha = 0.55f + 0.35f * rnd(i, 7)
                )
            }
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

/**
 * City, a photo of the Brooklyn Bridge and lower Manhattan at night: the towers hum with light,
 * windows brighten as people come home, the bridge lamps sparkle, the beacon on One World Trade
 * Center pulses, cars cross the bridge, a plane blinks its way over and the river shimmers with
 * the colours above it.
 */
private fun DrawScope.cityLights(t: Float, light: ImageBitmap?) {
    val w = size.width
    val unit = 1.dp.toPx()
    if (light != null) {
        drawImage(light, dstSize = IntSize(w.roundToInt(), h.roundToInt()), alpha = 0.1f + 0.06f * sin(t * 0.5f), blendMode = BlendMode.Plus)
    }
    // Windows coming on, one here and one there, and slowly going back to how they were
    val windows = PhotoSpots.windows
    for (i in 0 until windows.size / 2) {
        val period = 14f + 30f * rnd(i, 1)
        val p = fract(t / period + rnd(i, 2))
        val on = if (p < 0.08f) p / 0.08f else if (p < 0.4f) 1f - (p - 0.08f) / 0.32f else 0f
        if (on <= 0.02f) continue
        val c = at(windows[i * 2], windows[i * 2 + 1])
        softLight(c, unit * 7f, Color(PhotoSpots.windowColours[i]), 0.55f * on)
    }
    // Bridge lamps: a star each, breathing out of step
    val lamps = PhotoSpots.bridgeLamps
    for (i in 0 until lamps.size / 2) {
        val c = at(lamps[i * 2], lamps[i * 2 + 1])
        val shine = 0.5f + 0.5f * sin(t * (1.1f + 0.9f * rnd(i, 3)) + rnd(i, 4) * 6.28f)
        val ray = unit * (9f + 9f * shine)
        softLight(c, unit * 10f, Color(0xFFE8F4FF), 0.25f + 0.3f * shine)
        drawLine(Color(0xFFEFF7FF), c - Offset(ray, 0f), c + Offset(ray, 0f), strokeWidth = unit, cap = StrokeCap.Round, alpha = 0.45f * shine)
        drawLine(Color(0xFFEFF7FF), c - Offset(0f, ray * 0.8f), c + Offset(0f, ray * 0.8f), strokeWidth = unit, cap = StrokeCap.Round, alpha = 0.35f * shine)
    }
    // The beacon on the spire, a slow white heartbeat
    val beat = fract(t / 2.4f)
    val pulse = if (beat < 0.12f) beat / 0.12f else (1f - (beat - 0.12f) / 0.5f).coerceAtLeast(0f)
    softLight(at(PhotoSpots.SPIRE_X, PhotoSpots.SPIRE_Y), unit * 26f, Color(0xFFF2F8FF), 0.75f * pulse)
    // Cars on the bridge roadway, headlights coming, tail lights going, until the tiles hide them
    for (i in 0 until 7) {
        val inbound = i % 2 == 0
        val p = fract(rnd(i, 5) + t * (0.018f + 0.008f * rnd(i, 6)) * (if (inbound) 1f else -1f))
        val x = p * 0.46f
        val c = at(x, 0.363f + 0.3f * x + (if (inbound) 0f else 0.006f))
        val colour = if (inbound) Color(0xFFFFF1D6) else Color(0xFFFF4A3A)
        val fade = (p / 0.06f).coerceIn(0f, 1f) * ((1f - p) / 0.06f).coerceIn(0f, 1f)
        softLight(c, unit * 5f, colour, 0.7f * fade)
        softLight(c + Offset(unit * 4f, unit * 1.2f), unit * 5f, colour, 0.7f * fade)
    }
    // A plane high over the harbour, every 50 seconds, its lights blinking
    val flight = fract(t / 50f)
    if (flight < 0.6f) {
        val q = flight / 0.6f
        val c = at(1.03f - q * 0.62f, 0.06f + 0.035f * q)
        val strobe = if (fract(t * 0.9f) < 0.08f) 1f else 0f
        val beacon = if (fract(t * 0.9f + 0.5f) < 0.18f) 1f else 0.15f
        softLight(c, unit * 5f, Color(0xFFFFFFFF), 0.9f * strobe)
        softLight(c + Offset(unit * 6f, unit * 1.5f), unit * 4f, Color(0xFFFF3B30), 0.85f * beacon)
        drawCircle(Color(0xFFE8EEFF), radius = unit * 0.9f, center = c, alpha = 0.6f)
    }
    // The river: broken streaks of the lights above, rocking on the water
    val river = PhotoSpots.riverColours
    for (i in 0 until 60) {
        val depth = rnd(i, 7).pow(1.2f)
        val y = 0.885f + depth * 0.11f
        val band = (rnd(i, 8) * river.size).toInt().coerceAtMost(river.size - 1)
        val x = (band + rnd(i, 9)) / river.size + 0.006f * sin(t * 0.8f + i)
        val flash = sin(t * (0.9f + 1.1f * rnd(i, 10)) + rnd(i, 11) * 6.28f).coerceAtLeast(0f).pow(3)
        if (flash < 0.04f) continue
        val len = w * (0.006f + 0.02f * rnd(i, 12)) * (0.7f + depth)
        val c = at(x, y)
        drawLine(river[band], c - Offset(len / 2, 0f), c + Offset(len / 2, 0f), strokeWidth = unit * (1f + depth), cap = StrokeCap.Round, alpha = 0.5f * flash)
    }
}

/**
 * Faith, a photo of a cross on a hilltop against the setting sun: the sky breathes, rays of light
 * turn slowly behind the hill, kept to the sky so the hill stays dark, motes of gold drift up in
 * the light and now and then a few birds cross over.
 */
private fun DrawScope.glory(t: Float, sky: ImageBitmap?) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    val sun = at(0.48f, 0.57f)
    if (sky != null) {
        val dst = IntSize(w.roundToInt(), h.roundToInt())
        drawImage(sky, dstSize = dst, alpha = 0.07f + 0.06f * sin(t * 0.4f), blendMode = BlendMode.Plus)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, w, h), Paint().apply { blendMode = BlendMode.Plus })
            val reach = w * 1.1f
            for (i in 0 until 16) {
                val angle = (i * 22.5f + t * 0.9f + 5f * sin(t * 0.17f + i)) * (PI.toFloat() / 180f)
                val spread = (1.6f + 2.4f * rnd(i, 1)) * (PI.toFloat() / 180f)
                val strength = 0.09f + 0.07f * sin(t * (0.25f + 0.3f * rnd(i, 2)) + i * 1.7f)
                val ray = Path().apply {
                    moveTo(sun.x, sun.y)
                    lineTo(sun.x + reach * cos(angle - spread), sun.y + reach * sin(angle - spread))
                    lineTo(sun.x + reach * cos(angle + spread), sun.y + reach * sin(angle + spread))
                    close()
                }
                drawPath(ray, Brush.radialGradient(0f to Color(0xFFFFE9B8).copy(alpha = strength), 1f to Color.Transparent, center = sun, radius = reach))
            }
            // Only where there is sky
            drawImage(sky, dstSize = dst, blendMode = BlendMode.DstIn)
            canvas.restore()
        }
    }
    // Motes of gold rising in the light
    for (i in 0 until 26) {
        val life = fract(rnd(i, 3) + t * (0.02f + 0.02f * rnd(i, 4)))
        val x = 0.3f + 0.4f * rnd(i, 5) + 0.015f * sin(t * 0.7f + i * 2.1f)
        val y = 0.54f - life * 0.4f
        val a = sin(life * PI.toFloat()) * (0.35f + 0.35f * rnd(i, 6))
        drawCircle(Color(0xFFFFF2CC), radius = unit * (1.2f + 1.6f * rnd(i, 7)), center = at(x, y), alpha = a, blendMode = BlendMode.Plus)
    }
    // A few birds crossing the evening sky every 36 seconds
    val flight = fract(t / 36f)
    if (flight < 0.5f) {
        val q = flight / 0.5f
        for (b in 0 until 5) {
            val x = -0.05f + q * 1.1f - b * 0.028f - (b % 2) * 0.01f
            val y = 0.15f + b * 0.016f * (if (b % 2 == 0) 1f else -0.6f) + 0.02f * sin(q * 5f + b)
            val span = unit * (12f - b * 0.8f)
            val flap = sin(t * 8f + b * 1.3f)
            val c = at(x, y)
            val wing = Path().apply {
                moveTo(c.x - span, c.y - flap * span * 0.45f)
                quadraticTo(c.x - span * 0.45f, c.y - span * 0.3f, c.x, c.y)
                quadraticTo(c.x + span * 0.45f, c.y - span * 0.3f, c.x + span, c.y - flap * span * 0.45f)
            }
            drawPath(wing, Color(0xFF1C0F08), alpha = 0.85f, style = Stroke(width = unit * 1.7f, cap = StrokeCap.Round))
        }
    }
}

// Where the coupé's wheels are in both car paintings, and the size of their rims
private val CarWheels = listOf(0.6044f to 0.874f, 0.882f to 0.874f)
private const val CarRim = 0.0248f

/** The coupé's mesh wheels turning: a few bright spokes spinning over the painted rim. */
private fun DrawScope.spinningWheels(t: Float, color: Color) {
    val r = size.width * CarRim
    CarWheels.forEach { (x, y) ->
        val c = at(x, y)
        rotate(-t * 900f, pivot = c) {
            for (k in 0 until 3) {
                val a = k * (2f * PI.toFloat() / 3f)
                drawLine(color, c + Offset(cos(a), sin(a)) * (r * 0.25f), c + Offset(cos(a + 0.4f), sin(a + 0.4f)) * (r * 0.92f), strokeWidth = r * 0.12f, cap = StrokeCap.Round, alpha = 0.16f)
            }
        }
    }
}

/** Car: the track streaming past behind the coupé, the sun's flare breathing, the wheels turning. */
private fun DrawScope.speed(t: Float) {
    val w = size.width
    for (i in 0 until 26) {
        val near = i % 2 == 0
        val y = if (near) 0.89f + 0.1f * rnd(i, 1) else 0.53f + 0.08f * rnd(i, 1)
        val speed = if (near) 0.9f + 0.6f * rnd(i, 2) else 0.25f + 0.2f * rnd(i, 2)
        val len = w * (if (near) 0.12f + 0.15f * rnd(i, 3) else 0.04f + 0.06f * rnd(i, 3))
        val x = fract(rnd(i, 4) + t * speed) * (w + len * 2) - len
        val yy = y * size.height
        // The car is in front of the near streaks
        if (near && x + len > 0.52f * w && x < 0.98f * w && yy < 0.94f * size.height) continue
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFDDB0).copy(alpha = 0.16f), Color.Transparent), startX = x, endX = x + len),
            Offset(x, yy), Offset(x + len, yy), strokeWidth = (if (near) 1.6f else 1f).dp.toPx()
        )
    }
    softLight(at(0.552f, 0.4185f), w * 0.09f, Color(0xFFFFE6B0), 0.12f + 0.06f * sin(t * 0.6f))
    spinningWheels(t, Color(0xFFFFF0DA))
}

/** Car at night: rain falling through the street light, rings on the wet road. */
private fun DrawScope.nightRain(t: Float) {
    val h = size.height
    val unit = 1.dp.toPx()
    for (i in 0 until 70) {
        val p = fract(rnd(i, 1) + t * (0.9f + 0.5f * rnd(i, 2)))
        val x = rnd(i, 3) * size.width
        val y = p * (h + 60f * unit) - 30f * unit
        val len = (14f + 18f * rnd(i, 4)) * unit
        drawLine(Color(0xFFD6E4FF), Offset(x, y), Offset(x - len * 0.12f, y + len), strokeWidth = unit, alpha = 0.12f + 0.12f * rnd(i, 5))
    }
    for (i in 0 until 10) {
        val period = 1.4f + rnd(i, 6)
        val cycle = floor(t / period + rnd(i, 7))
        val p = fract(t / period + rnd(i, 7))
        val seed = i * 31 + cycle.toInt()
        val c = Offset(rnd(seed, 8) * size.width, (0.62f + 0.36f * rnd(seed, 9)) * h)
        val r = (4f + 18f * p) * unit
        scale(scaleX = 1f, scaleY = 0.28f, pivot = c) {
            drawCircle(Color(0xFFE6EEFF), radius = r, center = c, style = Stroke(unit), alpha = 0.3f * (1f - p))
        }
    }
    spinningWheels(t * 0.15f, Color(0xFFB8C0D0))
}

/** Cozy Lo-Fi: drops running down the window, the fairy lights twinkling and steam off the mug. */
private fun DrawScope.loFi(t: Float) {
    val unit = 1.dp.toPx()
    val left = 840f / 1920f
    val right = 1830f / 1920f
    val top = 92f / 1080f
    val bottom = 552f / 1080f
    for (i in 0 until 12) {
        val period = 5f + 6f * rnd(i, 1)
        val cycle = floor(t / period + rnd(i, 2))
        val p = fract(t / period + rnd(i, 2))
        val seed = i * 17 + cycle.toInt()
        // Drops hang, then run: most of the way down happens in the second half
        val run = if (p < 0.35f) p * 0.15f else 0.0525f + (p - 0.35f) / 0.65f * 0.9475f
        val x = left + 0.02f + (right - left - 0.04f) * rnd(seed, 3)
        val y = top + (bottom - top) * run
        val c = at(x, y)
        drawLine(Color(0xFFC8D4FF), at(x, (y - 0.06f).coerceAtLeast(top)), c, strokeWidth = 2f * unit, alpha = 0.12f, cap = StrokeCap.Round)
        drawOval(Color(0xFFE6ECFF), topLeft = c - Offset(2.5f * unit, 3f * unit), size = Size(5f * unit, 6.5f * unit), alpha = 0.45f)
    }
    // The fairy lights, as painted: four swags along the top of the frame
    var k = 0
    for (seg in 0 until 4) {
        val x0 = 740f + seg * 290f
        for (step in 1..9) {
            val s = step / 10f
            val x = (x0 + 290f * s) / 1920f
            val y = (92f + sin(s * PI.toFloat()) * 46f) / 1080f
            val tw = 0.5f + 0.5f * sin(t * (0.7f + rnd(k, 4)) + k * 1.3f)
            softLight(at(x, y), 22f * unit, if (k % 3 == 0) Color(0xFFFF9A7A) else Color(0xFFFFC874), 0.35f * tw)
            k++
        }
    }
    // Steam curling up off the mug
    for (i in 0 until 4) {
        val p = fract(t / 4.5f + i / 4f)
        val c = at(1330f / 1920f + 0.008f * sin(p * 6f + i), 450f / 1080f - p * 0.11f)
        softLight(c, (8f + 14f * p) * unit, Color(0xFFFFF4EA), 0.22f * sin(p * PI.toFloat()))
    }
}

/** Cottagecore: smoke from the chimney, butterflies over the meadow, pollen in the sun. */
private fun DrawScope.meadow(t: Float) {
    val unit = 1.dp.toPx()
    for (i in 0 until 5) {
        val p = fract(t / 9f + i / 5f)
        val c = at(1552f / 1920f + p * 0.04f + 0.006f * sin(p * 7f + i), 180f / 1080f - p * 0.14f)
        softLight(c, (10f + 26f * p) * unit, Color(0xFFFBF8F2), 0.5f * sin(p * PI.toFloat()) * (1f - p * 0.5f))
    }
    listOf(Triple(0.42f, 0.78f, Color(0xFFF2B24A)), Triple(0.88f, 0.8f, Color(0xFFF6F2FF)), Triple(0.62f, 0.9f, Color(0xFFE88AA0))).forEachIndexed { i, (bx, by, color) ->
        val x = bx + 0.06f * sin(t * 0.21f + i * 2f) + 0.02f * sin(t * 0.9f + i)
        val y = by + 0.03f * sin(t * 0.33f + i * 3f) + 0.01f * sin(t * 2.1f + i)
        val c = at(x, y)
        val flap = 0.25f + 0.75f * abs(sin(t * 11f + i))
        val s = 6f * unit
        listOf(-1f, 1f).forEach { side ->
            scale(scaleX = flap, scaleY = 1f, pivot = c) {
                drawOval(color, topLeft = Offset(c.x + side * s * 0.9f - s * 0.9f, c.y - s * 0.6f), size = Size(s * 1.8f, s * 1.2f), alpha = 0.9f)
            }
        }
    }
    for (i in 0 until 24) {
        val p = fract(rnd(i, 1) + t * (0.01f + 0.01f * rnd(i, 2)))
        val x = fract(rnd(i, 3) + p * 0.2f + 0.01f * sin(t * 0.6f + i))
        val y = 0.15f + 0.6f * fract(rnd(i, 4) - p * 0.3f)
        val glint = 0.4f + 0.6f * sin(t * (1f + rnd(i, 5)) + i).coerceAtLeast(0f)
        drawCircle(Color(0xFFFFF6D8), radius = (1f + rnd(i, 6)) * unit, center = at(x, y), alpha = 0.5f * glint)
    }
}
