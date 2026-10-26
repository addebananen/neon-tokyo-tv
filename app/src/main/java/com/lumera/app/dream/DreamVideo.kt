package com.lumera.app.dream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * The video someone sent from their phone to play as the screensaver. There is one at a time:
 * a new one replaces it only once the TV has checked it really is a video it can open.
 */
object DreamVideo {

    private fun folder(context: Context) = File(context.filesDir, "dream").apply { mkdirs() }

    fun file(context: Context) = File(folder(context), "video")

    /** Where an upload lands while it arrives, next to [file] so moving it there is a rename. */
    fun uploadFile(context: Context) = File(folder(context), "upload.tmp")

    fun exists(context: Context): Boolean = file(context).length() > 0

    /** Bytes free for an upload, keeping some room for the TV itself. */
    fun roomFor(context: Context): Long = (folder(context).usableSpace - RESERVE).coerceAtLeast(0)

    /**
     * Makes room for a video of [bytes] if the TV is short of it, by emptying the app's caches
     * (posters and pictures it can fetch again), and says whether there is room now.
     */
    fun makeRoom(context: Context, bytes: Long): Boolean {
        if (roomFor(context) >= bytes) return true
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() } }
        return roomFor(context) >= bytes
    }

    /** Space left for the TV itself, so a video never fills it to the last byte. */
    private const val RESERVE = 256L * 1024 * 1024

    /** Removes the screensaver video, freeing its space. */
    fun remove(context: Context) {
        file(context).delete()
        uploadFile(context).delete()
    }

    /**
     * Makes [upload] the screensaver video, at [target], if it has a picture the TV can open, and returns a
     * frame of it to show; otherwise deletes it, keeps the video there was, and returns null.
     */
    fun install(context: Context, upload: File, target: File = file(context)): Bitmap? {
        // A TV can play an HDR or Dolby Vision film it can't take a still from: the card then
        // shows a plain night sky instead of a frame
        val frame = frameOf(upload) ?: if (decodable(upload)) nightSky() else null
        if (frame == null || !upload.renameTo(target)) {
            upload.delete()
            return null
        }
        return frame
    }

    /** Whether the TV has a decoder for the video track of [video], at its size and pace. */
    fun decodable(video: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(video.path)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return false
            // The frame rate key can trip findDecoderForFormat on older TVs, so it is left out
            format.setString(MediaFormat.KEY_FRAME_RATE, null)
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.findDecoderForFormat(format) != null ||
                // Dolby Vision on a TV without its decoder still plays as the HEVC inside it
                (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION &&
                    list.findDecoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT))) != null)
        } catch (e: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun nightSky(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 360f, 0xFF2A0F4F.toInt(), 0xFF0D0721.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRect(0f, 0f, 640f, 360f, paint)
        return bitmap
    }

    /** A frame from a second in, scaled for a card, or null when it isn't a video at all. */
    fun frameOf(video: File, width: Int = 640): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.path)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val atUs = minOf(1_000L, duration / 2) * 1_000L
            val frame = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            if (frame.width <= width) frame
            else Bitmap.createScaledBitmap(frame, width, frame.height * width / frame.width, true).also { frame.recycle() }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
