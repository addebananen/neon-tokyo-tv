package com.lumera.app.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.domain.HubShape
import com.lumera.app.ui.theme.KanitFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Original neon scenes for the streaming service and genre cards, one per service and genre,
 * drawn for the app in tools/service-art rather than taken from anyone's artwork. Each scene
 * has its background, its subject and its moving parts on layers of their own, so the subject
 * can lift off the scene when the card gets focus and the parts can move (SceneMotion). The
 * home screen hero has a larger version of every scene.
 */
object ServiceArt {
    private const val DIR = "file:///android_asset/service_art"

    fun frameOf(shape: HubShape) = when (shape) {
        HubShape.HORIZONTAL -> "wide"
        HubShape.SQUARE -> "square"
        HubShape.VERTICAL -> "tall"
    }

    /** A layer of a scene in a frame: bg, fg, or p_<part> for a moving part. */
    fun layer(code: String, frame: String, name: String) = "$DIR/${code}_${frame}_$name.webp"

    /** The service's or genre's own neon colour, whatever the card was renamed to. */
    fun accent(code: String): Color = NeonAccents.forTitle(
        NeonTokyoStarterPack.SERVICES.firstOrNull { it.first == code }?.second
            ?: NeonTokyoStarterPack.GENRES.firstOrNull { NeonTokyoStarterPack.genreArt(it) == code }
            ?: code
    )

    fun request(context: Context, uri: String, crossfade: Boolean = true): ImageRequest =
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(if (crossfade) 160 else 0)
            .build()
}

/** A scene's motion in a frame, once motion.json has been read (off the main thread, once). */
@Composable
fun rememberSceneMotion(code: String, frame: String): FrameMotion? {
    val context = LocalContext.current
    return produceState(SceneMotion.cached(code, frame), code, frame) {
        value = SceneMotion.load(context.applicationContext, code, frame)
    }.value
}

/**
 * The time a scene has been moving and how far into motion it is. While [running] the clock
 * ticks every frame and the energy eases up to 1; after, the energy eases back down and the
 * clock slows with it and stops, so the scene settles instead of freezing. Time starts over
 * from 0 on a fresh focus, so a scene always begins the same way.
 */
@Stable
class SceneClock {
    val time = mutableFloatStateOf(0f)
    internal val energy = Animatable(0f)

    /** How far into motion, eased: 0 at rest, 1 in full motion. */
    val e: Float get() = energy.value.let { it * it * (3 - 2 * it) }
}

@Composable
fun rememberSceneClock(running: Boolean): SceneClock {
    val clock = remember { SceneClock() }
    LaunchedEffect(running) {
        if (running && clock.energy.value == 0f) clock.time.floatValue = 0f
        val fade = launch {
            clock.energy.animateTo(if (running) 1f else 0f, tween(if (running) 700 else 450, easing = LinearEasing))
        }
        var last = -1L
        while (running || fade.isActive) {
            withFrameNanos { now ->
                if (last >= 0) clock.time.floatValue += (now - last) / 1_000_000_000f * clock.e
                last = now
            }
        }
    }
    return clock
}

/**
 * A scene's layers filling the box they are given, which has the frame's shape: the background
 * with its parts and effects, then the subject with its own. [sceneLayer] and [subjectLayer]
 * move the two groups as one (the lift on focus, the hero's entrance). Without [clock] every
 * part stays in its resting pose. [fgBox] is where a cropped subject layer was cut from.
 */
