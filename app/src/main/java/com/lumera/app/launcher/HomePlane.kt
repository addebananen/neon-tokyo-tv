package com.lumera.app.launcher

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.lumera.app.dream.videoFrame
import kotlinx.coroutines.delay
import java.io.File

/*
 * The picture behind the home screen and a style's screensaver is shown by the TV's video chip
 * rather than drawn. A TV like the 75" TCL draws its menus at 1080p and scales them up to its 4K
 * panel coarsely, so a photo drawn there looks pixelated; video goes out at the panel's own
 * resolution through the TV's picture processing, and costs the graphics chip nothing. The film
 * or still sits on a SurfaceView under the window, and everything drawn shows on top of it.
 */

/** What the video chip shows: a film on a loop, or a still that plays once and stays. */
internal data class PlaneMedia(val uri: Uri, val loop: Boolean)

/** What the video chip shows for [style], or for a profile's own [picture]; null when it shows nothing. */
internal fun planeMedia(context: Context, style: HomeStyle, picture: String?): PlaneMedia? = when {
    picture != null && isVideo(picture) -> PlaneMedia(Uri.fromFile(File(picture)), loop = true)
    picture != null -> PictureStill.of(picture)?.let { PlaneMedia(Uri.fromFile(it), loop = false) }
    style.video != null -> PlaneMedia(resource(context, style.video), loop = true)
    else -> style.still?.let { PlaneMedia(resource(context, it), loop = false) }
}

private fun resource(context: Context, id: Int) = Uri.parse("android.resource://${context.packageName}/$id")

/** Which media the video chip has on screen, and those it could not play. */
@Stable
internal class Plane {
    var shown by mutableStateOf<Uri?>(null)
    var failed by mutableStateOf<Set<Uri>>(emptySet())

    /** Whether [media] has to be drawn instead: there is none, or the TV can't play it. */
    fun drawn(media: PlaneMedia?) = media == null || media.uri in failed
}

/**
 * The video chip's picture, under everything drawn after it. A new [media] waits until what is
 * drawn for it has faded in over the old one, so the change never shows as a cut. It stops while
 * an app is open, which hands the TV's video decoder back to that app.
 */
@Composable
internal fun PlaneSurface(plane: Plane, media: PlaneMedia?) {
    val context = LocalContext.current
    val view = remember { PlaneView(context) }
    val player = remember { quietPlayer(context) }
    var started by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                plane.shown = player.currentMediaItem?.localConfiguration?.uri
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) view.fit(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("PlaneSurface", "can't play the backdrop", error)
                player.currentMediaItem?.localConfiguration?.uri?.let { plane.failed = plane.failed + it }
                plane.shown = null
            }
        }
        player.addListener(listener)
        player.setVideoSurfaceView(view.surface)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(media, started) {
        if (!started || media == null || media.uri in plane.failed) {
            player.stop()
            player.clearMediaItems()
            plane.shown = null
            return@LaunchedEffect
        }
        if (plane.shown != null && plane.shown != media.uri) delay(PLANE_SWITCH_MS)
        player.repeatMode = if (media.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.fromUri(media.uri))
        player.prepare()
        player.play()
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}

// Long enough for the Crossfade between two styles to finish
private const val PLANE_SWITCH_MS = 700L

/**
 * How much of what is drawn for [media] still shows over the video chip: all of it until the chip
 * shows [media], then it fades away; at once again whenever the chip lets go of it.
 */
@Composable
internal fun rememberCover(plane: Plane, media: PlaneMedia?): State<Float> {
    val cover = remember { Animatable(1f) }
    val onPlane = media != null && plane.shown == media.uri
    LaunchedEffect(onPlane) { if (onPlane) cover.animateTo(0f, tween(600)) else cover.snapTo(1f) }
    return cover.asState()
}

/**
 * The shade over a profile's own film, which has none of its own: the clock's corner and the band
 * the tiles sit in. A style's film and stills have it painted in.
 */
@Composable
internal fun VideoShade() {
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(0f to Color.Black.copy(alpha = 0.42f), 1f to Color.Transparent, center = Offset(w * 0.14f, h * 0.12f), radius = w * 0.3f),
                radius = w * 0.3f,
                center = Offset(w * 0.14f, h * 0.12f)
            )
            drawRect(
                Brush.verticalGradient(
                    0.4f to Color.Transparent,
                    0.53f to Color.Black.copy(alpha = 0.3f),
                    0.69f to Color.Black.copy(alpha = 0.3f),
                    0.82f to Color.Transparent,
                    startY = 0f,
                    endY = h
                )
            )
        }
    )
}

/**
 * The SurfaceView, centred: it fills the screen when that only trims the edges a little, and
 * shows a phone's upright film whole rather than as a strip, as the video screensaver does.
 */
private class PlaneView(context: Context) : FrameLayout(context) {
    val surface = SurfaceView(context)
    private var aspect = 0f

    init {
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    fun fit(videoAspect: Float) {
        aspect = videoAspect
        place(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { place(w, h) }
    }

    private fun place(w: Int, h: Int) {
        if (aspect <= 0f || w == 0 || h == 0) return
        val (sw, sh) = videoFrame(w, h, aspect)
        val params = surface.layoutParams as LayoutParams
        if (params.width == sw && params.height == sh) return
        surface.layoutParams = LayoutParams(sw, sh, Gravity.CENTER)
    }
}

private fun quietPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        // Only the picture: no sound is decoded, and the TV's audio is left alone
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        volume = 0f
    }
