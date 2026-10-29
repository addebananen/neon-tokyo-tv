package com.lumera.app.control

import android.view.KeyEvent

/** What a press of Home turns into while the Control Center listens for a hold. */
enum class HomeStep {
    /** Not ours: it goes on to the TV as it came. */
    Pass,

    /** Held back while it is not yet known whether it is a press or a hold. */
    Swallow,

    /** Let go before it became a hold: the TV gets its Home after all. */
    PressHome
}

/**
 * Tells a held Home from a pressed one, as the TV sees the remote before any app does.
 *
 * Google TV keeps a held Home for its own panel, so the press can't go on to the TV and be taken
 * back later: Home goes down and nobody knows yet what it is. It is held back until it is let go,
 * a press that the TV then gets as Home, or until it was held for [holdMs], when it opens the
 * Control Center and the rest of it stays here.
 */
class HomeHold(private val holdMs: Long = HOLD_MS) {
    private var downAt = -1L
    private var fired = false

    /** Home is down and was not let go yet. */
    val holding: Boolean get() = downAt >= 0

    fun onKey(action: Int, repeat: Int, time: Long): HomeStep {
        if (action == KeyEvent.ACTION_DOWN) {
            if (repeat == 0) {
                downAt = time
                fired = false
                return HomeStep.Swallow
            }
            // Repeats of a press whose start went by (the service came on mid-press) are the TV's
            return if (downAt >= 0) HomeStep.Swallow else HomeStep.Pass
        }
        if (action != KeyEvent.ACTION_UP || downAt < 0) return HomeStep.Pass
        val wasHold = fired || time - downAt >= holdMs
        downAt = -1L
        fired = false
        return if (wasHold) HomeStep.Swallow else HomeStep.PressHome
    }

    /** True once, when Home has been held long enough at [now]. */
    fun due(now: Long): Boolean {
        if (downAt < 0 || fired || now - downAt < holdMs) return false
        fired = true
        return true
    }

    /** When the hold is due, from the moment Home went down. */
    fun dueAt(): Long = if (downAt < 0 || fired) -1L else downAt + holdMs

    fun reset() {
        downAt = -1L
        fired = false
    }

    companion object {
        /** About as long as Apple TV waits on its TV button, a little less than Google TV's own. */
        const val HOLD_MS = 600L
    }
}

/** The buttons of the Control Center's front page. */
enum class CcItem { Profile, Power, Wifi, Quiet, Audio, Sleep, Screensaver, Diagnostics, Gaming, Settings }

/** Where a button sits on the page's grid, in cells: four across, the header a row above. */
data class CcCell(val item: CcItem, val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
}

enum class Direction { Up, Down, Left, Right }

/**
 * The Control Center's layout and how the remote moves round it. The page is laid out as Apple
 * TV's is: the profile at the top, Power large beside Wi-Fi and Do Not Disturb, the sound and
 * the sleep timer under them, and a row of round buttons at the foot.
 */
object CcLayout {
    val CELLS = listOf(
        CcCell(CcItem.Profile, 2f, -1f, 2f, 1f),
        CcCell(CcItem.Power, 0f, 0f, 2f, 2f),
        CcCell(CcItem.Wifi, 2f, 0f, 2f, 1f),
        CcCell(CcItem.Quiet, 2f, 1f, 2f, 1f),
        CcCell(CcItem.Audio, 0f, 2f, 2f, 1f),
        CcCell(CcItem.Sleep, 2f, 2f, 2f, 1f),
        CcCell(CcItem.Screensaver, 0f, 3f, 1f, 1f),
        CcCell(CcItem.Diagnostics, 1f, 3f, 1f, 1f),
        CcCell(CcItem.Gaming, 2f, 3f, 1f, 1f),
        CcCell(CcItem.Settings, 3f, 3f, 1f, 1f)
    )

    /** Where the focus lands as the page opens: the sound, the thing most often changed. */
    val FIRST = CcItem.Audio

    fun cell(item: CcItem): CcCell = CELLS.first { it.item == item }

