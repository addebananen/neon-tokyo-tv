package com.neontokyo.desktop.core

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

    fun cached(address: String): ImageBitmap? = cache[address]

    suspend fun load(address: String): ImageBitmap? {
        cache[address]?.let { return it }
        val image = gate.withPermit {
            val metahub = "metahub.space" in address
            var bytes = if (metahub && metahubDown) null else Net.bytes(address)
            if (bytes == null && metahub) {
                metahubDown = true
                bytes = Net.bytes("https://wsrv.nl/?url=" + URLEncoder.encode(address, "UTF-8"))
            }
            bytes?.let { decode(it) }
        }
        if (image != null) cache[address] = image
        return image
    }

    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }
}

object AppInfo {
    val version: String by lazy {
        AppInfo::class.java.getResource("/neon-version.txt")?.readText()?.trim() ?: "0.2.0-dev"
    }
}
