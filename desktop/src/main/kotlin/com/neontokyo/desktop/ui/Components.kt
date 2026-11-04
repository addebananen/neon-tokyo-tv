package com.neontokyo.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.core.Images
import com.neontokyo.desktop.theme.FocusStyle
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.theme.neonHeading

/** A picture from an address, through the shared loader (with the metahub fallback). */
@Composable
fun NeonImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val theme = LocalNeon.current
    val image by produceState<ImageBitmap?>(url?.let { Images.cached(it) }, url) {
        value = url?.takeIf { it.isNotBlank() }?.let { Images.load(it) }
    }
    Box(modifier.background(theme.surface.copy(alpha = 0.55f))) {
        image?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Cards and buttons: Neon grows a little and glows; Deep Blue grows more and catches light like
 * glass. Keyboard focus and the mouse pointer both light a card; Enter or a click opens it.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(NeonTokens.shapeCardRadius.dp),
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalNeon.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val lit = focused || hovered
    val glow = theme.focus == FocusStyle.Glow
    val scale by animateFloatAsState(if (lit) theme.focusScale else 1f, tween(NeonTokens.motionFocusMs.toInt()))
    val border by animateColorAsState(if (lit) (if (glow) theme.primary else Color.White.copy(alpha = 0.7f)) else Color.Transparent)
    Box(
        modifier
            .scale(scale)
            .shadow(if (lit) NeonTokens.focusGlowRadius.dp else 0.dp, shape,
                ambientColor = if (glow) theme.primary else Color.Black, spotColor = if (glow) theme.primary else Color.Black)
            .clip(shape)
            .border(if (glow) 3.dp else 1.5.dp, border, shape)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar)) {
                    onClick(); true
                } else false
            }
    ) {
        content()
        if (lit && !glow) {
            Box(Modifier.matchParentSize().background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent, Color.White.copy(alpha = 0.05f)))
            ))
        }
    }
}

/** A poster on a row, with its title written on it when there is no picture. */
@Composable
fun PosterArt(title: String, poster: String?, progress: Float = 0f, watched: Boolean = false) {
    val theme = LocalNeon.current
    val w = Metrics.posterWidth.dp
    Box(Modifier.size(w, (Metrics.posterWidth / NeonTokens.posterAspect).dp)) {
        NeonImage(poster, Modifier.fillMaxSize())
        if (poster == null) {
            Text(title, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        if (progress > 0.01f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(8.dp))
        if (watched) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(theme.primary)) {
                Text("✓", color = theme.background, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun ProgressLine(value: Float, modifier: Modifier = Modifier) {
    val theme = LocalNeon.current
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).clip(CircleShape).background(theme.primary))
    }
}

/** A titled row that scrolls sideways. */
@Composable
fun RowSection(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy((Metrics.cardSpacing * 0.5f).dp)) {
        Text(title, style = neonHeading(), modifier = Modifier.padding(horizontal = Metrics.edge.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Metrics.cardSpacing.dp),
            contentPadding = PaddingValues(horizontal = Metrics.edge.dp, vertical = (Metrics.cardSpacing * 0.7f).dp),
            content = content,
        )
    }
}

/** A small rounded label (quality, HDR, the episode). */
@Composable
fun Badge(text: String, strong: Boolean = false) {
    val theme = LocalNeon.current
    Text(
        text,
        color = if (strong) theme.background else theme.text,
        fontSize = (Metrics.bodySize * 0.72f).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(if (strong) theme.primary else theme.surface).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A pill button for actions. */
@Composable
fun PillButton(text: String, prominent: Boolean = false, onClick: () -> Unit) {
    val theme = LocalNeon.current
    FocusCard(onClick, shape = CircleShape) {
        Text(
            text,
            color = if (prominent) theme.background else theme.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = Metrics.bodySize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(if (prominent) theme.primary else theme.surface.copy(alpha = 0.75f))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

/** The page background, like the Android app's LumeraBackground. */
@Composable
fun NeonBackground(modifier: Modifier = Modifier) {
    val theme = LocalNeon.current
    Box(modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(theme.background, theme.surface.copy(alpha = 0.55f),
            theme.primary.copy(alpha = if (theme.focus == FocusStyle.Glow) 0.18f else 0.08f)))
    ))
}
