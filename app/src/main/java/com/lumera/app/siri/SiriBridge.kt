package com.lumera.app.siri

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import java.io.File
import kotlin.system.exitProcess

/**
 * The Siri Remote bridge. Runs on the TV as the shell user, started over ADB:
 *
 *   CLASSPATH=$(pm path com.neontokyo.tv | cut -d: -f2) setsid app_process /system/bin \
 *       com.lumera.app.siri.SiriBridge com.neontokyo.tv
 *
 * It keeps the Bluetooth link to the remote ([SiriLink]), lets the Rust engine turn reports into
 * keys ([SiriNative]) and injects those keys into whatever is on screen, so the remote works in
 * every app, on Neon Home and over the consoles alike. [SiriStatusServer] lets Neon Tokyo's
 * settings page see and change it.
 */
object SiriBridge : SiriLink.Listener {
    private const val TAG = "NeonSiri"
    const val VERSION = 1

    private lateinit var handler: Handler
    private lateinit var link: SiriLink
    var config = SiriConfig.load()
        private set
    private var engine = 0L
    private var generation = 0
    private val downTimes = HashMap<Int, Long>()
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile var remoteName = ""
        private set
    @Volatile var connected = false
        private set
    @Volatile var battery = -1
        private set
    @Volatile var problem = ""
        private set
    @Volatile var keysSent = 0L
        private set
    val linkState: String get() = if (::link.isInitialized) link.state else "starting"