    /**
     * The button the remote reaches from [from] going [dir]: the nearest one that lies wholly that
     * way, the one in line with it before one off to the side, and the closer of two in line.
     */
    fun move(from: CcItem, dir: Direction, cells: List<CcCell> = CELLS): CcItem? {
        val a = cells.firstOrNull { it.item == from } ?: return null
        val e = 0.001f
        return cells.asSequence()
            .filter { it.item != from }
            .filter {
                when (dir) {
                    Direction.Right -> it.x >= a.right - e
                    Direction.Left -> it.right <= a.x + e
                    Direction.Down -> it.y >= a.bottom - e
                    Direction.Up -> it.bottom <= a.y + e
                }
            }
            .map { b ->
                val horizontal = dir == Direction.Left || dir == Direction.Right
                val gap = when (dir) {
                    Direction.Right -> b.x - a.right
                    Direction.Left -> a.x - b.right
                    Direction.Down -> b.y - a.bottom
                    Direction.Up -> a.y - b.bottom
                }
                // How far apart they lie across the way of travel: nothing while they overlap
                val side = if (horizontal) apart(a.y, a.bottom, b.y, b.bottom) else apart(a.x, a.right, b.x, b.right)
                val centre = if (horizontal) kotlin.math.abs(a.cy - b.cy) else kotlin.math.abs(a.cx - b.cx)
                Triple(b, gap + 2f * side, centre)
            }
            .sortedWith(compareBy<Triple<CcCell, Float, Float>> { it.second }.thenBy { it.third })
            .firstOrNull()?.first?.item
    }

    private fun apart(a0: Float, a1: Float, b0: Float, b1: Float): Float = when {
        b0 >= a1 -> b0 - a1
        a0 >= b1 -> a0 - b1
        else -> 0f
    }
}

/** The next row that takes the focus, from [index] by [step], or [index] when there is none. */
fun nextRow(selectable: List<Boolean>, index: Int, step: Int): Int {
    var i = index + step
    while (i in selectable.indices) {
        if (selectable[i]) return i
        i += step
    }
    return index
}

/** The first row that takes the focus, or 0. */
fun firstRow(selectable: List<Boolean>, preferred: Int = 0): Int =
    if (preferred in selectable.indices && selectable[preferred]) preferred
    else selectable.indexOfFirst { it }.coerceAtLeast(0)

/** A network the TV can see. */
data class WifiScan(val ssid: String, val rssi: Int, val secure: Boolean, val frequency: Int)

object NetRules {
    /** Four bars as the TV draws them, from the signal's strength in dBm. */
    fun bars(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    fun band(frequency: Int): String? = when (frequency) {
        in 2_400..2_500 -> "2.4 GHz"
        in 4_900..5_900 -> "5 GHz"
        in 5_925..7_125 -> "6 GHz"
        else -> null
    }

    /** A network's name as Android gives it, without its quotes, or null when it is hidden from the app. */
    fun ssid(raw: String?): String? {
        val name = raw?.trim()?.removeSurrounding("\"") ?: return null
        return name.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
    }

    /**
     * The networks to list: each name once, at its strongest, the one the TV is on first, then by
     * strength. Hidden networks have no name to list.
     */
    fun networks(scans: List<WifiScan>, current: String?): List<WifiScan> =
        scans.filter { it.ssid.isNotBlank() }
            .groupBy { it.ssid }
            .map { (_, same) -> same.maxBy { it.rssi } }
            .sortedWith(compareByDescending<WifiScan> { it.ssid == current }.thenByDescending { it.rssi }.thenBy { it.ssid.lowercase() })
}

object SleepRules {
    /** The sleep timer's choices in minutes; 0 is off. */
    val CHOICES = listOf(0, 15, 30, 45, 60, 90, 120)

    /** The last minute is a warning, which any button cuts short. */
    const val WARN_MS = 60_000L

    fun label(minutes: Int): String = when {
        minutes <= 0 -> "Off"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** What is left, as the button shows it: 1:05:00, 42:10 or 0:59. */
    fun left(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) + 999L) / 1000L
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
