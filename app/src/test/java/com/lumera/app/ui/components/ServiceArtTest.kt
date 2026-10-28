package com.lumera.app.ui.components

import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.domain.HubShape
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ServiceArtTest {

    // Unit tests run in the app module, where the assets are under src/main
    private fun asset(uri: String) = File("src/main/assets/" + uri.removePrefix("file:///android_asset/"))

    private val codes = NeonTokyoStarterPack.SERVICES.map { (code, _) -> code } +
        NeonTokyoStarterPack.GENRES.map(NeonTokyoStarterPack::genreArt)

    @Test
    fun everySceneHasArtForEveryCardShapeAndTheHero() {
        val frames = HubShape.entries.map(ServiceArt::frameOf) + "hero"
        codes.forEach { code ->
            frames.forEach { frame ->
                listOf("bg", "fg").forEach { layer ->
                    val file = asset(ServiceArt.layer(code, frame, layer))
                    assertTrue("missing ${file.path}", file.isFile)
                }
            }
        }
    }

    @Test
    fun everySceneSaysHowItMoves() {
        val motion = File("src/main/assets/service_art/motion.json").readText()
        codes.forEach { code -> assertTrue("no motion for $code", "\"$code\":{" in motion) }
    }

    @Test
    fun everyGenreHasItsOwnScene() {
        assertTrue(codes.size == codes.toSet().size)
        assertTrue(NeonTokyoStarterPack.artCode("x", "Sci-Fi") == "g_scifi")
    }
}
