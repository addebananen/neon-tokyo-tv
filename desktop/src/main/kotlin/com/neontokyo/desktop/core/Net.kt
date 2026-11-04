package com.neontokyo.desktop.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Collections

/** Moves bytes for the core: it builds every address and reads every answer. */
object Net {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private fun request(address: String) = HttpRequest.newBuilder(URI.create(address))
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", "NeonTokyo-Windows/${AppInfo.version}")
        .GET()
        .build()

    suspend fun text(address: String): String {
        val response = client.sendAsync(request(address), HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} for $address")
        return response.body()
    }

    /** An answer: its HTTP status and body. */
    class Answer(val status: Int, val body: String) {
        val ok get() = status in 200..299
    }

    /** A JSON call (Stremio's API, Trakt, GitHub): any status comes back, only no network throws. */
    suspend fun call(address: String, body: String? = null, headers: Map<String, String> = emptyMap()): Answer {
        val b = HttpRequest.newBuilder(URI.create(address))
            .timeout(Duration.ofSeconds(25))
            .header("User-Agent", "NeonTokyo-Windows/${AppInfo.version}")
        if (body != null) b.header("Content-Type", "application/json")
        headers.forEach { (k, v) -> b.setHeader(k, v) }
        if (body != null) b.POST(HttpRequest.BodyPublishers.ofString(body)) else b.GET()
        val r = client.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).await()
        return Answer(r.statusCode(), r.body() ?: "")
    }

    /** Downloads to [file], telling [progress] how far (0..1) as it goes. */
    suspend fun download(address: String, file: java.io.File, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val r = client.send(
            HttpRequest.newBuilder(URI.create(address)).header("User-Agent", "NeonTokyo-Windows/${AppInfo.version}").GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        if (r.statusCode() !in 200..299) error("Download answered ${r.statusCode()}")
        val total = r.headers().firstValueAsLong("Content-Length").orElse(-1L)
        var done = 0L
        r.body().use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
    }

    suspend fun bytes(address: String): ByteArray? = runCatching {
        val response = client.sendAsync(request(address), HttpResponse.BodyHandlers.ofByteArray()).await()
        if (response.statusCode() in 200..399) response.body() else null
    }.getOrNull()
}

/**
 * Posters and backgrounds. Like the Android app's MetahubFallbackInterceptor, a picture from
 * images.metahub.space that fails is fetched again through wsrv.nl.
 */
object Images {
    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 300
        }
    )
    private val gate = Semaphore(8)
    @Volatile private var metahubDown = false

    private val tints: MutableMap<String, Color> = Collections.synchronizedMap(HashMap())

    fun cached(address: String): ImageBitmap? = cache[address]

    /** The picture's own neon: its most colourful tone, lifted to glow. Known once loaded. */
    fun tint(address: String?): Color? = address?.let { tints[it] }

    suspend fun load(address: String): ImageBitmap? {
        cache[address]?.let { return it }
        val image = gate.withPermit {
            val metahub = "metahub.space" in address
            var bytes = if (metahub && metahubDown) null else Net.bytes(address)
            if (bytes == null && metahub) {
                metahubDown = true
                bytes = Net.bytes("https://wsrv.nl/?url=" + URLEncoder.encode(address, "UTF-8"))
            }
            bytes?.let { decode(address, it) }
        }
        if (image != null) cache[address] = image
        return image
    }

    private suspend fun decode(address: String, bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
            runCatching { tints[address] = tintOf(img) }
            img.toComposeImageBitmap()
        }.getOrNull()
    }

    private fun tintOf(img: org.jetbrains.skia.Image): Color {
        val n = 16
        val small = org.jetbrains.skia.Bitmap()
        small.allocN32Pixels(n, n)
        org.jetbrains.skia.Canvas(small).drawImageRect(img, org.jetbrains.skia.Rect.makeWH(n.toFloat(), n.toFloat()))
        val px = small.readPixels() ?: return Color(0xFFFF3D9A)
        var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
        val hsv = FloatArray(3)
        for (i in 0 until n * n) {
            // N32 is BGRA in memory on Windows and Linux
            val bb = px[i * 4].toInt() and 0xFF
            val gg = px[i * 4 + 1].toInt() and 0xFF
            val rr = px[i * 4 + 2].toInt() and 0xFF
            java.awt.Color.RGBtoHSB(rr, gg, bb, hsv)
            val w = (hsv[1] * hsv[1] * hsv[2]).toDouble() + 0.002
            r += rr * w; g += gg * w; b += bb * w; weight += w
        }
        java.awt.Color.RGBtoHSB((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsv)
        val rgb = java.awt.Color.HSBtoRGB(hsv[0], hsv[1].coerceAtLeast(0.55f).coerceAtMost(0.9f), hsv[2].coerceAtLeast(0.92f))
        return Color(rgb or (0xFF shl 24))
    }
}

object AppInfo {
    val version: String by lazy {
        AppInfo::class.java.getResource("/neon-version.txt")?.readText()?.trim() ?: "0.2.0-dev"
    }

    /** When CI made this build (ms since 1970), so the updater knows which releases are newer. */
    val builtAtMs: Long by lazy {
        AppInfo::class.java.getResource("/neon-built.txt")?.readText()?.trim()?.toLongOrNull() ?: 0L
    }

    /** The Trakt app's id, written in by CI from the repository's secret. */
    val traktClientId: String by lazy {
        AppInfo::class.java.getResource("/trakt-client.txt")?.readText()?.trim().orEmpty()
    }

    /** The Trakt app's secret, when its Trakt app still has one; null otherwise. */
    val traktClientSecret: String? by lazy {
        AppInfo::class.java.getResource("/trakt-secret.txt")?.readText()?.trim()?.ifEmpty { null }
    }
}

/**
 * Cinemeta's catalogs often leave out the wide picture and the logo; for an IMDb id, metahub
 * has both, as the Android app uses.
 */
object Art {
    fun background(id: String, given: String?): String? =
        given ?: id.takeIf { it.startsWith("tt") }?.let { "https://images.metahub.space/background/medium/${it.substringBefore(':')}/img" }

    fun logo(id: String, given: String?): String? =
        given ?: id.takeIf { it.startsWith("tt") }?.let { "https://images.metahub.space/logo/medium/${it.substringBefore(':')}/img" }
}
