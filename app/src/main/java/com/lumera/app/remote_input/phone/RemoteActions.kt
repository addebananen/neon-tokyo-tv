package com.lumera.app.remote_input.phone

import android.accessibilityservice.AccessibilityService
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lumera.app.MainActivity
import com.lumera.app.launcher.HomeApps
import com.lumera.app.overlay.NeonOverlayService
import com.lumera.app.siri.SearchIntents
import com.lumera.app.siri.VoiceCommand
import com.lumera.app.siri.VoiceCommands
import java.io.ByteArrayOutputStream

/**
 * What the phone's buttons do on the TV. Each answers how it went: "ok", "service" when it needs
 * the "Neon Tokyo overlays" accessibility service (the one way Android lets an app press keys in
 * other apps) and that is off, or "none" when there was nothing for it to do.
 *
 * Runs on [PhoneRemote.worker], never the main thread.
 */
object RemoteActions {
    private const val TAG = "PhoneRemote"
    private val main = Handler(Looper.getMainLooper())
    private val instrumentation by lazy { Instrumentation() }

    /** Arrows work in every app (Android 13 and later, with the overlay service on). */
    fun canSteerEverywhere(): Boolean = Build.VERSION.SDK_INT >= 33 && NeonOverlayService.running

    fun key(context: Context, name: String): String = when (name) {
        "up" -> arrow(KeyEvent.KEYCODE_DPAD_UP)
        "down" -> arrow(KeyEvent.KEYCODE_DPAD_DOWN)
        "left" -> arrow(KeyEvent.KEYCODE_DPAD_LEFT)
        "right" -> arrow(KeyEvent.KEYCODE_DPAD_RIGHT)
        "ok" -> arrow(KeyEvent.KEYCODE_DPAD_CENTER)
        "back" -> back()
        "home" -> home(context)
        "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
        "playpause" -> media(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        "next" -> media(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        "previous" -> media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        "rewind" -> media(context, KeyEvent.KEYCODE_MEDIA_REWIND)
        "forward" -> media(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        "volup" -> volume(context, AudioManager.ADJUST_RAISE)
        "voldown" -> volume(context, AudioManager.ADJUST_LOWER)
        "mute" -> volume(context, AudioManager.ADJUST_TOGGLE_MUTE)
        "control" -> if (NeonOverlayService.openControlCenter()) "ok" else "service"
        "sleep" -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else "none"
        else -> "none"
    }

    /** An arrow or OK: the Control Center's when it is up, else the TV's, else Neon's own screens. */
    private fun arrow(code: Int): String {
        val service = NeonOverlayService.current
        if (service != null && service.panelPress(code)) return "ok"
        if (service != null && Build.VERSION.SDK_INT >= 33) {
            val action = when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> AccessibilityService.GLOBAL_ACTION_DPAD_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> AccessibilityService.GLOBAL_ACTION_DPAD_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT
                else -> AccessibilityService.GLOBAL_ACTION_DPAD_CENTER
            }
            if (service.performGlobalAction(action)) return "ok"
        }
        if (PhoneRemote.neonInFront && inject(code)) return "ok"
        // Android 11 and 12: move the focus by hand, as far as the app lets anything see it
        if (service != null && byFocus(service, code)) return "ok"
        return if (service == null) "service" else "none"
    }

    private fun back(): String {
        val service = NeonOverlayService.current
        if (service != null && service.panelPress(KeyEvent.KEYCODE_BACK)) return "ok"
        if (service != null && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) return "ok"
        if (PhoneRemote.neonInFront && inject(KeyEvent.KEYCODE_BACK)) return "ok"
        return "service"
    }

    private fun home(context: Context): String {
        val service = NeonOverlayService.current
        if (service != null && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) return "ok"
        // Anyone may ask for the home screen, but Android only lets an app in front start it
        if (!PhoneRemote.neonInFront) return "service"
        return if (startOnMain(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))) "ok" else "none"
    }

    private fun global(action: Int): String {
        val service = NeonOverlayService.current ?: return "service"
        return if (service.performGlobalAction(action)) "ok" else "none"
    }

    /** Play, pause, skip: to whatever plays (the film in Neon, YouTube, Spotify). */
    private fun media(context: Context, code: Int): String {
        if (PhoneRemote.neonInFront && inject(code)) return "ok"
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "none"
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
        return "ok"
    }

    private fun volume(context: Context, direction: Int): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "none"
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        PhoneRemote.fieldChanged()
        return "ok"
    }

    /** The TV's loudness for the phone's slider, or null when the TV doesn't say (a soundbar decides). */
    fun volume(context: Context): JsonObject? {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val max = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        if (max <= 0) return null
        return JsonObject().apply {
            addProperty("level", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            addProperty("max", max)
            addProperty("muted", runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false))
        }
    }

