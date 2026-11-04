package com.neontokyo.desktop.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.core.Art
import com.neontokyo.desktop.core.Images
import com.neontokyo.desktop.theme.FocusStyle
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.theme.neonDisplay
import com.neontokyo.desktop.theme.neonHeading

/** Wide cards, shaped like Neon Home's app tiles (2 : 1). */
object WideCard {
    const val WIDTH = 368f
    const val HEIGHT = 184f
}

/** The picture's own glow colour, once it has loaded. */
@Composable
fun rememberTint(url: String?): Color? {
    val tint by produceState(Images.tint(url), url) {
        if (url != null) {
            Images.load(url)
            value = Images.tint(url)
        }
    }
    return tint
}

/** A poster on a row, its title written on it when there is no picture. */
@Composable
fun PosterCard(title: String, poster: String?, onClick: () -> Unit, onFocused: () -> Unit = {}, progress: Float = 0f, watched: Boolean = false) {
    val theme = LocalNeon.current
    val w = Metrics.posterWidth
    NeonTile(onClick, glow = rememberTint(poster), onFocused = onFocused, radius = 12.dp) { lit ->
        Box(Modifier.size(w.dp, (w / NeonTokens.posterAspect).dp)) {
            NeonImage(poster, Modifier.fillMaxSize())
            if (poster == null) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.surface, theme.panel))))
                Text(title, style = neonHeading((Metrics.bodySize * 1.1f).sp), maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            if (progress > 0.01f) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 9.dp))
            }
            if (watched) WatchedMark(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

@Composable
fun WatchedMark(modifier: Modifier = Modifier) {
    val theme = LocalNeon.current
    Box(modifier.size(24.dp).clip(CircleShape).background(theme.primary), contentAlignment = Alignment.Center) {
        Text("✓", color = theme.background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * A wide card like Neon Home's app tiles: the title's wide picture, its logo (or its name in
 * neon) lower left, and how far along. Used for Continue watching and the first row.
 */
@Composable
fun WideTile(
    id: String,
    name: String,
    background: String?,
    logo: String?,
    poster: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    progress: Float = 0f,
    badge: String? = null,
    fresh: Boolean = false,
    caption: String? = null,
) {
    val theme = LocalNeon.current
    val bg = Art.background(id, background) ?: poster
    val logoUrl = Art.logo(id, logo)
    var logoOk by remember(logoUrl) { mutableStateOf(logoUrl != null) }
    NeonTile(onClick, glow = rememberTint(bg), onFocused = onFocused, radius = 16.dp) { lit ->
        val zoom by animateFloatAsState(if (lit) 1.06f else 1f, tween(1400, easing = NeonMotion.FastOutSlowIn), label = "WideZoom")
        Box(Modifier.size(WideCard.WIDTH.dp, WideCard.HEIGHT.dp)) {
            NeonImage(bg, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom })
            Box(Modifier.matchParentSize().drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.82f))))
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endX = size.width * 0.6f))
            })
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (logoUrl != null && logoOk) {
                    NeonImage(logoUrl, Modifier.height(54.dp).widthIn(max = 220.dp).fillMaxWidth(), ContentScale.Fit, alignment = Alignment.BottomStart,
                        placeholder = false, onFailed = { logoOk = false })
                } else {
                    Text(name, style = neonDisplay((Metrics.bodySize * 1.35f).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    badge?.let { Badge(it) }
                    if (fresh) Badge("NEW", strong = true)
                    caption?.let { Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = (Metrics.bodySize * 0.78f).sp, maxLines = 1) }
                }
                if (progress > 0.01f) ProgressLine(progress)
            }
        }
    }
}

/** A thin neon bar for how far someone got. */
@Composable
fun ProgressLine(value: Float, modifier: Modifier = Modifier) {
    val theme = LocalNeon.current
    val glow = theme.focus == FocusStyle.Glow
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).clip(CircleShape)
                .background(if (glow) Brush.horizontalGradient(listOf(theme.secondary, theme.primary)) else Brush.horizontalGradient(listOf(theme.primary, theme.primary)))
        )
    }
}

/**
 * Keeps the focused card at the same place in its row, a little in from the left edge, so the
 * row slides under the focus instead of the focus wandering: the film app's FocusPivotSpec,
 * on the gliding spring.
 */
@OptIn(ExperimentalFoundationApi::class)
class PivotSpec(private val pivotPx: Float) : BringIntoViewSpec {
    @Deprecated("", level = DeprecationLevel.HIDDEN)
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 260f, visibilityThreshold = 0.5f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // a card wider than the room left after the pivot just needs to show whole
        if (size >= containerSize - pivotPx) return offset - (containerSize - size) / 2f
        return offset - pivotPx
    }
}

/** A titled row that slides sideways under the focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowSection(title: String, count: Int? = null, content: LazyListScope.() -> Unit) {
    val theme = LocalNeon.current
    val pivot = with(LocalDensity.current) { Metrics.edge.dp.toPx() }
    val spec = remember(pivot) { PivotSpec(pivot) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.padding(horizontal = Metrics.edge.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = neonHeading())
            count?.takeIf { it > 0 }?.let {
                Text("$it", color = theme.muted, fontSize = (Metrics.bodySize * 0.8f).sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                state = rememberLazyListState(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.cardSpacing.dp),
                // room above and below for the pop and the glow
                contentPadding = PaddingValues(horizontal = Metrics.edge.dp, vertical = 22.dp),
                content = content,
            )
        }
    }
}

/** A small rounded label (quality, HDR, the episode). */
@Composable
fun Badge(text: String, strong: Boolean = false) {
    val theme = LocalNeon.current
    Text(
        text,
        color = if (strong) theme.background else theme.text,
        fontSize = (Metrics.bodySize * 0.7f).sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (strong) theme.primary else Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
