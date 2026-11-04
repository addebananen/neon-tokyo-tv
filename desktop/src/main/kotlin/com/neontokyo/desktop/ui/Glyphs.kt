package com.neontokyo.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

/** Tokyo Tower in neon, the app's own glyph (the TV icon's tower), as lines. */
fun DrawScope.tower(color: Color, lit: Float = 1f, stroke: Float = size.width * 0.055f) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w / 2, h * 0.06f); lineTo(w * 0.12f, h)
        moveTo(w / 2, h * 0.06f); lineTo(w * 0.88f, h)
        moveTo(w / 2, 0f); lineTo(w / 2, h * 0.06f)
        moveTo(w * 0.36f, h * 0.38f); lineTo(w * 0.64f, h * 0.38f)
        moveTo(w * 0.24f, h * 0.66f); lineTo(w * 0.76f, h * 0.66f)
        moveTo(w * 0.32f, h * 0.38f); lineTo(w * 0.68f, h * 0.66f)
        moveTo(w * 0.68f, h * 0.38f); lineTo(w * 0.32f, h * 0.66f)
        moveTo(w * 0.28f, h * 0.99f); quadraticTo(w / 2, h * 0.80f, w * 0.72f, h * 0.99f)
    }
    for ((k, a) in listOf(3.2f to 0.16f, 1.8f to 0.32f, 1f to 1f)) {
        drawPath(p, color.copy(alpha = a * lit), style = Stroke(stroke * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawPath(p, Color.White.copy(alpha = 0.55f * lit), style = Stroke(stroke * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun TowerGlyph(color: Color, modifier: Modifier, lit: Float = 1f) {
    Canvas(modifier) { tower(color, lit) }
}

/**
 * Switching off like an old TV, as the TV app does when it closes: the picture squeezes to a
 * bright line, the line to a dot, the dot fades. Runs, then calls [done].
 */
@Composable
fun PowerOff(done: () -> Unit) {
    val squeeze = remember { Animatable(0f) }
    val shrink = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        squeeze.animateTo(1f, tween(230, easing = FastOutLinearInEasing))
        shrink.animateTo(1f, tween(200, easing = FastOutLinearInEasing))
        fade.animateTo(1f, tween(160))
        delay(60)
        done()
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val s = squeeze.value
            val k = shrink.value
            // everything but the closing slit goes black
            val lineH = size.height * (1f - s) + 3f * s
            val lineW = size.width * (1f - k) + 6f * k
            val top = (size.height - lineH) / 2f
            val left = (size.width - lineW) / 2f
            drawRect(Color.Black, size = Size(size.width, top))
            drawRect(Color.Black, topLeft = Offset(0f, top + lineH), size = Size(size.width, size.height - top - lineH))
            drawRect(Color.Black, topLeft = Offset(0f, top), size = Size(left, lineH))
            drawRect(Color.Black, topLeft = Offset(left + lineW, top), size = Size(size.width - left - lineW, lineH))
            if (s > 0.6f) {
                val a = (1f - fade.value)
                drawRect(Color.White.copy(alpha = a), topLeft = Offset(left, top), size = Size(lineW, lineH))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFBFF6FF).copy(alpha = 0.7f * a), Color.Transparent), center = center, radius = 60f + 240f * (1 - k)),
                    radius = 60f + 240f * (1 - k), center = center,
                )
            }
        }
    }
}
