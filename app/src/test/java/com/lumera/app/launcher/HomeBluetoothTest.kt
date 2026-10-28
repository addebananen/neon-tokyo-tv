package com.lumera.app.launcher

import android.bluetooth.BluetoothClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBluetoothTest {
    private val airpods = "AA:00"
    private val speaker = "BB:00"
    private val order = listOf(airpods, speaker)
    private val owners = mapOf(airpods to setOf("adrian"), speaker to setOf("adrian", "sara"))

    private fun arrived(
        before: Set<String> = emptySet(),
        now: Set<String>,
        profile: String = "adrian",
        connected: Set<String> = emptySet(),
        snoozed: Map<String, Long> = emptyMap(),
        clock: Long = 1_000L
    ) = BtRules.arrived(order, before, now, BtRules.mine(owners, profile), connected, snoozed, clock)

    @Test
    fun aDeviceOfThisProfileSwitchedOnPopsUp() {
        assertEquals(airpods, arrived(now = setOf(airpods)))
        assertEquals(speaker, arrived(before = setOf(airpods), now = setOf(airpods, speaker)))
    }

    @Test
    fun someoneElsesDeviceNeverPopsUp() {
        // His AirPods on her home screen: nothing, the speaker they share does
        assertNull(arrived(now = setOf(airpods), profile = "sara"))
        assertEquals(speaker, arrived(now = setOf(airpods, speaker), profile = "sara"))
        assertNull(arrived(now = setOf(airpods, speaker), profile = "guest"))
    }

    @Test
    fun alreadyOnConnectedOrPutAwayDoesNot() {
        assertNull(arrived(before = setOf(airpods), now = setOf(airpods)))
        assertNull(arrived(now = setOf(airpods), connected = setOf(airpods)))
        assertNull(arrived(now = setOf(airpods), snoozed = mapOf(airpods to 5_000L), clock = 1_000L))
        assertEquals(airpods, arrived(now = setOf(airpods), snoozed = mapOf(airpods to 5_000L), clock = 6_000L))
    }

    @Test
    fun takingAndLettingGo() {
        val taken = BtRules.take(emptyMap(), airpods, "adrian", mine = true)
        assertEquals(setOf(airpods), BtRules.mine(taken, "adrian"))
        assertTrue(BtRules.mine(taken, "sara").isEmpty())
        val shared = BtRules.take(taken, airpods, "sara", mine = true)
        assertEquals(setOf("adrian", "sara"), shared[airpods])
        val gone = BtRules.take(BtRules.take(shared, airpods, "sara", mine = false), airpods, "adrian", mine = false)
        assertFalse(airpods in gone)
    }

    @Test
    fun onlySoundDevicesAndTheirKind() {
        assertEquals(BtKind.Headphones, BtRules.kindOf(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, BluetoothClass.Device.Major.AUDIO_VIDEO))
        assertEquals(BtKind.Speaker, BtRules.kindOf(BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, BluetoothClass.Device.Major.AUDIO_VIDEO))
        assertTrue(BtRules.playsSound(BluetoothClass.Device.Major.AUDIO_VIDEO))
        // The remote and a gamepad are paired too, and stay out of the list
        assertFalse(BtRules.playsSound(BluetoothClass.Device.Major.PERIPHERAL))
    }

    @Test
    fun theCardSaysWhatCameOn() {
        assertEquals("AirPods turned on", btTitle("AirPods"))
        assertNull(btConnectNote(BtConnect.Helper, "AirPods"))
        assertEquals("Pick AirPods and press Connect", btConnectNote(BtConnect.Settings, "AirPods"))
    }
}
