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
import androidx.compose.ui.graphics.StrokeCap
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
        Ambience.Lake, Ambience.Glory, Ambience.LoFi, Ambience.Meadow, Ambience.Forest, Ambience.Bubbles, Ambience.Library, Ambience.Sunroom -> 24
        Ambience.Petals, Ambience.Speed, Ambience.NightRain, Ambience.Cosmos, Ambience.Vapor -> 30
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
                    Ambience.Forest -> forest(t)
                    Ambience.Cosmos -> cosmos(t, lightImage)
                    Ambience.Bubbles -> bubbles(t)
                    Ambience.Library -> library(t, lightImage)
                    Ambience.Sunroom -> sunroom(t, lightImage)
                    Ambience.Vapor -> vapor(t)
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

/**
 * Car at night, a photo of a dark supercar coming at you down a wet road: the headlights flare
 * with a streak across the lens, light rushes past from the point it comes out of, rain falls
 * through the beams and the road shines under them.
 */
private fun DrawScope.nightRain(t: Float) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    val origin = at(0.475f, 0.33f)
    // Light rushing past, out of the point the car comes from, faster the further out it is
    for (i in 0 until 28) {
        val angle = rnd(i, 1) * 2f * PI.toFloat()
        val p = fract(rnd(i, 2) + t * (0.35f + 0.3f * rnd(i, 3)))
        val near = p * p
        val from = w * (0.12f + near * 0.75f)
        val len = w * (0.02f + near * 0.12f)
        val dir = Offset(cos(angle), sin(angle) * 0.62f)
        val a = from * dir.x
        if (abs(a) < w * 0.12f && sin(angle) < 0.3f) continue
        drawLine(
            Color(0xFFDDEBEF),
            origin + dir * from, origin + dir * (from + len),
            strokeWidth = unit * (0.6f + 1.4f * near), cap = StrokeCap.Round, alpha = 0.22f * near * (1f - p)
        )
    }
    // Rain, slanting through the beams
    for (i in 0 until 80) {
        val p = fract(rnd(i, 4) + t * (1.1f + 0.6f * rnd(i, 5)))
        val x = rnd(i, 6) * w
        val y = p * (h + 60f * unit) - 30f * unit
        val len = (12f + 16f * rnd(i, 7)) * unit
        val lit = (1f - abs(x / w - 0.475f) / 0.25f).coerceIn(0f, 1f) * (1f - abs(y / h - 0.42f) / 0.35f).coerceIn(0f, 1f)
        drawLine(Color(0xFFE2EEFF), Offset(x, y), Offset(x - len * 0.18f, y + len), strokeWidth = unit, alpha = 0.1f + 0.1f * rnd(i, 8) + 0.35f * lit)
    }
    // The headlights: a slow breath and a blue streak across the lens
    val lights = listOf(at(0.385f, 0.335f), at(0.57f, 0.35f))
    val breath = 0.5f + 0.5f * sin(t * 1.3f)
    lights.forEachIndexed { i, c ->
        softLight(c, w * 0.035f, Color(0xFFEAF6FF), 0.35f + 0.2f * breath)
        softLight(c, w * 0.09f, Color(0xFF9FD0FF), 0.08f + 0.06f * breath)
        val reach = w * (0.16f + 0.04f * sin(t * 0.9f + i))
        drawLine(
            Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color(0xFFB8DCFF), 1f to Color.Transparent, startX = c.x - reach, endX = c.x + reach),
            c - Offset(reach, 0f), c + Offset(reach, 0f), strokeWidth = unit * 1.6f, alpha = 0.4f + 0.25f * breath
        )
    }
    // The road shining in the beams below the tiles, rushing towards you
    for (i in 0 until 26) {
        val p = fract(rnd(i, 9) + t * (0.5f + 0.4f * rnd(i, 10)))
        val side = if (i % 2 == 0) 0.39f else 0.565f
        val y = 0.7f + p * p * 0.3f
        val x = side + (rnd(i, 11) - 0.5f) * (0.05f + p * 0.12f)
        val len = h * (0.01f + 0.05f * p)
        val c = at(x, y)
        drawLine(Color(0xFFEFF6FF), c, c + Offset(0f, len), strokeWidth = unit * (1f + p * 2f), cap = StrokeCap.Round, alpha = 0.3f * (1f - p) * (0.5f + rnd(i, 12)))
    }
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

