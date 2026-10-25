package com.lumera.app.remote_input

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lumera.app.R
import com.lumera.app.dream.DreamVideo
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

/**
 * Serves the page a phone opens from the QR code in Settings › Personalization › Screensaver:
 * it picks a video and sends the file here as it is, straight into the TV's storage. Every
 * address starts with a random [token] that only the QR code carries. The page follows [state].
 */
class DreamVideoUploadServer(
    port: Int,
    private val context: Context,
    private val token: String,
    private val listener: Listener,
    /** For a home profile's background rather than the screensaver: the page says so. */
    private val background: Boolean = false
) : NanoHTTPD(port) {

    interface Listener {
        /** The page was opened on a phone. */
        fun onPhoneConnected()

        /** How much of the video has arrived; called on the main thread a few times a second. */
        fun onProgress(received: Long, total: Long)

        /** The whole file arrived in [file]; called on the main thread. */
        fun onVideoReceived(file: File)

        /** The phone stopped sending halfway; called on the main thread. */
        fun onUploadFailed()
    }

    /** What the phone shows: waiting, receiving, checking, done or error. */
    @Volatile
    var state: String = STATE_WAITING

    private val mainHandler = Handler(Looper.getMainLooper())
    private val base = "/$token"
    private val page: String by lazy {
        context.assets.open("dream_video_upload.html").bufferedReader().use { it.readText() }
            .replace("{{BASE}}", base)
            .replace("<body>", if (background) "<body class=\"background\">" else "<body>")
    }

    companion object {
        const val STATE_WAITING = "waiting"
        const val STATE_RECEIVING = "receiving"
        const val STATE_CHECKING = "checking"
        const val STATE_DONE = "done"
        const val STATE_ERROR = "error"
        const val MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/ping") return DisconnectBanner.pingResponse()
        if (uri != base && !uri.startsWith("$base/")) return notFound()
        return when (uri.removePrefix(base)) {
            "", "/" -> if (session.method == Method.GET) servePage() else notFound()
            "/status" -> json("{\"state\":\"$state\"}")
            "/video" -> if (session.method == Method.POST) receiveVideo(session) else notFound()
            "/skyline.webp" -> resource(R.drawable.nt_skyline, "image/webp")
            "/kanit-medium.ttf" -> resource(R.font.kanit_medium, "font/ttf")
            "/kanit-semibold-italic.ttf" -> resource(R.font.kanit_semibold_italic, "font/ttf")
            else -> notFound()
        }
    }

    private fun servePage(): Response {
        mainHandler.post { listener.onPhoneConnected() }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page).apply {
            addHeader("Cache-Control", "no-store")
        }
    }

    // The body is the file itself, copied to storage as it comes rather than held in memory
    @Synchronized
    private fun receiveVideo(session: IHTTPSession): Response {
        if (state == STATE_RECEIVING || state == STATE_CHECKING) return conflict("The TV is still busy with the last video.")
        val total = session.headers["content-length"]?.toLongOrNull()
            ?: return badRequest("Your phone didn't say how big the video is. Try again.")
        if (total <= 0L) return badRequest("No video arrived. Try again.")
        if (total > MAX_VIDEO_SIZE) return badRequest("That video is over 2 GB. Pick a shorter one.")
        if (total > DreamVideo.roomFor(context)) return badRequest("The TV doesn't have room for that video. Pick a shorter one.")

        state = STATE_RECEIVING
        val upload = DreamVideo.uploadFile(context)
        var received = 0L
        try {
            val input = session.inputStream
            val buffer = ByteArray(256 * 1024)
            var lastReport = 0L
            upload.outputStream().use { out ->
                while (received < total) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), total - received).toInt())
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    received += n
                    val now = SystemClock.uptimeMillis()
                    if (now - lastReport >= 250L) {
                        lastReport = now
                        val soFar = received
                        mainHandler.post { listener.onProgress(soFar, total) }
                    }
                }
            }
            if (received < total) throw IOException("stopped at $received of $total")
        } catch (e: Exception) {
            if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("DreamVideoUpload", "upload broke off", e)
            upload.delete()
            state = STATE_WAITING
            mainHandler.post { listener.onUploadFailed() }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "The video stopped arriving. Try again.")
        }
        state = STATE_CHECKING
        mainHandler.post {
            listener.onProgress(total, total)
            listener.onVideoReceived(upload)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
    }

    private fun resource(id: Int, mime: String): Response {
        val bytes = context.resources.openRawResource(id).use { it.readBytes() }
        return newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "max-age=86400")
        }
    }

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body).apply {
            addHeader("Cache-Control", "no-store")
        }

    private fun badRequest(message: String): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, message)

    private fun conflict(message: String): Response =
        newFixedLengthResponse(Response.Status.CONFLICT, MIME_PLAINTEXT, message)

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
}
