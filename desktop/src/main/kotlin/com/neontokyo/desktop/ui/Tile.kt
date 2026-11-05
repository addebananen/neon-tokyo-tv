package com.neontokyo.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neontokyo.desktop.core.Sound
import com.neontokyo.desktop.theme.FocusStyle
import com.neontokyo.desktop.theme.LocalNeon
import kotlinx.coroutines.launch
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/**
 * A card that lands like a Neon Home tile: a bouncy pop to 107 %, a neon tube that lights
 * around its edge with a white-hot core, a glow that spills past it, light sweeping across it
 * once, and, because this is the PC, a tilt toward the mouse as if the card were glass held
 * up to the light. Deep Blue swaps the tube for a frosted rim and a deeper lift.
 *
 * The mouse and the keyboard both light it; Enter, Space or a click opens it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NeonTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    glow: Color? = null,
    onFocused: () -> Unit = {},
    pop: Float = NeonMotion.POP,
    tilt: Boolean = true,
    content: @Composable BoxScope.(lit: Boolean) -> Unit,
) {
    val theme = LocalNeon.current
    val spot = LocalSpot.current
    val motion = LocalMotion.current.amount
    val glass = theme.focus == FocusStyle.Glass
    val tint = glow ?: theme.primary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    var keyDown by remember { mutableStateOf(false) }
    val lit = focused || hovered
    val down = pressed || keyDown

    val target = when {
        down -> if (glass) 0.985f else 1.02f
        lit -> 1f + (pop - 1f) * (if (glass) 1.43f else 1f) * motion.coerceAtLeast(0.35f)
        else -> 1f
    }
    val scale by animateFloatAsState(
        target,
        when {
            lit && !down && glass -> androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 300f)
            lit && !down -> NeonMotion.pop<Float>()
            else -> NeonMotion.settle<Float>()
        },
        label = "TileScale",
    )
    val tube by animateFloatAsState(if (lit) 1f else 0f, tween(if (lit) NeonMotion.TUBE_ON_MS else NeonMotion.TUBE_OFF_MS), label = "TileTube")
    val sheen = remember { Animatable(0f) }
    LaunchedEffect(lit) {
        if (lit && motion > 0f) {
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(NeonMotion.SHEEN_MS, delayMillis = 50, easing = NeonMotion.Sheen))
        } else sheen.snapTo(0f)
    }
    // Tilt toward the mouse: where it is over the card, -1..1 each way
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }

    LaunchedEffect(focused) {
        if (focused) {
            onFocused()
            if (!hovered) Sound.move()
        }
    }
    LaunchedEffect(lit, placed[0]) {
        if (lit) placed[0]?.takeIf { it.isAttached }?.let { spot.land(it.boundsInWindow(), tint) }
    }

    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .zIndex(if (lit) 2f else if (scale > 1.001f) 1f else 0f)
            .onGloballyPositioned {
                placed[0] = it
                if (lit) spot.land(it.boundsInWindow(), tint)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (tilt && motion > 0f) {
                    rotationY = tiltX.value * 7f * motion
                    rotationX = -tiltY.value * 5f * motion
                    cameraDistance = 14f * density
                }
            }
            .drawBehind {
                if (tube <= 0.01f) return@drawBehind
                // The glow spilling past the card: the neon colour on Neon Tokyo, a soft
                // shadow under lifted glass on Deep Blue
                val r = radius.toPx()
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val fp = paint.asFrameworkPaint()
                    if (glass) {
                        fp.color = Color.Black.copy(alpha = 0.55f * tube).toArgbInt()
                        fp.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 22.dp.toPx())
                        canvas.nativeCanvas.drawRRect(
                            org.jetbrains.skia.RRect.makeLTRB(8.dp.toPx(), 22.dp.toPx(), size.width - 8.dp.toPx(), size.height + 18.dp.toPx(), r),
                            fp,
                        )
                    } else {
                        fp.color = tint.copy(alpha = 0.55f * tube).toArgbInt()
                        fp.maskFilter = MaskFilter.makeBlur(FilterBlurMode.OUTER, 16.dp.toPx())
                        canvas.nativeCanvas.drawRRect(org.jetbrains.skia.RRect.makeLTRB(0f, 0f, size.width, size.height, r), fp)
                        fp.color = tint.copy(alpha = 0.35f * tube).toArgbInt()
                        fp.maskFilter = MaskFilter.makeBlur(FilterBlurMode.OUTER, 40.dp.toPx())
                        canvas.nativeCanvas.drawRRect(org.jetbrains.skia.RRect.makeLTRB(0f, 0f, size.width, size.height, r), fp)
                    }
                }
            }
            .clip(shape)
            .onPointerEvent(PointerEventType.Move) { e ->
                val p = e.changes.firstOrNull()?.position ?: return@onPointerEvent
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                scope.launch { tiltX.animateTo(((p.x / w) - 0.5f) * 2f, NeonMotion.glide<Float>()) }
                scope.launch { tiltY.animateTo(((p.y / h) - 0.5f) * 2f, NeonMotion.glide<Float>()) }
            }
            .onPointerEvent(PointerEventType.Exit) {
                scope.launch { tiltX.animateTo(0f, NeonMotion.glide<Float>()) }
                scope.launch { tiltY.animateTo(0f, NeonMotion.glide<Float>()) }
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { Sound.select(); onClick() }
            .onPreviewKeyEvent {
                val select = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar
                if (!select) return@onPreviewKeyEvent false
                when (it.type) {
                    KeyEventType.KeyDown -> { keyDown = true; true }
                    KeyEventType.KeyUp -> { keyDown = false; Sound.select(); onClick(); true }
                    else -> false
                }
            }
            .drawWithContent {
                drawContent()
                val r = CornerRadius(radius.toPx())
                // light sweeping across, once each time the focus lands
                val s = sheen.value
                if (s > 0f && s < 1f) {
                    val w = size.width
                    val x = -w * 0.6f + (w * 2.2f) * s
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.45f to Color.White.copy(alpha = if (glass) 0.18f else 0.14f),
                            0.55f to Color.White.copy(alpha = if (glass) 0.22f else 0.16f), 1f to Color.Transparent,
                            start = Offset(x, 0f), end = Offset(x + w * 0.5f, size.height),
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
                if (tube <= 0.01f) return@drawWithContent
                if (glass) {
                    // frosted rim: light along the top edge, darker toward the bottom
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f * tube), Color.White.copy(alpha = 0.18f * tube))),
                        cornerRadius = r, style = Stroke(1.5.dp.toPx()),
                    )
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f * tube), Color.Transparent), endY = size.height * 0.5f))
                } else {
                    // the neon tube: its colour, then the white-hot core down its middle
                    val inset = 0.75.dp.toPx()
                    drawRoundRect(tint.copy(alpha = tube), topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius(radius.toPx() - inset), style = Stroke(2.dp.toPx()))
                    drawRoundRect(lerp(tint, Color.White, 0.62f).copy(alpha = tube), topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius(radius.toPx() - inset), style = Stroke(0.8.dp.toPx()))
                }
            }
    ) {
        content(lit)
    }
}

private fun Color.toArgbInt(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()
