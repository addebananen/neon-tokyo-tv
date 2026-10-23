package com.lumera.app.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Turns the Home button alias on or off for the TV setup script:
 *
 *     am broadcast --include-stopped-packages -n com.neontokyo.tv/com.lumera.app.launcher.NeonHomeSwitch --es home neon
 *
 * Android lets only an app itself switch its own parts on and off, the TV's shell included, so
 * the script asks the app. The manifest only takes this from senders holding a permission that
 * the shell and the system have and other apps can't get, so no app can make itself the home
 * screen through it. The answer is "neon" or "off", for the script to check.
 */
class NeonHomeSwitch : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val on = when (intent.getStringExtra(EXTRA_HOME)) {
            "neon" -> true
            "off" -> false
            else -> {
                resultCode = Activity.RESULT_CANCELED
                resultData = "send home neon or home off"
                return
            }
        }
        setEnabled(context, on)
        resultCode = Activity.RESULT_OK
        resultData = if (on) "neon" else "off"
    }

    companion object {
        const val EXTRA_HOME = "home"

        /** The alias in the manifest that answers the Home button, named after the app's package. */
        fun alias(context: Context) = ComponentName(context.packageName, "${context.packageName}.NeonHome")

        /** Off puts the alias back as it was installed, which is off. */
        fun setEnabled(context: Context, on: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                alias(context),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
