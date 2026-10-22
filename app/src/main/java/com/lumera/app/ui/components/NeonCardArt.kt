package com.lumera.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lumera.app.ui.theme.KanitFamily

/**
 * Neon sign style art for hub cards without an image: a night sky tinted by the
 * card's accent colour, a glowing horizon and a perspective grid, with the title
 * lit like a neon tube. Everything is cached per size, so it is as cheap as a bitmap.
 */
@Composable
fun NeonCardArt(
    title: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    fontSize: Int = 19,
    maxLines: Int = 2
) {
    val accent = remember(title) { NeonAccents.forTitle(title) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val night = Color(0xFF120826)
                val sky = Brush.verticalGradient(
                    0f to lerp(Color(0xFF2A0F4F), accent, 0.28f),
                    0.66f to night,
                    1f to Color(0xFF0D0721)
                )
                val horizonY = size.height * 0.72f
                val glow = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.5f, horizonY),
                    radius = size.width * 0.55f
                )
                val vanishX = size.width / 2f
                val rays = (-6..6).map { i -> vanishX + i * size.width / 5f }
                val rows = (1..3).map { i -> horizonY + (size.height - horizonY) * (i / 3f) * (i / 3f) }
                val line = 1.dp.toPx()
                onDrawBehind {
                    drawRect(sky)
                    drawRect(glow)
                    drawLine(accent.copy(alpha = 0.35f), Offset(0f, horizonY), Offset(size.width, horizonY), strokeWidth = line * 4)
                    drawLine(accent, Offset(0f, horizonY), Offset(size.width, horizonY), strokeWidth = line * 1.5f)
                    rays.forEach { x ->
                        drawLine(NeonAccents.Cyan.copy(alpha = 0.28f), Offset(vanishX, horizonY), Offset(x, size.height), strokeWidth = line)
                    }
                    rows.forEach { y ->
                        drawLine(accent.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = line)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 18.dp),
            style = TextStyle(
                fontFamily = KanitFamily,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = fontSize.sp,
                letterSpacing = 0.3.sp,
                color = if (focused) Color.White else Color(0xFFF6EAFF),
                // Blurred text shadows cost a little per frame, so poster placeholders skip them
                shadow = if (glow) Shadow(color = accent.copy(alpha = if (focused) 0.95f else 0.7f), blurRadius = if (focused) 22f else 14f) else null
            ),
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

object NeonAccents {
    val Magenta = Color(0xFFFF3D9A)
    val Cyan = Color(0xFF3EF0FF)

    private val known = mapOf(
        "netflix" to Color(0xFFFF2B3A),
        "hbo max" to Color(0xFF9D6BFF),
        "max" to Color(0xFF9D6BFF),
        "disney+" to Color(0xFF3D8BFF),
        "prime video" to Color(0xFF2FC4FF),
        "apple tv+" to Color(0xFFE6E8F2),
        "skyshowtime" to Color(0xFFD45BFF),
        "paramount+" to Color(0xFF4D7CFF),
        "crunchyroll" to Color(0xFFFF8A3D),
        "viaplay" to Color(0xFFFF4D8D),
        "action" to Color(0xFFFF5A3D),
        "comedy" to Color(0xFFFFD36B),
        "sci-fi" to Color(0xFF3EF0FF),
        "horror" to Color(0xFFFF2E4D),
        "thriller" to Color(0xFFB24BFF),
        "drama" to Color(0xFFFF6FB5),
        "animation" to Color(0xFF86FF6A),
        "crime" to Color(0xFFFF8A3D),
        "adventure" to Color(0xFF3DFFB0),
        "fantasy" to Color(0xFFC38CFF),
        "romance" to Color(0xFFFF7AA8),
        "mystery" to Color(0xFF7B7BFF),
        "family" to Color(0xFF6AE3FF),
        "documentary" to Color(0xFFE8F6FF),
        "anime" to Color(0xFFFF6FB5)
    )
    private val palette = listOf(Magenta, Cyan, Color(0xFFFFD36B), Color(0xFFB58CFF), Color(0xFF86FF6A), Color(0xFFFF8A3D))

    fun forTitle(title: String): Color {
        val key = title.trim().lowercase()
        return known[key] ?: palette[(key.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}
