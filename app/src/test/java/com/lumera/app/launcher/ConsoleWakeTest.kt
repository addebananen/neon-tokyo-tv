package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsoleWakeTest {
    private val order = listOf("pc", "switch2", "xbox360", "wiiu", "wii", "xbox")

    @Test
    fun aConsoleSwitchedOnWakes() {
        assertEquals("switch2", ConsoleWake.woke(order, emptySet(), setOf("switch2")))
        assertEquals("wiiu", ConsoleWake.woke(order, setOf("pc"), setOf("pc", "wiiu")))
    }

    @Test
    fun oneAlreadyOnOrSwitchedOffDoesNot() {
        assertNull(ConsoleWake.woke(order, setOf("pc"), setOf("pc")))
        assertNull(ConsoleWake.woke(order, setOf("pc", "wii"), setOf("pc")))
        assertNull(ConsoleWake.woke(order, emptySet(), emptySet()))
    }

    @Test
    fun consolesWakingTogetherGoInTheirOrderOnKallor() {
        // Wii and Xbox on one HDMI switch light up together; the one placed first wins
        assertEquals("wii", ConsoleWake.woke(order, emptySet(), setOf("xbox", "wii")))
        assertEquals("xbox", ConsoleWake.woke(listOf("xbox", "wii"), emptySet(), setOf("wii", "xbox")))
    }
}
