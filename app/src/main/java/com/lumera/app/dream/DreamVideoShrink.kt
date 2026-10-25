package com.lumera.app.dream

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Makes a video sent from a phone light enough for the TV before it is kept: at most 1080 lines
 * (1920 when it stands upright), 30 frames a second, H.264 at a steady bit rate and no sound. A
 * minute of a phone's 4K film is some 400 MB; shrunk it is about 45. The TV's own video chip
 * does the work, so it takes about as long as the video plays, or less.
 */
@OptIn(UnstableApi::class)
object DreamVideoShrink {
    private const val SHORT_SIDE = 1080
    private const val FPS = 30f
    private const val BITRATE = 6_000_000

    /** Whether [video] is bigger than the TV needs: larger than 1080 lines, too dense, or with sound. */
    fun needed(video: File): Boolean = probe(video)?.let { it.shortSide > SHORT_SIDE || it.bitrate > BITRATE * 3 / 2 || it.sound } ?: false

    /**
     * Writes [input] shrunk to [output], telling [onProgress] how far it is from 0 to 1, and returns
     * whether it worked; on a failure [output] is gone and [input] untouched.
     */
    suspend fun shrink(context: Context, input: File, output: File, onProgress: (Float) -> Unit): Boolean {
        val shape = probe(input) ?: return false
        val rate = if (shape.bitrate > 0) shape.bitrate.coerceIn(1_000_000L, BITRATE.toLong()).toInt() else BITRATE
        output.delete()
        // Transformer wants a thread with a looper, and reports on it
        val done = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val holder = ProgressHolder()
                lateinit var transformer: Transformer
                val tick = object : Runnable {
                    override fun run() {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        handler.postDelayed(this, 500)
                    }
                }
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            // Never more than the video had: a light one stays light
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(rate).build())
                            .build()
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(tick)
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            android.util.Log.w("DreamVideoShrink", "couldn't shrink the video", exportException)
                            handler.removeCallbacks(tick)
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                // The long side follows from the short one; an upright video keeps standing
                val presentation = if (shape.upright) Presentation.createForWidthAndHeight(
                    minOf(shape.width, SHORT_SIDE), minOf(shape.height, SHORT_SIDE * 16 / 9), Presentation.LAYOUT_SCALE_TO_FIT
                ) else Presentation.createForHeight(minOf(shape.height, SHORT_SIDE))
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), listOf(FrameDropEffect.createDefaultFrameDropEffect(FPS), presentation)))
                    .build()
                transformer.start(item, output.path)
                handler.post(tick)
                cont.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacks(tick)
                        transformer.cancel()
                    }
                }
            }
        }
        // Kept only when it came out smaller, which a video that was light already may not
        if (!done || output.length() == 0L || output.length() >= input.length()) output.delete()
        val ok = done && output.exists()
        if (ok) android.util.Log.i("DreamVideoShrink", "shrunk ${shape.width}x${shape.height} from ${input.length() / 1024} kB to ${output.length() / 1024} kB")
        return ok
    }

    private class Shape(val width: Int, val height: Int, val bitrate: Long, val sound: Boolean) {
        val shortSide get() = minOf(width, height)
        val upright get() = height > width
    }

    /** The video's size as it shows (turned the way the phone held it), bit rate and sound. */
    private fun probe(video: File): Shape? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.path)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val turned = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 180 != 0
            Shape(
                width = if (turned) h else w,
                height = if (turned) w else h,
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                sound = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
