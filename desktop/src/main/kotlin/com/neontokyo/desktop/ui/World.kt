package com.neontokyo.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.neontokyo.desktop.theme.FocusStyle
import com.neontokyo.desktop.theme.LocalNeon
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Neon Tokyo's world behind every screen on the PC: the banner's skyline at dusk (the striped
 * sun, the tower, the karaoke signs) over an endless neon grid that rolls toward you, stars
 * that twinkle, now and then a shooting star, the sun's light on the wet floor, and the whole
 * scene leaning a little toward wherever the focus or the mouse is, like Neon Home's backdrop.
 *
 * Deep Blue keeps its calm: the same world, cooled to blue and still.
 */
@Composable
fun NeonWorld(modifier: Modifier = Modifier, dim: Float = 0f) {
    val theme = LocalNeon.current
    val spot = LocalSpot.current
    val motion = LocalMotion.current.amount
    val glass = theme.focus == FocusStyle.Glass
    val city = remember { cityArt() }

    // The clock that drives the grid and the sky; stands still with motion off
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(motion) {
        if (motion <= 0f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += ((now - last) / 1_000_000_000f) * motion
                last = now
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        // Lean: the scene drifts a few pixels against where the focus (or mouse) is
        val focus = spot.bounds?.takeIf { spot.on }?.center
        val aim = focus?.let { Offset(it.x / w, it.y / h) } ?: spot.pointer
        val lean by animateOffsetAsState(
            Offset(-(aim.x - 0.5f) * 2f * 18f * motion, -(aim.y - 0.5f) * 2f * 7f * motion),
            NeonMotion.lean(), label = "WorldLean",
        )
        val glowColor by animateColorAsState(if (glass) Color(0xFF8FDCFF) else spot.color, tween(600), label = "WorldTint")
        val horizon = h * 0.60f

        // Sky and city: the art, wide enough to lean without showing an edge
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = lean.x
            translationY = lean.y * 0.6f
            scaleX = 1.04f
            scaleY = 1.04f
        }) {
            Image(
                city, null, contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxWidth().height(with(androidx.compose.ui.platform.LocalDensity.current) { (horizon + 2f).toDp() })
                    .graphicsLayer { if (glass) { alpha = 0.55f } },
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            if (glass) drawRect(Brush.verticalGradient(listOf(Color(0xCC061423), Color(0x99061423), Color(0xFF061423))))
            stars(t, horizon, lean)
            if (!glass) shootingStar(t, horizon)
            floor(t, horizon, lean, glass, glowColor)
            if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        }
    }
}

private fun cityArt(): ImageBitmap {
    val bytes = NeonWorldArt::class.java.getResourceAsStream("/art/world-city.jpg")!!.readBytes()
    return org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

private object NeonWorldArt

private val starField: List<Triple<Float, Float, Float>> = Random(7).let { r ->
    List(70) { Triple(r.nextFloat(), r.nextFloat() * r.nextFloat(), r.nextFloat() * 6.28f) }
}

/** Stars that breathe in and out, each at its own pace. */
private fun DrawScope.stars(t: Float, horizon: Float, lean: Offset) {
    for ((i, s) in starField.withIndex()) {
        val (x, y, phase) = s
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.6f + (i % 5) * 0.23f) + phase))
        val px = x * size.width + lean.x * 0.3f
        val py = y * horizon * 0.62f + lean.y * 0.2f
        drawCircle(Color.White.copy(alpha = 0.55f * tw), radius = if (i % 9 == 0) 1.8.dp.toPx() else 1.1.dp.toPx(), center = Offset(px, py))
        if (i % 9 == 0) drawCircle(Color(0xFFB9A6FF).copy(alpha = 0.18f * tw), radius = 5.dp.toPx(), center = Offset(px, py))
    }
}

/** Every eleven seconds, a streak across the upper sky. */
private fun DrawScope.shootingStar(t: Float, horizon: Float) {
    val period = 11f
    val k = (t % period) / 1.1f
    if (k > 1f) return
    val n = (t / period).toInt()
    val r = Random(n * 31 + 5)
    val sx = size.width * (0.15f + r.nextFloat() * 0.6f)
    val sy = horizon * (0.05f + r.nextFloat() * 0.2f)
    val len = size.width * 0.16f
    val head = Offset(sx + len * k, sy + len * 0.32f * k)
    val tail = Offset(head.x - len * 0.45f, head.y - len * 0.45f * 0.32f)
    val fade = sin(k * PI.toFloat())
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * fade)), start = tail, end = head),
        start = tail, end = head, strokeWidth = 1.6.dp.toPx(),
    )
}

/**
 * The grid floor: lines to a vanishing point on the horizon, and cross lines that roll toward
 * the viewer, spaced by perspective so they speed up as they come close. The horizon glows,
 * and the sun (or the focused card's colour) lies on the floor as a soft pool.
 */
