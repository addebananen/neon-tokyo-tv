package com.lumera.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.lumera.app.ui.components.NeonText
import kotlin.math.cos
import kotlin.math.sin

/**
 * A console switched on while the home screen shows: its card slides in at the top, the console
 * boots up in neon on it, and OK switches the TV over to it. The card goes again by itself.
 */
internal object ConsoleWake {
    /** How long the card stays when nothing is pressed. */
    const val SHOW_MS = 14_000

    /**
     * The first console in [order] that is on [now] and was not on [before]: the one that just
     * woke. Consoles sharing an input through an HDMI switch wake together, so the first wins.
     */
    fun woke(order: List<String>, before: Set<String>, now: Set<String>): String? =
        order.firstOrNull { it in now && it !in before }
}

/**
 * The card for [console], on [input] (the TV's name for it, such as "HDMI 2"). It takes the focus
 * when it comes, so OK plays at once; Back, "Not now" or the time running out put it away.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ConsoleWakeCard(
    console: Console,
    input: String?,
    onPlay: () -> Unit,
    onDismiss: (hadFocus: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalHomeLook.current
    val color = look.tint(console.color)
    val shape = RoundedCornerShape(16.dp)
    val play = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)

    // The boot: a ring of light runs once round the console's mark, and the mark flickers on
    // like a neon tube as the ring closes
    val boot = remember(console) { Animatable(0f) }
    val mark = remember(console) { Animatable(0f) }
    val left = remember(console) { Animatable(1f) }
    LaunchedEffect(console) {
        boot.animateTo(1f, tween(1100, delayMillis = 180, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(console) {
        mark.animateTo(1f, keyframes {
            durationMillis = 1250
            0f at 520
            0.9f at 600
            0.15f at 670
            1f at 790
            0.55f at 900
            1f at 1250
        })
    }
    LaunchedEffect(console) {
        withFrameNanos { }
        runCatching { play.requestFocus() }
        left.snapTo(1f)
        left.animateTo(0f, tween(ConsoleWake.SHOW_MS, easing = LinearEasing))
        dismiss(focused)
    }
    // Once booted the ring breathes, as a console's lit tile on Källor does
    val breath by rememberInfiniteTransition(label = "WakeBreath").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "WakeBreathValue"
    )
    BackHandler(enabled = focused) { dismiss(true) }

    Column(
        modifier = modifier
            .width(340.dp)
            .homePanel(look, shape)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                BootRing(color, progress = { boot.value }, breath = { if (boot.value >= 1f) breath else 1f })
                NeonGlyph(console.glyph, color, Modifier.size(38.dp).graphicsLayer { alpha = mark.value })
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NeonText(
                    text = "${console.name} is on",
                    style = TextStyle(
                        fontFamily = look.font,
                        fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                        fontStyle = look.fontStyle,
                        fontSize = 17.sp,
                        color = look.text,
                        shadow = if (look.glowingText) Shadow(color.copy(alpha = 0.7f), blurRadius = 16f) else null
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    glow = { mark.value }
                )
                if (input != null) {
                    BasicText(
                        text = input,
                        maxLines = 1,
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, color = look.dim)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WakeButton("Play", color, primary = true, onClick = onPlay, modifier = Modifier.focusRequester(play))
                    WakeButton("Not now", color, primary = false, onClick = { dismiss(true) })
                }
            }
        }
        // The time left before the card goes by itself, a lit line that shortens
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 9.dp)
                .height(2.dp)
                .graphicsLayer {
                    scaleX = left.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.15f), color, lerp(color, Color.White, 0.5f))))
                }
        )
    }
}

/** The ring that runs round the console's mark as it boots, with a white-hot head. */
@Composable
private fun BootRing(color: Color, progress: () -> Float, breath: () -> Float) {
    val hot = remember(color) { lerp(color, Color.White, 0.65f) }
    Spacer(
        Modifier
            .size(70.dp)
            .drawWithCache {
                val width = 2.5.dp.toPx()
                val inset = 9.dp.toPx()
                val ring = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val line = Stroke(width, cap = StrokeCap.Round)
                val halo = Stroke(width * 4.5f, cap = StrokeCap.Round)
                val soft = Brush.radialGradient(
                    0f to color.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                    center = center,
                    radius = size.minDimension / 2
                )
                onDrawBehind {
                    val p = progress()
                    if (p <= 0f) return@onDrawBehind
                    val b = breath()
                    // The light the console gives off, coming up as the ring closes
                    drawCircle(soft, alpha = p * b)
                    val sweep = 360f * p
                    drawArc(color, -90f, sweep, false, topLeft, ring, alpha = 0.22f * b, style = halo)
                    drawArc(color, -90f, sweep, false, topLeft, ring, alpha = 0.9f * b, style = line)
                    if (p < 1f) {
                        // The head of the light, running round
                        val angle = Math.toRadians((-90f + sweep).toDouble())
                        val r = ring.width / 2
                        val head = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
                        drawCircle(color, radius = width * 2.6f, center = head, alpha = 0.35f)
                        drawCircle(hot, radius = width * 1.1f, center = head)
                    }
                }
            }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WakeButton(text: String, color: Color, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) color.copy(alpha = 0.22f) else look.pill,
            contentColor = look.text,
            focusedContainerColor = if (primary) color.copy(alpha = 0.42f) else look.pillFocused,
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, if (primary) color.copy(alpha = 0.6f) else look.text.copy(alpha = 0.18f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, if (primary) lerp(color, Color.White, 0.3f) else look.accent), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = color.copy(alpha = 0.55f), elevation = look.glow * 0.85f))
    ) {
        BasicText(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = look.text)
        )
    }
}
