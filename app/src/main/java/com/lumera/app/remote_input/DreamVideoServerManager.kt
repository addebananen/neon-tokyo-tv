package com.lumera.app.remote_input

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.BindException

/**
 * Runs the [DreamVideoUploadServer] while the screensaver video dialog is open, on the first
 * free port from 8080. The address carries a fresh random token each time.
 */
class DreamVideoServerManager {

    @Volatile
    private var server: DreamVideoUploadServer? = null

    // Set once the dialog is gone, so a server that finishes starting after that stops right away
    @Volatile
    private var closed = false

    companion object {
        private const val PORT_START = 8080
        private const val PORT_END = 8090
        // A phone on weak Wi-Fi can pause for a while in the middle of a big video
        private const val READ_TIMEOUT_MS = 30_000
    }

    /** The phone's page follows this, see [DreamVideoUploadServer.state]. */
    var state: String
        get() = server?.state ?: DreamVideoUploadServer.STATE_WAITING
        set(value) { server?.state = value }

    /** Starts the server; returns where the phone finds it, or null without a network. */
    suspend fun startServer(context: Context, listener: DreamVideoUploadServer.Listener, background: Boolean = false): ServerInfo? =
        withContext(Dispatchers.IO) {
            val ip = NetworkUtils.getLocalIpAddress() ?: return@withContext null
            val token = AvatarServerManager.newToken()
            for (port in PORT_START..PORT_END) {
                try {
                    val videoServer = DreamVideoUploadServer(port, context.applicationContext, token, listener, background)
                    videoServer.start(READ_TIMEOUT_MS)
                    server = videoServer
                    if (closed) {
                        stopServer()
                        return@withContext null
                    }
                    return@withContext ServerInfo(ip, port, "/$token")
                } catch (e: BindException) {
                    continue
                } catch (e: Exception) {
                    if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("DreamVideoServer", "Port binding failed", e)
                    continue
                }
            }
            null
        }

    /** Stops the server, after [graceMs] more so the phone hears how it went. */
    fun stopServer(graceMs: Long = 0L) {
        closed = true
        val running = server ?: return
        server = null
        if (graceMs <= 0L) running.stop() else Handler(Looper.getMainLooper()).postDelayed({ running.stop() }, graceMs)
    }
}