/**
 * Cottagecore, a photo of a turf-roofed cottage above the sea on a misty day: smoke curls from the
 * chimney, gusts of wind run through the meadow and set the buttercups nodding, sea fog drifts
 * past, a fine drizzle falls and now and then a gull glides over the water.
 */
private fun DrawScope.meadow(t: Float) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    // Smoke from the chimney, leaning with the wind
    for (i in 0 until 7) {
        val p = fract(t / 11f + i / 7f)
        val c = at(PhotoSpots.CHIMNEY_X - p * 0.07f + 0.008f * sin(p * 6f + i), PhotoSpots.CHIMNEY_Y - 0.01f - p * 0.16f)
        softLight(c, (8f + 30f * p) * unit, Color(0xFF7D838C), 0.32f * sin(p * PI.toFloat()) * (1f - p * 0.6f))
    }
    // Sea fog drifting along the horizon
    for (i in 0 until 4) {
        val x = fract(rnd(i, 1) + t * (0.004f + 0.002f * rnd(i, 2))) * 1.5f - 0.25f
        val c = at(x, 0.33f + 0.05f * rnd(i, 3))
        scale(scaleX = 1f, scaleY = 0.18f, pivot = c) {
            softLight(c, w * (0.16f + 0.08f * rnd(i, 4)), Color(0xFFF4F5FA), 0.35f)
        }
    }
    // Gusts running through the meadow, left to right
    val gusts = 2
    val strength = FloatArray(gusts)
    for (g in 0 until gusts) {
        val p = fract(t / 9f + g * 0.5f)
        val x = -0.3f + p * 1.6f
        strength[g] = x
        val band = w * 0.18f
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color(0x26F2FFC8), 1f to Color.Transparent, startX = x * w - band, endX = x * w + band),
            topLeft = Offset(x * w - band, h * 0.74f),
            size = Size(band * 2, h * 0.26f)
        )
    }
    // Buttercups nodding as a gust passes
    val flowers = PhotoSpots.buttercups
    for (i in 0 until flowers.size / 2) {
        val fx = flowers[i * 2]
        var push = 0f
        for (g in 0 until gusts) push += exp(-((fx - strength[g]) / 0.12f).pow(2))
        val sway = (0.4f * sin(t * 2.2f + i) + push * 1.6f) * unit
        drawCircle(Color(0xFFFFD84A), radius = unit * 1.6f, center = at(fx, flowers[i * 2 + 1]) + Offset(sway, -abs(sway) * 0.3f), alpha = 0.55f + 0.3f * push.coerceAtMost(1f))
    }
    // A fine drizzle
    for (i in 0 until 50) {
        val p = fract(rnd(i, 5) + t * (0.6f + 0.3f * rnd(i, 6)))
        val x = rnd(i, 7) * w
        val y = p * (h + 40f * unit) - 20f * unit
        val len = (8f + 8f * rnd(i, 8)) * unit
        drawLine(Color(0xFFFFFFFF), Offset(x, y), Offset(x - len * 0.25f, y + len), strokeWidth = unit * 0.8f, alpha = 0.1f + 0.1f * rnd(i, 9))
    }
    // A gull over the sea, every 30 seconds
    val flight = fract(t / 30f)
    if (flight < 0.4f) {
        val q = flight / 0.4f
        val c = at(0.02f + q * 0.2f, 0.42f - 0.04f * sin(q * PI.toFloat()))
        val span = unit * 11f
        val flap = 0.3f + 0.7f * sin(t * 5f)
        val wing = Path().apply {
            moveTo(c.x - span, c.y - flap * span * 0.4f)
            quadraticTo(c.x - span * 0.4f, c.y - span * 0.32f, c.x, c.y)
            quadraticTo(c.x + span * 0.4f, c.y - span * 0.32f, c.x + span, c.y - flap * span * 0.4f)
        }
        drawPath(wing, Color(0xFF4E555E), alpha = 0.8f * sin(q * PI.toFloat()).pow(0.3f), style = Stroke(width = unit * 1.6f, cap = StrokeCap.Round))
    }
}

/**
 * Naturecore, a photo of an old forest of ferns with the sun low between the trunks: shafts of
 * light turn and breathe out of the sun, dust floats in them, the sun itself flares softly and a
 * leaf comes down now and then.
 */
