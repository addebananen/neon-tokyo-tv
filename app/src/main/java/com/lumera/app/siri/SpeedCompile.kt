package com.lumera.app.siri

import android.content.Context
import android.os.Build
import android.util.Log
import kotlin.concurrent.thread

/**
 * Has the TV compile Neon ahead of time right after an update.
 *
 * An app put on by hand comes with its baseline profile, but the TV only compiles it in its
 * nightly maintenance; until then Neon Home and the app run interpreted and start and scroll
 * slower. Adrian updates often, so that was most of the time. Where Shizuku is up and has said
 * yes (as for the Siri Remote), the shell asks for the compile at once, once per version, a while
 * after the home screen opened so it never competes with it. Without Shizuku nothing happens and
 * the TV does it overnight as before.
 */
object SpeedCompile {
    private const val TAG = "NeonSpeedCompile"
    private const val PREFS = "speed_compile"
    private const val DONE_VERSION = "done_version"
    private const val WAIT_MS = 45_000L

    @Volatile private var running = false

    fun maybeRun(context: Context) {
        val app = context.applicationContext
        val version = versionOf(app) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(DONE_VERSION, -1L) == version || running) return
        running = true
        thread(name = "speed-compile", isDaemon = true) {
            try {
                Thread.sleep(WAIT_MS)
                if (!ShizukuBridge.granted()) return@thread
                val out = ShizukuBridge.run("cmd package compile -m speed-profile -f ${app.packageName}") ?: return@thread
                Log.i(TAG, "compile for version $version: ${out.trim().take(200)}")
                if (out.contains("Success", ignoreCase = true)) prefs.edit().putLong(DONE_VERSION, version).apply()
            } catch (e: Exception) {
                Log.w(TAG, "compile not done", e)
            } finally {
                running = false
            }
        }
    }

    private fun versionOf(context: Context): Long? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrNull()
}
