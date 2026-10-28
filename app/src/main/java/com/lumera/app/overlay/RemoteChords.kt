package com.lumera.app.overlay

import android.view.KeyEvent

/** Something the remote asks for by holding two buttons together. */
enum class ChordAction { Diagnostics, GamingHud }

/** Two or more buttons held together for [holdMs]. */
data class Chord(val keys: Set<Int>, val holdMs: Long, val action: ChordAction)

/**
 * Finds the chords in the remote's presses as the TV sees them, before any app does.
 *
 * The first button of a chord goes on to the app as normal: nobody knows yet that a second one
 * is coming. Once the second goes down, both belong to the chord, so the app under it doesn't
 * scroll while they are held. The first button's release still goes through, so the app never
 * sees a press that doesn't end.
 */
class ChordDetector(private val chords: List<Chord>) {
    private val down = HashSet<Int>()
    /** Buttons whose press reached the app, so their release must reach it too. */
    private val passed = HashSet<Int>()
    /** Buttons held for a chord: their repeats and releases stay with the chord. */
    private val held = HashSet<Int>()
    private val watched = chords.flatMap { it.keys }.toSet()

    /** The chord being held, from [armedAt]. */
    var armed: Chord? = null
        private set
    var armedAt: Long = 0L
        private set
    private var fired = false

    /** True when the press is the chord's and the app shouldn't get it. */
    fun onKey(code: Int, action: Int, repeat: Int, time: Long): Boolean {
        if (code !in watched) return false
        if (action == KeyEvent.ACTION_DOWN) {
            if (repeat > 0 || code in down) return code in held
            down += code
            if (armed == null) {
                chords.firstOrNull { code in it.keys && down.containsAll(it.keys) }?.let { chord ->
                    armed = chord
                    armedAt = time
                    fired = false
                    held += chord.keys
                    return true
                }
            }
            if (code in held) return true
            passed += code
            return false
        }
        if (action != KeyEvent.ACTION_UP) return code in held
        down -= code
        val wentThrough = passed.remove(code)
        val chordKey = held.remove(code)
        if (armed?.keys?.contains(code) == true) {
            armed = null
        }
        return chordKey && !wentThrough
    }

    /** The chord held long enough at [now], once per hold. */
    fun due(now: Long): Chord? {
        val chord = armed ?: return null
        if (fired || now - armedAt < chord.holdMs) return null
        fired = true
        return chord
    }

    /** Forgets every button, as when the screen goes off with them held. */
    fun reset() {
        down.clear()
        passed.clear()
        held.clear()
        armed = null
        fired = false
    }

    companion object {
        const val HOLD_MS = 2_000L

        /**
         * Up and Down together for the picture, Left and Right together for the gaming HUD.
         * Holding Home is the TV's own (Google TV opens its panel), so it isn't used.
         */
        val DEFAULT = listOf(
            Chord(setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN), HOLD_MS, ChordAction.Diagnostics),
            Chord(setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT), HOLD_MS, ChordAction.GamingHud)
        )
    }
}
