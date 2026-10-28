package com.lumera.app.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

class LiveTvParsersTest {

    @Test
    fun `m3u keeps live channels with their groups, logos and guide ids`() {
        val playlist = """
            #EXTM3U url-tvg="http://guide.example/epg.xml.gz,http://other.example/epg.xml"
            #EXTINF:-1 tvg-id="svt1.se" tvg-name="SVT1 HD" tvg-logo="http://logo/svt1.png" group-title="Sverige",SVT1 HD
            http://tv.example/live/u/p/1.ts
            #EXTINF:-1 tvg-id="svt1.se" group-title="Sverige",SVT1 HD
            http://tv.example/live/u/p/2.ts
            #EXTINF:-1 tvg-chno="7",Kanal 7
            #EXTGRP:Nyheter
            http://tv.example/hls/7.m3u8
            #EXTINF:-1 group-title="Film",Some Film (2020)
            http://tv.example/movie/u/p/99.mkv
        """.trimIndent()
        val result = M3uParser.parse(StringReader(playlist).buffered())
        assertEquals("http://guide.example/epg.xml.gz", result.epgUrl)
        assertEquals(3, result.channels.size)
        val first = result.channels[0]
        assertEquals("SVT1 HD", first.name)
        assertEquals("Sverige", first.group)
        assertEquals("http://logo/svt1.png", first.logo)
        assertEquals("svt1.se", first.epgId)
        // The same channel listed twice keeps two different ids
        assertTrue(result.channels[1].id != first.id)
        val seventh = result.channels[2]
        assertEquals("Nyheter", seventh.group)
        assertEquals(7, seventh.number)
        assertFalse(result.channels.any { "/movie/" in it.url })
    }

    @Test
    fun `xtream login, categories and streams are read whatever types the server uses`() {
        val account = XtreamApi.readAccount(StringReader(
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","allowed_output_formats":["m3u8","ts"]},"server_info":{"url":"x"}}"""
        ))
        assertTrue(account.ok)
        assertEquals("ts", XtreamApi.pickFormat(account))
        assertEquals(1893456000000L, account.expires)

        val refused = XtreamApi.readAccount(StringReader("""{"user_info":{"auth":0}}"""))
        assertFalse(refused.ok)

        val categories = XtreamApi.readCategories(StringReader(
            """[{"category_id":"3","category_name":"SE | Sport","parent_id":0},{"category_id":4,"category_name":" Nyheter "}]"""
        ))
        assertEquals(listOf("SE | Sport", "Nyheter"), categories.values.toList())

        val source = IptvSource.Xtream("example.com:8080/", "me", "p&ss")
        val streams = XtreamApi.readStreams(StringReader(
            """[{"num":1,"name":"TV4 HD","stream_id":101,"stream_icon":"http://l/tv4.png","epg_channel_id":"tv4.se","category_id":"3"},
                {"num":"2","name":"Ny","stream_id":"102","stream_icon":null,"epg_channel_id":null,"category_id":"9"}]"""
        ), categories) { XtreamApi.streamUrl(source, it, "ts") }
        assertEquals(2, streams.size)
        assertEquals("x101", streams[0].id)
        assertEquals("SE | Sport", streams[0].group)
        assertEquals("http://example.com:8080/live/me/p%26ss/101.ts", streams[0].url)
        assertEquals("Other", streams[1].group)
        assertEquals("", streams[1].logo)
        assertEquals(2, streams[1].number)
    }

    @Test
    fun `xmltv keeps only wanted channels within the window, in order`() {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val now = format.parse("20261027120000 +0000")!!.time
        val guide = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv>
              <channel id="tv4.se"><display-name>TV4</display-name></channel>
              <programme start="20261027130000 +0000" stop="20261027140000 +0000" channel="tv4.se"><title lang="sv">Nyheterna</title></programme>
              <programme start="20261027113000 +0000" stop="20261027130000 +0000" channel="tv4.se"><title>Lunch &amp; Sport</title><desc>Live.</desc></programme>
              <programme start="20261020113000 +0000" stop="20261020130000 +0000" channel="tv4.se"><title>Old</title></programme>
              <programme start="20261027113000 +0000" stop="20261027130000 +0000" channel="unwanted"><title>No</title></programme>
            </tv>
        """.trimIndent()
        val parsed = XmlTvParser.parse(guide.byteInputStream(), setOf("tv4.se"), now - 3_600_000L, now + 86_400_000L)
        assertEquals(setOf("tv4.se"), parsed.keys)
        val list = parsed.getValue("tv4.se")
        assertEquals(listOf("Lunch & Sport", "Nyheterna"), list.map { it.title })
        val nowNext = list.nowNext(now)
        assertEquals("Lunch & Sport", nowNext.now?.title)
        assertEquals("Live.", nowNext.now?.description)
        assertEquals("Nyheterna", nowNext.next?.title)
        assertNull(emptyList<Programme>().nowNext(now).now)
    }
}
