package com.vknxworks.talkctrl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaipTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun controlPacketLayout() {
        assertArrayEquals(b(0x30, 0x05, 0x55, 0x00), Caip.factoryReset())
        assertArrayEquals(b(0x30, 0x20, 0x07, 0x01, 0x03), Caip.fmPlayPreset(3))
        assertArrayEquals(b(0x30, 0x20, 0x08, 0x02, 0x27, 0xA6), Caip.fmSetFrequency(101.5))
        assertArrayEquals(b(0x30, 0x22, 0x01, 0x01, 0x01), Caip.dmcMute(true))
        assertArrayEquals(b(0x30, 0x22, 0x58), Caip.icPair(1).copyOf(3))
        assertArrayEquals(b(0x30, 0x23, 0x00, 0x00), Caip.musicSharing(true))
        assertArrayEquals(b(0x30, 0x23, 0x01, 0x00), Caip.musicSharing(false))
    }

    @Test fun plainPackets() {
        assertArrayEquals(b(0x03, 0x00, 0x00), Caip.getVersion())
        assertArrayEquals(b(0x02), Caip.getSerial())
        assertArrayEquals(b(0x00, 0x02, 0x00, 0x82), Caip.getConfig(listOf(0x00, 0x82)))
        assertArrayEquals(b(0x22, 0xF1, 0xFF, 0x80), Caip.subscribeAll())
        assertArrayEquals(b(0x12, 0x03, 'A'.code, 'x'.code, 'e'.code), Caip.setName("Axe"))
        assertArrayEquals(b(0x11, 0x03, '1'.code, '2'.code, '3'.code), Caip.setSpeedDial("123"))
    }

    @Test(expected = IllegalArgumentException::class) fun nameTooLong() {
        Caip.setName("x".repeat(Caip.NAME_MAX_BYTES + 1))
    }

    @Test fun setConfigConcatenatesRecords() {
        val u = UserConfig(0x14, 0x45, 0x03)
        val v = Volumes(8, 9, 10, 10, 5, 7, 7, 9, 1)
        assertArrayEquals(b(0x10, 11, 0x00, 0x14, 0x45, 0x03, 0x02, 0x00, 0x89, 0xAA, 0x57, 0x79, 0x01), Caip.setConfig(u.record(), v.record()))
    }

    @Test fun parseConfigUpdate() {
        // UPDATE_PARAM with USER_CONFIG, VOLUMES, HS_TYPE (firmware 2.5) and capabilities.
        val msg = b(0x40, 20, 0x00, 0x14, 0x45, 0x03, 0x02, 0x00, 0x89, 0xAA, 0x57, 0x79, 0x01, 0x03, 0x00, 0x40, 0x00, 0x02, 0x00, 0x05, 0x82, 0xC6, 0x60)
        val m = Caip.parse(msg) as Caip.ConfigUpdate
        assertEquals(listOf(0x00, 0x02, 0x03, 0x82), m.records.map { it.type })
        val u = UserConfig.from(m.records[0])
        assertEquals(1, u.language)
        assertTrue(u.voicePrompts)
        assertFalse(u.voiceCommands)
        assertEquals(0, u.fmRegion)
        assertEquals(2, u.micSensitivity)
        assertEquals(2, u.autoVolume)
        assertTrue(u.noiseGate)
        assertEquals(3, u.eqProfile)
        val v = Volumes.from(m.records[1])
        assertEquals(8, v.announcements); assertEquals(9, v.intercom); assertEquals(10, v.phone); assertEquals(5, v.fm); assertEquals(7, v.music)
        assertEquals(10, Volumes.reductionPercent(v.musicUnderIntercom))
        assertEquals(FirmwareVersion(2, 5), FirmwareVersion.from(m.records[2]))
        val caps = Capabilities.from(m.records[3])
        assertTrue(caps.autoVolume); assertTrue(caps.musicSharing); assertTrue(caps.dmc); assertTrue(caps.fm); assertTrue(caps.ota)
        assertFalse(caps.bluetoothIntercom)
    }

    @Test fun unknownRecordStopsParsing() {
        val msg = b(0x40, 6, 0x88, 1, 2, 3, 0x00, 0x14, 0x45, 0x03)
        assertTrue((Caip.parse(msg) as Caip.ConfigUpdate).records.isEmpty())
    }

    @Test fun userConfigSettersOnlyTouchTheirBits() {
        val u = UserConfig(0xFF, 0xFF, 0xFF)
        assertEquals(UserConfig(0x2F, 0xFF, 0xFF), u.withLanguage(2))
        assertEquals(UserConfig(0xF7, 0xFF, 0xFF), u.withVoiceCommands(false))
        assertEquals(UserConfig(0xFD, 0xFF, 0xFF), u.withFmRegion(0))
        assertEquals(UserConfig(0xFF, 0xE3, 0xFF), u.withAutoVolume(1))
        assertEquals(UserConfig(0xFF, 0xBF, 0xFF), u.withMicSensitivity(1))
        assertEquals(UserConfig(0xFF, 0x7F, 0xFF), u.withPhoneMixing(false))
        assertEquals(UserConfig(0xFF, 0xFF, 0xD4), u.withEq(20))
        val z = UserConfig(0, 0, 0)
        assertEquals(UserConfig(0x04, 0, 0), z.withVoicePrompts(true))
        assertEquals(UserConfig(0, 0x01, 0), z.withNoiseGate(true))
    }

    @Test fun serviceMessages() {
        assertEquals(Caip.Battery(85, true), Caip.parse(b(0x51, 0x80 or 85, 0)))
        assertEquals(Caip.State(1, 3, 101.5), Caip.parse(b(0x50, 0, 0, 0x13, 0x27, 0xA6, 0)))
        assertEquals(Caip.Serial("PT123"), Caip.parse(b(0x43, 5, 'P'.code, 'T'.code, '1'.code, '2'.code, '3'.code, 1, 2, 3, 4, 5, 6)))
        assertEquals(Caip.Name("Axel"), Caip.parse(b(0x42, 4, 'A'.code, 'x'.code, 'e'.code, 'l'.code)))
        assertEquals(Caip.MusicSharing(1), Caip.parse(b(0x69, 0x01)))
        assertEquals(Caip.OtaReady, Caip.parse(b(0xA3)))
    }

    @Test fun fmPresetsRoundTrip() {
        val presets = listOf(87.5, 101.5, 0.0, 99.95, 108.0, 90.1)
        val rec = fmPresetsRecord(presets)
        assertEquals(14, rec.size)
        assertEquals(0x80, rec[0].toInt() and 0xFF)
        assertEquals(presets, fmPresets(ConfigRecord(0x80, rec)))
    }

    @Test fun languagesAndEqMasks() {
        assertEquals(listOf(0, 1, 4), supportedLanguages(ConfigRecord(0x05, b(0x05, 0x00, 0x13))))
        assertEquals(listOf(0, 1, 2, 3, 20), eqProfiles(ConfigRecord(0x84, b(0x84, 0x00, 0x10, 0x00, 0x0F))))
    }

    @Test fun rfcommSplitting() {
        val parts = splitRfcomm(b(0x42, 2, 'h'.code, 'i'.code, 0, 0x41, 1, '7'.code, 0))
        assertEquals(2, parts.size)
        assertArrayEquals(b(0x42, 2, 'h'.code, 'i'.code, 0), parts[0])
        assertArrayEquals(b(0x41, 1, '7'.code, 0), parts[1])
    }

    @Test fun versionsCompareAndParse() {
        assertTrue(FirmwareVersion(2, 10) > FirmwareVersion(2, 9))
        assertEquals(FirmwareVersion(2, 5), FirmwareVersion.parse("Packtalk_Edge_2_5.bin"))
        assertEquals(FirmwareVersion(3, 1), FirmwareVersion.parse("3.1.2"))
    }

    @Test fun modelsAndAddresses() {
        assertEquals("Packtalk_Edge", Models.guess("PT0012345")?.folder)
        assertTrue(Models.isKnownFolder("Freecom4X"))
        assertFalse(Models.isKnownFolder("../etc"))
        assertTrue(Caip.isCardoAddress("00:0a:9b:12:34:56"))
        assertFalse(Caip.isCardoAddress("AA:BB:CC:12:34:56"))
    }
}
