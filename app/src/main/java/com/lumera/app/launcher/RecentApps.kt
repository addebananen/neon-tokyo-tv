package com.lumera.app.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lumera.app.siri.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** An app in the app switcher, and when it was last in front. */
data class RecentApp(val packageName: String, val at: Long)

/** Where a card of the app switcher sits, [d] cards away from the focus (negative: to the left). */
internal data class CardPose(
    /** Across, in card widths from the focused card's place. */
    val x: Float,
    val scale: Float,
    val alpha: Float,
    /** How much the card is put in shadow, 0 for the focused one. */
    val shade: Float,
    val z: Float
)

/**
 * The rules of the app switcher, kept apart from Android so they can be tested. As on Apple TV,
 * the apps run oldest to newest from left to right, the newest has the focus, and the older ones
 * lie stacked behind it on the left while the next one peeks in from the right.
 */
internal object SwitcherRules {
    /** As many apps as the switcher keeps. */
    const val MAX = 10

    /** The gap between the focused card and the one on its right, in card widths. */
    const val GAP = 0.06f

    /** How far each card of the stack on the left lies out from the one in front of it. */
    const val STACK = 0.14f

    /** Cards further back than this in the stack have faded away. */
    const val DEEP = 4f

    /** [pkg] came to the front: first in the list, once, and the oldest drop off past [MAX]. */
    fun touch(list: List<RecentApp>, pkg: String, at: Long): List<RecentApp> =
        (listOf(RecentApp(pkg, at)) + list.filter { it.packageName != pkg }).take(MAX)

    fun encode(list: List<RecentApp>): String = list.joinToString("\n") { "${it.packageName} ${it.at}" }

    fun decode(text: String?): List<RecentApp> =
        text.orEmpty().lines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            val pkg = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RecentApp(pkg, parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        }.distinctBy { it.packageName }.take(MAX)

    /** The order on screen, left to right: the newest last, where the focus starts. */
    fun onScreen(list: List<RecentApp>): List<RecentApp> = list.reversed()

    /** Where the focus goes once the card at [closed] went, of [size] before it went. */
    fun afterClose(closed: Int, size: Int): Int = when {
        size <= 1 -> 0
        closed > 0 -> closed - 1
        else -> 0
    }

    fun pose(d: Float): CardPose {
        if (d >= 0f) {
            // The focused card and the ones on its right, side by side
            return CardPose(
                x = d * (1f + GAP),
                scale = 1f - 0.04f * d.coerceAtMost(1f),
                alpha = (2f - d).coerceIn(0f, 1f),
                shade = 0.35f * d.coerceAtMost(1f),
                z = -d
            )
        }
        val a = -d
        // The first step back slides a whole stack step; each one further back less
        val out = if (a <= 1f) a else 1f + (a - 1f) * 0.55f
        return CardPose(
            x = -STACK * out,
            scale = 1f - 0.05f * a.coerceAtMost(DEEP),
            alpha = (DEEP - a).coerceIn(0f, 1f),
            shade = (0.3f * a).coerceAtMost(0.5f),
            z = -a
        )
    }
}

/**
 * The apps opened lately, for the app switcher: the ones Neon Home started, Neon Tokyo itself,
 * and, while the overlay service runs, any app that came to the front. With the overlay service
 * there is also a picture of each, taken now and then while it is in front, so its card shows
 * where it was left; without, the card shows the app's own banner.
 */
object RecentApps {
    private const val TAG = "RecentApps"
    private const val PREFS = "app_switcher"
    private const val KEY = "recent"

    private val _list = MutableStateFlow<List<RecentApp>?>(null)

    /** Each new picture of an app counts this up, so a card that shows one reads it again. */
    private val _shots = MutableStateFlow(0)
    val shots: StateFlow<Int> = _shots.asStateFlow()

    /** The packages that are a home screen, an assistant or a keyboard: never in the switcher. */
    @Volatile private var notApps: Set<String>? = null
    private val launchable = ConcurrentHashMap<String, Boolean>()

