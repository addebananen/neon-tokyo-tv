package com.neontokyo.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.core.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neontokyo.desktop.theme.FocusStyle
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics

/**
 * A picture from an address, through the shared loader (with the metahub fallback). It fades
 * in once loaded instead of popping, over a faint placeholder.
 */
@Composable
fun NeonImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: Boolean = true,
    onFailed: () -> Unit = {},
) {
    val theme = LocalNeon.current
    val image by produceState<ImageBitmap?>(url?.let { Images.cached(it) }, url) {
        val loaded = url?.takeIf { it.isNotBlank() }?.let { Images.load(it) }
        value = loaded
        if (loaded == null && url != null) onFailed()
    }
    val shown by animateFloatAsState(if (image != null) 1f else 0f, tween(if (url?.let { Images.cached(it) } != null) 0 else 260), label = "ImageIn")
    Box(modifier.then(if (placeholder) Modifier.background(theme.surface.copy(alpha = 0.45f)) else Modifier)) {
        image?.let {
            Image(it, contentDescription = null, contentScale = contentScale, alignment = alignment, modifier = Modifier.fillMaxSize().alpha(shown))
        }
    }
}

/**
 * A title's logo. A dark one (a deep purple on the night) gets a soft white light behind it,
 * like a sign lit from the back, so it always reads.
 */
@Composable
fun NeonLogo(url: String, modifier: Modifier = Modifier, alignment: Alignment = Alignment.CenterStart, onFailed: () -> Unit = {}) {
    val image by produceState<ImageBitmap?>(Images.cached(url), url) {
        val loaded = Images.load(url)
        value = loaded
        if (loaded == null) onFailed()
    }
    val dark by produceState(false, image) {
        value = image?.let { withContext(Dispatchers.Default) { lightness(it) < 0.3f } } ?: false
    }
    val shown by animateFloatAsState(if (image != null) 1f else 0f, tween(if (Images.cached(url) != null) 0 else 260), label = "LogoIn")
    Box(modifier) {
        image?.let {
            if (dark) {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit, alignment = alignment, colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.fillMaxSize().blur(9.dp, BlurredEdgeTreatment.Unbounded).alpha(0.55f * shown))
            }
            Image(it, contentDescription = null, contentScale = ContentScale.Fit, alignment = alignment, modifier = Modifier.fillMaxSize().alpha(shown))
        }
    }
}

/** How light a picture is where it is not see-through, 0 (black) ... 1 (white), from about 600 of its pixels. */
fun lightness(img: ImageBitmap): Float {
    val w = img.width
    val h = img.height
    if (w <= 0 || h <= 0) return 1f
    val px = IntArray(w * h)
    img.readPixels(px)
    val step = maxOf(1, px.size / 600)
    var sum = 0f
    var weight = 0f
    var i = 0
    while (i < px.size) {
        val c = px[i]
        val a = ((c ushr 24) and 0xff) / 255f
        if (a > 0.2f) {
            val lum = 0.2126f * ((c shr 16) and 0xff) + 0.7152f * ((c shr 8) and 0xff) + 0.0722f * (c and 0xff)
            sum += lum / 255f * a
            weight += a
        }
        i += step
    }
    return if (weight > 0f) sum / weight else 1f
}

/** Anything that lights up and opens: a thin wrapper over [NeonTile] for round and small things. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val radius = if (shape == CircleShape) 999.dp else 12.dp
    NeonTile(onClick, modifier, radius = radius, onFocused = onFocused, tilt = false) { content() }
}

/** A capsule for actions: Play glows in the theme colour; others are frosted until focused. */
@Composable
fun PillButton(text: String, prominent: Boolean = false, icon: String? = null, onClick: () -> Unit) {
    val theme = LocalNeon.current
    val glow = theme.focus == FocusStyle.Glow
    NeonTile(onClick, radius = 999.dp, tilt = false, pop = 1.06f) { lit ->
        Row(
            Modifier
                .background(
                    when {
                        lit && !glow -> Brush.verticalGradient(listOf(Color.White, Color(0xFFDDEBF7)))
                        prominent && glow -> Brush.horizontalGradient(listOf(theme.primary, Color(0xFFB46BFF)))
                        prominent -> Brush.verticalGradient(listOf(theme.primary, theme.primary))
                        lit -> Brush.verticalGradient(listOf(theme.surface, theme.panel))
                        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f)))
                    }
                )
                .padding(horizontal = 24.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val ink = when {
                lit && !glow -> Color(0xFF061423)
                prominent -> if (glow) Color.White else theme.background
                else -> theme.text
            }
            icon?.let { Text(it, color = ink, fontSize = Metrics.bodySize.sp) }
            Text(text, color = ink, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A frosted panel for settings groups and dialogs. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalNeon.current
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(theme.panel.copy(alpha = 0.86f), theme.background.copy(alpha = 0.92f))), RoundedCornerShape(22.dp))
            // a glass edge, lit from above in the theme's colour
            .border(1.dp, Brush.verticalGradient(listOf(theme.primary.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f), Color.Transparent)), RoundedCornerShape(22.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The page background: the living world, dimmed for pages full of text. */
@Composable
fun NeonBackground(modifier: Modifier = Modifier) {
    NeonWorld(modifier)
}
