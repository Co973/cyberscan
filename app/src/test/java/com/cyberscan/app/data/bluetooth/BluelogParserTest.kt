package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.DeviceClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BluelogParserTest {
    @Test
    fun `comma format extracts name class and RSSI`() {
        val line = "[14:32:10] aa:bb:cc:dd:ee:ff,Cyber Deck,Computer,RSSI -42 dBm"

        val device = BluelogParser.parse(line, nowMs = 100)!!

        assertEquals("AA:BB:CC:DD:EE:FF", device.macAddress)
        assertEquals("Cyber Deck", device.name)
        assertEquals(DeviceClass.COMPUTER, device.deviceClass)
        assertEquals(-42, device.rssi)
        assertEquals(100, device.firstSeenAtMs)
    }

    @Test
    fun `labeled format extracts quoted name and class of device`() {
        val line = "MAC: 12:34:56:78:9a:bc Name: \"Field Phone\" CoD: 0x020C RSSI: -61"

        val device = BluelogParser.parse(line, nowMs = 200)!!

        assertEquals("Field Phone", device.name)
        assertEquals(DeviceClass.PHONE, device.deviceClass)
        assertEquals(-61, device.rssi)
    }

    @Test
    fun `MAC-only line degrades optional fields`() {
        val device = BluelogParser.parse("seen 01:23:45:67:89:AB", nowMs = 300)!!

        assertNull(device.name)
        assertEquals(DeviceClass.UNKNOWN, device.deviceClass)
        assertNull(device.rssi)
    }

    @Test
    fun `malformed line is ignored`() {
        assertNull(BluelogParser.parse("scanner ready", nowMs = 1))
        assertNull(BluelogParser.parse("AA:BB:CC:DD:EE:GG invalid", nowMs = 1))
    }
}

