package com.lumera.app.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.lumera.app.R

/**
 * The home screen's little synth sounds: a soft tick as the focus moves, a blip on OK and a
 * city-pop chime when a console wakes. Quiet, and off in Settings › Personalization for anyone
 * who wants the TV silent. Made by tools/sounds/make_sounds.py.
 */
internal class HomeSounds(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ready = mutableSetOf<Int>()
    private val move: Int
    private val select: Int
    private val wake: Int
    private var lastMove = 0L

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        move = pool.load(context, R.raw.nt_ui_move, 1)
        select = pool.load(context, R.raw.nt_ui_select, 1)
        wake = pool.load(context, R.raw.nt_ui_wake, 1)
    }

    /** The focus moved. Held arrows tick at most every 60 ms, so a fast run stays a soft patter. */
    fun move() {
        val now = SystemClock.uptimeMillis()
        if (now - lastMove < 60) return
        lastMove = now
        play(move, 0.32f)
    }

    fun select() = play(select, 0.45f)

    fun wake() = play(wake, 0.5f)

    fun release() = pool.release()

    private fun play(id: Int, volume: Float) {
        if (id !in ready || !enabled(prefs)) return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    companion object {
        private const val PREFS = "neon_home"
        private const val KEY = "sounds"

        private fun enabled(prefs: android.content.SharedPreferences) = prefs.getBoolean(KEY, true)

        fun enabled(context: Context) = enabled(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        }
    }
}
