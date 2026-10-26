package com.lumera.app.dream

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
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
import androidx.media3.transformer.EditedMediaItemSequence
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
import kotlin.math.roundToInt

/**
 * Makes a video sent from a phone right for the TV before it is kept, keeping as much of its
 * quality as the TV can use. The TV's video chip shows it at the panel's own resolution with
 * its own picture processing, so 1080 lines is plenty, but every frame counts: a phone's 60
 * frames a second stay 60 (a slow motion film's 120 or 240 come down to 60). An iPhone films in
 * HDR, which on most TVs plays washed out, so that is turned into ordinary colour. The sound
 * goes. A video that needs nothing but that is only copied without its sound, untouched; one
 * that must be made again gets a bit rate to suit its size and pace, never more than it had.
 * The TV's own video chip does the work, so it takes about as long as the video plays, or less.
 *
 * Not every TV can do every part of that: some have no HEVC encoder, some can't turn HDR into
 * ordinary colour on their graphics chip. So it tries the best way first and then plainer ones,
 * and says what went wrong when none of them works.
 */
@OptIn(UnstableApi::class)
object DreamVideoShrink {
    private const val SHORT_SIDE = 1080
    private const val MAX_FPS = 60f

    // For 1080 lines; a smaller video gets less in proportion
    private const val RATE_30 = 8_000_000L
    private const val RATE_60 = 12_000_000L

    /** Whether [video] needs anything done: too large, too fast, too dense, in HDR, or with sound. */
    fun needed(video: File): Boolean = probe(video)?.let { it.sound || it.reencode } ?: false

    /** How it went: [ok] when [output] holds the video made right, else why not, for the log and the phone. */
    class Outcome(val ok: Boolean, val reason: String?)

    /** One way of making the video again: the codec to write and how to bring HDR down. */
    private class Way(val mime: String, val hdrMode: Int, val name: String)

    /**
     * Writes [input] made right to [output], telling [onProgress] how far it is from 0 to 1, trying
     * plainer ways when the TV can't do the best one; on a failure [output] is gone and [input]
     * untouched.
     */
    suspend fun shrink(context: Context, input: File, output: File, onProgress: (Float) -> Unit): Outcome {
        val shape = probe(input) ?: return Outcome(false, "the TV couldn't read the video")
        val ways = if (!shape.reencode) {
            listOf(Way(MimeTypes.VIDEO_H265, Composition.HDR_MODE_KEEP_HDR, "copy"))
        } else {
            val gl = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            val codec = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
            buildList {
                add(Way(MimeTypes.VIDEO_H265, gl, "HEVC"))
                add(Way(MimeTypes.VIDEO_H264, gl, "H.264"))
                // Some TVs' decoders turn HDR into ordinary colour themselves, where the graphics chip can't
                if (shape.hdr) {
                    add(Way(MimeTypes.VIDEO_H265, codec, "HEVC, decoder tone map"))
                    add(Way(MimeTypes.VIDEO_H264, codec, "H.264, decoder tone map"))
                }
            }
        }
        val failures = mutableListOf<String>()
        for (way in ways) {
            val failure = attempt(context, input, output, shape, way, onProgress) ?: break
            failures += "${way.name}: $failure"
        }
        // A light video made again may come out bigger, and is then kept as it was; one in HDR is
        // kept made again whatever its size, for its colours
        val made = failures.size < ways.size && output.length() > 0L
        if (!made || (output.length() >= input.length() && !shape.hdr)) output.delete()
        val ok = output.exists()
        val about = "${shape.width}x${shape.height} at ${shape.fps.roundToInt()} fps${if (shape.hdr) " HDR" else ""}${shape.mime?.let { " $it" } ?: ""}"
        if (ok) android.util.Log.i("DreamVideoShrink", "shrunk $about from ${input.length() / 1024} kB to ${output.length() / 1024} kB")
        else android.util.Log.w("DreamVideoShrink", "kept as it was: $about; ${failures.joinToString("; ").ifEmpty { "no lighter" }}")
        return Outcome(ok, failures.lastOrNull())
    }

