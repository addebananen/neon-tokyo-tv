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
 * drawn for it has faded in over the old one, so the change never shows as a cut.
 *
 * While an app is open the film only pauses, so coming back it carries on from the same frame
 * at once instead of loading again from the start; after [PLANE_KEEP_MS] away it lets go of the
 * TV's video decoder for good, and the TV can take it back sooner for the app in front if it
 * needs it. A film that stops moving while it should play is started again.
 */
@Composable
internal fun PlaneSurface(plane: Plane, media: PlaneMedia?) {
    val context = LocalContext.current
    val view = remember { PlaneView(context) }
    val player = remember { quietPlayer(context) }
    var started by remember { mutableStateOf(false) }
    // Goes up to load the film again after the TV took the decoder or the film got stuck
    var reload by remember { mutableStateOf(0) }
    val errors = remember { intArrayOf(0) }
    // The reload last done, and the claim on the video chip this film made last
    val reloaded = remember { intArrayOf(0) }
    val mine = remember { intArrayOf(-1) }
    val claims = PlaneClaim.claims
    val startedNow = remember { booleanArrayOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                plane.shown = player.currentMediaItem?.localConfiguration?.uri
                android.util.Log.i("PlaneSurface", "showing ${plane.shown?.lastPathSegment}")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) view.fit(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("PlaneSurface", "can't play the backdrop", error)
                plane.shown = null
                // Away, the TV may take the decoder back for the app in front: load again on return
                if (!startedNow[0]) {
                    player.stop()
                    return
                }
                // A film that has played before gets a few more tries; one that never plays is drawn instead
                errors[0]++
                if (errors[0] <= PLANE_TRIES) {
                    reload++
                } else {
                    player.currentMediaItem?.localConfiguration?.uri?.let { plane.failed = plane.failed + it }
                }
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
                Lifecycle.Event.ON_START -> {
                    // This film takes the chip: another one of ours, held while away or paused
                    // under a screensaver, lets go of it
                    if (mine[0] != PlaneClaim.claims) {
                        PlaneClaim.claims++
                        mine[0] = PlaneClaim.claims
                    }
                    started = true
                    startedNow[0] = true
                }
                // An app is opening over the home screen: the film holds its frame and gives the
                // decoder and the memory it reads through back to that app at once, not only
                // once it covers the screen
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> {
                    // Back in front without having stopped, as from under a screensaver
                    if (mine[0] != PlaneClaim.claims) {
                        PlaneClaim.claims++
                        mine[0] = PlaneClaim.claims
                    } else if (player.mediaItemCount > 0) {
                        player.play()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    started = false
                    startedNow[0] = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(media, started, reload, claims) {
        if (media == null || media.uri in plane.failed) {
            player.stop()
            player.clearMediaItems()
            plane.shown = null
            return@LaunchedEffect
        }
        val owner = claims == mine[0]
        if (!started || !owner) {
            // An app is in front: hold the frame, and let go of the decoder only after a while, or
            // at once when another film of ours (the screensaver) has taken the chip
            player.pause()
            plane.shown = null
            if (owner) delay(PLANE_KEEP_MS)
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        // Plays only while in front; started but not yet resumed, it shows its frame and waits
        val front = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        fun fresh() {
            player.repeatMode = if (media.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.setMediaItem(MediaItem.fromUri(media.uri))
            player.prepare()
            player.playWhenReady = front
        }
        val loaded = player.currentMediaItem?.localConfiguration?.uri == media.uri &&
            player.playbackState != Player.STATE_IDLE
        if (loaded && reload == reloaded[0]) {
            // Back from an app within the while: on from the same frame
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.playWhenReady = front
            // It shows again within a moment on the new surface; if not, it loads afresh
            var waited = 0L
            while (plane.shown != media.uri && waited < PLANE_BACK_MS) {
                delay(50)
                waited += 50
            }
            if (plane.shown != media.uri) {
                android.util.Log.i("PlaneSurface", "no frame on return, loading again")
                player.stop()
                fresh()
            }
        } else {
            reloaded[0] = reload
            if (plane.shown != null && plane.shown != media.uri) delay(PLANE_SWITCH_MS)
            fresh()
        }
        if (!media.loop) return@LaunchedEffect
        // A film must never freeze: when it has stood still while it should play, it starts over
        var last = -1L
        var still = 0
        while (true) {
            delay(PLANE_CHECK_MS)
            if (!player.playWhenReady) {
                still = 0
                continue
            }
            val at = player.currentPosition
            still = if (player.playbackState != Player.STATE_READY || at == last) still + 1 else 0
            if (player.playbackState == Player.STATE_READY && at != last) errors[0] = 0
            last = at
            if (still >= PLANE_STUCK_CHECKS) {
                android.util.Log.w("PlaneSurface", "the film stood still, starting it again")
                still = 0
                last = -1L
                player.stop()
                player.setMediaItem(MediaItem.fromUri(media.uri))
                player.prepare()
                player.play()
            }
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}

/** Which film last took the video chip: one starting tells the others, held while away, to let go. */
private object PlaneClaim {
    var claims by mutableStateOf(0)
}

// Long enough for the Crossfade between two styles to finish
private const val PLANE_SWITCH_MS = 700L

// How long an app may be in front before the film lets go of the decoder for good
private const val PLANE_KEEP_MS = 30_000L

// How long a film held while away may take to show again before it is loaded afresh
private const val PLANE_BACK_MS = 1_500L

// How often a playing film is checked, and how many checks in a row it may stand still
private const val PLANE_CHECK_MS = 2_000L
private const val PLANE_STUCK_CHECKS = 3

// Tries again after an error, before the painting stands in for good
private const val PLANE_TRIES = 3

/**
 * How much of what is drawn for [media] still shows over the video chip: all of it until the chip
 * shows [media], then it fades away; at once again whenever the chip lets go of it.
 */
@Composable
internal fun rememberCover(plane: Plane, media: PlaneMedia?): State<Float> {
    val cover = remember { Animatable(1f) }
    val onPlane = media != null && plane.shown == media.uri
    // A still is the same picture as its painting, so it can melt in slowly; a film has moved on
    // from the painting, so it takes over quickly
    val fade = if (media?.loop == true) 280 else 600
    LaunchedEffect(onPlane) { if (onPlane) cover.animateTo(0f, tween(fade)) else cover.snapTo(1f) }
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
