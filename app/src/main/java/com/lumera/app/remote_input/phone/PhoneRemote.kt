package com.lumera.app.remote_input.phone

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import com.google.gson.JsonObject
import com.lumera.app.overlay.NeonOverlayService
import com.lumera.app.remote_input.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.BindException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The phone remote: the TV runs a small page on the home network that turns a phone (an iPhone
 * in Safari, or Neon Tokyo Web) into a remote with a touchpad, a keyboard and voice.
 *
 * The page lives on the TV and not on the web because a page from the internet (https) may not
 * talk to a TV on the home network (http): Safari blocks it. Neon Tokyo Web only remembers the TV
 * and opens its page.
 *
 * A phone joins by scanning the QR code in Settings, which carries a key, or by typing the
 * four-digit code the same screen shows, which only works while that screen is open. Without the
 * key the TV answers nothing but the page itself.
 */
object PhoneRemote {
    private const val TAG = "PhoneRemote"
    private const val PREFS = "phone_remote"
    private const val KEY_ON = "on"
    private const val KEY_TOKEN = "token"
    private const val KEY_NAME = "name"

    /** The port the page is on. Kept the same, so a phone's bookmark keeps working. */
    const val PORT = 8765
    private val PORTS = PORT..PORT + 4

    private lateinit var app: Context
    private var started = false

    /** Where the phone's presses are carried out: never the main thread, which the TV draws on. */
    val worker: Handler by lazy {
        Handler(HandlerThread("NeonPhoneRemote", Process.THREAD_PRIORITY_FOREGROUND).also { it.start() }.looper)
    }

    @Volatile
    private var server: PhoneRemoteServer? = null

    @Volatile
    var port = 0
        private set

    private val _phones = MutableStateFlow(0)

    /** How many phones have the remote open right now. */
    val phones: StateFlow<Int> = _phones.asStateFlow()

    /** Neon's own screens (the app, Neon Home) in front: presses can go straight into them. */
    @Volatile
    var neonInFront = false
        private set
    private var resumed = 0

    fun init(application: Application) {
        if (started) return
        // Only the app's own process serves the page
        if (Build.VERSION.SDK_INT >= 28 && Application.getProcessName() != application.packageName) return
        started = true
        app = application.applicationContext
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed++
                neonInFront = true
            }

