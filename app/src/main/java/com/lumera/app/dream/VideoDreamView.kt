package com.lumera.app.dream

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.lumera.app.dream.drive.CityDriveView
import java.io.File

/**
 * The screensaver as someone's own video: on a loop, without sound, rising out of black once its
 * first frame is ready. It fills the screen when that only trims the edges a little, and is shown
 * whole otherwise, so a phone's upright video isn't cropped to a strip. A video the TV can't play
 * gives way to the city drive.
 */
class VideoDreamView(context: Context, private val video: File) : FrameLayout(context) {

    private val surface = SurfaceView(context)
    private val curtain = View(context).apply { setBackgroundColor(Color.BLACK) }
    private var player: ExoPlayer? = null
    private var videoAspect = 0f

    init {
        setBackgroundColor(Color.BLACK)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addView(curtain, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width == 0 || videoSize.height == 0) return
            videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            fit(width, height)
        }

        override fun onRenderedFirstFrame() {
            curtain.animate().alpha(0f).setDuration(900L).start()
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w("VideoDreamView", "can't play the screensaver video", error)
            post { fallBack() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (player != null) return
        player = ExoPlayer.Builder(context).build().apply {
            // Only the picture: no sound is decoded, and the TV's audio is left alone
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(listener)
            setVideoSurfaceView(surface)
            setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
            prepare()
            playWhenReady = true
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun onPause() {
        player?.pause()
    }

    fun onResume() {
        player?.play()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fit(w, h) }
    }

    private fun fit(w: Int, h: Int) {
        if (videoAspect <= 0f || w == 0 || h == 0) return
        val (sw, sh) = videoFrame(w, h, videoAspect)
        val params = surface.layoutParams as LayoutParams
        if (params.width == sw && params.height == sh) return
        surface.layoutParams = LayoutParams(sw, sh, Gravity.CENTER)
    }

    private fun release() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
    }

    private fun fallBack() {
        val parent = parent as? ViewGroup ?: return
        val index = parent.indexOfChild(this)
        val params = layoutParams
        release()
        parent.removeView(this)
        parent.addView(CityDriveView(context), index, params)
    }
}

// Filling the screen may draw a video up to this much bigger than showing it whole: 4:3 or 21:9
// on a 16:9 screen still fills it, an upright video does not
private const val MAX_FILL_ZOOM = 1.34f

/** The size to draw a video of [videoAspect] on a [w] by [h] screen, centred; it may overflow to fill. */
internal fun videoFrame(w: Int, h: Int, videoAspect: Float): Pair<Int, Int> {
    val screenAspect = w.toFloat() / h
    val fill = maxOf(videoAspect / screenAspect, screenAspect / videoAspect) <= MAX_FILL_ZOOM
    val wider = videoAspect > screenAspect
    return if (wider == fill) Math.round(h * videoAspect) to h else w to Math.round(w / videoAspect)
}
