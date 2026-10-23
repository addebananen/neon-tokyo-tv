package com.lumera.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.lumera.app.R
import com.lumera.app.ui.theme.KanitFamily

/** The Neon Tokyo colours, for screens that look the same whatever theme a profile picked. */
object NeonPalette {
    val Night = Color(0xFF0D0721)
    val Violet = Color(0xFF2A0F4F)
    val Panel = Color(0xF2160A2E)
    val Text = Color(0xFFF6EAFF)
    val Lilac = Color(0xFFB9A6D6)
    val Magenta = Color(0xFFFF3D9A)
    val Cyan = Color(0xFF3EF0FF)
    val Sun = Color(0xFFFFC46B)
}

/**
 * Kanit italic lit like a neon sign: big titles, headings and the small spaced label above them.
 * The glow is the style's shadow; show these with [NeonText] so the glow is never cut off.
 */
object NeonType {
    fun display(size: TextUnit = 60.sp, glow: Color = NeonPalette.Magenta) = TextStyle(
        fontFamily = KanitFamily,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = size,
        lineHeight = 1.08.em,
        letterSpacing = 1.sp,
        color = NeonPalette.Text,
        shadow = Shadow(color = glow.copy(alpha = 0.85f), blurRadius = 28f)
    )

    fun headline(size: TextUnit = 30.sp, glow: Color = NeonPalette.Magenta) = TextStyle(
        fontFamily = KanitFamily,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = size,
        lineHeight = 1.12.em,
        letterSpacing = 0.5.sp,
        color = NeonPalette.Text,
        shadow = Shadow(color = glow.copy(alpha = 0.55f), blurRadius = 18f)
    )

    /** Row and section titles. No glow: there are many on screen and they scroll. */
    fun rowTitle(size: TextUnit = 19.sp) = TextStyle(
        fontFamily = KanitFamily,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = size,
        letterSpacing = 0.3.sp
    )

    fun eyebrow(size: TextUnit = 14.sp, color: Color = NeonPalette.Cyan) = TextStyle(
        fontFamily = KanitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = size,
        letterSpacing = 6.sp,
        color = color,
        shadow = Shadow(color = color.copy(alpha = 0.6f), blurRadius = 12f)
    )
}

/**
 * Text lit like a neon sign. The glow comes from the style's shadow and is drawn in a margin of
 * its own around the text, so it fades out softly instead of stopping in a hard box at the edges
 * of the text. [glow] scales it from 0 to 1 and is read while drawing, so animating it never
 * recomposes or re-lays out the text.
 */
@Composable
fun NeonText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    glow: () -> Float = { 1f }
) {
    val shadow = style.shadow
    val plain = remember(style, color, textAlign) {
        style.copy(
            color = if (color.isSpecified) color else if (style.color.isSpecified) style.color else NeonPalette.Text,
            textAlign = if (textAlign != TextAlign.Unspecified) textAlign else style.textAlign,
            shadow = null
        )
    }
    if (shadow == null || shadow.blurRadius <= 0f) {
        BasicText(text, modifier, plain, overflow = overflow, softWrap = softWrap, maxLines = maxLines)
        return
    }
    val density = LocalDensity.current
    // A little more than the blur radius, which is where the glow has faded out
    val margin = remember(shadow, density) { with(density) { (shadow.blurRadius * 1.15f + 2f).toDp() } }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    // Text cut short with an ellipsis is painted clipped to its box, glow and all. So a text that
    // doesn't fit is shortened here instead, and the part that shows is laid out whole.
    val measurer = rememberTextMeasurer()
    var shortened by remember(text, plain, maxLines, softWrap) { mutableStateOf<Shortened?>(null) }
    BasicText(
        text = shortened?.text ?: text,
        style = plain,
        onTextLayout = { result ->
            layout.value = result
            val room = result.layoutInput.constraints
            val cut = shortened
            if (cut != null && cut.room != room) {
                // The room changed: start again from the whole text
                shortened = null
            } else if (cut == null && overflow == TextOverflow.Ellipsis && result.hasVisualOverflow) {
                val fitted = shorten(text) { candidate ->
                    !measurer.measure(
                        AnnotatedString(candidate),
                        style = plain,
                        softWrap = softWrap,
                        maxLines = maxLines,
                        constraints = room
                    ).hasVisualOverflow
                }
                shortened = Shortened(fitted, room)
            }
        },
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        modifier = modifier
            .overhang(margin)
            .drawBehind {
                val result = layout.value ?: return@drawBehind
                val strength = glow().coerceIn(0f, 1f)
                if (strength == 0f) return@drawBehind
                val lit = shadow.copy(color = shadow.color.copy(alpha = shadow.color.alpha * strength))
                // The glyphs drawn here sit under the text itself; only their glow shows
                val inset = margin.roundToPx().toFloat()
                drawText(result, color = lit.color, topLeft = Offset(inset, inset), shadow = lit)
            }
            .padding(margin)
    )
}

