package com.lumera.app.launcher

import android.content.Context

/**
 * Where Adrian has put the tiles: one saved order per row, as a list of keys (an app's package,
 * a console's id). Holding OK on a tile for three seconds lifts it, and the arrows move it.
 */
class HomeOrder(context: Context) {

    enum class Row(val key: String) { Favourites("order.favourites"), Consoles("order.consoles"), Apps("order.apps") }

    private val prefs = context.getSharedPreferences("neon_home", Context.MODE_PRIVATE)

    fun load(row: Row): List<String> =
        prefs.getString(row.key, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    fun save(row: Row, keys: List<String>) {
        prefs.edit().putString(row.key, keys.joinToString("\n")).apply()
    }

    companion object {
        /**
         * [items] in the [saved] order; anything the saved order doesn't know yet, like an app
         * put on the TV since, keeps its place relative to the default order at the end.
         */
        fun <T> arrange(items: List<T>, saved: List<String>, keyOf: (T) -> String): List<T> {
            if (saved.isEmpty()) return items
            val byKey = items.associateBy(keyOf)
            val placed = saved.mapNotNull { byKey[it] }.distinct()
            val placedSet = placed.toSet()
            return placed + items.filter { it !in placedSet }
        }

        /** [list] with the item at [from] moved to [to], or [list] itself when [to] is off the end. */
        fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
            if (from !in list.indices || to !in list.indices || from == to) return list
            return list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
}
