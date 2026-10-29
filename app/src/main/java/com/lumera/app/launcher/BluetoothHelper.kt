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
 * finds the device there and presses Connect on its page, then goes home again. The Control
 * Center sends it the same way to press Disconnect (back to the TV's speakers) or to join a
 * Wi-Fi network, and then it goes Back to the app beneath instead.
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
        // The settings app this job opened, to know when Back has left it
        if (job.settingsApp == null && root.packageName != packageName) job.settingsApp = root.packageName?.toString()
        when (job.task) {
            Task.Connect -> {
                // The device's own page: Connect, or already connected
                if (job.opened) {
                    find(root) { it in CONNECT }?.let { button ->
                        if (click(button)) finish(job, done = true)
                        return
                    }
                    if (find(root) { it in DISCONNECT } != null) finish(job, done = true)
                    return
                }
            }
            Task.Disconnect -> {
                if (job.opened) {
                    find(root) { it in DISCONNECT }?.let { button ->
                        if (click(button)) finish(job, done = true)
                        return
                    }
                    // Not connected after all
                    if (find(root) { it in CONNECT } != null) finish(job, done = true)
                    return
                }
            }
            Task.Wifi -> {
                if (job.opened) {
                    // A network it has never joined asks for its password: that is for the remote
                    if (findNode(root) { it.isEditable } != null) return leave(job)
                    find(root) { it in WIFI_CONNECT }?.let { button ->
                        if (click(button)) finish(job, done = true)
                        return
                    }
                    // A known network is joined by the press on its row alone
                    if (SystemClock.uptimeMillis() - job.openedAt > WIFI_SETTLE_MS) finish(job, done = true)
                    else handler.postDelayed({ if (pending === job) step() }, 400L)
                    return
                }
            }
        }
        // The list: open this one
        find(root) { it == job.name.lowercase() }?.let { row ->
            if (click(row)) {
                job.opened = true
                job.openedAt = SystemClock.uptimeMillis()
                if (job.task == Task.Wifi) handler.postDelayed({ if (pending === job) step() }, 400L)
            }
        }
    }

    private fun find(node: AccessibilityNodeInfo?, match: (String) -> Boolean): AccessibilityNodeInfo? = findNode(node) {
        val text = (it.text ?: it.contentDescription)?.toString()?.trim()?.lowercase()
        text != null && match(text)
    }

    private fun findNode(node: AccessibilityNodeInfo?, depth: Int = 0, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (depth > 40) return null
        if (node.isVisibleToUser && match(node)) return node
        for (i in 0 until node.childCount) findNode(node.getChild(i), depth + 1, match)?.let { return it }
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
        // A moment on the page so the press takes, then away: the TV keeps connecting in the background
        handler.postDelayed({
            // Some TVs ask once more before they let a device go
            if (job.task == Task.Disconnect) rootInActiveWindow?.let { root -> find(root) { it in CONFIRM }?.let { click(it) } }
            if (job.back) backOut(job, BACK_TRIES) else {
                performGlobalAction(GLOBAL_ACTION_HOME)
                finishIdle()
            }
        }, HOME_AFTER_MS)
    }

    /** Back out of the settings app, to the film or app the Control Center was opened over. */
    private fun backOut(job: Request, tries: Int) {
        val app = rootInActiveWindow?.packageName?.toString()
        if (tries <= 0 || app == null || app != job.settingsApp) return finishIdle()
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ backOut(job, tries - 1) }, BACK_GAP_MS)
    }

    /** Left on the page for the remote, as when a password is asked. */
    private fun leave(job: Request) {
        if (pending === job) pending = null
        finishIdle()
    }

    /** Nothing left to do: off again. */
    private fun finishIdle() {
        if (pending == null) disableSelf()
    }

    /** What the hand is asked to do on the TV's settings page. */
    enum class Task { Connect, Disconnect, Wifi }

    private class Request(val name: String, val until: Long, val task: Task, val back: Boolean) {
        var opened = false
        var openedAt = 0L
        var settingsApp: String? = null
    }

    companion object {
        private const val WORK_MS = 15_000L
        private const val HOME_AFTER_MS = 1_200L
        private const val BACK_GAP_MS = 450L
        private const val BACK_TRIES = 5
        private const val WIFI_SETTLE_MS = 3_000L

        /** The TV's words for Connect and Disconnect, in English and Swedish. */
        private val CONNECT = setOf("connect", "anslut", "koppla", "koppla till")
        private val DISCONNECT = setOf("disconnect", "koppla från", "koppla bort")
        private val CONFIRM = setOf("ok", "yes", "ja", "disconnect", "koppla från")
        private val WIFI_CONNECT = setOf("connect", "anslut", "connect to network", "anslut till nätverket", "anslut till nätverk")

        @Volatile
        private var instance: BluetoothHelper? = null

        @Volatile
        private var pending: Request? = null

        /** The TV lets Neon Tokyo turn the helper on by itself: WRITE_SECURE_SETTINGS was granted over ADB. */
        fun canTurnOn(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /**
         * Asks for [task] to be done on [name]'s row, just before the TV's Bluetooth or Wi-Fi page
         * opens. False when the helper can't be on, and the press is left to the remote. [back]
         * leaves the page with Back when done, to the app beneath, rather than going Home.
         */
        fun request(context: Context, name: String, task: Task = Task.Connect, back: Boolean = false): Boolean {
            if (!canTurnOn(context)) return false
            pending = Request(name, SystemClock.uptimeMillis() + WORK_MS, task, back)
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
