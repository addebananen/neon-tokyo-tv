package com.lumera.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.lumera.app.launcher.RecentApps
import java.util.concurrent.Executors

/**
 * The app switcher's pictures: while an app is in front, the overlay service takes a small
 * picture of the screen now and then, so the app's card shows where it was left, as on Apple TV.
 * Pictures stay on the TV, in Neon Tokyo's cache, one per app, and go when the app is closed.
 *
 * A film behind copy protection comes out black; such a picture is not kept, and the card keeps
 * the last one that showed something, or the app's banner.
 */
internal class SwitcherShots(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "switcher-shots").apply { isDaemon = true }
    }
    private var current: String? = null
    private var busy = false
    private val tick = Runnable { capture() }

    /** [pkg] came to the front: the app is recorded, and its pictures start a moment later. */
    fun foreground(pkg: String) {
        if (pkg == current) return
        handler.removeCallbacks(tick)
        current = null
        if (pkg == service.packageName || !RecentApps.switchable(service, pkg)) return
        RecentApps.seen(service, pkg)
        if (Build.VERSION.SDK_INT < 30) return
        current = pkg
        handler.postDelayed(tick, FIRST_MS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        current = null
        worker.shutdown()
    }

    private fun capture() {
        val pkg = current ?: return
        if (Build.VERSION.SDK_INT < 30 || busy) return
        busy = true
        runCatching { take(pkg) }.onFailure {
            busy = false
            Log.w(TAG, "No picture: ${it.javaClass.simpleName}")
        }
    }

    @RequiresApi(30)
    private fun take(pkg: String) {
        service.takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(shot: AccessibilityService.ScreenshotResult) {
                val buffer = shot.hardwareBuffer
                try {
                    val whole = Bitmap.wrapHardwareBuffer(buffer, shot.colorSpace)
                    val small = whole?.let { shrink(it) }
                    whole?.recycle()
                    if (small != null) {
                        if (!blank(small)) RecentApps.keepShot(service, pkg, small)
                        small.recycle()
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Picture of $pkg failed: ${e.javaClass.simpleName}")
                } finally {
                    buffer.close()
                }
                handler.post { done(pkg, EVERY_MS) }
            }

            override fun onFailure(errorCode: Int) {
                // Asked too soon after the last one, or not allowed: try again later
                handler.post { done(pkg, if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) 2_000L else EVERY_MS * 3) }
            }
        })
    }

    private fun done(pkg: String, next: Long) {
        busy = false
        if (current == pkg) handler.postDelayed(tick, next)
    }

    /**
     * The picture at the size of a card, scaled on the graphics chip: copying a 4K screen into
     * memory whole first would take 33 MB each time on a TV that has little to spare.
     */
    @RequiresApi(30)
    private fun shrink(hardware: Bitmap): Bitmap? {
        val w = WIDTH
        val h = (WIDTH * hardware.height / hardware.width.coerceAtLeast(1)).coerceAtLeast(1)
        return runCatching { shrinkOnChip(hardware, w, h) }.getOrNull()
            ?: runCatching {
                val soft = hardware.copy(Bitmap.Config.ARGB_8888, false)
                Bitmap.createScaledBitmap(soft, w, h, true).also { if (it !== soft) soft.recycle() }
            }.getOrNull()
    }

    @RequiresApi(30)
    private fun shrinkOnChip(hardware: Bitmap, w: Int, h: Int): Bitmap? {
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val node = RenderNode("switcher-shot")
        val renderer = HardwareRenderer()
        try {
            node.setPosition(0, 0, w, h)
            val canvas = node.beginRecording()
            canvas.drawBitmap(hardware, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: return null
            return image.use {
                val buffer = it.hardwareBuffer ?: return null
                try {
                    Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))?.let { onChip ->
                        onChip.copy(Bitmap.Config.ARGB_8888, false).also { onChip.recycle() }
                    }
                } finally {
                    buffer.close()
                }
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }

    /** All but black: a protected film, or the screen between two pages. */
    private fun blank(bitmap: Bitmap): Boolean {
        var brightest = 0
        for (gy in 1..7) for (gx in 1..11) {
            val c = bitmap.getPixel(bitmap.width * gx / 12, bitmap.height * gy / 8)
            val l = ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
            if (l > brightest) brightest = l
        }
        return brightest < 14
    }

    companion object {
        private const val TAG = "SwitcherShots"

        /** The width of a picture: about the size its card has on the screen. */
        private const val WIDTH = 768

        /** The app has a moment to draw itself after coming to the front. */
        private const val FIRST_MS = 2_500L

        /** How often a picture is taken while the same app stays in front. */
        private const val EVERY_MS = 15_000L
    }
}
