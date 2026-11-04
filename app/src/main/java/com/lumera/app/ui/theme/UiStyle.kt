package com.lumera.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.R

/**
 * How a theme dresses the app, besides its colours. Most themes are [Neon]: Kanit italic lit like
 * neon signs, glowing rings round what has the focus. [Glass] is the calm one, after the Apple TV:
 * one quiet face in sentence case, no glow anywhere, and what has the focus lifts towards you on a
 * soft shadow with a gloss on it, as if it were a pane of glass.
 */
enum class UiStyle {
    Neon, Glass;

    companion object {
        fun of(themeId: String): UiStyle = if (themeId in GLASS_THEMES) Glass else Neon

        private val GLASS_THEMES = setOf("deep_blue")
    }
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.Neon }

/** True while the theme in use is a [UiStyle.Glass] one. */
val isGlass: Boolean
    @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.Glass

/** The face of the glass themes: Outfit (SIL Open Font License, see third_party/fonts). */
val GlassFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

/**
 * The measures of the glass themes, in one place so every screen moves and rounds alike.
 */
object Glass {
    /** What a poster or a card grows to in focus: enough to read as lifted, not to crowd its row. */
    const val LIFT = 1.1f

    /** Smaller things, buttons and rows of a menu, lift less. */
    const val LIFT_SMALL = 1.06f

    /**
     * The spring the focus moves on. A spring picks up from wherever it is, at the speed it has,
     * so a card left before it has grown turns back at once, without a jump: quick presses on the
     * remote run through a row smoothly instead of queueing up animations.
     */
    val Motion = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

    /** Colours fade a little slower than things move, as on the Apple TV. */
    val Fade = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)

    val Text = Color(0xFFF2F8FF)
    val Dim = Color(0xFF9DB4C8)

    /** A resting pane of glass: a faint frost and a hairline round it. */
    val Pane = Color(0x1FFFFFFF)
    val PaneFocused = Color(0xF2F4F9FF)
    val Hairline = Color(0x2EFFFFFF)

    /** Writing on a focused pane, which is near white. */
    val OnFocused = Color(0xFF0A1726)

    val CardCorner = 14.dp
    val PillCorner = 999.dp
}

/**
 * A text style as the theme in use wants it: unchanged in the neon themes; in the glass ones the
 * same size in the glass face, upright, with tighter spacing and a soft shade instead of a glow.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.themed(): TextStyle = if (!isGlass) this else glassy()

internal fun TextStyle.glassy(): TextStyle {
    val wide = letterSpacing.isSp && letterSpacing.value >= 3f
    return copy(
        fontFamily = GlassFamily,
        fontStyle = FontStyle.Normal,
        fontWeight = when {
            fontWeight == null -> FontWeight.SemiBold
            fontWeight!!.weight >= 600 -> FontWeight.SemiBold
            else -> fontWeight
        },
        // Labels spaced wide stay a little spaced, all else sits close as Apple sets it
        letterSpacing = if (wide) 1.5.sp else 0.sp,
        shadow = shadow?.let { if (it.blurRadius > 0f) Shadow(Color(0x73000814), Offset(0f, 2f), 14f) else it }
    )
}

/**
 * A card or button that lifts in focus, the glass way: it grows on [Glass.Motion], rises on a
 * soft shadow and catches a gloss along its top. All of it is read while drawing, so the focus
 * coming and going costs no more than the one recomposition the focus brings anyway.
 */
fun Modifier.glassLift(
    focused: Boolean,
    shape: Shape,
    lift: Float = Glass.LIFT,
    gloss: Boolean = true
): Modifier = composed {
    val t by animateFloatAsState(if (focused) 1f else 0f, Glass.Motion, label = "glass_lift")
    graphicsLayer {
        val k = t.coerceIn(-0.1f, 1.15f)
        val s = 1f + (lift - 1f) * k
        scaleX = s
        scaleY = s
        shadowElevation = (18.dp.toPx() * t.coerceIn(0f, 1f))
        this.shape = shape
        clip = false
        ambientShadowColor = Color.Black
        spotShadowColor = Color.Black
    }.then(
        if (!gloss) Modifier else Modifier.drawWithCache {
            // A gloss over the top of the pane, as light catches glass tipped towards it
            val sheen = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.22f),
                0.35f to Color.White.copy(alpha = 0.06f),
                0.6f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.55f, size.height)
            )
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                val a = t.coerceIn(0f, 1f)
                if (a > 0.01f) {
                    androidx.compose.ui.graphics.drawscope.clipPath(outline.toPath()) {
                        drawRect(sheen, alpha = a)
                    }
                }
            }
        }
    )
}

private fun androidx.compose.ui.graphics.Outline.toPath(): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().also { path ->
        when (this) {
            is androidx.compose.ui.graphics.Outline.Rectangle -> path.addRect(rect)
            is androidx.compose.ui.graphics.Outline.Rounded -> path.addRoundRect(roundRect)
            is androidx.compose.ui.graphics.Outline.Generic -> path.addPath(this.path)
        }
    }
