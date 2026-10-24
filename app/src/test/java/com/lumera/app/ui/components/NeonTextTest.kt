package com.lumera.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun descriptionsEndOnAWholeWord() {
        val text = "What they don't know is that a ferocious storm is on its way"
        assertEquals("What they don't know is that a ferocious…", shortenToWords(text) { it.length <= 45 })
    }

    @Test
    fun aCutBetweenTwoWordsStaysThere() {
        val text = "What they don't know is that a ferocious storm is on its way"
        assertEquals("What they don't know is that a ferocious…", shortenToWords(text) { it.length <= 42 })
    }

    @Test
    fun noCommaBeforeTheEllipsis() {
        assertEquals("Filip is turning 45…", shortenToWords("Filip is turning 45, and has invited his closest friends") { it.length <= 23 })
    }

    @Test
    fun oneLongWordIsCutWhereItHasTo() {
        assertEquals("Supercali…", shortenToWords("Supercalifragilisticexpialidocious") { it.length <= 10 })
    }

    @Test
    fun aDescriptionCutInsideAWordByItsCatalogLosesThatWord() {
        val text = "Filip is turning 45, and has invited his closest friends for dinner at his and his wife's lavish and " +
            "newly bought farm estate up north in Sweden. What they don't know is that a ferocious storm is on its way " +
            "and that they'll be tra..."
        assertTrue(tidySynopsis(text).endsWith("storm is on its way and that they'll be…"))
    }

    @Test
    fun aDescriptionCutAfterASentenceJustEndsThere() {
        val text = "It's 1890, in America, the Steel Ball Run — the first horse racing championship across the North " +
            "American continent — was about to begin. Spanning around 4,000 miles, the race offered 50 million dollars " +
            "as the winning prize...."
        assertTrue(tidySynopsis(text).endsWith("50 million dollars as the winning prize."))
    }

    @Test
    fun aShortTextKeepsItsDots() {
        assertEquals("And then...", tidySynopsis("And then..."))
    }
}
