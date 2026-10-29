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
 * device, and the TV's own Bluetooth page is the system: Neon Tokyo opens it, and the hand finds
 * the device there and presses Connect on its page, then goes home again. The Control Center
 * sends it the same way to press Disconnect (back to the TV's speakers) or to join a Wi-Fi
 * network, and then it goes Back to the app beneath instead.
 *
 * Pressing on another app's page takes an accessibility service. The overlay service, which the
 * TV keeps on once it was switched on under Accessibility, lends its hand while it runs, so no
 * computer is needed. Without it, this service is the hand: Neon Tokyo turns it on for a press
 * (the TV allows that once WRITE_SECURE_SETTINGS was granted over ADB) and it turns itself off
 * when done, so it costs the TV nothing the rest of the time.
 */
class BluetoothHelper : AccessibilityService() {
    private val hand = SettingsHand(this) { if (SettingsHand.pending == null) disableSelf() }

    override fun onServiceConnected() {
        instance = this
        // Left on by a run that ended halfway: it only lives for a press
        if (SettingsHand.pending == null) disableSelf() else hand.step()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (SettingsHand.pending != null) hand.step()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        hand.stop()
        super.onDestroy()
    }

    /** What the hand is asked to do on the TV's settings page. */
    enum class Task { Connect, Disconnect, Wifi }

    companion object {
        private const val WORK_MS = 15_000L

        @Volatile
        private var instance: BluetoothHelper? = null

        /** The overlay service's hand, while it runs: it needs no turning on. */
        @Volatile
        internal var lent: SettingsHand? = null

        /** The TV lets Neon Tokyo turn the helper on by itself: WRITE_SECURE_SETTINGS was granted over ADB. */
        fun canTurnOn(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /** A hand is there to press, with or without a computer having been used. */
        fun available(context: Context): Boolean = lent != null || canTurnOn(context)

        /**
         * Asks for [task] to be done on [name]'s row, just before the TV's Bluetooth or Wi-Fi page
         * opens. False when no hand can press, and the press is left to the remote. [back]
         * leaves the page with Back when done, to the app beneath, rather than going Home.
         */
        fun request(context: Context, name: String, task: Task = Task.Connect, back: Boolean = false): Boolean {
            val job = SettingsHand.Request(name, SystemClock.uptimeMillis() + WORK_MS, task, back)
            lent?.let { overlay ->
                SettingsHand.pending = job
                overlay.begin()
                return true
            }
            if (!canTurnOn(context)) return false
            SettingsHand.pending = job
            if (instance == null && !turnOn(context)) {
                SettingsHand.pending = null
                return false
            }
            instance?.hand?.begin()
            return true
        }

        fun cancel() {
            SettingsHand.pending = null
            lent?.idle()
            instance?.hand?.idle()
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

/**
 * The pressing itself, for whichever accessibility service lends it: it reads the settings page
 * in front and presses through to the job's end. [onIdle] runs when nothing is left to do.
 */
internal class SettingsHand(private val svc: AccessibilityService, private val onIdle: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())

    internal class Request(val name: String, val until: Long, val task: BluetoothHelper.Task, val back: Boolean) {
        var opened = false
        var openedAt = 0L
        var settingsApp: String? = null
    }

    /** A job was just set: look now, and give up by itself if the page never shows the row. */
    fun begin() {
        handler.post { step() }
        val job = pending ?: return
        handler.postDelayed({ if (pending === job) step() }, job.until - SystemClock.uptimeMillis() + 200)
    }

    fun stop() = handler.removeCallbacksAndMessages(null)

    fun idle() {
        if (pending == null) onIdle()
    }

    fun step() {
        val job = pending ?: return idle()
        if (SystemClock.uptimeMillis() > job.until) return finish(job, done = false)
        val root = svc.rootInActiveWindow ?: return
        // Neon Tokyo's own screens show the same names (the Bluetooth list): never press there
        if (root.packageName == svc.packageName) return
        when (job.task) {
            BluetoothHelper.Task.Connect -> {
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
            BluetoothHelper.Task.Disconnect -> {
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
            BluetoothHelper.Task.Wifi -> {
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
                // The settings app this job opened, to know when Back has left it
                job.settingsApp = root.packageName?.toString()
                job.opened = true
                job.openedAt = SystemClock.uptimeMillis()
                if (job.task == BluetoothHelper.Task.Wifi) handler.postDelayed({ if (pending === job) step() }, 400L)
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
            idle()
            return
        }
        // A moment on the page so the press takes, then away: the TV keeps connecting in the background
        handler.postDelayed({
            // Some TVs ask once more before they let a device go
            if (job.task == BluetoothHelper.Task.Disconnect) svc.rootInActiveWindow?.let { root -> find(root) { it in CONFIRM }?.let { click(it) } }
            if (job.back) backOut(job, BACK_TRIES) else {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                idle()
            }
        }, HOME_AFTER_MS)
    }

    /** Back out of the settings app, to the film or app the Control Center was opened over. */
    private fun backOut(job: Request, tries: Int) {
        val app = svc.rootInActiveWindow?.packageName?.toString()
        if (tries <= 0 || app == null || app != job.settingsApp) return idle()
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        handler.postDelayed({ backOut(job, tries - 1) }, BACK_GAP_MS)
    }

    /** Left on the page for the remote, as when a password is asked. */
    private fun leave(job: Request) {
        if (pending === job) pending = null
        idle()
    }

    companion object {
        private const val HOME_AFTER_MS = 1_200L
        private const val BACK_GAP_MS = 450L
        private const val BACK_TRIES = 5
        private const val WIFI_SETTLE_MS = 3_000L

        /** The TV's words for Connect and Disconnect, in English and Swedish. */
        private val CONNECT = setOf("connect", "anslut", "koppla", "koppla till")
        private val DISCONNECT = setOf("disconnect", "koppla från", "koppla bort")
        private val CONFIRM = setOf("ok", "yes", "ja", "disconnect", "koppla från")
        private val WIFI_CONNECT = setOf("connect", "anslut", "connect to network", "anslut till nätverket", "anslut till nätverk")

        /** The one job at a time, whichever service presses it. */
        @Volatile
        internal var pending: Request? = null
    }
}
