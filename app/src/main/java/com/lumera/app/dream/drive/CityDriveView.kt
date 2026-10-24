package com.lumera.app.dream.drive

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.widget.FrameLayout
import com.lumera.app.R
import com.lumera.app.dream.NeonNightView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The night drive: a car cruising an endless neon Tokyo, drawn with OpenGL on a surface of its
 * own, and the clock over it as an ordinary view. A GPU that can't run it gets the Neon Tokyo
 * night instead.
 */
class CityDriveView(context: Context) : FrameLayout(context) {

    private val fonts = DriveFonts(
        jp = font(R.font.neon_jp, Typeface.DEFAULT_BOLD),
        medium = font(R.font.kanit_medium, Typeface.DEFAULT),
        mediumItalic = font(R.font.kanit_medium_italic, Typeface.DEFAULT),
        semiboldItalic = font(R.font.kanit_semibold_italic, Typeface.DEFAULT_BOLD)
    )
    private val surface = GLSurfaceView(context)
    private val hud = DriveHud(context, fonts)
    private var failed = false

    init {
        setBackgroundColor(Color.BLACK)
        surface.setEGLContextClientVersion(2)
        surface.setEGLConfigChooser(ConfigChooser())
        surface.preserveEGLContextOnPause = true
        surface.setRenderer(Loop(Random.nextInt(), DISTRICTS.random().key))
        surface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        // The city is drawn at 720 lines and scaled up by the display hardware, which keeps a TV's
        // GPU at an even pace; the clock over it stays sharp at the screen's own resolution
        val metrics = resources.displayMetrics
        if (metrics.heightPixels > RENDER_LINES && metrics.widthPixels > metrics.heightPixels) {
            val w = (RENDER_LINES.toDouble() * metrics.widthPixels / metrics.heightPixels).roundToInt()
            surface.holder.setFixedSize(w, RENDER_LINES)
        }
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** For an activity showing it: the GL thread rests while it is in the background. */
    fun onPause() {
        if (!failed) surface.onPause()
    }

    fun onResume() {
        if (!failed) surface.onResume()
    }

    private fun font(resId: Int, fallback: Typeface): Typeface =
        runCatching { resources.getFont(resId) }.getOrNull() ?: fallback

    /** Steps aside for the Neon Tokyo night, on the UI thread. */
    private fun fail(e: Throwable) {
        Log.e(TAG, "the night drive can't run here, showing the skyline", e)
        post {
            if (failed) return@post
            failed = true
            removeAllViews()
            addView(NeonNightView(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    /**
     * The GL thread: shows black at once while another thread paints the art, then builds the
     * city and drives. The art goes into textures and its bitmaps are let go of; if the context is
     * lost, the art is painted again for the new one and the drive carries on where it was.
     */
    private inner class Loop(private val seed: Int, private val firstDistrict: String) : GLSurfaceView.Renderer {
        private var renderer: DriveRenderer? = null
        private var world: World? = null
        @Volatile private var painted: DriveArt? = null
        @Volatile private var paintFailure: Throwable? = null
        private var checks = 0
        private var width = 0
        private var height = 0
        private var last = 0L
        private var fade = 0.0
        private var broken = false

        init {
            paint()
        }

        private fun paint() {
            Thread({
                try {
                    val start = System.nanoTime()
                    painted = DriveArt.build(fonts)
                    Log.i(TAG, "art painted in ${(System.nanoTime() - start) / 1_000_000} ms")
                } catch (e: Throwable) {
                    paintFailure = e
                }
            }, "CityDriveArt").start()
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            // A new context: whatever was in the old one is gone, the art included
            if (renderer != null) {
                renderer = null
                paint()
            }
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            width = w
            height = h
            renderer?.resize(w, h)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (broken) return clear()
            try {
                val r = renderer
                if (r == null) {
                    paintFailure?.let { throw it }
                    val art = painted ?: return clear()
                    painted = null
                    try {
                        if (world == null) world = World(seed, art, firstDistrict).also { hud.world = it }
                        renderer = DriveRenderer(art).also { it.resize(width, height) }
                    } finally {
                        art.recycle()
                    }
                    checks = 0
                    last = 0L
                    fade = 0.0
                    return clear()
                }
                val w = world ?: return
                val now = System.nanoTime()
                val dt = if (last == 0L) 1 / 60.0 else min(0.05, (now - last) / 1e9)
                last = now
                w.update(dt)
                fade = min(1.0, fade + dt / 1.2)
                r.render(w, smooth(fade))
                // A driver that can't draw this says so in the first frames
                if (checks < 3) {
                    checks++
                    val error = GLES20.glGetError()
                    check(error == GLES20.GL_NO_ERROR) { "GL error 0x" + Integer.toHexString(error) }
                }
            } catch (e: Throwable) {
                broken = true
                renderer = null
                fail(e)
                clear()
            }
        }

        private fun clear() {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
    }

    /**
     * Eight bits a colour with a depth buffer, smoothed with 4x multisampling where the GPU has
     * it, and less where it hasn't.
     */
    private class ConfigChooser : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            for (attribs in CHOICES) {
                val count = IntArray(1)
                if (!egl.eglChooseConfig(display, attribs, null, 0, count) || count[0] <= 0) continue
                val configs = arrayOfNulls<EGLConfig>(count[0])
                if (!egl.eglChooseConfig(display, attribs, configs, configs.size, count)) continue
                val found = configs.take(count[0]).filterNotNull()
                fun size(c: EGLConfig, attr: Int): Int {
                    val v = IntArray(1)
                    return if (egl.eglGetConfigAttrib(display, c, attr, v)) v[0] else 0
                }
                val exact = found.firstOrNull {
                    size(it, EGL10.EGL_RED_SIZE) == 8 && size(it, EGL10.EGL_GREEN_SIZE) == 8 && size(it, EGL10.EGL_BLUE_SIZE) == 8
                }
                (exact ?: found.firstOrNull())?.let { return it }
            }
            throw IllegalStateException("no EGL config for OpenGL ES 2")
        }

        private companion object {
            const val ES2 = EGL14.EGL_OPENGL_ES2_BIT
            val CHOICES = arrayOf(
                intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_RENDERABLE_TYPE, ES2, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE
                ),
                intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_RENDERABLE_TYPE, ES2, EGL10.EGL_NONE
                ),
                intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_RENDERABLE_TYPE, ES2, EGL10.EGL_NONE
                ),
                intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, ES2, EGL10.EGL_NONE)
            )
        }
    }

    private companion object {
        const val TAG = "CityDrive"
        const val RENDER_LINES = 720
    }
}