private class Shortened(val text: String, val room: Constraints)

private const val ELLIPSIS = "…"

/** The longest start of [text] that still [fits] with an ellipsis after it. */
internal fun shorten(text: String, fits: (String) -> Boolean): String {
    fun cut(length: Int): String {
        // Never split a character made of two chars
        val end = if (length in 1 until text.length && text[length - 1].isHighSurrogate()) length - 1 else length
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }
    var low = 0
    var high = text.length - 1
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(cut(mid))) low = mid else high = mid - 1
    }
    return cut(low)
}

/** Lays the content out with [margin] of room on every side that hangs over its neighbours. */
private fun Modifier.overhang(margin: Dp): Modifier = layout { measurable, constraints ->
    val room = margin.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * room, 2 * room))
    val width = (placeable.width - 2 * room).coerceAtLeast(0)
    val height = (placeable.height - 2 * room).coerceAtLeast(0)
    layout(width, height) { placeable.place(-room, -room) }
}

/**
 * The Tokyo skyline at sunset behind the profile and first run screens. [dim] darkens it
 * for screens with more to read, [leftScrim] shades the left side behind a block of text.
 * Both animate in the draw phase only, so changing them never recomposes the content.
 */
@Composable
fun NeonSkylineBackdrop(
    dim: Float,
    modifier: Modifier = Modifier,
    leftScrim: Boolean = false
) {
    val dimAlpha by animateFloatAsState(dim, tween(700), label = "skyline_dim")
    val scrimAlpha by animateFloatAsState(if (leftScrim) 1f else 0f, tween(700), label = "skyline_scrim")
    Box(modifier = modifier.fillMaxSize().background(NeonPalette.Night)) {
        Image(
            painter = painterResource(R.drawable.nt_skyline),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val side = Brush.horizontalGradient(
                        0f to NeonPalette.Night.copy(alpha = 0.92f),
                        0.42f to NeonPalette.Night.copy(alpha = 0.55f),
                        0.75f to Color.Transparent
                    )
                    val floor = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to NeonPalette.Night.copy(alpha = 0.7f)
                    )
                    onDrawBehind {
                        drawRect(floor)
                        if (scrimAlpha > 0f) drawRect(side, alpha = scrimAlpha)
                        if (dimAlpha > 0f) drawRect(NeonPalette.Night.copy(alpha = dimAlpha))
                    }
                }
        )
    }
}

/** Dialog panel: deep violet glass with a magenta to cyan edge. */
fun Modifier.neonPanel(shape: Shape = RoundedCornerShape(24.dp)): Modifier = this
    .background(NeonPalette.Panel, shape)
    .border(
        width = 1.5.dp,
        brush = Brush.linearGradient(listOf(NeonPalette.Magenta.copy(alpha = 0.75f), NeonPalette.Cyan.copy(alpha = 0.45f))),
        shape = shape
    )
