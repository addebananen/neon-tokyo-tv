package com.lumera.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun newerBuildIsOffered() {
        assertTrue(isNewerVersion("v0.2.0-nt.80", "0.2.0-nt.75"))
        assertTrue(isNewerVersion("v0.2.0-nt.100", "0.2.0-nt.99"))
        assertTrue(isNewerVersion("v0.3.0-nt.1", "0.2.0-nt.75"))
    }

    @Test
    fun sameOrOlderIsNot() {
        assertFalse(isNewerVersion("v0.2.0-nt.75", "0.2.0-nt.75"))
        assertFalse(isNewerVersion("v0.2.0-nt.65", "0.2.0-nt.75"))
        assertFalse(isNewerVersion("v0.1.9", "0.2.0-nt.75"))
        assertFalse(isNewerVersion("latest", "0.2.0-nt.75"))
    }
}
