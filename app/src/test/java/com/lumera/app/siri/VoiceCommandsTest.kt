package com.lumera.app.siri

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsTest {
    private val apps = listOf(
        "com.google.android.youtube.tv" to "YouTube",
        "com.spotify.tv.android" to "Spotify",
        "com.stremio.one" to "Stremio",
        "com.netflix.ninja" to "Netflix",
        "com.neontokyo.tv" to "Neon Tokyo"
    )

    @Test fun opensAppsInSwedishAndEnglish() {
        assertEquals(VoiceCommand.OpenApp("com.google.android.youtube.tv", "YouTube"), VoiceCommands.parse("Öppna YouTube", apps))
        assertEquals(VoiceCommand.OpenApp("com.netflix.ninja", "Netflix"), VoiceCommands.parse("open Netflix.", apps))
        assertEquals(VoiceCommand.OpenApp("com.neontokyo.tv", "Neon Tokyo"), VoiceCommands.parse("starta neon tokyo", apps))
    }

    @Test fun forgivesAMisheardLetter() {
        assertEquals(VoiceCommand.OpenApp("com.stremio.one", "Stremio"), VoiceCommands.parse("öppna stremo", apps))
    }

    @Test fun aBareAppNameOpensIt() {
        assertEquals(VoiceCommand.OpenApp("com.spotify.tv.android", "Spotify"), VoiceCommands.parse("Spotify", apps))
    }

    @Test fun playsInAnApp() {
        assertEquals(VoiceCommand.PlayIn("youtube", "akira trailer"), VoiceCommands.parse("Spela Akira trailer på YouTube", apps))
        assertEquals(VoiceCommand.PlayIn("spotify", "city pop"), VoiceCommands.parse("play city pop on Spotify", apps))
    }

    @Test fun anythingElseIsASearch() {
        assertEquals(VoiceCommand.Search("Blade Runner 2049"), VoiceCommands.parse("Blade Runner 2049", apps))
        assertEquals(VoiceCommand.Search("öppna dörren"), VoiceCommands.parse("öppna dörren", apps))
    }
}