    fun list(context: Context): StateFlow<List<RecentApp>?> {
        if (_list.value == null) {
            _list.value = SwitcherRules.decode(prefs(context).getString(KEY, null))
        }
        return _list.asStateFlow()
    }

    /** [pkg] is in front now. Cheap: called for every app that comes to the front. */
    fun seen(context: Context, pkg: String) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val before = list(app).value.orEmpty()
        if (before.firstOrNull()?.packageName == pkg && now - before.first().at < 5_000) return
        val after = SwitcherRules.touch(before, pkg, now)
        _list.value = after
        prefs(app).edit().putString(KEY, SwitcherRules.encode(after)).apply()
    }

    /** Whether a package that came to the front is an app to show, rather than part of the TV. */
    fun switchable(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return false
        if (pkg in notApps(context)) return false
        return launchable.getOrPut(pkg) {
            val pm = context.packageManager
            runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) }.getOrNull() != null
        }
    }

    private fun notApps(context: Context): Set<String> {
        notApps?.let { return it }
        val pm = context.packageManager
        val homes = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        val keyboards = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
                .orEmpty().split(':').map { it.substringBefore('/') }
        }.getOrDefault(emptyList())
        return (homes + keyboards + NOT_APPS).filter { it.isNotEmpty() }.toSet().also { notApps = it }
    }

    /**
     * Closes [pkg] for good, as swiping it up on Apple TV does: Android lets any app end another
     * app's process once it is in the background, which it is while the switcher shows; with
     * Shizuku running, the app is also stopped outright, foreground services and all. Neon Tokyo
     * itself is closed by finishing its own window, the home screen's aside.
     */
    fun close(context: Context, pkg: String) {
        val app = context.applicationContext
        _list.update { list -> list.orEmpty().filter { it.packageName != pkg } }
        prefs(app).edit().putString(KEY, SwitcherRules.encode(_list.value.orEmpty())).apply()
        shotFile(app, pkg).delete()
        cache.remove(pkg)
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        if (pkg == app.packageName) {
            val home = (context as? Activity)?.taskId
            runCatching { am.appTasks.filter { it.taskInfo.taskId != home }.forEach { it.finishAndRemoveTask() } }
                .onFailure { Log.w(TAG, "Could not close Neon Tokyo's window", it) }
            return
        }
        runCatching { am.killBackgroundProcesses(pkg) }.onFailure { Log.w(TAG, "Could not end $pkg", it) }
        thread(name = "switcher-close", isDaemon = true) {
            val stopped = runCatching { ShizukuBridge.run("am force-stop $pkg") }.getOrNull()
            Log.i(TAG, "Closed $pkg${if (stopped != null) " (stopped through Shizuku)" else ""}")
        }
    }

    // --- Pictures of the apps

    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** The picture last taken of [pkg], small, or null when there is none. */
    suspend fun shot(context: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = shotFile(context, pkg)
            if (!file.exists()) return@withContext null
            runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()?.let { bitmap ->
                bitmap.prepareToDraw()
                bitmap.asImageBitmap().also { cache.put(pkg, it) }
            }
        }
    }

    /** Keeps [bitmap] as the picture of [pkg]. Off the main thread. */
    internal fun keepShot(context: Context, pkg: String, bitmap: Bitmap) {
        val file = shotFile(context, pkg)
        val part = File(file.parentFile, "${file.name}.part")
        val ok = runCatching {
            file.parentFile?.mkdirs()
            part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) }
            part.renameTo(file)
        }.getOrDefault(false)
        if (ok) {
            cache.remove(pkg)
            _shots.update { it + 1 }
        }
    }

    private fun shotFile(context: Context, pkg: String) = File(File(context.cacheDir, "switcher"), "$pkg.jpg")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Part of the TV rather than apps one switches between. */
    private val NOT_APPS = listOf(
        "android",
        "com.android.systemui",
        "com.google.android.katniss",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.tungsten.setupwraith",
        "com.google.android.leanbacklauncher",
        "com.android.tv.settings.overlay",
        "com.google.android.tv.remote.service",
        "com.google.android.backdrop",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )
}
