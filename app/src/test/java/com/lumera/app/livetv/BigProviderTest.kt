package com.lumera.app.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A provider the size of a real one: forty thousand channels from every country and a guide for
 * fifteen thousand of them. The household's service froze the TV on "Tuning in the channels";
 * these keep the work that runs before the list shows within what a TV can do.
 */
class BigProviderTest {

    private val channelCount = 40_000
    private val guided = 15_000

    private fun streamsJson(): String = buildString {
        append('[')
        for (i in 1..channelCount) {
            if (i > 1) append(',')
            val country = listOf("SE", "UK", "DE", "AR", "US", "FR", "PL", "TR")[i % 8]
            val name = when (i) {
                5 -> "SE: SVT1 FHD"
                6 -> "SE: TV4 HD"
                7 -> "SE: TV4 Hockey"
                else -> "$country: Channel $i ${listOf("HD", "FHD", "SD", "4K")[i % 4]}"
            }
            val epg = if (i <= guided) "ch$i.$country" else ""
            append("""{"num":$i,"name":"$name","stream_type":"live","stream_id":$i,"stream_icon":"http://logo.example/$i.png",""")
            append(""""epg_channel_id":"$epg","added":"1700000000","category_id":"${i % 600}","tv_archive":0}""")
        }
        append(']')
    }

    private val categories = (0 until 600).associateTo(LinkedHashMap()) { it.toString() to "Group $it" }

    @Test
    fun `forty thousand channels are read and indexed quickly`() {
        val json = streamsJson()
        val started = System.nanoTime()
        var reported = 0
        val channels = XtreamApi.readStreams(StringReader(json), categories, onProgress = { reported = it }) { "http://tv.example/live/u/p/$it.ts" }
        val index = ChannelIndex.build(channels)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("read and indexed ${channels.size} channels in $ms ms")
        assertEquals(channelCount, channels.size)
        assertEquals(channelCount, reported)
        assertEquals(600, index.groups.size)
        assertEquals(listOf("SE: SVT1 FHD", "SE: TV4 HD", "SE: TV4 Hockey"), index.popular.map { it.name })
        assertTrue("svt1" in index.searchable[4])
        // A TV is several times slower than a build machine; this used to run on its main thread
        assertTrue("indexing took $ms ms", ms < 6_000)
    }

    @Test
    fun `sport groups over a big guide are worked out quickly`() {
        val channels = XtreamApi.readStreams(StringReader(streamsJson()), categories) { "http://tv.example/live/u/p/$it.ts" }
        val index = ChannelIndex.build(channels)
        val now = System.currentTimeMillis()
        val guide = HashMap<String, List<Programme>>()
        for (channel in channels) {
            if (channel.epgId.isEmpty()) continue
            guide[channel.epgId] = (0 until XmlTvParser.MAX_PER_CHANNEL).map { k ->
                val title = when {
                    channel.number == 100 && k == 1 -> "SHL: Frölunda – Färjestad"
                    channel.number == 200 && k == 2 -> "Premier League: Arsenal – Spurs"
                    else -> "Show ${(channel.number + k) % 3000}"
                }
                Programme(now + (k - 1) * 3_600_000L, now + k * 3_600_000L, title)
            }
        }
        val started = System.nanoTime()
        val sports = index.sports(guide, now)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("sport groups over ${guide.size} guides in $ms ms")
        assertEquals(100, sports.getValue("hockey").first().number)
        assertTrue(sports.getValue("hockey").any { it.name == "SE: TV4 Hockey" })
        assertEquals(200, sports.getValue("football").first().number)
        assertTrue("sport groups took $ms ms", ms < 6_000)
    }

    @Test
    fun `a big guide keeps descriptions only for the next hours`() {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis() / 3_600_000L * 3_600_000L
        // Streamed as it is made, like a download: the whole guide is well over a hundred megabytes
        val description = "A long description of what happens. ".repeat(8)
        val parts = sequence {
            yield("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>")
            for (c in 1..guided) {
                yield(buildString {
                    for (h in -1 until 24) {
                        val start = now + h * 3_600_000L
                        append("<programme start=\"${fmt.format(start)}\" stop=\"${fmt.format(start + 3_600_000L)}\" channel=\"c$c\">")
                        append("<title>Show ${h + 1}</title><desc>$description</desc></programme>")
                    }
                })
            }
            yield("</tv>")
        }.map { it.toByteArray().inputStream() }.iterator()
        val xml = java.io.SequenceInputStream(object : java.util.Enumeration<java.io.InputStream> {
            override fun hasMoreElements() = parts.hasNext()
            override fun nextElement() = parts.next()
        })
        val wanted = (1..guided).mapTo(HashSet()) { "c$it" }
        val started = System.nanoTime()
        val guide = XmlTvParser.parse(xml, wanted, now - 3 * 3_600_000L, now + 24 * 3_600_000L, detailUntil = now + 3 * 3_600_000L)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("parsed a guide of ${guided * 25} programmes in $ms ms")
        assertEquals(guided, guide.size)
        val one = guide.getValue("c1")
        assertEquals(XmlTvParser.MAX_PER_CHANNEL, one.size)
        assertTrue(one.first().description.length <= XmlTvParser.MAX_DESCRIPTION)
        assertTrue(one.first().description.isNotEmpty())
        assertTrue(one.filter { it.start >= now + 3 * 3_600_000L }.all { it.description.isEmpty() })
        // The same title on every channel is kept once
        assertTrue(guide.getValue("c1")[2].title === guide.getValue("c2")[2].title)
    }
}