@Composable
fun SceneLayers(
    code: String,
    frame: String,
    motion: FrameMotion?,
    clock: SceneClock?,
    modifier: Modifier = Modifier,
    crossfade: Boolean = true,
    fgBox: FloatArray? = null,
    sceneLayer: GraphicsLayerScope.() -> Unit = {},
    subjectLayer: GraphicsLayerScope.() -> Unit = {},
    onScene: () -> Unit = {},
    onSubject: () -> Unit = {}
) {
    val context = LocalContext.current
    val scene = remember(code, frame) { ServiceArt.request(context, ServiceArt.layer(code, frame, "bg"), crossfade) }
    val subject = remember(code, frame) { ServiceArt.request(context, ServiceArt.layer(code, frame, "fg"), crossfade) }
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer(sceneLayer)) {
            AsyncImage(
                model = scene,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                onSuccess = { onScene() },
                onError = { onScene() },
                modifier = Modifier.fillMaxSize()
            )
            if (motion != null) {
                motion.parts.forEach { if (it.inScene) ScenePartImage(code, frame, it, motion, clock, crossfade) }
                if (motion.sceneFx.isNotEmpty()) SceneFxLayer(motion, motion.sceneFx, clock)
            }
        }
        Box(modifier = Modifier.fillMaxSize().graphicsLayer(subjectLayer)) {
            motion?.parts?.forEach { if (!it.inScene && it.under) ScenePartImage(code, frame, it, motion, clock, crossfade) }
            val fgMotion = motion?.fg
            AsyncImage(
                model = subject,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                onSuccess = { onSubject() },
                onError = { onSubject() },
                modifier = (if (fgBox != null) Modifier.placedAt(fgBox) else Modifier.fillMaxSize())
                    .then(if (fgMotion != null && motion != null) Modifier.posed(fgMotion, motion, clock) else Modifier)
            )
            if (motion != null) {
                motion.parts.forEach { if (!it.inScene && !it.under) ScenePartImage(code, frame, it, motion, clock, crossfade) }
                if (motion.topFx.isNotEmpty()) SceneFxLayer(motion, motion.topFx, clock)
            }
        }
    }
}

@Composable
private fun ScenePartImage(code: String, frame: String, part: ScenePart, motion: FrameMotion, clock: SceneClock?, crossfade: Boolean) {
    val context = LocalContext.current
    val request = remember(code, frame, part.name) { ServiceArt.request(context, ServiceArt.layer(code, frame, "p_${part.name}"), crossfade) }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.placedAt(part.box).posed(part, motion, clock)
    )
}

/** Moves a part's layer to its pose on the clock, round its pivot. */
private fun Modifier.posed(part: ScenePart, motion: FrameMotion, clock: SceneClock?): Modifier {
    if (!part.moves && !part.hidden) return this
    val pose = PartPose()
    return graphicsLayer {
        pose.of(part, clock?.time?.floatValue ?: 0f, clock?.e ?: 0f)
        // The layer is the part's own box; the frame is the box it was cut from
        val box = part.box
        val frameW = size.width / (box[2] - box[0]).coerceAtLeast(0.0001f)
        val frameH = size.height / (box[3] - box[1]).coerceAtLeast(0.0001f)
        val f = motion.frame
        val px = f.x(part.pivotX, frameW) - box[0] * frameW
        val py = f.y(part.pivotY, frameW) - box[1] * frameH
        transformOrigin = TransformOrigin(px / size.width, py / size.height)
        translationX = f.len(pose.dx, frameW)
        translationY = f.len(pose.dy, frameW)
        rotationZ = pose.rot
        scaleX = pose.scaleX
        scaleY = pose.scaleY
        alpha = pose.alpha
    }
}

@Composable
private fun SceneFxLayer(motion: FrameMotion, fx: List<SceneFx>, clock: SceneClock?) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val painter = SceneFxPainter(motion, size)
                onDrawBehind {
                    with(painter) { draw(fx, clock?.time?.floatValue ?: 0f, clock?.e ?: 0f) }
                }
            }
    )
}

/** Lays a layer out over the part of its frame it was cut from: left, top, right and bottom fractions. */
fun Modifier.placedAt(box: FloatArray): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(
        Constraints.fixed(
            ((box[2] - box[0]) * width).roundToInt().coerceAtLeast(1),
            ((box[3] - box[1]) * height).roundToInt().coerceAtLeast(1)
        )
    )
    layout(width, height) {
        placeable.place((box[0] * width).roundToInt(), (box[1] * height).roundToInt())
    }
}

/**
 * A streaming service or genre card: its scene, and its subject on a layer of its own. On focus
 * the subject lifts out of the scene with a little spring, a light sweeps across once, the name
 * lights up like a neon tube and the scene comes alive (SceneMotion). The animations only
 * change layer and draw values, so they never recompose the card, and only the focused card
 * runs a clock.
 */
@Composable
fun ServiceCardArt(
    code: String,
    title: String,
    shape: HubShape,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = remember(code) { ServiceArt.accent(code) }
    val frame = ServiceArt.frameOf(shape)
    val motion = rememberSceneMotion(code, frame)
    val clock = rememberSceneClock(running = focused && motion?.animated == true)
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
        SceneLayers(
            code = code,
            frame = frame,
            motion = motion,
            clock = clock,
            sceneLayer = {
                val scale = 1f + 0.04f * lift.value
                scaleX = scale
                scaleY = scale
            },
            subjectLayer = {
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
