package com.lumera.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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

/** Kanit italic lit like a neon sign: big titles, headings and the small spaced label above them. */
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
