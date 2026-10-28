package com.lumera.app.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The hand that presses Connect. Android 13 and later only let the system connect a Bluetooth
 * device, and the TV's own Bluetooth page is the system: Neon Tokyo opens it, and this service
 * finds the device there and presses Connect on its page, then goes home again.
 *
 * It is an accessibility service, which can read and press what is on the screen, so it is only
 * on while it works: Neon Tokyo turns it on for a press (the TV allows that once
 * WRITE_SECURE_SETTINGS was granted over ADB) and it turns itself off when done, so it costs
 * the TV nothing the rest of the time.
 */
class BluetoothHelper : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        // Left on by a run that ended halfway: it only lives for a press
        if (pending == null) disableSelf() else step()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pending != null) step()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun step() {
        val job = pending ?: return finishIdle()
        if (SystemClock.uptimeMillis() > job.until) return finish(job, done = false)
        val root = rootInActiveWindow ?: return
        // The device's own page: Connect, or already connected
        if (job.opened) {
            find(root) { it in CONNECT }?.let { button ->
                if (click(button)) finish(job, done = true)
                return
            }
            if (find(root) { it in DISCONNECT } != null) finish(job, done = true)
            return
        }
        // The list of devices: open this one
        find(root) { it == job.name.lowercase() }?.let { row ->
            if (click(row)) job.opened = true
        }
    }

    private fun find(node: AccessibilityNodeInfo?, depth: Int = 0, match: (String) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (depth > 40) return null
        val text = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
        if (text != null && node.isVisibleToUser && match(text)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), depth + 1, match)?.let { return it }
        return null
    }

    /** Presses the node, or the row it sits in when the text itself can't be pressed. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var at: AccessibilityNodeInfo? = node
        while (at != null) {
            if (at.isClickable && at.isEnabled) return at.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            at = at.parent
        }
        return false
    }

    private fun finish(job: Request, done: Boolean) {
        if (pending === job) pending = null
        if (!done) {
            // The page never showed the device: the remote takes over there
            finishIdle()
            return
        }
        // A moment on the page so the press takes, then home: the TV keeps connecting in the background
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            finishIdle()
        }, HOME_AFTER_MS)
    }

    /** Nothing left to do: off again. */
    private fun finishIdle() {
        if (pending == null) disableSelf()
    }

    private class Request(val name: String, val until: Long) {
        var opened = false
    }

    companion object {
        private const val WORK_MS = 15_000L
        private const val HOME_AFTER_MS = 1_200L

        /** The TV's words for Connect and Disconnect, in English and Swedish. */
        private val CONNECT = setOf("connect", "anslut", "koppla", "koppla till")
        private val DISCONNECT = setOf("disconnect", "koppla från", "koppla bort")

        @Volatile
        private var instance: BluetoothHelper? = null

        @Volatile
        private var pending: Request? = null

        /** The TV lets Neon Tokyo turn the helper on by itself: WRITE_SECURE_SETTINGS was granted over ADB. */
        fun canTurnOn(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /**
         * Asks for Connect to be pressed on [name]'s page, just before the TV's Bluetooth page
         * opens. False when the helper can't be on, and the press is left to the remote.
         */
        fun request(context: Context, name: String): Boolean {
            if (!canTurnOn(context)) return false
            pending = Request(name, SystemClock.uptimeMillis() + WORK_MS)
            if (instance == null && !turnOn(context)) {
                pending = null
                return false
            }
            instance?.let { helper -> helper.handler.post { helper.step() } }
            // Gone by itself if the page never showed the device
            Handler(Looper.getMainLooper()).postDelayed({ instance?.step() }, WORK_MS + 200)
            return true
        }

        fun cancel() {
            pending = null
            instance?.finishIdle()
        }

        private fun turnOn(context: Context): Boolean =
            runCatching {
                val resolver = context.contentResolver
                val me = ComponentName(context, BluetoothHelper::class.java).flattenToString()
                val list = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                    .split(':').filter { it.isNotBlank() }
                if (list.none { it.equals(me, ignoreCase = true) }) {
                    Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (list + me).joinToString(":"))
                }
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }.isSuccess
    }
}
