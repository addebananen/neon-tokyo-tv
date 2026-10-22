package com.lumera.app

import com.lumera.app.data.model.stremio.CatalogManifest
import com.lumera.app.data.model.stremio.Manifest
import com.lumera.app.data.profile.NeonTokyoStarterPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class NeonTokyoStarterPackTest {

    @Test
    fun streamingConfigDecodesToSwedishServices() {
        val segment = NeonTokyoStarterPack.STREAMING_URL.substringAfterLast('/')
        val decoded = String(Base64.getDecoder().decode(java.net.URLDecoder.decode(segment, "UTF-8")))
        val parts = decoded.split(':')
        assertEquals("nfx,hbm,dnp,amp,atp,sst,pmp,cru", parts[0])
        assertEquals("SE", parts[2])
        assertEquals("SE", parts[6])
    }

    @Test
    fun offlineFirstRunStillHasRowsAndCards() {
        val snapshot = NeonTokyoStarterPack.build(emptyMap())
        assertEquals(2, snapshot.addons.size) // Cinemeta + Streaming Catalogs; anime needs its manifest
        assertTrue(snapshot.catalogConfigs.any { it.showInHome && it.customTitle == "Popular movies" })
        val hubIds = snapshot.hubRows.map { it.id }.toSet()
        assertTrue("nt_services_home" in hubIds && "nt_genres_home" in hubIds)
        // Every hub card points at configs that exist
        val configIds = snapshot.catalogConfigs.map { it.uniqueId }.toSet()
        snapshot.hubRowItems.forEach { item ->
            val targets = NeonTokyoStarterPack.parseCombo(item.configUniqueId) ?: listOf(item.configUniqueId)
            targets.forEach { assertTrue("missing $it", it in configIds) }
        }
        assertEquals(NeonTokyoStarterPack.SERVICES.size, snapshot.hubRowItems.count { it.hubRowId == "nt_services_home" })
        assertEquals(NeonTokyoStarterPack.GENRES.size, snapshot.hubRowItems.count { it.hubRowId == "nt_genres_home" })
    }

    @Test
    fun animeSearchOnlyCatalogsAreNotRows() {
        val anime = Manifest(
            id = "anime", name = "Anime Catalogs",
            catalogs = listOf(
                CatalogManifest(type = "anime", id = "anilist_trending-now", name = "AniList Trending Now"),
                CatalogManifest(type = "anime", id = "search", name = "Search",
                    extra = listOf(com.lumera.app.data.model.stremio.CatalogExtra(name = "search", isRequired = true)))
            )
        )
        val snapshot = NeonTokyoStarterPack.build(mapOf(NeonTokyoStarterPack.ANIME_URL to anime))
        val animeRows = snapshot.catalogConfigs.filter { it.transportUrl == NeonTokyoStarterPack.ANIME_URL }
        assertEquals(1, animeRows.size)
        assertEquals("Trending anime", animeRows.single().customTitle)
        assertNotNull(snapshot.addons.firstOrNull { it.transportUrl == NeonTokyoStarterPack.ANIME_URL })
    }

    @Test
    fun genreConfigsCarryTheirFilter() {
        val snapshot = NeonTokyoStarterPack.build(emptyMap())
        val action = snapshot.catalogConfigs.single { it.customTitle == "Action films" }
        assertEquals("genre=Action", action.extraArgs)
        assertTrue(action.uniqueId.endsWith("#genre=Action"))
    }
}
