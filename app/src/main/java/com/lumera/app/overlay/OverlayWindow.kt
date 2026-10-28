package com.lumera.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A window drawn over whatever the TV shows, the film, an app or a console, from the overlay
 * service. It never takes the focus or a press: the remote keeps working the app beneath it.
 *
 * [content] gets whether it is showing, so it can animate in and out; [hide] waits for the way
 * out before the window goes. One window lives for one showing.
 */
internal class OverlayWindow(
    private val context: Context,
    private val gravity: Int,
    private val exitMs: Long,
    private val content: @Composable (shown: Boolean) -> Unit
) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val shown: MutableState<Boolean> = mutableStateOf(false)
    private var view: ComposeView? = null
    private var leaving = false

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    /** On screen and not on its way out. */
    val isShowing: Boolean get() = view != null && !leaving

    fun show(): Boolean {
        if (view != null) return true
        saved.performAttach()
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // Right-aligned in a window as wide as the screen: a window that wraps its content is
            // measured at the narrow dialog width first, which squeezed the HUD's chips
            setContent { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) { content(shown.value) } }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = this@OverlayWindow.gravity
            title = "NeonOverlay"
        }
        return runCatching { windows.addView(compose, params) }
            .onSuccess {
                view = compose
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                // A frame first at rest, so the way in animates
                handler.post { shown.value = true }
            }
            .onFailure {
                Log.w("OverlayWindow", "Could not show the overlay", it)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            .isSuccess
    }

    /** Animates out, then the window goes. [then] runs once it is gone. */
    fun hide(then: () -> Unit = {}) {
        val compose = view ?: return then()
        if (leaving) return
        if (!shown.value) {
            // Never got as far as showing
            handler.removeCallbacksAndMessages(null)
            remove(compose)
            return then()
        }
        shown.value = false
        leaving = true
        handler.postDelayed({ remove(compose); then() }, exitMs)
    }

    /** Gone at once, as when the service stops. */
    fun close() {
        handler.removeCallbacksAndMessages(null)
        view?.let { remove(it) }
    }

    private fun remove(compose: ComposeView) {
        if (view !== compose) return
        view = null
        leaving = false
        runCatching { windows.removeViewImmediate(compose) }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    companion object {
        const val RIGHT = Gravity.END or Gravity.CENTER_VERTICAL
        const val TOP_RIGHT = Gravity.END or Gravity.TOP
    }
}
