package com.lumera.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NeonTextTest {

    @Test
    fun keepsAsMuchAsFits() {
        assertEquals("Netflix O…", shorten("Netflix Originals") { it.length <= 10 })
    }

    @Test
    fun noSpaceBeforeTheEllipsis() {
        assertEquals("Netflix…", shorten("Netflix Originals") { it.length <= 8 })
    }

    @Test
    fun neverSplitsAnEmoji() {
        assertEquals("Ab…", shorten("Ab😀cd") { it.length <= 4 })
    }

    @Test
    fun anEllipsisAloneWhenNothingFits() {
        assertEquals("…", shorten("Netflix") { false })
    }
}
