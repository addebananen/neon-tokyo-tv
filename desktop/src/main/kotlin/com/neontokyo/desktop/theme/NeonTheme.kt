package com.neontokyo.desktop.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** How a focused card shows focus: Neon Tokyo glows, Deep Blue lifts like glass. */
enum class FocusStyle { Glow, Glass }

/** One theme's colours, from design/neon-tokens.json (see NeonTokens.kt). */
data class NeonThemeTokens(
    val id: String,
    val name: String,
    val focus: FocusStyle,
    val primary: Color,
    val secondary: Color,
    val warm: Color,
    val background: Color,
    val surface: Color,
    val panel: Color,
    val text: Color,
    val muted: Color,
    val error: Color,
) {
    val focusScale: Float get() = if (focus == FocusStyle.Glow) NeonTokens.focusGlowScale else NeonTokens.focusGlassScale
}

val LocalNeon = staticCompositionLocalOf { NeonTokens.neonTokyo }

/** Kanit, the Android app's neon font, from the same font files. */
val Kanit = FontFamily(
    Font("fonts/kanit_medium.ttf", FontWeight.Medium),
    Font("fonts/kanit_medium_italic.ttf", FontWeight.Medium, FontStyle.Italic),
    Font("fonts/kanit_semibold_italic.ttf", FontWeight.SemiBold, FontStyle.Italic),
)

/** Sizes for a TV-like screen across the room, scaled by the window. */
object Metrics {
    const val posterWidth = NeonTokens.posterDesktopWidth
    const val edge = 56f
    const val rowSpacing = 36f
    const val cardSpacing = 22f
    const val titleSize = 30f
    const val bodySize = 17f
    const val heroHeight = 520f
}

/** A title lit like a neon sign, as NeonType.display on Android. */
@Composable
fun neonDisplay(size: TextUnit = Metrics.titleSize.sp, theme: NeonThemeTokens = LocalNeon.current): TextStyle =
    if (theme.focus == FocusStyle.Glow) {
        TextStyle(
            fontFamily = Kanit, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = size,
            letterSpacing = 1.sp, color = theme.text,
            shadow = Shadow(color = theme.primary.copy(alpha = NeonTokens.focusGlowOpacity), blurRadius = 28f),
        )
    } else {
        TextStyle(fontWeight = FontWeight.SemiBold, fontSize = size, color = theme.text)
    }

@Composable
fun neonHeading(size: TextUnit = (Metrics.titleSize * 0.8f).sp, theme: NeonThemeTokens = LocalNeon.current): TextStyle =
    if (theme.focus == FocusStyle.Glow) {
        TextStyle(fontFamily = Kanit, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = size, color = theme.text)
    } else {
        TextStyle(fontWeight = FontWeight.SemiBold, fontSize = size, color = theme.text)
    }

fun hexColor(hex: String): Color =
    Color(0xFF000000 or (hex.trim().removePrefix("#").toLongOrNull(16) ?: 0xFF3D9A))