            override fun onActivityPaused(activity: Activity) {
                resumed = (resumed - 1).coerceAtLeast(0)
                neonInFront = resumed > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        if (isOn(app)) worker.post { startServer() }
        // From a PC (and the TV test): `adb shell am broadcast -a com.neontokyo.tv.PHONE_REMOTE --es do pair`
        // opens pairing for two minutes and prints the code. Only the shell and the system hold DUMP
        androidx.core.content.ContextCompat.registerReceiver(app, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                if (intent.getStringExtra("do") != "pair") return
                ensureRunning()
                val code = openPairing()
                worker.postDelayed({ if (pin == code) closePairing() }, 120_000L)
                resultData = "pin=$code port=${port.takeIf { it > 0 } ?: PORT}"
            }
        }, android.content.IntentFilter(ACTION_ADB), "android.permission.DUMP", null, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }

    private const val ACTION_ADB = "com.neontokyo.tv.PHONE_REMOTE"

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, true)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        worker.post { if (on) startServer() else stopServer() }
    }

    /** The key a phone needs. Made once; [forgetPhones] makes a new one. */
    fun token(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        synchronized(this) {
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
            val token = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            prefs.edit().putString(KEY_TOKEN, token).commit()
            return token
        }
    }

    /** Every phone has to scan the code again. */
    fun forgetPhones(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).commit()
        token(context)
        server?.dropAll()
    }

    fun tokenMatches(context: Context, given: String?): Boolean {
        if (given.isNullOrEmpty()) return false
        return MessageDigest.isEqual(given.toByteArray(), token(context).toByteArray())
    }

    /** What the phone calls this TV. */
    fun name(context: Context): String =
        prefs(context).getString(KEY_NAME, null)
            ?: runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty().ifBlank { "Neon Tokyo TV" }

    // ── Pairing with a code: only while the pairing screen is open ──

    @Volatile
    var pin: String? = null
        private set
    @Volatile
    private var wrong = 0

    /** The pairing screen opened: a fresh code, good until it closes. */
    fun openPairing(): String {
        val code = (SecureRandom().nextInt(9000) + 1000).toString()
        pin = code
        wrong = 0
        return code
    }

    fun closePairing() {
        pin = null
    }

    /** The key for a phone that typed the right code; after five wrong codes it stops trying. */
    @Synchronized
    fun pairWith(code: String?): String? {
        val now = pin ?: return null
        if (code != null && MessageDigest.isEqual(code.trim().toByteArray(), now.toByteArray())) {
            wrong = 0
            return token(app)
        }
        if (++wrong >= 5) pin = null
        return null
    }

    // ── The server ──

    /** http://192.168.1.x:8765, or null without a network. */
    fun address(): String? {
        val ip = NetworkUtils.getLocalIpAddress() ?: return null
        val p = port.takeIf { it > 0 } ?: PORT
        return "http://$ip:$p"
    }

    /** What the QR code holds: the page, with the key after the # so it never leaves the phone. */
    fun pairUrl(context: Context): String? = address()?.let { "$it/#k=${token(context)}" }

    fun ensureRunning() {
        if (!started) return
        worker.post { if (isOn(app)) startServer() }
    }

    private fun startServer() {
        if (server?.isAlive == true) return
        for (p in PORTS) {
            try {
                val s = PhoneRemoteServer(p, app)
                s.start(SOCKET_TIMEOUT_MS, true)
                server = s
                port = p
                Log.i(TAG, "Phone remote on port $p")
                return
            } catch (e: BindException) {
                continue
            } catch (e: Exception) {
                Log.w(TAG, "Phone remote could not start on $p", e)
            }
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        port = 0
        phonesChanged(0)
    }

    internal fun phonesChanged(count: Int) {
        if (_phones.value == count) return
        _phones.value = count
        // Only while a phone watches does the TV tell it when a text field comes up
        NeonOverlayService.watchFields(count > 0)
    }

    // ── What the phone is told ──

    /** A text field on the TV, as the overlay service saw it. */
    @Volatile
    private var field: Field? = null

    data class Field(val text: String, val hint: String, val password: Boolean, val app: String?)

    /** The overlay service: a text field got focus ([f]), or nothing to type in has it (null). */
    fun fieldSeen(f: Field?) {
        if (field == f) return
        field = f
        fieldChanged()
    }

    /** Something the phone shows changed: it hears, from the remote's own thread. */
    fun fieldChanged() {
        if (server == null || _phones.value == 0) return
        worker.removeCallbacks(tell)
        worker.post(tell)
    }

    private val tell = Runnable { server?.broadcast("state", state()) }

    @Volatile
    private var appInFront: String? = null

    fun appChanged(pkg: String?) {
        if (appInFront == pkg) return
        appInFront = pkg
        fieldChanged()
    }

    /** Everything the phone shows about the TV, sent when it connects and whenever it changes. */
    fun state(): JsonObject = JsonObject().apply {
        addProperty("name", name(app))
        addProperty("service", NeonOverlayService.running)
        addProperty("dpad", RemoteActions.canSteerEverywhere())
        appInFront?.let { pkg ->
            addProperty("app", pkg)
            appLabel(pkg)?.let { addProperty("appLabel", it) }
        }
        val f = field
        when {
            RemoteSearch.visible && neonInFront -> add("field", JsonObject().apply {
                addProperty("text", RemoteSearch.query)
                addProperty("hint", "Search films and series")
                addProperty("target", "neon")
            })
            f != null -> add("field", JsonObject().apply {
                addProperty("text", if (f.password) "" else f.text)
                addProperty("hint", f.hint)
                addProperty("password", f.password)
                addProperty("target", "field")
                f.app?.let { pkg -> appLabel(pkg)?.let { addProperty("app", it) } }
            })
        }
        RemoteActions.volume(app)?.let { add("volume", it) }
    }

    private val labels = HashMap<String, String>()

    private fun appLabel(pkg: String): String? = synchronized(labels) {
        labels[pkg] ?: runCatching {
            val pm = app.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrNull()?.also { labels[pkg] = it }
    }

    internal fun context(): Context = app

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val SOCKET_TIMEOUT_MS = 20_000
}
