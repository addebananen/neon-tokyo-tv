package com.lumera.app.launcher

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A style's film on a loop, without sound, over its still [poster], which shows until the first
 * frame is ready and stays if the TV can't play the film. It pauses while an app is open. The
 * film has no shade of its own, so the clock's corner and the tile band are darkened here.
 */
@Composable
internal fun VideoBackdrop(video: Int, poster: Int?) {
    val context = LocalContext.current
    val shown = remember(video) { Animatable(0f) }
    var failed by remember(video) { mutableStateOf(false) }
    val player = remember(video) { loopingPlayer(context, video, onError = { failed = true }) }
    var firstFrame by remember(video) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(firstFrame, failed) {
        if (firstFrame && !failed) shown.animateTo(1f, tween(700)) else if (failed) shown.snapTo(0f)
    }
    Box(Modifier.fillMaxSize()) {
        if (poster != null) PaintingBackdrop(poster)
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }
        )
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val w = size.width
                val h = size.height
                // The clock's corner
                drawCircle(
                    Brush.radialGradient(0f to Color.Black.copy(alpha = 0.42f), 1f to Color.Transparent, center = Offset(w * 0.14f, h * 0.12f), radius = w * 0.3f),
                    radius = w * 0.3f,
                    center = Offset(w * 0.14f, h * 0.12f)
                )
                // The band the tiles sit in
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
}

private fun loopingPlayer(context: Context, video: Int, onError: () -> Unit): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        // Only the picture: no sound is decoded, and the TV's audio is left alone
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("VideoBackdrop", "can't play the style's film", error)
                onError()
            }
        })
        setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$video")))
        prepare()
        playWhenReady = true
    }
