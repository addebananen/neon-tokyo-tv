package com.lumera.app.di

import android.util.Log
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Cinemeta's posters, backgrounds and logos live on images.metahub.space, which some
 * networks can't reach (it is why Stremio showed blank posters on Adrian's network).
 * The first metahub image triggers a quick reachability check; if the host is down or
 * blocked, metahub images are fetched through the wsrv.nl image cache instead for the
 * rest of the session. A plain 404 (title without a logo) does not count as unreachable.
 */
class MetahubFallbackInterceptor(okHttpClient: OkHttpClient) : Interceptor {

    private val probeClient = okHttpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
    private val probeLock = Mutex()
    @Volatile private var reachable: Boolean? = null

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()
        if (!isMetahub(url)) return chain.proceed(request)

        if (isReachable() == false) return chain.proceed(request.newBuilder().data(proxied(url)).build())

        val result = chain.proceed(request)
        if (result is ErrorResult && result.throwable is IOException) {
            reachable = false
            Log.w(TAG, "metahub image failed, switching to the image cache", result.throwable)
            return chain.proceed(request.newBuilder().data(proxied(url)).build())
        }
        return result
    }

    private suspend fun isReachable(): Boolean? {
        reachable?.let { return it }
        return probeLock.withLock {
            reachable ?: withContext(Dispatchers.IO) {
                val ok = try {
                    probeClient.newCall(Request.Builder().url(PROBE_URL).head().build()).execute().use { true }
                } catch (_: IOException) {
                    false
                }
                Log.i(TAG, "images.metahub.space reachable: $ok")
                ok
            }.also { reachable = it }
        }
    }

    companion object {
        private const val TAG = "MetahubFallback"
        private const val PROBE_URL = "https://images.metahub.space/"

        fun isMetahub(url: String) = url.startsWith("https://images.metahub.space/") ||
            url.startsWith("http://images.metahub.space/") ||
            url.startsWith("https://live.metahub.space/")

        fun proxied(url: String) = "https://wsrv.nl/?url=" + URLEncoder.encode(url.substringAfter("://"), "UTF-8")
    }
}