    /** A key into Neon's own window; Android allows that much without any permission. */
    private fun inject(code: Int): Boolean = runCatching {
        instrumentation.sendKeyDownUpSync(code)
        true
    }.getOrElse {
        Log.d(TAG, "inject $code: ${it.message}")
        false
    }

    private fun byFocus(service: NeonOverlayService, code: Int): Boolean {
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (code == KeyEvent.KEYCODE_DPAD_CENTER) return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val direction = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            else -> View.FOCUS_RIGHT
        }
        val next = focused.focusSearch(direction) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    // ── Typing ──

    /**
     * The phone's text into what is up on the TV: Neon's search page, or the text field in front.
     * Answers where it went: "neon", "field", or "none" when nothing on the TV takes text.
     */
    fun text(text: String, submit: Boolean): String {
        if (RemoteSearch.visible && PhoneRemote.neonInFront) {
            RemoteSearch.type(text)
            return "neon"
        }
        val service = NeonOverlayService.current ?: return "service"
        return if (service.typeInto(text, submit)) "field" else "none"
    }

    // ── Searching and apps ──

    /**
     * A search from the phone. [where] is "neon" (films and series in Neon Tokyo), "youtube",
     * "spotify", "youtube music", "tv" (Google TV's own search), or "smart": the words decide, as
     * with the Siri Remote's voice ("open YouTube", "play city pop on Spotify", or a title).
     */
    fun search(context: Context, query: String, where: String): JsonObject {
        val q = query.trim()
        val out = JsonObject()
        if (q.isEmpty()) return out.apply { addProperty("result", "none") }
        if (!PhoneRemote.neonInFront && !NeonOverlayService.running) {
            // Android only lets an app start screens from the background with the service on
            return out.apply { addProperty("result", "service") }
        }
        when (where) {
            "smart" -> {
                val apps = HomeApps.load(context).map { it.packageName to it.label }
                when (val command = VoiceCommands.parse(q, apps)) {
                    is VoiceCommand.OpenApp -> {
                        onMain { HomeApps.open(context, command.packageName) }
                        out.addProperty("did", "Opening ${command.label}")
                    }
                    is VoiceCommand.PlayIn -> {
                        onMain { SearchIntents.playIn(context, command.app, command.query) }
                        out.addProperty("did", "Searching ${command.app.replaceFirstChar { it.uppercase() }} for “${command.query}”")
                    }
                    is VoiceCommand.Search -> {
                        neon(context, command.query)
                        out.addProperty("did", "Searching Neon Tokyo for “${command.query}”")
                    }
                }
            }
            "neon" -> {
                neon(context, q)
                out.addProperty("did", "Searching Neon Tokyo")
            }
            "tv" -> {
                onMain { SearchIntents.global(context, q) }
                out.addProperty("did", "Searching Google TV")
            }
            else -> {
                onMain { SearchIntents.playIn(context, where, q) }
                out.addProperty("did", "Searching ${where.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }}")
            }
        }
        return out.apply { addProperty("result", "ok") }
    }

    /** Neon Tokyo's Search tab with [query] in it. */
    private fun neon(context: Context, query: String) {
        RemoteSearch.want(query)
        if (!(RemoteSearch.visible && PhoneRemote.neonInFront)) {
            startOnMain(context, Intent(context, MainActivity::class.java))
        }
    }

    fun apps(context: Context): JsonArray = JsonArray().apply {
        HomeApps.load(context).forEach { app ->
            add(JsonObject().apply {
                addProperty("pkg", app.packageName)
                addProperty("label", app.label)
            })
        }
    }

    fun open(context: Context, pkg: String): String {
        if (!PhoneRemote.neonInFront && !NeonOverlayService.running) return "service"
        onMain { HomeApps.open(context, pkg) }
        return "ok"
    }

    private val icons = object : LruCache<String, ByteArray>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    /** The app's TV banner (16:9) as a PNG, or its icon when it has none. */
    fun icon(context: Context, pkg: String): ByteArray? {
        icons.get(pkg)?.let { return it }
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
        val banner = runCatching {
            launch?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(info)
        }.getOrNull()
        val drawable = banner ?: runCatching { pm.getApplicationIcon(info) }.getOrNull() ?: return null
        val (w, h) = if (banner != null) 320 to 180 else 192 to 192
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        bitmap.recycle()
        icons.put(pkg, bytes)
        return bytes
    }

    private fun onMain(block: () -> Unit) {
        main.post { runCatching(block).onFailure { Log.w(TAG, "phone remote action", it) } }
    }

    private fun startOnMain(context: Context, intent: Intent): Boolean {
        onMain { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }
}
