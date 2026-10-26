package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeIconsTest {
    @Test
    fun aPackIsReadBackFromItsKey() {
        IconPack.values().forEach { assertEquals(it, IconPack.of(it.key)) }
    }

    @Test
    fun aProfileFromBeforeThePacksKeepsTheNeonIcons() {
        assertEquals(IconPack.Neon, IconPack.of(null))
        assertEquals(IconPack.Neon, IconPack.of(""))
        assertEquals(IconPack.Neon, IconPack.of("chrome"))
    }
}
