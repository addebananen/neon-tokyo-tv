package com.lumera.app.dream

import android.content.Context
import android.graphics.Bitmap
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
    fun roomFor(context: Context): Long = (folder(context).usableSpace - 512L * 1024 * 1024).coerceAtLeast(0)

    /**
     * Makes [upload] the screensaver video if it has a picture the TV can open, and returns a
     * frame of it to show; otherwise deletes it, keeps the video there was, and returns null.
     */
    fun install(context: Context, upload: File): Bitmap? {
        val frame = frameOf(upload)
        if (frame == null || !upload.renameTo(file(context))) {
            upload.delete()
            return null
        }
        return frame
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
