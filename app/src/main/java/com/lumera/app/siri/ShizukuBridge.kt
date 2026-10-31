package com.lumera.app.siri

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Starts the Siri Remote bridge through Shizuku. Shizuku is an app the owner installs and starts
 * once on the TV (no PC); it then runs small services for other apps as the shell user — the same
 * user the bridge needs. So when Shizuku is running, Neon can (re)start the bridge itself: on
 * boot, when Neon Home opens, or from the settings page, with nothing to do on a computer.
 *
 * This does not touch adb pairing or keys itself; Shizuku, the owner-installed privileged helper,
 * does the shell part, and Neon only asks it over Shizuku's own API.
 */
object ShizukuBridge {
    private const val TAG = "NeonSiriShizuku"
    private const val PREFS = "siri_shizuku"
    private const val ENABLED = "enabled"
    private const val PERM = 51

    private val userService = Shizuku.UserServiceArgs(ComponentName("com.neontokyo.tv", SiriRunnerService::class.java.name))
        .daemon(false)
        .processNameSuffix("siri")
        .version(1)

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)
    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, on).apply()

    /** Whether Shizuku is installed and running, so it can be asked to do anything at all. */
    fun available(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** Whether Shizuku has already granted Neon access. */
    fun granted(): Boolean = runCatching {
        available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Asks Shizuku for access; the owner approves once on screen. */
    fun requestPermission() = runCatching { if (available()) Shizuku.requestPermission(PERM) }

    /**
     * Binds the shell-uid runner and starts the bridge, if Shizuku is granted and the bridge is
     * not already up. Returns null on success, else a short reason. Blocks up to a few seconds.
     */
    fun startBridge(ctx: Context): String? {
        if (bridgeAnswers()) return null
        if (!available()) return "Shizuku is not running"
        if (!granted()) return "Shizuku has not granted access yet"
        val launch = SiriBridge.LAUNCH.format(ctx.applicationInfo.sourceDir, ctx.packageName)
        val runner = bind() ?: return "could not reach the Shizuku runner"
        return runCatching {
            runner.run(launch)
            Thread.sleep(1500)
            if (bridgeAnswers()) null else "the runner ran but the bridge did not answer"
        }.getOrElse { it.message ?: it.toString() }
    }

    /**
     * Runs one [command] as the shell user through Shizuku and returns its output, or null when
     * Shizuku is not there or has not granted access. Blocks: call it off the main thread.
     */
    fun run(command: String): String? {
        if (!granted()) return null
        val runner = bind() ?: return null
        return runCatching { runner.run(command) }.getOrNull()
    }

    @Volatile private var bound: ISiriRunner? = null
    private val lock = Object()

    private fun bind(): ISiriRunner? {
        bound?.let { if (runCatching { it.asBinder().isBinderAlive }.getOrDefault(false)) return it }
        synchronized(lock) {
            bound?.let { if (runCatching { it.asBinder().isBinderAlive }.getOrDefault(false)) return it }
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    synchronized(lock) {
                        bound = binder?.let { ISiriRunner.Stub.asInterface(it) }
                        lock.notifyAll()
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    synchronized(lock) { bound = null }
                }
            }
            runCatching { Shizuku.bindUserService(userService, conn) }
                .onFailure { Log.w(TAG, "bindUserService failed", it); return null }
            val deadline = System.currentTimeMillis() + 8000
            while (bound == null && System.currentTimeMillis() < deadline) {
                runCatching { lock.wait(500) }
            }
            return bound
        }
    }

    private fun bridgeAnswers(): Boolean = StatusLink.ask("status", 1_500)?.contains("\"version\"") == true
}
