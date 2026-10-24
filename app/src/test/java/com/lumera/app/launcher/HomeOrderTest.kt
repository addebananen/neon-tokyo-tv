package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOrderTest {

    @Test
    fun withNothingSavedTheDefaultOrderStays() {
        assertEquals(listOf("a", "b", "c"), HomeOrder.arrange(listOf("a", "b", "c"), emptyList()) { it })
    }

    @Test
    fun theSavedOrderWinsAndNewItemsGoLast() {
        val items = listOf("youtube", "netflix", "spotify", "new")
        assertEquals(
            listOf("spotify", "youtube", "netflix", "new"),
            HomeOrder.arrange(items, listOf("spotify", "youtube", "netflix")) { it }
        )
    }

    @Test
    fun appsTakenOffTheTvDropOutOfTheSavedOrder() {
        assertEquals(listOf("b", "a"), HomeOrder.arrange(listOf("a", "b"), listOf("gone", "b", "a")) { it })
    }

    @Test
    fun movingSlidesTheOthersOver() {
        assertEquals(listOf("b", "c", "a", "d"), HomeOrder.move(listOf("a", "b", "c", "d"), 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), HomeOrder.move(listOf("a", "b", "c", "d"), 3, 0))
    }

    @Test
    fun movingPastTheEndDoesNothing() {
        val list = listOf("a", "b")
        assertEquals(list, HomeOrder.move(list, 1, 2))
        assertEquals(list, HomeOrder.move(list, 0, -1))
    }
}
