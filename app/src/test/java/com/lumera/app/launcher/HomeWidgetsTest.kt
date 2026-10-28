package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HomeWidgetsTest {
    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    @Test
    fun `weeks are counted the Swedish way`() {
        assertEquals(44, HomeWidgets.week(at(2026, 10, 28)))
        // 1 January 2027 is a Friday: still week 53 of 2026
        assertEquals(53, HomeWidgets.week(at(2027, 1, 1)))
        assertEquals(1, HomeWidgets.week(at(2027, 1, 4)))
    }

    @Test
    fun `widgets sit in their own order and shortcuts need an app`() {
        val setup = WidgetSetup(kinds = setOf(WidgetKind.Network, WidgetKind.Weather, WidgetKind.Shortcuts))
        assertEquals(listOf(WidgetKind.Weather, WidgetKind.Network), setup.shown)
        assertEquals(
            listOf(WidgetKind.Weather, WidgetKind.Network, WidgetKind.Shortcuts),
            setup.copy(shortcuts = listOf("com.spotify.tv.android")).shown
        )
    }

    @Test
    fun `wifi bars follow the signal`() {
        assertEquals(3, wifiLevel(-50))
        assertEquals(2, wifiLevel(-65))
        assertEquals(1, wifiLevel(-75))
        assertEquals(0, wifiLevel(-90))
        assertEquals(3, wifiLevel(Int.MIN_VALUE))
    }
}