    @JvmStatic
    fun main(args: Array<String>) {
        val pkg = args.firstOrNull() ?: "com.neontokyo.tv"
        VoiceRelay.packageName = pkg
        Looper.prepareMainLooper()
        handler = Handler(Looper.getMainLooper())
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e(TAG, "bridge crashed", e)
            System.err.println("bridge crashed: $e")
            exitProcess(1)
        }
        loadEngine(pkg)
        if (args.getOrNull(1) == "check") {
            check()
            exitProcess(0)
        }
        debug = args.getOrNull(1) == "debug"
        if (!SiriStatusServer.start(this)) {
            System.err.println("another bridge is already running")
            exitProcess(0)
        }
        println("Neon Tokyo Siri Remote bridge $VERSION running (pid ${android.os.Process.myPid()})")
        updateWakeLock()
        link = SiriLink(handler, this)
        link.start(config.address)
        Looper.loop()
    }

    private var debug = false

    /** `... SiriBridge <package> check`: what this TV lets the bridge do, without starting it. */
    private fun check() {
        val ctx = ShellEnv.context
        println("uid ${android.os.Process.myUid()} package ${ctx.opPackageName}")
        for (p in listOf("android.permission.BLUETOOTH_PRIVILEGED", "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN", "android.permission.INJECT_EVENTS", "android.permission.WAKE_LOCK")) {
            println("$p: ${if (ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) "yes" else "NO"}")
        }
        val adapter = runCatching {
            android.bluetooth.BluetoothManager::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }.newInstance(ctx).adapter
        }.onFailure { println("bluetooth manager: $it") }.getOrNull()
        println("bluetooth: ${runCatching { adapter?.isEnabled }.getOrElse { it.toString() }}")
        runCatching {
            adapter?.bondedDevices?.forEach { println("  paired: ${it.address} ${it.name} type ${it.type}") }
        }.onFailure { println("paired devices: $it") }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        println("screen on: ${pm.isInteractive}")
        runCatching {
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeonTokyo:SiriCheck")
            wl.acquire(1_000); wl.release(); println("wake lock: yes")
        }.onFailure { println("wake lock: $it") }
        val t = SystemClock.uptimeMillis()
        val probe = KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, 0)
        println("key injection: ${ShellEnv.inject(probe)}")
        val e = SiriNative.nativeCreate(3)
        println("rust engine: ${if (e != 0L) "loaded" else "NO"}")
        SiriNative.nativeDestroy(e)
    }

    /** The Rust library ships in the APK; shell may not map it from there, so it is copied first. */
    private fun loadEngine(pkg: String) {
        val dir = ShellEnv.systemContext.packageManager.getApplicationInfo(pkg, 0).nativeLibraryDir
        val lib = File(dir, "libneon_siri.so")
        val ok = runCatching { System.load(lib.absolutePath) }.isSuccess
        if (!ok) {
            SiriConfig.DIR.mkdirs()
            val copy = File(SiriConfig.DIR, "libneon_siri.so")
            // no library in the app (the emulator test strips it): one pushed there beforehand
            if (lib.exists()) lib.inputStream().use { input -> copy.outputStream().use { input.copyTo(it) } }
            System.load(copy.absolutePath)
        }
    }

    // ------------------------------------------------------------------ commands

    fun pair() = handler.post {
        problem = ""
        link.pair()
    }

    fun forget() = handler.post {
        config = config.copy(address = "")
        config.save()
        link.close()
        link.start("")
    }

    fun configure(pairs: Map<String, String>) = handler.post {
        config = config.with(pairs)
        config.save()
        applyConfig()
        updateWakeLock()
    }

    fun stop() = handler.post {
        release()
        link.close()
        wakeLock?.let { if (it.isHeld) it.release() }
        exitProcess(0)
    }

    private fun applyConfig() {
        if (engine == 0L) return
        val c = config
        SiriNative.nativeConfigure(
            engine, c.scrollSpeed, c.momentum, c.ringScrub && generation != 1,
            c.edgeTaps || generation == 1, c.backHoldHome, c.siriKey, c.tvDoubleKey, c.tvHoldKey
        )
    }

    /**
     * A TV asleep with its CPU stopped cannot hear the remote. Holding a partial wake lock keeps
     * the Bluetooth link alive with the screen off, so Power turns the TV back on.
     */
    private fun updateWakeLock() {
        val pm = ShellEnv.context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (config.wakeFromRemote) {
            if (wakeLock == null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeonTokyo:SiriRemote")
            wakeLock?.let { if (!it.isHeld) it.acquire() }
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    // ------------------------------------------------------------------ link events

    override fun onReady(generation: Int, name: String, address: String) {
        if (engine == 0L || this.generation != generation) {
            if (engine != 0L) SiriNative.nativeDestroy(engine)
            engine = SiriNative.nativeCreate(generation)
            this.generation = generation
        }
        applyConfig()
        remoteName = name
        connected = true
        problem = ""
        if (config.address != address) {
            config = config.copy(address = address)
            config.save()
        }
        Log.i(TAG, "ready: gen $generation $name $address")
        if (debug) println("ready: gen $generation $name $address")
        apply(SiriNative.nativeConnected(engine, now()))
    }

    override fun onReport(reportId: Int, data: ByteArray) {
        if (debug) println("report %02x %s".format(reportId, data.joinToString(" ") { "%02x".format(it) }))
        if (engine == 0L) return
        if (reportId == 0xFA) {
            SiriNative.nativeAudioFrame(data)?.let { VoiceRelay.frame(it) }
            return
        }
        apply(SiriNative.nativeReport(engine, reportId, data, now()))
    }

    override fun onLost() {
        connected = false
        if (engine != 0L) apply(SiriNative.nativeDisconnected(engine, now()))
    }

    override fun onBattery(percent: Int) {
        battery = percent
    }

    override fun onProblem(text: String) {
        problem = text
        if (debug) println("problem: $text")
        Log.w(TAG, text)
    }

    override fun onPaired(address: String) {
        config = config.copy(address = address)
        config.save()
    }

    // ------------------------------------------------------------------ output

    private val tick = Runnable { if (engine != 0L) apply(SiriNative.nativeTick(engine, now())) }

    private fun now() = SystemClock.uptimeMillis()

    private fun apply(out: IntArray) {
        var i = 1
        while (i + 3 < out.size) {
            val kind = out[i]
            val code = out[i + 1]
            val repeat = out[i + 2]
            val flags = out[i + 3]
            when (kind) {
                SiriOut.KEY_DOWN -> key(code, true, repeat, false)
                SiriOut.KEY_UP -> key(code, false, 0, flags and SiriOut.FLAG_CANCELED != 0)
                SiriOut.POWER -> power()
                SiriOut.VOICE_START -> VoiceRelay.start()
                SiriOut.VOICE_STOP -> VoiceRelay.stop()
            }
            i += 4
        }
        handler.removeCallbacks(tick)
        val due = out.firstOrNull() ?: -1
        if (due >= 0) handler.postDelayed(tick, due.toLong().coerceAtLeast(1))
    }

    private fun key(code: Int, down: Boolean, repeat: Int, canceled: Boolean) {
        if (code == SiriConfig.CONTROL_CENTER) {
            if (down && repeat == 0) controlCenter()
            return
        }
        val t = now()
        val downTime = if (down && repeat == 0) t.also { downTimes[code] = it } else downTimes[code] ?: t
        if (!down) downTimes.remove(code)
        var flags = KeyEvent.FLAG_FROM_SYSTEM
        if (canceled) flags = flags or KeyEvent.FLAG_CANCELED
        if (repeat == 1) flags = flags or KeyEvent.FLAG_LONG_PRESS
        val source = when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER -> InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD
            else -> InputDevice.SOURCE_KEYBOARD
        }
        val event = KeyEvent(
            downTime, t, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, repeat, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, source
        )
        if (ShellEnv.inject(event)) keysSent++
    }

    /**
     * Neon Tokyo's Control Center. A held Home on the TV's remote reaches it through the overlay
     * service's key filter, but injected keys skip that filter, so the bridge asks for it directly
     * (shell holds DUMP, which guards the broadcast). With the overlays off: quick settings.
     */
    private fun controlCenter() = Thread {
        // `am` takes a moment; keys keep flowing meanwhile
        val services = runCatching {
            android.provider.Settings.Secure.getString(
                ShellEnv.context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull().orEmpty()
        val sent = services.contains("${VoiceRelay.packageName}/") && runCatching {
            Runtime.getRuntime().exec(
                arrayOf("am", "broadcast", "-a", "com.neontokyo.tv.OVERLAY", "--es", "show", "control", "-p", VoiceRelay.packageName)
            ).waitFor() == 0
        }.getOrDefault(false)
        if (!sent) handler.post {
            key(KeyEvent.KEYCODE_SETTINGS, true, 0, false)
            key(KeyEvent.KEYCODE_SETTINGS, false, 0, false)
        }
    }.start()

    /** Off when on, on when off, like the TCL remote's power button. */
    private fun power() {
        val pm = ShellEnv.context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val code = if (pm.isInteractive) KeyEvent.KEYCODE_POWER else KeyEvent.KEYCODE_WAKEUP
        key(code, true, 0, false)
        key(code, false, 0, false)
    }

    private fun release() {
        downTimes.keys.toList().forEach { key(it, false, 0, true) }
    }
}
