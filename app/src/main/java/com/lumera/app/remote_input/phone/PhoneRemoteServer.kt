package com.lumera.app.remote_input.phone

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lumera.app.BuildConfig
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The phone remote's page and its few calls, on the home network. See [PhoneRemote].
 *
 *  GET  /                the remote itself (from the app's assets, phone-remote/)
 *  GET  /api/hello       the TV's name, and whether the phone's key is right
 *  POST /api/pair        {pin} → {k}: the code from the pairing screen, for a phone without the QR
 *  GET  /api/events?k=   what the TV is doing, as server-sent events
 *  POST /api/key         {key}: up, down, left, right, ok, back, home, playpause, volup, …
 *  POST /api/text        {text, submit}: into the text field or search page in front
 *  POST /api/search      {q, where}
 *  GET  /api/apps        the TV's apps;  GET /api/icon?pkg=&k= one app's banner
 *  POST /api/open        {pkg}
 *
 * Everything under /api but hello and pair wants the key, in the X-Neon-Key header or as k=.
 */
class PhoneRemoteServer(port: Int, private val context: Context) : NanoHTTPD(port) {

    private val streams = CopyOnWriteArrayList<EventStream>()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return try {
            when {
                uri.startsWith("/api/") -> api(session, uri.removePrefix("/api/"))
                session.method == Method.GET -> asset(uri)
                else -> notFound()
            }
        } catch (e: Exception) {
            Log.w(TAG, "phone remote $uri", e)
            json(Response.Status.INTERNAL_ERROR, JsonObject().apply { addProperty("error", "TV error") })
        }
    }

    // Server-sent events must reach the phone as they happen, never held back to be zipped
    override fun useGzipWhenAccepted(r: Response): Boolean =
        r.mimeType?.startsWith("text/event-stream") != true && super.useGzipWhenAccepted(r)

    private fun api(session: IHTTPSession, call: String): Response {
        when (call) {
            "hello" -> return json(Response.Status.OK, JsonObject().apply {
                addProperty("name", PhoneRemote.name(context))
                addProperty("paired", PhoneRemote.tokenMatches(context, key(session)))
                addProperty("pairing", PhoneRemote.pin != null)
                addProperty("version", BuildConfig.VERSION_NAME)
            })
            "pair" -> {
                if (session.method != Method.POST) return notFound()
                val pin = body(session)?.get("pin")?.asStringOrNull()
                val token = PhoneRemote.pairWith(pin)
                    ?: return json(Response.Status.FORBIDDEN, JsonObject().apply {
                        addProperty("error", if (PhoneRemote.pin == null) "closed" else "wrong")
                    })
                return json(Response.Status.OK, JsonObject().apply { addProperty("k", token) })
            }
        }
        if (!PhoneRemote.tokenMatches(context, key(session))) {
            return json(Response.Status.UNAUTHORIZED, JsonObject().apply { addProperty("error", "pair") })
        }
        return when (call) {
            "events" -> events()
            "state" -> json(Response.Status.OK, PhoneRemote.state())
            "key" -> {
                val name = body(session)?.get("key")?.asStringOrNull() ?: return bad()
                result(onWorker("none") { RemoteActions.key(context, name) })
            }
            "text" -> {
                val b = body(session) ?: return bad()
                val text = b.get("text")?.asStringOrNull() ?: ""
                val submit = b.get("submit")?.asBooleanOrNull() ?: false
                result(onWorker("none") { RemoteActions.text(text.take(500), submit) })
            }
            "search" -> {
                val b = body(session) ?: return bad()
                val q = b.get("q")?.asStringOrNull()?.take(200) ?: return bad()
                val where = b.get("where")?.asStringOrNull() ?: "smart"
                json(Response.Status.OK, onWorker(JsonObject().apply { addProperty("result", "none") }) {
                    RemoteActions.search(context, q, where)
                })
            }
            "apps" -> json(Response.Status.OK, JsonObject().apply {
                add("apps", RemoteActions.apps(context))
            })
            "icon" -> {
                val pkg = session.parameters["pkg"]?.firstOrNull()?.takeIf { PKG.matches(it) } ?: return bad()
                val bytes = RemoteActions.icon(context, pkg) ?: return notFound()
                newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                    addHeader("Cache-Control", "private, max-age=86400")
                }
            }
            "open" -> {
                val pkg = body(session)?.get("pkg")?.asStringOrNull()?.takeIf { PKG.matches(it) } ?: return bad()
                result(onWorker("none") { RemoteActions.open(context, pkg) })
            }
            else -> notFound()
        }
    }

    /** Runs [block] on the remote's own thread, so presses happen in the order they were made. */
    private fun <T> onWorker(fallback: T, block: () -> T): T {
        val out = AtomicReference(fallback)
        val done = CountDownLatch(1)
        PhoneRemote.worker.post {
            try {
                out.set(block())
            } catch (e: Exception) {
                Log.w(TAG, "phone remote action", e)
            } finally {
                done.countDown()
            }
        }
        done.await(3, TimeUnit.SECONDS)
        return out.get()
    }

    // ── Events ──

    private fun events(): Response {
        // A phone that comes and goes leaves streams behind; only the newest few stay
        while (streams.size >= MAX_STREAMS) streams.firstOrNull()?.close()
        val stream = EventStream()
        streams.add(stream)
        PhoneRemote.phonesChanged(streams.size)
        stream.send("state", PhoneRemote.state().toString())
        return newChunkedResponse(Response.Status.OK, "text/event-stream", stream).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("X-Accel-Buffering", "no")
        }
    }

    fun broadcast(event: String, data: JsonObject) {
        if (streams.isEmpty()) return
        val text = data.toString()
        streams.forEach { it.send(event, text) }
    }

    fun dropAll() {
        streams.forEach { it.close() }
    }

    override fun stop() {
        dropAll()
        super.stop()
    }

    /**
     * What NanoHTTPD reads the event stream from: blocks until there is something to send, and
     * sends a comment every 15 s so a phone that went away is noticed (the write fails) and dropped.
     */
    private inner class EventStream : InputStream() {
        private val queue = LinkedBlockingQueue<ByteArray>()
        private var current: ByteArray = "retry: 1500\n\n".toByteArray()
        private var at = 0
        @Volatile
        private var closed = false

        fun send(event: String, data: String) {
            if (!closed) queue.offer("event: $event\ndata: $data\n\n".toByteArray())
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (at >= current.size) {
                if (closed) return -1
                val next = try {
                    queue.poll(15, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    return -1
                }
                current = next ?: PING
                at = 0
            }
            val n = minOf(len, current.size - at)
            System.arraycopy(current, at, b, off, n)
            at += n
            return n
        }

        override fun available(): Int = current.size - at

        override fun close() {
            if (closed) return
            closed = true
            queue.offer(ByteArray(0))
            streams.remove(this)
            PhoneRemote.phonesChanged(streams.size)
        }
    }

    // ── The page ──

    private fun asset(uri: String): Response {
        val path = when (uri) {
            "/", "", "/index.html" -> "index.html"
            else -> uri.removePrefix("/")
        }
        if (!SAFE_PATH.matches(path) || path.contains("..")) return notFound()
        val bytes = try {
            context.assets.open("$ASSETS/$path").use { it.readBytes() }
        } catch (e: IOException) {
            return notFound()
        }
        val type = when (path.substringAfterLast('.', "")) {
            "html" -> "text/html; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "woff2" -> "font/woff2"
            "webmanifest", "json" -> "application/manifest+json"
            else -> "application/octet-stream"
        }
        return newFixedLengthResponse(Response.Status.OK, type, ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            // The page itself is always asked for again, so an updated TV hands out its new remote
            addHeader("Cache-Control", if (path == "index.html") "no-cache" else "public, max-age=3600")
            addHeader("X-Content-Type-Options", "nosniff")
            if (path == "index.html") addHeader("Referrer-Policy", "no-referrer")
        }
    }

    // ── Helpers ──

    private fun key(session: IHTTPSession): String? =
        session.headers["x-neon-key"] ?: session.parameters["k"]?.firstOrNull()

    private fun body(session: IHTTPSession): JsonObject? {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return null
        if (length <= 0 || length > 16_384) return null
        val bytes = ByteArray(length)
        var read = 0
        val input = session.inputStream
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n <= 0) break
            read += n
        }
        return runCatching { JsonParser.parseString(String(bytes, 0, read, Charsets.UTF_8)).asJsonObject }.getOrNull()
    }

    private fun com.google.gson.JsonElement.asStringOrNull(): String? = runCatching { if (isJsonNull) null else asString }.getOrNull()
    private fun com.google.gson.JsonElement.asBooleanOrNull(): Boolean? = runCatching { asBoolean }.getOrNull()

    private fun result(outcome: String) = json(Response.Status.OK, JsonObject().apply { addProperty("result", outcome) })

    private fun json(status: Response.Status, body: JsonObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString()).apply {
            addHeader("Cache-Control", "no-store")
        }

    private fun bad() = json(Response.Status.BAD_REQUEST, JsonObject().apply { addProperty("error", "bad request") })

    private fun notFound(): Response = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

    companion object {
        private const val TAG = "PhoneRemote"
        private const val ASSETS = "phone-remote"
        private const val MAX_STREAMS = 6
        private val PING = ": ping\n\n".toByteArray()
        private val SAFE_PATH = Regex("[A-Za-z0-9._/-]{1,80}")
        private val PKG = Regex("[A-Za-z0-9._]{1,120}")
    }
}
