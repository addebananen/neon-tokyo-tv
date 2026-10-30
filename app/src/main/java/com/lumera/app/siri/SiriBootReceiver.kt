package com.lumera.app.siri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * After the TV restarts, starts the Siri Remote bridge again through Shizuku, if the owner turned
 * that on. Works only while Shizuku is running; whether Shizuku itself survives a reboot is its
 * own setting, so this also runs when Neon Home opens ([tryStart]) to catch the moment it is.
 */
class SiriBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        tryStart(context.applicationContext, "boot")
    }

    companion object {
        /** Starts the bridge via Shizuku if self-start is on; safe to call often. Off the main thread. */
        fun tryStart(ctx: Context, from: String) {
            if (!ShizukuBridge.isEnabled(ctx)) return
            thread(name = "siri-selfstart") {
                val why = ShizukuBridge.startBridge(ctx)
                if (why == null) Log.i("NeonSiriStart", "bridge started via Shizuku ($from)")
                else Log.i("NeonSiriStart", "self-start ($from) not done: $why")
            }
        }
    }
}
