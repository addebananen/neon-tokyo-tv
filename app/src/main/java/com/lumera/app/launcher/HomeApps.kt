package com.lumera.app.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log
import android.widget.Toast

/** Finding and opening the TV's apps. */
object HomeApps {
    private const val TAG = "HomeApps"

    /**
     * The apps with a TV launcher entry, plus apps added by hand that only have a phone one.
     * The system's own phone entries are left out, as Google TV's own app list does.
     */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        fun query(category: String) = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.onFailure { Log.w(TAG, "Could not list $category apps", it) }.getOrDefault(emptyList())

        val apps = LinkedHashMap<String, InstalledApp>()
        fun add(info: ResolveInfo) {
            val pkg = info.activityInfo?.packageName ?: return
            if (pkg in apps) return
            val label = runCatching { info.loadLabel(pm).toString().trim() }.getOrDefault("").ifEmpty { pkg }
            apps[pkg] = InstalledApp(pkg, label, HomeCatalog.known(pkg, label))
        }
        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach(::add)
        query(Intent.CATEGORY_LAUNCHER)
            .filter { ((it.activityInfo?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .forEach(::add)
        return HomeCatalog.sortForAllApps(apps.values)
    }

    /** Whose an app is: one's own, or one that came with the TV, as it came or updated since. */
    enum class Kind { Own, System, UpdatedSystem }

    fun kind(context: Context, packageName: String): Kind {
        val flags = runCatching { context.packageManager.getApplicationInfo(packageName, 0).flags }.getOrDefault(0)
        return when {
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> Kind.UpdatedSystem
            flags and ApplicationInfo.FLAG_SYSTEM != 0 -> Kind.System
            else -> Kind.Own
        }
    }

    /**
     * Asks the TV to uninstall [packageName], or for an app that came with the TV to take it back
     * to how it came; the TV asks to be sure itself.
     */
    fun uninstall(context: Context, packageName: String) {
        start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The TV's own page for [packageName]: its storage, clearing its data, switching it off. */
    fun appInfo(context: Context, packageName: String) {
        start(
            context,
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isInstalled(context: Context, app: KnownApp): Boolean = app.packages.any { launchIntent(context, it) != null }

    /** Opens [app], or its Play Store page when it is not installed. */
    fun open(context: Context, app: KnownApp) {
        val pkg = app.packages.firstOrNull { launchIntent(context, it) != null }
        if (pkg != null) {
            open(context, pkg)
        } else {
            app.packages.firstOrNull()?.let { start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$it"))) }
        }
    }

    /** Opens the app with [packageName], or its Play Store page when it has gone from the TV. */
    fun open(context: Context, packageName: String) {
        val intent = launchIntent(context, packageName)
        if (intent != null) {
            start(context, intent)
            return
        }
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(store)
        } catch (e: Exception) {
            Toast.makeText(context, "That app is no longer on the TV", Toast.LENGTH_SHORT).show()
        }
    }

    /** Starts [intent], and says so on screen when nothing on the TV can open it. */
    fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Nothing on this TV can open that", Toast.LENGTH_SHORT).show()
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to open $intent", e)
            Toast.makeText(context, "Nothing on this TV can open that", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun launchIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        return runCatching {
            pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        }.getOrNull()
    }
}
