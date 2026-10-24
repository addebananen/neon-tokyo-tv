package com.lumera.app.di

import android.os.SystemClock
import android.util.Log
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cinemeta's posters, backgrounds and logos live on images.metahub.space, which some
 * networks can't reach (it is why Stremio showed blank posters on Adrian's network).
 * The first metahub image triggers a quick reachability check; while the host is down or
 * blocked, metahub images are fetched through the wsrv.nl image cache instead. Metahub is
 * asked again in the background every minute, so a check made before the TV was online, or
 * one slow image, never sticks for the rest of the session. A plain 404 (title without a
 * logo) does not count as unreachable.
 */
class MetahubFallbackInterceptor(okHttpClient: OkHttpClient) : Interceptor {

    private val probeClient = okHttpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
    private val probeLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rechecking = AtomicBoolean(false)
    @Volatile private var reachable: Boolean? = null
    @Volatile private var recheckAt = 0L

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()
        if (!isMetahub(url)) return chain.proceed(request)

        if (isReachable() == false) {
            val cached = chain.proceed(request.newBuilder().data(proxied(url)).build())
            // The image cache can't be reached either: metahub itself is the last try
            return if (cached.isNetworkError()) chain.proceed(request) else cached
        }

        val result = chain.proceed(request)
        if (!result.isNetworkError()) return result
        markUnreachable()
        Log.w(TAG, "metahub image failed, switching to the image cache", (result as ErrorResult).throwable)
        return chain.proceed(request.newBuilder().data(proxied(url)).build())
    }

    private fun ImageResult.isNetworkError() = this is ErrorResult && throwable is IOException

    private suspend fun isReachable(): Boolean? {
        when (reachable) {
            true -> return true
            false -> {
                recheckInBackground()
                return false
            }
            null -> Unit
        }
        return probeLock.withLock {
            reachable ?: probe().also { ok -> if (ok) reachable = true else markUnreachable() }
        }
    }

    private fun markUnreachable() {
        recheckAt = SystemClock.elapsedRealtime() + RECHECK_MS
        reachable = false
    }

    // Once the verdict is a minute old metahub is asked again, without holding up the images
    private fun recheckInBackground() {
        if (SystemClock.elapsedRealtime() < recheckAt || !rechecking.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (probe()) reachable = true else recheckAt = SystemClock.elapsedRealtime() + RECHECK_MS
            } finally {
                rechecking.set(false)
            }
        }
    }

    private suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        val ok = try {
            probeClient.newCall(Request.Builder().url(PROBE_URL).head().build()).execute().use { true }
        } catch (_: IOException) {
            false
        }
        Log.i(TAG, "images.metahub.space reachable: $ok")
        ok
    }

    companion object {
        private const val TAG = "MetahubFallback"
        private const val PROBE_URL = "https://images.metahub.space/"
        private const val RECHECK_MS = 60_000L

        fun isMetahub(url: String) = url.startsWith("https://images.metahub.space/") ||
            url.startsWith("http://images.metahub.space/") ||
            url.startsWith("https://live.metahub.space/")

        fun proxied(url: String) = "https://wsrv.nl/?url=" + URLEncoder.encode(url.substringAfter("://"), "UTF-8")
    }
}
