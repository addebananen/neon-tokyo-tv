package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitcherRulesTest {

    @Test
    fun anAppThatComesToTheFrontGoesFirstOnce() {
        val before = listOf(RecentApp("a", 1), RecentApp("b", 2), RecentApp("c", 3))
        assertEquals(listOf("b", "a", "c"), SwitcherRules.touch(before, "b", 9).map { it.packageName })
        assertEquals(9L, SwitcherRules.touch(before, "b", 9).first().at)
    }

    @Test
    fun theOldestDropOffPastTheMost() {
        val many = (1..SwitcherRules.MAX).map { RecentApp("p$it", it.toLong()) }
        val after = SwitcherRules.touch(many, "new", 99)
        assertEquals(SwitcherRules.MAX, after.size)
        assertEquals("new", after.first().packageName)
        assertTrue(after.none { it.packageName == "p${SwitcherRules.MAX}" })
    }

    @Test
    fun theListSurvivesBeingSaved() {
        val list = listOf(RecentApp("com.netflix.ninja", 5), RecentApp("com.neontokyo.tv", 3))
        assertEquals(list, SwitcherRules.decode(SwitcherRules.encode(list)))
        assertEquals(emptyList<RecentApp>(), SwitcherRules.decode(null))
        assertEquals(listOf(RecentApp("a", 0)), SwitcherRules.decode("a\n\na nonsense"))
    }

    @Test
    fun theNewestIsOnTheRightWhereTheFocusStarts() {
        val list = listOf(RecentApp("newest", 3), RecentApp("older", 2), RecentApp("oldest", 1))
        assertEquals(listOf("oldest", "older", "newest"), SwitcherRules.onScreen(list).map { it.packageName })
    }

    @Test
    fun closingTheCardInFrontHandsTheFocusToItsLeftNeighbour() {
        assertEquals(1, SwitcherRules.afterClose(2, 3))
        // The oldest has nothing on its left: the next one along comes
        assertEquals(0, SwitcherRules.afterClose(0, 3))
        assertEquals(0, SwitcherRules.afterClose(0, 1))
    }

    @Test
    fun theCardInFrontIsWholeAndTheOthersStepBack() {
        val front = SwitcherRules.pose(0f)
        assertEquals(0f, front.x, 0.0001f)
        assertEquals(1f, front.scale, 0.0001f)
        assertEquals(0f, front.shade, 0.0001f)
        // The next one peeks in from the right, past a gap
        assertEquals(1f + SwitcherRules.GAP, SwitcherRules.pose(1f).x, 0.0001f)
        // The older ones lie stacked a little to the left, smaller, darker and behind
        val back1 = SwitcherRules.pose(-1f)
        val back2 = SwitcherRules.pose(-2f)
        assertEquals(-SwitcherRules.STACK, back1.x, 0.0001f)
        assertTrue(back2.x < back1.x && back2.x > back1.x - SwitcherRules.STACK)
        assertTrue(back2.scale < back1.scale && back1.scale < 1f)
        assertTrue(back2.z < back1.z && back1.z < front.z)
        assertTrue(back1.shade > 0f)
        // Far back they have faded away
        assertEquals(0f, SwitcherRules.pose(-SwitcherRules.DEEP).alpha, 0.0001f)
    }

    @Test
    fun aCardGlidesWithoutJumpsBetweenPlaces() {
        var d = -3f
        var last = SwitcherRules.pose(d)
        while (d < 2f) {
            d += 0.01f
            val now = SwitcherRules.pose(d)
            assertTrue("x jumps at $d", kotlin.math.abs(now.x - last.x) < 0.03f)
            assertTrue("scale jumps at $d", kotlin.math.abs(now.scale - last.scale) < 0.01f)
            last = now
        }
    }
}
