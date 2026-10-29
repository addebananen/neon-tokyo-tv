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
 * it picks a video and sends the file here as it is, straight into the TV's storage. The page
 * sends it in pieces, each to its place in the file, so a phone whose screen goes dark or whose
 * Wi-Fi drops halfway carries on where it was instead of starting again. Every
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

    // The video arriving in pieces: how big it is and how much of it is here
    @Volatile
    private var expected = 0L

    @Volatile
    private var arrived = 0L

    // Which video it is, as the phone names it, so the phone can carry on with the same one later
    @Volatile
    private var expectedId = ""

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
            "/status" -> json("{\"state\":\"$state\",\"received\":$arrived,\"id\":\"$expectedId\",\"room\":${DreamVideo.roomFor(context)}}")
            "/video" -> if (session.method == Method.POST) receiveVideo(session) else notFound()
            "/piece" -> if (session.method == Method.POST) receivePiece(session) else notFound()
            "/awake.mp4" -> asset("dream_video_awake.mp4", "video/mp4")
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
        DreamVideo.uploadFile(context).delete()
        if (!DreamVideo.makeRoom(context, total)) return badRequest(noRoom(total))

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
            android.util.Log.w("DreamVideoUpload", "upload broke off at $received of $total bytes", e)
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

    /**
     * One piece of the video, to go at the place its "x-offset" says in a video of "x-total"
     * bytes. A piece at 0 starts the video afresh. Answers how much of it is here, which is
     * where the next piece goes: after a piece that broke off, the phone asks and carries on.
     */
    @Synchronized
    private fun receivePiece(session: IHTTPSession): Response {
        if (state == STATE_CHECKING) return early(conflict("The TV is still busy with the last video."))
        val offset = session.headers["x-offset"]?.toLongOrNull() ?: return early(badRequest("Your phone didn't say where this piece goes. Try again."))
        val total = session.headers["x-total"]?.toLongOrNull() ?: return early(badRequest("Your phone didn't say how big the video is. Try again."))
        val length = session.headers["content-length"]?.toLongOrNull() ?: return early(badRequest("Your phone didn't say how big the piece is. Try again."))
        val id = idOf(session)
        val upload = DreamVideo.uploadFile(context)
        if (offset == 0L) {
            if (total <= 0L) return early(badRequest("No video arrived. Try again."))
            if (total > MAX_VIDEO_SIZE) return early(badRequest("That video is over 2 GB. Pick a shorter one."))
            upload.delete()
            if (!DreamVideo.makeRoom(context, total)) return early(badRequest(noRoom(total)))
            expected = total
            expectedId = id
            arrived = 0L
            state = STATE_RECEIVING
        } else if (state != STATE_RECEIVING || total != expected || (id.isNotEmpty() && expectedId.isNotEmpty() && id != expectedId)) {
            return early(conflict("The TV lost the start of the video. Send it again."))
        }
        // Not where the video has got to: say where that is, and the phone sends from there
        if (offset != arrived || offset + length > total) return early(placed())
        try {
            val input = session.inputStream
            val buffer = ByteArray(256 * 1024)
            var lastReport = 0L
            java.io.RandomAccessFile(upload, "rw").use { out ->
                out.seek(offset)
                var left = length
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("stopped at ${length - left} of a $length byte piece")
                    out.write(buffer, 0, n)
                    left -= n
                    // What is written stays, so a piece that breaks off goes on from its last byte
                    // rather than starting over: on weak Wi-Fi a whole piece might never make it
                    arrived += n
                    val soFar = arrived
                    val now = SystemClock.uptimeMillis()
                    if (now - lastReport >= 250L) {
                        lastReport = now
                        mainHandler.post { listener.onProgress(soFar, total) }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DreamVideoUpload", "a piece at $offset of $total broke off; the phone carries on from $arrived", e)
            val soFar = arrived
            mainHandler.post { listener.onProgress(soFar, total) }
            return early(placed())
        }
        if (arrived == total) {
            state = STATE_CHECKING
            mainHandler.post {
                listener.onProgress(total, total)
                listener.onVideoReceived(upload)
            }
        }
        return placed()
    }

    /** The phone's name for the video, kept to the characters it encodes it in so it is safe in JSON. */
    private fun idOf(session: IHTTPSession): String =
        session.headers["x-id"].orEmpty().filter { it.isLetterOrDigit() || it in "%._~!*'()-" }.take(200)

    // A piece whose bytes weren't all read leaves the rest on the connection, so it is closed
    private fun early(response: Response): Response = response.apply { addHeader("Connection", "close") }

    private fun noRoom(total: Long): String =
        "The TV has ${DreamVideo.roomFor(context) / (1024 * 1024)} MB free and the video is ${total / (1024 * 1024)} MB. Trim it on your phone, or free up space on the TV."

    private fun placed(): Response = json("{\"state\":\"$state\",\"received\":$arrived}")

    private fun resource(id: Int, mime: String): Response {
        val bytes = context.resources.openRawResource(id).use { it.readBytes() }
        return newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "max-age=86400")
        }
    }

    private fun asset(name: String, mime: String): Response {
        val bytes = context.assets.open(name).use { it.readBytes() }
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
