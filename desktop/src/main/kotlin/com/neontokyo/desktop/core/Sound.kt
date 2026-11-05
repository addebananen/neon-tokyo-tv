package com.neontokyo.desktop.core

import java.io.BufferedInputStream
import java.util.concurrent.Executors
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl

/**
 * The TV's synth sounds (the same files as Neon Home's): a soft tick as the focus moves, a
 * chime on select, and the wake swell under the intro. Played off the UI thread, quietly,
 * and only when Settings has sounds on.
 */
object Sound {
    @Volatile var enabled: Boolean = Store.read("sounds")?.trim() != "off"
        private set

    fun set(on: Boolean) {
        enabled = on
        Store.write("sounds", if (on) "on" else "off")
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "neon-sound").apply { isDaemon = true } }
    private val clips = HashMap<String, Clip>()
    private var lastMove = 0L

    private fun clip(name: String, gainDb: Float): Clip? = clips.getOrPut(name) {
        val stream = Sound::class.java.getResourceAsStream("/art/$name.wav") ?: return null
        val audio = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
        AudioSystem.getClip().apply {
            open(audio)
            runCatching { (getControl(FloatControl.Type.MASTER_GAIN) as FloatControl).value = gainDb }
        }
    }

    private fun play(name: String, gainDb: Float) {
        if (!enabled) return
        worker.execute {
            runCatching {
                val c = clip(name, gainDb) ?: return@runCatching
                c.stop()
                c.framePosition = 0
                c.start()
            }
        }
    }

    /** A tick, at most every 45 ms so holding an arrow key does not buzz. */
    fun move() {
        val now = System.currentTimeMillis()
        if (now - lastMove < 45) return
        lastMove = now
        play("nt_ui_move", -14f)
    }

    fun select() = play("nt_ui_select", -10f)
    fun wake() = play("nt_ui_wake", -8f)
}
