package com.lumera.app.launcher

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * A picture sent from a phone, made into a one frame film so the TV's video chip can show it at
 * the panel's own resolution, as the styles' stills are (see [PlaneSurface]). It is made once,
 * next to the picture, the first time the picture is shown; until then the picture is drawn.
 */
@OptIn(UnstableApi::class)
internal object PictureStill {
    private val making = mutableSetOf<String>()

    /** Counts the stills made, so a backdrop waiting for one looks again. */
    var made by mutableIntStateOf(0)
        private set

    /** The still made from [picture], if there is one yet. */
    fun of(picture: String): File? = still(picture).takeIf { it.length() > 0 }

    /** Deletes [picture]'s still along with it. */
    fun delete(picture: String) {
        still(picture).delete()
    }

    fun still(picture: String) = File("$picture.mp4")

    /** Makes [picture]'s still unless it has one or it is being made; a failure leaves the picture drawn. */
    suspend fun make(context: Context, picture: String) {
        if (of(picture) != null || !synchronized(making) { making.add(picture) }) return
        try {
            val out = still(picture)
            val partial = File("$picture.part.mp4")
            partial.delete()
            if (export(context.applicationContext, File(picture), partial) && partial.length() > 0 && partial.renameTo(out)) {
                android.util.Log.i("PictureStill", "made ${out.name} (${out.length() / 1024} kB)")
                made++
            } else {
                partial.delete()
            }
        } finally {
            synchronized(making) { making.remove(picture) }
        }
    }

    private suspend fun export(context: Context, input: File, output: File): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H265)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        // One frame, so a high rate costs little and keeps the photo's fine detail
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(STILL_BITRATE).build())
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        android.util.Log.w("PictureStill", "couldn't make a still of the picture", exportException)
                        if (cont.isActive) cont.resume(false)
                    }
                })
                .build()
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(Uri.fromFile(input)).setImageDurationMs(1_000).build()
            ).setFrameRate(1).build()
            transformer.start(item, output.path)
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
        }
    }

    private const val STILL_BITRATE = 20_000_000
}
