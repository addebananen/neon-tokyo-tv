package com.lumera.app.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.domain.HubShape
import com.lumera.app.ui.theme.KanitFamily
import kotlinx.coroutines.launch

/**
 * Original neon scenes for the streaming service cards, one per service, drawn for the app
 * in tools/service-art rather than taken from the services' own artwork. Each card has its
 * scene and its subject on separate layers, so the subject can lift off the scene when the
 * card gets focus. The home screen hero has a larger version of every scene.
 */
object ServiceArt {
    private const val DIR = "file:///android_asset/service_art"

    fun cardLayer(code: String, shape: HubShape, subject: Boolean): String {
        val frame = when (shape) {
            HubShape.HORIZONTAL -> "wide"
            HubShape.SQUARE -> "square"
            HubShape.VERTICAL -> "tall"
        }
        return "$DIR/${code}_${frame}_${if (subject) "fg" else "bg"}.webp"
    }

    fun heroLayer(code: String, subject: Boolean) = "$DIR/${code}_hero_${if (subject) "fg" else "bg"}.webp"

    /**
     * Where each hero's subject sits in its 16:9 frame, as left, top, right and bottom
     * fractions. The subject layer is cropped to that box to keep it small in memory.
     * Written by tools/service-art/pack.py.
     */
    val heroSubjects: Map<String, List<Float>> = mapOf(
        "nfx" to listOf(0.5414f, 0.1125f, 0.95f, 0.6417f),
        "hbm" to listOf(0.5016f, 0.0972f, 0.9797f, 0.6083f),
        "dnp" to listOf(0.5383f, 0.0889f, 0.9523f, 0.6139f),
        "amp" to listOf(0.5648f, 0.0722f, 0.9211f, 0.6417f),
        "atp" to listOf(0.5539f, 0.1417f, 0.9273f, 0.6097f),
        "sst" to listOf(0.5422f, 0.0833f, 0.9391f, 0.6389f),
        "pmp" to listOf(0.4695f, 0.1611f, 1.0f, 0.6167f),
        "cru" to listOf(0.5453f, 0.1181f, 0.9336f, 0.6458f)
    )

    /** The service's own neon colour, whatever the card was renamed to. */
    fun accent(code: String): Color =
        NeonAccents.forTitle(NeonTokyoStarterPack.SERVICES.firstOrNull { it.first == code }?.second ?: code)

    fun request(context: Context, uri: String, crossfade: Boolean = true): ImageRequest =
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(if (crossfade) 160 else 0)
            .build()
}

/**
 * A streaming service card: its scene, and its subject on a layer of its own. On focus the
 * subject lifts out of the scene with a little spring, a light sweeps across once and the
 * name lights up like a neon tube. The animations only change layer and draw values, so
 * they never recompose the card.
 */
@Composable
fun ServiceCardArt(
    code: String,
    title: String,
    shape: HubShape,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = remember(code) { ServiceArt.accent(code) }
    val scene = remember(code, shape) { ServiceArt.request(context, ServiceArt.cardLayer(code, shape, subject = false)) }
    val subject = remember(code, shape) { ServiceArt.request(context, ServiceArt.cardLayer(code, shape, subject = true)) }
    val lit = rememberNeonLit(focused)
    // 0 = resting, 1 = the subject lifted off the scene
    val lift = remember { Animatable(if (focused) 1f else 0f) }
    // Where the light sweeping across is, from 0 to 1; 1 is gone
    val sheen = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused) {
            launch {
                sheen.snapTo(0f)
                sheen.animateTo(1f, tween(durationMillis = 720, delayMillis = 60, easing = FastOutSlowInEasing))
            }
            lift.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        } else {
            // A sweep cut short by leaving the card must not stay behind on it
            sheen.snapTo(1f)
            lift.animateTo(0f, tween(220))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = scene,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f + 0.04f * lift.value
                    scaleX = scale
                    scaleY = scale
                }
        )
        AsyncImage(
            model = subject,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Grows from where it stands, so it rises out of the scene
                    transformOrigin = TransformOrigin(0.5f, 0.88f)
                    val scale = 1f + 0.09f * lift.value
                    scaleX = scale
                    scaleY = scale
                    translationY = -3.dp.toPx() * lift.value
                }
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    // A slanted band of light, made once and slid across by moving the canvas
                    val band = size.width * 0.32f
                    val rise = size.height * 0.35f
                    val light = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(band, rise)
                    )
                    // How wide the band is along a row, and how far it leans over the card's height
                    val reach = (band * band + rise * rise) / band
                    val lean = rise * size.height / band
                    val scrim = Brush.verticalGradient(
                        0.54f to Color(0x00080314),
                        1f to Color(0xB8080314)
                    )
                    onDrawBehind {
                        val t = sheen.value
                        if (t < 1f) {
                            val dx = -reach + (size.width + lean + reach) * t
                            translate(left = dx) {
                                drawRect(light, topLeft = Offset(-dx, 0f), size = size)
                            }
                        }
                        drawRect(scrim)
                    }
                }
        )
        val fontSize = when (shape) {
            HubShape.HORIZONTAL -> 19.sp
            HubShape.SQUARE -> 18.sp
            HubShape.VERTICAL -> 15.sp
        }
        NeonText(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = 5.dp),
            style = TextStyle(
                fontFamily = KanitFamily,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = fontSize,
                letterSpacing = 0.3.sp,
                color = Color(0xFFF6EAFF),
                shadow = Shadow(color = accent, blurRadius = 22f)
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            glow = { 0.55f + 0.45f * lit.value }
        )
    }
}
