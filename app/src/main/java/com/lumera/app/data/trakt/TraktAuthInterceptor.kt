package com.lumera.app.data.trakt

import android.util.Log
import com.lumera.app.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktAuthInterceptor @Inject constructor(
    private val traktAuthManager: TraktAuthManager
) : Interceptor {

    companion object {
        private const val TAG = "TraktAuthInterceptor"
    }

    // Fix #14: prevent hammering the refresh endpoint
    @Volatile private var refreshAttemptedForRequest = false

    override fun intercept(chain: Interceptor.Chain): Response {
        var token = traktAuthManager.getAccessToken()

        // Fix #7: proactively refresh if token is expired or about to expire
        if (token != null && traktAuthManager.needsRefresh) {
            Log.d(TAG, "Token expiring soon, proactive refresh")
            val newToken = runBlocking { traktAuthManager.refreshAccessToken() }
            if (newToken != null) {
                token = newToken
            }
        }

        val response = chain.proceed(buildRequest(chain.request(), token))

        // If we get a 401, try refreshing the token and retry once
        if (response.code == 401 && token != null && !refreshAttemptedForRequest) {
            Log.d(TAG, "Got 401, attempting token refresh")
            refreshAttemptedForRequest = true
            response.close()

            val newToken = runBlocking { traktAuthManager.refreshAccessToken() }
            refreshAttemptedForRequest = false

            if (newToken != null) {
                Log.d(TAG, "Token refreshed, retrying request")
                return chain.proceed(buildRequest(chain.request(), newToken))
            } else {
                Log.w(TAG, "Token refresh failed")
                // Return a new response since we closed the original
                return chain.proceed(buildRequest(chain.request(), token))
            }
        }

        return response
    }

    private fun buildRequest(original: okhttp3.Request, token: String?): okhttp3.Request {
        val builder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID)
            .header("User-Agent", "Lumera/${BuildConfig.VERSION_NAME}")

        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }

        return builder.build()
    }
}
