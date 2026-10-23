package com.lumera.app.ui.components

import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.domain.HubShape
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ServiceArtTest {

    // Unit tests run in the app module, where the assets are under src/main
    private fun asset(uri: String) = File("src/main/assets/" + uri.removePrefix("file:///android_asset/"))

    @Test
    fun everyServiceHasArtForEveryCardShapeAndTheHero() {
        NeonTokyoStarterPack.SERVICES.forEach { (code, _) ->
            HubShape.entries.forEach { shape ->
                listOf(false, true).forEach { subject ->
                    val file = asset(ServiceArt.cardLayer(code, shape, subject))
                    assertTrue("missing ${file.path}", file.isFile)
                }
            }
            listOf(false, true).forEach { subject ->
                val file = asset(ServiceArt.heroLayer(code, subject))
                assertTrue("missing ${file.path}", file.isFile)
            }
            val box = ServiceArt.heroSubjects[code]
            assertNotNull("no place for the hero subject of $code", box)
            assertTrue("$code: $box", box!!.size == 4 && box[0] < box[2] && box[1] < box[3] && box.all { it in 0f..1f })
        }
    }
}
