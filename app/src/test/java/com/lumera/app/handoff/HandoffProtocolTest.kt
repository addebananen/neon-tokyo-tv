package com.lumera.app.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffProtocolTest {
    // As the web app (web/js/handoff.js) writes it
    private val sent = """{"v":1,"nonce":"n1","to":"*","from":"iPhone","profile":"Adrian","sentAt":1000000,
        "id":"tt0903747","type":"series","video":"tt0903747:2:3","name":"Breaking Bad","title":"S2:E3 - Breaking Bad",
        "poster":"https://img/p.jpg","background":"","logo":"","positionMs":1234567,"durationMs":2820000}"""

    @Test
    fun readsWhatThePhoneSends() {
        val m = HandoffProtocol.parseMessage(sent)!!
        assertEquals("tt0903747", m.id)
        assertEquals("tt0903747:2:3", m.video)
        assertEquals("series", m.type)
        assertEquals(1_234_567L, m.positionMs)
        assertEquals(2_820_000L, m.durationMs)
        assertEquals("Adrian", m.profile)
        assertTrue(m.isFor("any-tv-id"))
        assertNull(m.takenBy)
    }

    @Test
    fun onlyFreshMessagesCount() {
        val m = HandoffProtocol.parseMessage(sent)!!
        assertTrue(m.isFresh(1_000_000L + 60_000L))
        assertFalse(m.isFresh(1_000_000L + HandoffProtocol.MAX_AGE_MS + 1))
    }

    @Test
    fun aMessageForAnotherTvIsLeftAlone() {
        val m = HandoffProtocol.parseMessage(sent.replace("\"to\":\"*\"", "\"to\":\"tv-b\""))!!
        assertFalse(m.isFor("tv-a"))
        assertTrue(m.isFor("tv-b"))
    }

    @Test
    fun takingKeepsTheMessageAndNamesTheTv() {
        val taken = HandoffProtocol.markTaken(sent, "tv-a", "Vardagsrum", 2_000_000L)!!
        val m = HandoffProtocol.parseMessage(taken)!!
        assertEquals("tv-a", m.takenBy)
        assertEquals("Vardagsrum", m.takenName)
        assertEquals("tt0903747:2:3", m.video)
    }

    @Test
    fun notAMessage() {
        assertNull(HandoffProtocol.parseMessage("Breaking Bad"))
        assertNull(HandoffProtocol.parseMessage("""{"v":2,"id":"x","nonce":"y"}"""))
        assertNull(HandoffProtocol.parseMessage(null))
    }

    @Test
    fun presenceKeepsOtherTvsAndDropsOldOnes() {
        val now = 10L * 24 * 3_600_000L
        var name = HandoffProtocol.withTv(null, HandoffProtocol.Tv("a", "Vardagsrum", now - 8L * 24 * 3_600_000L, false), now)
        name = HandoffProtocol.withTv(name, HandoffProtocol.Tv("b", "Sovrum", now - 1000, true), now)
        name = HandoffProtocol.withTv(name, HandoffProtocol.Tv("c", "Kök", now, true), now)
        val tvs = HandoffProtocol.parsePresence(name).associateBy { it.id }
        assertNull(tvs["a"])
        assertEquals("Sovrum", tvs["b"]?.name)
        assertTrue(tvs["c"]!!.on)
    }

    @Test
    fun hiddenItemNeverShowsInStremio() {
        val item = HandoffProtocol.hiddenItem(HandoffProtocol.MAILBOX_ID, sent, 1_000_000L)
        assertEquals("other", item.get("type").asString)
        assertTrue(item.get("removed").asBoolean)
        assertTrue(item.get("temp").asBoolean)
        assertEquals(0, item.getAsJsonObject("state").get("timeOffset").asInt)
        assertNotNull(HandoffProtocol.parseMessage(item.get("name").asString))
        assertEquals(1_000_000L, HandoffProtocol.parseIso(item.get("_mtime").asString))
    }

    @Test
    fun profileByNameElseTheOneWatching() {
        val profiles = listOf(1 to "Adrian", 2 to " barn ")
        assertEquals(2, HandoffProtocol.pickProfile("Barn", profiles, { it.second }, profiles[0])?.first)
        assertEquals(1, HandoffProtocol.pickProfile("Okänd", profiles, { it.second }, profiles[0])?.first)
        assertEquals(1, HandoffProtocol.pickProfile("", profiles, { it.second }, null)?.first)
    }
}
