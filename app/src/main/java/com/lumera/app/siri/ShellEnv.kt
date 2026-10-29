package com.lumera.app.siri

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Process
import android.view.InputEvent

/**
 * What the bridge needs from Android while it runs as the shell user (started over ADB with
 * app_process, so there is no app around it): a Context that speaks for com.android.shell, and
 * the input manager to inject keys with.
 *
 * Why shell: Android only lets privileged callers read a Bluetooth device's HID reports, which is
 * where the Siri Remote puts its buttons and touch; the shell user holds BLUETOOTH_PRIVILEGED and
 * may inject keys into any app, like `adb shell input keyevent` does.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object ShellEnv {
    const val SHELL_PACKAGE = "com.android.shell"

    val systemContext: Context by lazy {
        val at = Class.forName("android.app.ActivityThread")
        val ctor = at.getDeclaredConstructor().apply { isAccessible = true }
        val thread = ctor.newInstance()
        at.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
        at.getDeclaredMethod("getSystemContext").invoke(thread) as Context
    }

    /** A Context that says it is com.android.shell, so Bluetooth and power checks pass. */
    val context: Context by lazy { ShellContext(systemContext) }

    private class ShellContext(base: Context) : ContextWrapper(base) {
        override fun getPackageName() = SHELL_PACKAGE
        override fun getOpPackageName() = SHELL_PACKAGE
        override fun getApplicationContext(): Context = this

        override fun getAttributionSource(): AttributionSource =
            if (Build.VERSION.SDK_INT >= 31) {
                AttributionSource.Builder(Process.myUid()).setPackageName(SHELL_PACKAGE).build()
            } else {
                super.getAttributionSource()
            }
    }

    private val injector: Pair<Any, java.lang.reflect.Method> by lazy {
        // Android 14 moved injection to InputManagerGlobal; older versions keep it on InputManager
        runCatching {
            val g = Class.forName("android.hardware.input.InputManagerGlobal")
            val inst = g.getDeclaredMethod("getInstance").invoke(null)!!
            inst to g.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        }.getOrElse {
            val c = Class.forName("android.hardware.input.InputManager")
            val inst = c.getDeclaredMethod("getInstance").invoke(null)!!
            inst to c.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        }
    }

    /** Injects without waiting for the app to handle it, like a real remote. */
    fun inject(event: InputEvent): Boolean = runCatching {
        val (target, method) = injector
        method.invoke(target, event, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */) as Boolean
    }.getOrDefault(false)
}