private fun DrawScope.forest(t: Float) {
    val w = size.width
    val unit = 1.dp.toPx()
    val sun = at(0.451f, 0.378f)
    val reach = w * 0.9f
    for (i in 0 until 14) {
        val angle = (i * 25.7f + 4f * sin(t * 0.13f + i * 1.9f)) * (PI.toFloat() / 180f)
        // Keep the shafts off the clock in the top left corner
        val towardClock = (cos(angle) < -0.4f && sin(angle) < -0.2f)
        val spread = (1.2f + 2f * rnd(i, 1)) * (PI.toFloat() / 180f)
        val strength = (0.07f + 0.06f * sin(t * (0.2f + 0.25f * rnd(i, 2)) + i * 2.3f)) * (if (towardClock) 0.3f else 1f)
        val ray = Path().apply {
            moveTo(sun.x, sun.y)
            lineTo(sun.x + reach * cos(angle - spread), sun.y + reach * sin(angle - spread))
            lineTo(sun.x + reach * cos(angle + spread), sun.y + reach * sin(angle + spread))
            close()
        }
        drawPath(ray, Brush.radialGradient(0f to Color(0xFFFFE7A8).copy(alpha = strength), 1f to Color.Transparent, center = sun, radius = reach), blendMode = BlendMode.Plus)
    }
    val flare = 0.5f + 0.5f * sin(t * 0.8f)
    softLight(sun, w * 0.06f, Color(0xFFFFF4D2), 0.25f + 0.15f * flare)
    // Ghosts of the sun across the lens
    listOf(0.6f to 0.018f, 1.25f to 0.03f, 1.7f to 0.012f).forEachIndexed { i, (k, r) ->
        val c = Offset(sun.x + (w * 0.5f - sun.x) * k, sun.y + (size.height * 0.5f - sun.y) * k)
        softLight(c, w * r, if (i == 1) Color(0xFFB8F0C8) else Color(0xFFFFE1A0), 0.1f * flare)
    }
    // Dust in the light, brighter the nearer it floats to the sun
    for (i in 0 until 40) {
        val x = fract(rnd(i, 3) + t * 0.004f * (rnd(i, 4) - 0.5f)) + 0.01f * sin(t * 0.5f + i)
        val y = fract(rnd(i, 5) - t * (0.004f + 0.004f * rnd(i, 6))) * 0.7f + 0.1f
        val c = at(x, y)
        val near = (1f - (c - sun).getDistance() / (w * 0.45f)).coerceIn(0f, 1f)
        if (near <= 0.02f) continue
        val twinkle = 0.6f + 0.4f * sin(t * (1.5f + rnd(i, 7)) + i)
        drawCircle(Color(0xFFFFF3CF), radius = unit * (0.9f + 1.4f * rnd(i, 8)), center = c, alpha = 0.7f * near * twinkle, blendMode = BlendMode.Plus)
    }
    // Leaves coming down, turning as they fall
    for (i in 0 until 5) {
        val p = fract(rnd(i, 9) + t * (0.03f + 0.02f * rnd(i, 10)))
        val c = at(0.1f + 0.85f * rnd(i, 11) + 0.04f * sin(p * 9f + i), -0.05f + p * 1.1f)
        val turn = sin(t * 2.4f + i * 1.7f)
        val s = unit * (4f + 2f * rnd(i, 12))
        rotate(degrees = 40f * turn + 30f * i, pivot = c) {
            scale(scaleX = 0.25f + 0.75f * abs(turn), scaleY = 1f, pivot = c) {
                drawOval(Color(0xFFC9B24E), topLeft = c - Offset(s * 0.5f, s), size = Size(s, s * 2f), alpha = 0.8f)
            }
        }
    }
}

/**
 * Space Infinitum, a photo of the Milky Way: its core glows and fades like breath, the bright
 * stars twinkle, a satellite crawls across now and then and once in a while a star falls.
 */