private fun DrawScope.floor(t: Float, horizon: Float, lean: Offset, glass: Boolean, glow: Color) {
    val top = horizon + lean.y * 0.6f
    val w = size.width
    val h = size.height
    val floorH = h - top
    if (floorH <= 0f) return
    val base = if (glass) Color(0xFF061423) else Color(0xFF0D0721)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B3A).takeIf { !glass } ?: Color(0xFF0B1E31), base), startY = top, endY = h),
        topLeft = Offset(0f, top), size = Size(w, floorH))
    val cyan = if (glass) Color(0xFF8FDCFF) else Color(0xFF3EF0FF)
    val magenta = if (glass) Color(0xFFBFE8FF) else Color(0xFFFF3D9A)
    val vx = w / 2f + lean.x * 0.8f
    // Lines to the vanishing point
    val lanes = 26
    for (i in -lanes..lanes) {
        val bottomX = vx + i * (w / 14f)
        val a = 0.55f * (1f - (kotlin.math.abs(i).toFloat() / lanes) * 0.6f)
        drawLine(
            Brush.verticalGradient(listOf(cyan.copy(alpha = 0f), cyan.copy(alpha = a * (if (glass) 0.35f else 0.7f))), startY = top, endY = h),
            start = Offset(vx + i * 2f, top), end = Offset(bottomX, h), strokeWidth = 1.2.dp.toPx(),
        )
    }
    // Cross lines rolling toward the viewer
    val rows = 16
    val roll = (t * 0.42f) % 1f
    for (j in 0 until rows) {
        val z = (j + roll) / rows
        val y = top + floorH * z.pow(2.4f)
        val a = (z * 1.25f).coerceAtMost(1f)
        drawLine(magenta.copy(alpha = a * (if (glass) 0.22f else 0.55f)), Offset(0f, y), Offset(w, y), strokeWidth = (0.8f + z * 1.6f).dp.toPx())
    }
    // The horizon: a lit line with a soft halo
    drawRect(Brush.verticalGradient(listOf(magenta.copy(alpha = 0f), magenta.copy(alpha = if (glass) 0.18f else 0.38f), magenta.copy(alpha = 0f)),
        startY = top - 26.dp.toPx(), endY = top + 26.dp.toPx()), topLeft = Offset(0f, top - 26.dp.toPx()), size = Size(w, 52.dp.toPx()))
    drawLine(lerpWhite(magenta, 0.35f).copy(alpha = if (glass) 0.4f else 0.95f), Offset(0f, top), Offset(w, top), strokeWidth = 1.6.dp.toPx())
    // The pool of light on the floor, in the sun's or the focused card's colour
    val pool = Offset(vx + w * 0.16f, top + floorH * 0.10f)
    scale(scaleX = 1f, scaleY = 0.22f, pivot = pool) {
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = if (glass) 0.18f else 0.30f), Color.Transparent), center = pool, radius = w * 0.22f),
            radius = w * 0.22f, center = pool, blendMode = BlendMode.Plus,
        )
    }
    // Floor fades into the night at the bottom, so cards read clearly over it
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.85f)), startY = top + floorH * 0.35f, endY = h),
        topLeft = Offset(0f, top), size = Size(w, floorH))
}

private fun lerpWhite(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)

/**
 * The light the focused card throws on the floor under it, and its reflection, gliding from
 * card to card with the focus as Neon Home's FloorLight does. Drawn in window coordinates,
 * so put it in a box that fills the window.
 */
@Composable
fun FloorLight(modifier: Modifier = Modifier) {
    val spot = LocalSpot.current
    val theme = LocalNeon.current
    val target = spot.bounds ?: return
    val center by animateOffsetAsState(Offset(target.center.x, target.bottom), NeonMotion.glide(), label = "LightSpot")
    val width by animateFloatAsState(target.width, NeonMotion.glide(), label = "LightWidth")
    val color by animateColorAsState(spot.color, tween(360), label = "LightColor")
    val strength by animateFloatAsState(if (spot.on) 1f else 0f, tween(if (spot.on) 240 else 420), label = "LightOn")
    val glass = theme.focus == FocusStyle.Glass
    Box(modifier.fillMaxSize().graphicsLayer().drawBehind {
        if (strength <= 0f) return@drawBehind
        val pool = Offset(center.x, center.y + 14.dp.toPx())
        val rx = max(width * 0.72f, 1f)
        val ry = 22.dp.toPx()
        scale(scaleX = 1f, scaleY = ry / rx, pivot = pool) {
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = if (glass) 0.18f else 0.38f),
                    0.55f to color.copy(alpha = if (glass) 0.06f else 0.13f),
                    1f to Color.Transparent,
                    center = pool, radius = rx,
                ),
                radius = rx, center = pool, alpha = strength,
            )
        }
        if (!glass) {
            val top = center.y + 4.dp.toPx()
            val depth = 34.dp.toPx()
            val side = 8.dp.toPx()
            drawRect(
                Brush.verticalGradient(0f to color.copy(alpha = 0.16f), 1f to Color.Transparent, startY = top, endY = top + depth),
                topLeft = Offset(center.x - width / 2f + side, top),
                size = Size((width - 2 * side).coerceAtLeast(0f), depth),
                alpha = strength,
            )
        }
    })
}
