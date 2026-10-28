package com.lumera.app.launcher

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lumera.app.dream.VideoDreamView
import com.lumera.app.dream.drive.CityDriveView
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The screensaver that follows Neon Home: the video the profile on Home sent for it, or else its
 * style's picture with the style's little lights and a quiet clock. Neon, which has the skyline
 * rather than a picture, gets the city drive.
 */
object HomeDream {
    fun view(context: Context): View {
        val profile = HomeProfiles.get(context).active()
        profile.dreamVideo?.let { File(it) }?.takeIf { it.length() > 0 }?.let { return VideoDreamView(context, it) }
        val style = profile.style
        if (profile.background == null && style.backdrop == null && style.video == null) return CityDriveView(context)
        return StyleDreamView(context, profile)
    }

    /** The screensaver of the style with [key], as if a profile on Home wore it. */
    fun preview(context: Context, key: String): View =
        StyleDreamView(context, HomeProfile("preview", "Preview", HomeStyle.of(key)))
}

/**
 * The active profile's widgets over a screensaver that draws its own clock (the city drive, the
 * skyline) or none (a video), placed under that clock and drifting slowly with the minutes so
 * nothing stands still on the panel. A home style's screensaver has them under its own clock.
 */
object DreamWidgetsView {
    fun wrap(context: Context, scene: View): View {
        if (scene is StyleDreamView) return scene
        val profile = HomeProfiles.get(context).active()
        val setup = HomeWidgets.get(context, profile.id)
        if (!setup.onDream || setup.shown.none { it != WidgetKind.Shortcuts }) return scene
        // Under the drive's clock and its sign, under the skyline's clock, or at the top for a video
        val (start, top) = when (scene) {
            is CityDriveView -> 62.dp to 196.dp
            is com.lumera.app.dream.NeonNightView -> 78.dp to 214.dp
            else -> 58.dp to 34.dp
        }
        return DreamFrame(context, scene, profile.style, start, top)
    }
}

/** A screensaver's own view with the widgets on top, keeping a Compose lifecycle of its own. */
class DreamFrame internal constructor(
    context: Context,
    val scene: View,
    style: HomeStyle,
    start: Dp,
    top: Dp
) : FrameLayout(context) {
    private val owner = DreamOwner()

    init {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        addView(scene)
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalHomeLook provides style.look) {
                    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
                            minute = System.currentTimeMillis() / 60_000L
                        }
                    }
                    val driftX by animateDpAsState((minute % 5 * 5).dp, tween(3_000, easing = FastOutSlowInEasing), label = "DreamWidgetsX")
                    val driftY by animateDpAsState((minute / 5 % 4 * 4).dp, tween(3_000, easing = FastOutSlowInEasing), label = "DreamWidgetsY")
                    DreamWidgets(Modifier.padding(start = start, top = top).offset(driftX, driftY))
                }
            }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onAttachedToWindow() {
        owner.move(Lifecycle.State.RESUMED)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        owner.move(Lifecycle.State.DESTROYED)
    }
}

/**
 * Hosts [StyleDream] in a screensaver's window, which has no activity to give Compose its
 * lifecycle, so the view keeps one of its own from being attached until it is taken away.
 */
class StyleDreamView internal constructor(context: Context, profile: HomeProfile) : FrameLayout(context) {

    private val owner = DreamOwner()

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { StyleDream(profile) }
        })
    }

    override fun onAttachedToWindow() {
        owner.move(Lifecycle.State.RESUMED)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        owner.move(Lifecycle.State.DESTROYED)
    }

    fun onPause() = owner.move(Lifecycle.State.CREATED)

    fun onResume() = owner.move(Lifecycle.State.RESUMED)
}

internal class DreamOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun move(state: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = state
        if (state == Lifecycle.State.DESTROYED) store.clear()
    }
}

/**
 * A style at rest: its picture or film as on Home, a touch dimmer for the night, and
 * the time where the clock sits on Home, breathing slowly. It rises out of black.
 */
@Composable
internal fun StyleDream(profile: HomeProfile) {
    val style = profile.style
    val look = style.look
    val curtain = remember { Animatable(1f) }
    LaunchedEffect(Unit) { curtain.animateTo(0f, tween(1600, easing = FastOutSlowInEasing)) }
    CompositionLocalProvider(LocalHomeLook provides look) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val context = LocalContext.current
            val picture = profile.background
            val plane = remember { Plane() }
            LaunchedEffect(picture) { if (picture != null && !isVideo(picture)) PictureStill.make(context, picture) }
            val made = PictureStill.made
            val media = remember(style, picture, made) { planeMedia(context, style, picture) }
            PlaneSurface(plane, media)
            StyleCover(plane, media, style, picture, wallpaper = null)
            // A little darker overall, and a soft shadow pooled under the clock so it reads on busy art
            Spacer(Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black.copy(alpha = 0.12f))
                drawRect(Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(size.width * 0.14f, size.height * 0.2f),
                    radius = size.width * 0.3f
                ))
            })
            DreamClock(look)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = curtain.value }.background(Color.Black))
        }
    }
}

/** The time and date in the style's own letters, where the clock is on Home, gently breathing. */
@Composable
private fun DreamClock(look: HomeLook) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val breath by rememberInfiniteTransition(label = "DreamClock").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "DreamClockBreath"
    )
    // A few dp further each minute, eased over seconds, so nothing stands still on the panel for hours
    val minute = (now / 60_000L).toInt()
    val driftX by animateDpAsState((minute % 5 * 5).dp, tween(3_000, easing = FastOutSlowInEasing), label = "DreamClockX")
    val driftY by animateDpAsState((minute / 5 % 4 * 5).dp, tween(3_000, easing = FastOutSlowInEasing), label = "DreamClockY")
    val locale = Locale.getDefault()
    val time = remember(now) { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(Date(now)) }
    val date = remember(now) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(Date(now)).uppercase(locale)
    }
    Column(Modifier.padding(start = 58.dp, top = 34.dp).offset(driftX, driftY).graphicsLayer { alpha = breath }) {
        BasicText(
            time,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = look.clockSize * 1.25f,
                letterSpacing = (-0.5).sp,
                color = look.clockColor,
                shadow = if (look.clockBlur > 0f) Shadow(look.clockGlow, blurRadius = look.clockBlur) else null
            )
        )
        BasicText(
            date,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = 14.sp,
                letterSpacing = 0.8.sp,
                color = if (look.glowingText) look.dim else look.text.copy(alpha = 0.82f),
                shadow = look.shade?.let { Shadow(it, blurRadius = 10f) }
            )
        )
        DreamWidgets(Modifier.padding(top = 12.dp))
    }
}
