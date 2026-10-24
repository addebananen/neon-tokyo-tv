package com.lumera.app

import com.lumera.app.data.repository.CatalogUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogUrlsTest {

    @Test
    fun plainCatalog() {
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top.json",
            CatalogUrls.catalog("https://v3-cinemeta.strem.io/", "movie", "top")
        )
    }

    @Test
    fun genreCatalogPagesWithAmpersand() {
        val url = CatalogUrls.catalog("https://v3-cinemeta.strem.io", "movie", "top", CatalogUrls.genreExtra("Sci-Fi"))
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Sci-Fi.json", url)
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Sci-Fi&skip=100.json", CatalogUrls.withSkip(url, 100))
    }

    @Test
    fun plainCatalogPagesWithNewSegment() {
        val url = CatalogUrls.catalog("https://a.example/%7B%22x%22%3A%22on%22%7D", "anime", "mal_top")
        assertEquals("https://a.example/%7B%22x%22%3A%22on%22%7D/catalog/anime/mal_top/skip=50.json", CatalogUrls.withSkip(url, 50))
        assertEquals(url, CatalogUrls.withSkip(url, 0))
    }

    @Test
    fun genreWithSpaceIsPercentEncoded() {
        assertEquals("genre=Reality%20TV", CatalogUrls.genreExtra("Reality TV"))
    }
}