private fun DrawScope.cosmos(t: Float, core: ImageBitmap?) {
    val w = size.width
    val unit = 1.dp.toPx()
    if (core != null) {
        drawImage(core, dstSize = IntSize(w.roundToInt(), size.height.roundToInt()), alpha = 0.08f + 0.08f * sin(t * 0.25f), blendMode = BlendMode.Plus)
    }
    val stars = PhotoSpots.stars
    for (i in 0 until stars.size / 2) {
        val twinkle = sin(t * (1.2f + 2.2f * rnd(i, 1)) + rnd(i, 2) * 6.28f)
        if (twinkle < 0.2f) continue
        val c = at(stars[i * 2], stars[i * 2 + 1])
        val a = (twinkle - 0.2f) / 0.8f
        softLight(c, unit * (3f + 3f * rnd(i, 3)), Color(0xFFF2F5FF), 0.6f * a)
        if (rnd(i, 4) > 0.75f) {
            val ray = unit * (4f + 5f * a)
            drawLine(Color(0xFFF2F5FF), c - Offset(ray, 0f), c + Offset(ray, 0f), strokeWidth = unit * 0.7f, alpha = 0.5f * a)
            drawLine(Color(0xFFF2F5FF), c - Offset(0f, ray), c + Offset(0f, ray), strokeWidth = unit * 0.7f, alpha = 0.5f * a)
        }
    }
    // A satellite, steady and slow, every 70 seconds
    val pass = fract(t / 70f)
    if (pass < 0.5f) {
        val q = pass / 0.5f
        val c = at(0.12f + q * 0.8f, 0.04f + q * 0.36f)
        val glint = 0.5f + 0.5f * sin(t * 0.7f)
        drawCircle(Color(0xFFEFF3FF), radius = unit * 1.2f, center = c, alpha = (0.35f + 0.4f * glint) * sin(q * PI.toFloat()))
    }
    // A falling star every 23 seconds, somewhere new each time
    val cycle = floor(t / 23f).toInt()
    val fall = fract(t / 23f)
    if (fall < 0.06f) {
        val q = fall / 0.06f
        val start = at(0.25f + 0.6f * rnd(cycle, 5), 0.04f + 0.25f * rnd(cycle, 6))
        val dir = Offset(-0.85f, 0.5f)
        val head = start + dir * (w * 0.22f * q)
        val tail = head - dir * (w * 0.07f * (1f - q * 0.5f))
        drawLine(
            Brush.linearGradient(0f to Color.Transparent, 1f to Color(0xFFFFFFFF), start = tail, end = head),
            tail, head, strokeWidth = unit * 1.6f, cap = StrokeCap.Round, alpha = sin(q * PI.toFloat())
        )
    }
}

/**
 * Frutiger Aero, a photo of a green field under a deep blue sky: glossy bubbles rise and sway with
 * a shine on each, the sun glints in the corner, gusts run through the grass and a plane draws a
 * fresh trail across the sky.
 */
private fun DrawScope.bubbles(t: Float) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    // The sun's glint in the top right
    val sun = at(0.93f, 0.04f)
    val glint = 0.5f + 0.5f * sin(t * 0.6f)
    softLight(sun, w * 0.16f, Color(0xFFFFFFFF), 0.28f + 0.12f * glint)
    rotate(degrees = t * 3f, pivot = sun) {
        for (i in 0 until 6) {
            val a = i * 60f * (PI.toFloat() / 180f)
            val reach = w * (0.1f + 0.04f * glint)
            drawLine(
                Brush.linearGradient(0f to Color(0x99FFFFFF), 1f to Color.Transparent, start = sun, end = sun + Offset(cos(a), sin(a)) * reach),
                sun, sun + Offset(cos(a), sin(a)) * reach, strokeWidth = unit * 2f
            )
        }
    }
    // A plane drawing a fresh trail, every 45 seconds
    val flight = fract(t / 45f)
    if (flight < 0.7f) {
        val q = flight / 0.7f
        val from = at(0.98f, 0.08f)
        val to = at(0.35f, 0.3f)
        val head = from + (to - from) * q
        val tail = from + (to - from) * (q - 0.35f).coerceAtLeast(0f)
        val fade = (1f - (q - 0.85f) / 0.15f).coerceIn(0f, 1f)
        drawLine(Brush.linearGradient(0f to Color.Transparent, 1f to Color(0xCCFFFFFF), start = tail, end = head), tail, head, strokeWidth = unit * 2.4f, cap = StrokeCap.Round, alpha = fade)
        drawCircle(Color(0xFFFFFFFF), radius = unit * 1.6f, center = head, alpha = fade)
    }
    // Gusts through the grass
    for (g in 0 until 2) {
        val x = -0.3f + fract(t / 8f + g * 0.5f) * 1.6f
        val band = w * 0.2f
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color(0x30F4FFB0), 1f to Color.Transparent, startX = x * w - band, endX = x * w + band),
            topLeft = Offset(x * w - band, h * 0.72f),
            size = Size(band * 2, h * 0.28f)
        )
    }
    // Bubbles rising, each with a rim of colour and a window of light
    for (i in 0 until 11) {
        val life = fract(rnd(i, 1) + t * (0.018f + 0.016f * rnd(i, 2)))
        val r = unit * (14f + 46f * rnd(i, 3).pow(2))
        val x = (0.08f + 0.88f * rnd(i, 4)) * w + 0.03f * w * sin(t * (0.3f + 0.3f * rnd(i, 5)) + i)
        val y = (1.1f - life * 1.3f) * h
        val c = Offset(x, y)
        val wobble = 1f + 0.04f * sin(t * 2f + i)
        scale(scaleX = wobble, scaleY = 2f - wobble, pivot = c) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0x08FFFFFF), 0.78f to Color(0x14BFF4FF), 0.92f to Color(0x55E0F8FF), 1f to Color(0x10FFFFFF),
                    center = c, radius = r
                ),
                radius = r, center = c
            )
            drawCircle(Color(0x66B8FFE0), radius = r * 0.97f, center = c, style = Stroke(unit * 1.2f))
            drawArc(
                Color(0x99FFFFFF), startAngle = 200f, sweepAngle = 70f, useCenter = false,
                topLeft = c - Offset(r * 0.72f, r * 0.72f), size = Size(r * 1.44f, r * 1.44f),
                style = Stroke(unit * (1.5f + r / (unit * 30f)), cap = StrokeCap.Round)
            )
            drawCircle(Color(0xCCFFFFFF), radius = r * 0.09f, center = c + Offset(r * 0.38f, r * 0.42f))
        }
    }
}

