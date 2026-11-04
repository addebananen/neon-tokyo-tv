package com.neontokyo.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.core.Sound
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.neonDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The neon sign switching on, as on the TV: the tower's tube buzzes and flickers alight, the
 * striped sun rises behind it, the name follows with its katakana, a scan line sweeps once,
 * and the night opens onto the world behind.
 */
@Composable
fun Intro(done: () -> Unit) {
    val theme = LocalNeon.current
    val tower = remember { Animatable(0f) }
    val sun = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val scan = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        Sound.wake()
        delay(180)
        for (step in listOf(0.7f, 0.05f, 0.9f, 0.2f, 0.6f, 1f)) {
            tower.animateTo(step, tween(60, easing = LinearEasing))
            delay(35)
        }
        launch { sun.animateTo(1f, tween(700, easing = NeonMotion.FastOutSlowIn)) }
        delay(260)
        launch { name.animateTo(1f, tween(520, easing = NeonMotion.FastOutSlowIn)) }
        launch { scan.animateTo(1f, tween(900, easing = LinearEasing)) }
        delay(1150)
        open.animateTo(1f, tween(520, easing = NeonMotion.FastOutSlowIn))
        done()
    }
    val t = Metrics.titleSize
    Box(
        Modifier.fillMaxSize().background(theme.background.copy(alpha = 1f - open.value)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = 1f - open.value
                val s = 1f + open.value * 0.08f
                scaleX = s; scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                // the striped sun, rising behind the tower
                Canvas(Modifier.size((t * 6).dp).graphicsLayer {
                    alpha = sun.value
                    translationY = (1f - sun.value) * 40f
                }) {
                    val r = size.minDimension / 2f
                    drawCircle(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.35f), Color.Transparent), radius = r * 1.5f), radius = r * 1.5f)
                    drawCircle(Brush.verticalGradient(listOf(theme.warm, theme.primary), startY = center.y - r, endY = center.y + r), radius = r)
                    // the stripes cut through its lower half
                    for ((i, f) in listOf(0.58f, 0.68f, 0.77f, 0.85f, 0.92f).withIndex()) {
                        val y = center.y - r + 2 * r * f
                        drawRect(theme.background, topLeft = Offset(0f, y), size = Size(size.width, r * (0.035f + 0.016f * i)))
                    }
                }
                TowerGlyph(theme.primary, Modifier.size((t * 2.4f).dp, (t * 5.2f).dp).padding(bottom = (t * 0.4f).dp), tower.value)
            }
            Spacer(Modifier.height((t * 0.8f).dp))
            Text(
                "NEON TOKYO", style = neonDisplay((t * 2.3f).sp).copy(letterSpacing = 6.sp),
                modifier = Modifier.alpha(name.value).graphicsLayer { translationY = (1f - name.value) * 18f },
            )
            Text(
                "ネ オ ン 東 京", color = theme.secondary.copy(alpha = 0.85f), fontSize = (t * 0.6f).sp, letterSpacing = 8.sp,
                modifier = Modifier.alpha(name.value * 0.9f).padding(top = 6.dp),
            )
        }
        // one bright scan line passing down, like an old set warming up
        Canvas(Modifier.fillMaxSize()) {
            val s = scan.value
            if (s <= 0f || s >= 1f) return@Canvas
            val y = size.height * s
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, theme.secondary.copy(alpha = 0.10f), Color.White.copy(alpha = 0.16f), Color.Transparent), startY = y - 60f, endY = y + 6f),
                topLeft = Offset(0f, y - 60f), size = Size(size.width, 66f),
            )
        }
    }
}
