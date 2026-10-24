package com.lumera.app.remote_input

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.lumera.app.R
import fi.iki.elonen.NanoHTTPD

/**
 * Serves the page a phone opens from the QR code on the TV: it takes a selfie or picks a
 * photo, fits it in the circle and sends it here. Every address starts with a random
 * [token] that only the QR code carries, so nobody else on the network can send a photo.
 * The page follows [state], so the phone shows what the TV is doing with the photo.
 */
class AvatarUploadServer(
    port: Int,
    private val context: Context,
    private val token: String,
    profileName: String,
    private val listener: Listener
) : NanoHTTPD(port) {

    interface Listener {
        /** The page was opened on a phone. */
        fun onPhoneConnected()

        /** A photo arrived; called on the main thread. */
        fun onPhotoReceived(bytes: ByteArray)
    }

    /** What the phone shows: waiting, received, preview, done, retry or error. */
    @Volatile
    var state: String = STATE_WAITING

    private val mainHandler = Handler(Looper.getMainLooper())
    private val base = "/$token"
    private val page: String by lazy {
        context.assets.open("photo_upload.html").bufferedReader().use { it.readText() }
            .replace("{{BASE}}", base)
            .replace("{{NAME}}", escapeHtml(profileName.ifBlank { "you" }))
    }

    companion object {
        const val STATE_WAITING = "waiting"
        const val STATE_RECEIVED = "received"
        const val STATE_PREVIEW = "preview"
        const val STATE_DONE = "done"
        const val STATE_RETRY = "retry"
        const val STATE_ERROR = "error"
        private const val MAX_IMAGE_SIZE = 5 * 1024 * 1024 // 5 MB

        fun escapeHtml(text: String): String = buildString(text.length) {
            for (c in text) when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/ping") return DisconnectBanner.pingResponse()
        if (uri != base && !uri.startsWith("$base/")) return notFound()
        return when (uri.removePrefix(base)) {
            "", "/" -> if (session.method == Method.GET) servePage() else notFound()
            "/status" -> json("{\"state\":\"$state\"}")
            "/photo" -> if (session.method == Method.POST) receivePhoto(session) else notFound()
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

    private fun receivePhoto(session: IHTTPSession): Response {
        return try {
            session.parseBody(mutableMapOf())
            val base64Image = session.parms["image"]
            if (base64Image.isNullOrBlank()) return badRequest("No photo arrived. Try again.")
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            if (imageBytes.size > MAX_IMAGE_SIZE) return badRequest("That photo is too big. Try another one.")
            if (!isValidImage(imageBytes)) return badRequest("That file isn't a photo. Try another one.")
            state = STATE_RECEIVED
            mainHandler.post { listener.onPhotoReceived(imageBytes) }
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("AvatarUploadServer", "Error handling upload", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "The TV couldn't take that photo. Try again.")
        }
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

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

    private fun isValidImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        // WebP: RIFF....WEBP
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()) return true
        return false
    }
}
