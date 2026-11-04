package com.neontokyo.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.theme.neonDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The neon sign switching on: the tower flickers alight, the sun rises behind it, the name
 * follows, then it all fades into the app. Same beats as the Android and Apple intros.
 */
@Composable
fun Intro(done: () -> Unit) {
    val theme = LocalNeon.current
    val tower = remember { Animatable(0f) }
    val sun = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        for (step in listOf(0.6f, 0.1f, 0.9f, 0.3f, 1f)) {
            tower.animateTo(step, tween(70, easing = LinearEasing))
            delay(20)
        }
        launch { sun.animateTo(1f, tween(500)) }
        delay(250)
        name.animateTo(1f, tween(450))
        delay(NeonTokens.motionIntroMs.toLong() - 900)
        done()
    }
    val t = Metrics.titleSize
    Box(Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(t.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size((t * 5).dp).alpha(sun.value).blur(((1 - sun.value) * 20).dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(theme.warm, theme.primary)))
                )
                Canvas(Modifier.size((t * 2.6f).dp, (t * 6).dp).alpha(tower.value)) {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(w / 2, 0f); lineTo(0f, h)
                        moveTo(w / 2, 0f); lineTo(w, h)
                        moveTo(w / 2, -h * 0.06f); lineTo(w / 2, 0f)
                        moveTo(w * 0.33f, h * 0.36f); lineTo(w * 0.67f, h * 0.36f)
                        moveTo(w * 0.18f, h * 0.64f); lineTo(w * 0.82f, h * 0.64f)
                        moveTo(w * 0.25f, h * 0.98f); quadraticTo(w / 2, h * 0.8f, w * 0.75f, h * 0.98f)
                    }
                    // a soft halo under the sharp tube, like the Apple shadow pair
                    for ((width, a) in listOf(t * 0.9f to 0.18f * tower.value, t * 0.45f to 0.35f, t * 0.14f to 1f)) {
                        drawPath(p, theme.primary.copy(alpha = a), style = Stroke(width = width * density / 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            Text("NEON TOKYO", style = neonDisplay((t * 2.2f).sp), modifier = Modifier.alpha(name.value).scale(0.94f + 0.06f * name.value))
        }
    }
}
