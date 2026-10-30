package com.lumera.app.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class VodCatalogTest {

    private val movies = VodCatalog.readMovies(StringReader("""
        [
          {"num":1,"name":"SE - Dune (2021) 4K","stream_id":501,"container_extension":"mkv","tmdb":"438631"},
          {"num":2,"name":"Dune (1984)","stream_id":502,"container_extension":"mp4","tmdb":""},
          {"num":3,"name":"EN | Dune: Part Two [MULTI-SUB]","stream_id":503,"container_extension":"mp4","year":"2024"},
          {"num":4,"name":"Sagan om ringen: Härskarringen","stream_id":504,"container_extension":"mkv","tmdb_id":120},
          {"num":5,"name":"Blade Runner 2049","stream_id":505,"container_extension":"mkv"},
          {"num":6,"name":"The Matrix 1999","stream_id":506,"container_extension":"mp4"},
          {"num":7,"name":"TRON: Legacy (2010)","stream_id":507,"container_extension":"mp4"},
          "junk"
        ]
    """.trimIndent()))
    private val index = VodCatalog.Index(movies)

    private fun ids(title: String, year: Int?, tmdb: String? = null) = index.find(title, year, tmdb).map { it.id }

    @Test
    fun `films are read with their ids, years and tmdb ids`() {
        assertEquals(7, movies.size)
        assertEquals(VodItem("501", "SE - Dune (2021) 4K", 2021, "438631", "mkv"), movies[0])
        assertEquals(2024, movies[2].year)
        assertEquals("120", movies[3].tmdb)
    }

    @Test
    fun `a title is found by name and year whatever the provider puts around it`() {
        assertEquals(listOf("501"), ids("Dune", 2021))
        assertEquals(listOf("502"), ids("Dune", 1984))
        assertEquals(listOf("503"), ids("Dune: Part Two", 2024))
        assertEquals(listOf("505"), ids("Blade Runner 2049", 2017))
        assertEquals(listOf("506"), ids("The Matrix", 1999))
        assertEquals(listOf("507"), ids("TRON: Legacy", 2010))
        assertTrue(ids("Blade Runner", 1982).isEmpty())
        assertTrue(ids("Dune", 2000).isEmpty())
    }

    @Test
    fun `the tmdb id finds a title under another name and keeps namesakes apart`() {
        assertEquals(listOf("504"), ids("The Lord of the Rings: The Fellowship of the Ring", 2001, "120"))
        // Dune 2021 knows its own id, so another film called Dune with another id is not it
        assertEquals(listOf("501"), ids("Dune", 2021, "438631"))
        assertTrue(ids("Dune", 2021, "999").isEmpty())
    }

    @Test
    fun `episodes are read from both shapes panels send`() {
        val byObject = VodCatalog.readEpisodes(StringReader("""
            {"seasons":[],"info":{"name":"Dune: Prophecy"},"episodes":{"1":[
              {"id":"9001","episode_num":1,"title":"The Hidden Hand","container_extension":"mkv","season":1},
              {"id":"9002","episode_num":"2","title":"Two Wolves","container_extension":"mkv"}]}}
        """.trimIndent()))
        assertEquals(listOf(VodEpisode("9001", 1, 1, "The Hidden Hand", "mkv"), VodEpisode("9002", 1, 2, "Two Wolves", "mkv")), byObject)
        val byList = VodCatalog.readEpisodes(StringReader("""{"episodes":[[{"id":"7","episode_num":3,"season":2,"container_extension":"mp4"}]]}"""))
        assertEquals(listOf(VodEpisode("7", 2, 3, "", "mp4")), byList)
    }

    @Test
    fun `picture size and years are read the way the panel shows them`() {
        assertEquals("4K", VodCatalog.quality("SE - Dune (2021) 4K"))
        assertEquals("1080p", VodCatalog.quality("Dune FHD"))
        assertEquals(null, VodCatalog.quality("Dune (2021)"))
        assertEquals(2008, VodCatalog.yearIn("2008–2013"))
        assertEquals(2021, VodCatalog.yearIn("2021-10-22"))
    }
}