/**
 * Dark Academia, a photo of an old library of carved wood: the light from the skylight swells and
 * settles like a lamp turned up and down, and dust drifts down through it to the reading tables.
 */
private fun DrawScope.library(t: Float, light: ImageBitmap?) {
    val w = size.width
    val unit = 1.dp.toPx()
    if (light != null) {
        val flicker = 0.12f + 0.07f * sin(t * 0.45f) + 0.02f * sin(t * 3.1f) * sin(t * 1.7f)
        drawImage(light, dstSize = IntSize(w.roundToInt(), size.height.roundToInt()), alpha = flicker, blendMode = BlendMode.Plus)
    }
    // A soft cone of light from the skylight
    val top = at(0.5f, 0.2f)
    val beam = Path().apply {
        moveTo(top.x - w * 0.08f, top.y)
        lineTo(top.x + w * 0.08f, top.y)
        lineTo(top.x + w * 0.2f, size.height)
        lineTo(top.x - w * 0.2f, size.height)
        close()
    }
    drawPath(
        beam,
        Brush.verticalGradient(0f to Color(0xFFFFE2B0).copy(alpha = 0.07f + 0.03f * sin(t * 0.45f)), 1f to Color.Transparent, startY = top.y, endY = size.height),
        blendMode = BlendMode.Plus
    )
    // Dust drifting down through the light
    for (i in 0 until 36) {
        val p = fract(rnd(i, 1) + t * (0.008f + 0.008f * rnd(i, 2)))
        val spread = 0.06f + p * 0.13f
        val x = 0.5f + (rnd(i, 3) - 0.5f) * 2f * spread + 0.01f * sin(t * 0.6f + i * 1.3f)
        val c = at(x, 0.22f + p * 0.7f)
        val twinkle = 0.5f + 0.5f * sin(t * (1.2f + rnd(i, 4)) + i)
        drawCircle(Color(0xFFFFEBC4), radius = unit * (0.8f + 1.2f * rnd(i, 5)), center = c, alpha = 0.55f * twinkle * sin(p * PI.toFloat()), blendMode = BlendMode.Plus)
    }
}

/**
 * Japandi, a photo of a quiet room with the sun falling through the window onto the wall: clouds
 * pass now and then and the light on the wall dims and comes back, and a few motes float in it.
 */
private fun DrawScope.sunroom(t: Float, light: ImageBitmap?) {
    val w = size.width
    val unit = 1.dp.toPx()
    if (light != null) {
        val dst = IntSize(w.roundToInt(), size.height.roundToInt())
        // Two slow waves make the clouds come irregularly: mostly sun, sometimes shade
        val cloud = ((sin(t * 0.11f) + sin(t * 0.047f + 1.3f)) * 0.5f).coerceAtLeast(0f)
        drawImage(light, dstSize = dst, alpha = 0.18f * (1f - cloud), blendMode = BlendMode.Plus)
        drawImage(light, dstSize = dst, alpha = 0.45f * cloud, colorFilter = ColorFilter.tint(Color(0xFF8E8070)), blendMode = BlendMode.Multiply)
    }
    for (i in 0 until 22) {
        val x = fract(rnd(i, 1) + t * 0.003f * (rnd(i, 2) - 0.3f)) + 0.008f * sin(t * 0.4f + i)
        val y = 0.05f + 0.38f * fract(rnd(i, 3) + t * 0.002f * (rnd(i, 4) - 0.5f) + 0.01f * sin(t * 0.3f + i))
        val twinkle = 0.5f + 0.5f * sin(t * (0.8f + rnd(i, 5)) + i * 2f)
        drawCircle(Color(0xFFFFF4E0), radius = unit * (0.8f + rnd(i, 6)), center = at(x, y), alpha = 0.6f * twinkle)
    }
}