    /** Makes [output] from [input] one [way]; null when it worked, otherwise what went wrong. */
    private suspend fun attempt(context: Context, input: File, output: File, shape: Shape, way: Way, onProgress: (Float) -> Unit): String? {
        output.delete()
        // Transformer wants a thread with a looper, and reports on it
        val failure = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
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
                    .apply {
                        // Without these the picture is only copied, as it was
                        if (shape.reencode) {
                            // HEVC keeps more of the picture for the same size; H.264 is the next way
                            setVideoMimeType(way.mime)
                            setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(shape.rate.toInt()).build())
                                    .build()
                            )
                        }
                    }
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(tick)
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            android.util.Log.w("DreamVideoShrink", "couldn't make the video right for the TV (${way.name})", exportException)
                            handler.removeCallbacks(tick)
                            if (cont.isActive) cont.resume(exportException.errorCodeName)
                        }
                    })
                    .build()
                val effects = buildList {
                    if (shape.fps > MAX_FPS + 1) add(FrameDropEffect.createDefaultFrameDropEffect(MAX_FPS))
                    // The long side follows from the short one; an upright video keeps standing
                    if (shape.shortSide > SHORT_SIDE) add(
                        if (shape.upright) Presentation.createForWidthAndHeight(SHORT_SIDE, SHORT_SIDE * 16 / 9, Presentation.LAYOUT_SCALE_TO_FIT)
                        else Presentation.createForHeight(SHORT_SIDE)
                    )
                }
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), effects))
                    .build()
                val composition = Composition.Builder(EditedMediaItemSequence.withVideoFrom(listOf(item)))
                    .setHdrMode(if (shape.hdr && shape.reencode) way.hdrMode else Composition.HDR_MODE_KEEP_HDR)
                    .build()
                try {
                    transformer.start(composition, output.path)
                } catch (e: Exception) {
                    android.util.Log.w("DreamVideoShrink", "couldn't start (${way.name})", e)
                    if (cont.isActive) cont.resume(e.javaClass.simpleName)
                    return@suspendCancellableCoroutine
                }
                handler.post(tick)
                cont.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacks(tick)
                        transformer.cancel()
                    }
                }
            }
        }
        if (failure != null) output.delete()
        return failure
    }

    private class Shape(val width: Int, val height: Int, val fps: Float, val bitrate: Long, val sound: Boolean, val hdr: Boolean, val mime: String?) {
        val shortSide get() = minOf(width, height)
        val upright get() = height > width

        /** The bit rate to make it again at: enough for its size and pace, never more than it had. */
        val rate: Long
            get() {
                val lines = minOf(shortSide, SHORT_SIDE).toFloat() / SHORT_SIDE
                val full = if (minOf(fps, MAX_FPS) > 31f) RATE_60 else RATE_30
                val fit = (full * lines * lines).toLong().coerceAtLeast(2_000_000L)
                return if (bitrate > 0) minOf(fit, bitrate) else fit
            }

        /** Whether the picture itself has to be made again, rather than only copied without sound. */
        val reencode get() = shortSide > SHORT_SIDE || fps > MAX_FPS + 1 || hdr || bitrate > rate * 3 / 2
    }

    /** The video's size as it shows (turned the way the phone held it), pace, bit rate, sound and HDR. */
    private fun probe(video: File): Shape? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.path)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val turned = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 180 != 0
            val track = videoTrack(video)
            val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull() ?: 0f
            val seconds = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloatOrNull() ?: 0f) / 1000f
            val fps = track?.fps?.takeIf { it > 0f } ?: if (frames > 0f && seconds > 0f) frames / seconds else 30f
            Shape(
                width = if (turned) h else w,
                height = if (turned) w else h,
                fps = fps,
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                sound = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                hdr = track?.hdr ?: false,
                mime = track?.mime
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private class Track(val fps: Float, val hdr: Boolean, val mime: String?)

    /** The frame rate and whether it is HDR (PQ or HLG), as the video's own track says. */
    private fun videoTrack(video: File): Track? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(video.path)
            (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?.let { format ->
                    val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrElse { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                    } else 0f
                    val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
                    // An iPhone's Dolby Vision is HDR whatever its track says about its colour
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    val hdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG || mime == MimeTypes.VIDEO_DOLBY_VISION
                    Track(fps, hdr, mime)
                }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }
}
