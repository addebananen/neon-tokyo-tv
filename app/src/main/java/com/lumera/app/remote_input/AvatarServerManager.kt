package com.lumera.app.remote_input

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.BindException
import java.security.SecureRandom

/**
 * Runs the [AvatarUploadServer] for one photo, on the first free port from 8080.
 * The address carries a fresh random token each time.
 */
class AvatarServerManager {

    @Volatile
    private var server: AvatarUploadServer? = null

    // Set once the dialog is gone, so a server that finishes starting after that stops right away
    @Volatile
    private var closed = false

    companion object {
        private const val PORT_START = 8080
        private const val PORT_END = 8090
        private const val TOKEN_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"
        private const val TOKEN_LENGTH = 6
        private val random = SecureRandom()

        fun newToken(): String = String(CharArray(TOKEN_LENGTH) { TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)] })
    }

    /** The phone's page follows this, see [AvatarUploadServer.state]. */
    var state: String
        get() = server?.state ?: AvatarUploadServer.STATE_WAITING
        set(value) { server?.state = value }

    /** Starts the server; returns where the phone finds it, or null without a network. */
    suspend fun startServer(
        context: Context,
        profileName: String,
        listener: AvatarUploadServer.Listener
    ): ServerInfo? = withContext(Dispatchers.IO) {
        val ip = NetworkUtils.getLocalIpAddress() ?: return@withContext null
        val token = newToken()
        for (port in PORT_START..PORT_END) {
            try {
                val avatarServer = AvatarUploadServer(port, context.applicationContext, token, profileName, listener)
                avatarServer.start()
                server = avatarServer
                if (closed) {
                    stopServer()
                    return@withContext null
                }
                return@withContext ServerInfo(ip, port, "/$token")
            } catch (e: BindException) {
                continue
            } catch (e: Exception) {
                if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("AvatarServerManager", "Port binding failed", e)
                continue
            }
        }
        null
    }

    /**
     * Stops the server. After a photo is chosen it keeps answering for [graceMs] more,
     * so the phone hears that it is done before the page loses the TV.
     */
    fun stopServer(graceMs: Long = 0L) {
        closed = true
        val running = server ?: return
        server = null
        if (graceMs <= 0L) running.stop() else Handler(Looper.getMainLooper()).postDelayed({ running.stop() }, graceMs)
    }
}