/** The vaporwave sky from top to horizon, as painted, so the sun's stripes cut it cleanly. */
private val VaporSky = listOf(0f to Color(0xFF1B0B3A), 0.3f to Color(0xFF4B1670), 0.45f to Color(0xFFC2387F), 0.5f to Color(0xFFFF7A8A))

private fun vaporSkyAt(y: Float): Color {
    for (i in 1 until VaporSky.size) {
        val (y1, c1) = VaporSky[i]
        val (y0, c0) = VaporSky[i - 1]
        if (y <= y1) return lerp(c0, c1, ((y - y0) / (y1 - y0)).coerceIn(0f, 1f))
    }
    return VaporSky.last().second
}

/**
 * Vaporwave: the stripes run down through the setting sun, the grid floor rolls towards you
 * forever, a tracking band drifts down the picture like an old tape and the stars blink.
 */
private fun DrawScope.vapor(t: Float) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    val horizon = h * 0.5f
    val sun = at(0.6615f, 0.3056f)
    val r = h * 0.2176f
    softLight(sun, r * 1.9f, Color(0xFFFF5FA8), 0.1f + 0.06f * sin(t * 0.8f))
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(sun, r)) }
    clipRect(bottom = horizon) {
        clipPath(disc) {
            for (j in 0 until 7) {
                val p = fract(t * 0.07f + j / 7f)
                val y = sun.y + r * (0.02f + 0.98f * p)
                val thick = unit * (1.5f + 13f * p)
                drawRect(vaporSkyAt(y / h), topLeft = Offset(sun.x - r, y), size = Size(r * 2f, thick))
            }
        }
    }
    // The grid floor: lines out of the vanishing point, and rows rolling towards you
    val vanish = Offset(w * 0.5f, horizon)
    val fade = Brush.verticalGradient(0f to Color(0x00FF5FD0), 0.35f to Color(0x99FF5FD0), 1f to Color(0xEEFF5FD0), startY = horizon, endY = h)
    for (i in -24..24) {
        drawLine(fade, Offset(vanish.x + i * w * 0.022f, horizon), Offset(vanish.x + i * w * 0.15f, h), strokeWidth = unit * 1.2f)
    }
    val rows = 16
    val roll = fract(t * 0.22f)
    for (k in 0 until rows) {
        val u = (k + roll) / rows
        val y = horizon + (h - horizon) * u.pow(2.6f)
        drawLine(Color(0xFFFF5FD0), Offset(0f, y), Offset(w, y), strokeWidth = unit * (0.6f + 1.6f * u), alpha = 0.12f + 0.7f * u)
    }
    drawLine(Color(0xFFFFB0D8), Offset(0f, horizon), Offset(w, horizon), strokeWidth = unit * 1.5f, alpha = 0.6f + 0.3f * sin(t * 1.3f))
    // Stars blinking in the upper sky
    for (i in 0 until 24) {
        val blink = sin(t * (0.8f + 1.6f * rnd(i, 1)) + rnd(i, 2) * 6.28f)
        if (blink < 0.5f) continue
        val c = at(rnd(i, 3), rnd(i, 4) * 0.28f)
        if (c.x < w * 0.25f && c.y < h * 0.25f) continue
        val a = (blink - 0.5f) * 2f
        drawLine(Color(0xFFFFE6FF), c - Offset(unit * 4f * a, 0f), c + Offset(unit * 4f * a, 0f), strokeWidth = unit, alpha = a)
        drawLine(Color(0xFFFFE6FF), c - Offset(0f, unit * 4f * a), c + Offset(0f, unit * 4f * a), strokeWidth = unit, alpha = a)
    }
    // The tracking band of an old tape, drifting down
    val band = fract(t / 14f) * (h * 1.3f) - h * 0.15f
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color(0x14FFFFFF), 1f to Color.Transparent, startY = band - unit * 30f, endY = band + unit * 30f),
        topLeft = Offset(0f, band - unit * 30f), size = Size(w, unit * 60f)
    )
}
