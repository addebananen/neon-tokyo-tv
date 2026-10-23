package com.lumera.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.ui.components.NeonAccents
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType
import com.lumera.app.ui.components.NoiseOverlay
import com.lumera.app.ui.components.ServiceArt
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What the top of the home screen shows while a folder card (a streaming service or a
 * genre) has focus: the folder's name as a neon sign and a line on what is inside,
 * so the hero never sits empty.
 */
@Composable
fun HubHero(title: String, rowTitle: String, rowId: String, modifier: Modifier = Modifier) {
    val accent = remember(title) { NeonAccents.forTitle(title) }
    Column(modifier = modifier.padding(top = 28.dp)) {
        NeonText(rowTitle.uppercase(), style = NeonType.eyebrow(13.sp, color = accent))
        Spacer(Modifier.height(6.dp))
        NeonText(
            text = title,
            style = NeonType.display(56.sp, glow = accent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = NeonTokyoStarterPack.hubBlurb(title, rowId),
            style = MaterialTheme.typography.bodyLarge,
            color = NeonPalette.Lilac,
            maxLines = 2,
            // Wraps before it reaches a service's scene on the right
            modifier = Modifier.widthIn(max = 520.dp)
        )
    }
}

/** Soft glow in the folder's colour behind the hero, in place of a backdrop image. */
@Composable
fun HubHeroGlow(title: String, modifier: Modifier = Modifier) {
    val accent = remember(title) { NeonAccents.forTitle(title) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.74f, size.height * 0.18f),
                    radius = size.width * 0.55f
                )
                onDrawBehind { drawRect(glow) }
            }
    )
}

/**
 * A streaming service's scene as the hero backdrop while its card has focus, at the top right
 * where a film's backdrop goes. The scene settles in from a little closer and its subject
 * rises into place on a soft spring, the depth the card itself shows on focus.
 */
@Composable
fun ServiceHeroArt(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val scene = remember(code) { ServiceArt.request(context, ServiceArt.heroLayer(code, subject = false), crossfade = false) }
    val subject = remember(code) { ServiceArt.request(context, ServiceArt.heroLayer(code, subject = true), crossfade = false) }
    val subjectBox = ServiceArt.heroSubjects[code]
    // The entrance waits for both layers, so the subject never shows before its scene
    var sceneDone by remember { mutableStateOf(false) }
    var subjectDone by remember { mutableStateOf(subjectBox == null) }
    val settle = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(sceneDone, subjectDone) {
        if (!sceneDone || !subjectDone) return@LaunchedEffect
        launch { settle.animateTo(1f, tween(1400, easing = LinearOutSlowInEasing)) }
        rise.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 120f))
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.65f)
                .graphicsLayer {
                    alpha = (settle.value * 4f).coerceAtMost(1f)
                    // The scene starts a little larger than its box; the fades only cover the box
                    clip = true
                }
        ) {
            AsyncImage(
                model = scene,
                contentDescription = null,
                onSuccess = { sceneDone = true },
                onError = { sceneDone = true },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1.05f - 0.05f * settle.value
                        scaleX = scale
                        scaleY = scale
                    }
            )
            if (subjectBox != null) {
                AsyncImage(
                    model = subject,
                    contentDescription = null,
                    onSuccess = { subjectDone = true },
                    onError = { subjectDone = true },
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .placedInScene(subjectBox)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            val scale = 0.94f + 0.06f * rise.value
                            scaleX = scale
                            scaleY = scale
                            translationY = (1f - rise.value) * 22.dp.toPx()
                            alpha = (rise.value * 2.5f).coerceIn(0f, 1f)
                        }
                )
            }
            Spacer(modifier = Modifier.fillMaxSize().backdropFades(backgroundColor))
            NoiseOverlay()
        }
    }
}

/**
 * Lays a hero's subject layer out over the part of the 16:9 scene it was cut from
 * ([box] is left, top, right and bottom as fractions), with the scene scaled to cover
 * the space and centred the way ContentScale.Crop draws it.
 */
private fun Modifier.placedInScene(box: List<Float>): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    val scale = maxOf(width / 16f, height / 9f)
    val sceneWidth = 16f * scale
    val sceneHeight = 9f * scale
    val placeable = measurable.measure(
        Constraints.fixed(
            ((box[2] - box[0]) * sceneWidth).roundToInt(),
            ((box[3] - box[1]) * sceneHeight).roundToInt()
        )
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            ((width - sceneWidth) / 2f + box[0] * sceneWidth).roundToInt(),
            ((height - sceneHeight) / 2f + box[1] * sceneHeight).roundToInt()
        )
    }
}
