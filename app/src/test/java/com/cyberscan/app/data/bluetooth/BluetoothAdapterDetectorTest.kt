package com.cyberscan.app.data.bluetooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BluetoothAdapterDetectorTest {
    @Test
    fun `active adapter wins over lower numbered down adapter`() {
        val output = """
            hci0:   Type: Primary  Bus: USB
                    BD Address: 00:11:22:33:44:55
                    DOWN
            hci1:   Type: Primary  Bus: USB
                    BD Address: AA:BB:CC:DD:EE:FF
                    UP RUNNING PSCAN
        """.trimIndent()

        val selected = BluetoothAdapterDetector.select(BluetoothAdapterDetector.parse(output))

        assertEquals("hci1", selected?.name)
        assertEquals(true, selected?.isUp)
        assertEquals(true, selected?.isRunning)
    }

    @Test
    fun `lowest active adapter is selected deterministically`() {
        val output = """
            hci2: Type: Primary Bus: USB
                  UP RUNNING
            hci1: Type: Primary Bus: USB
                  UP RUNNING
        """.trimIndent()

        val selected = BluetoothAdapterDetector.select(BluetoothAdapterDetector.parse(output))

        assertEquals("hci1", selected?.name)
    }

    @Test
    fun `down or absent adapters are not usable`() {
        val down = BluetoothAdapterDetector.parse("hci0: Type: Primary Bus: USB\n      DOWN")

        assertNull(BluetoothAdapterDetector.select(down))
        assertNull(BluetoothAdapterDetector.select(emptyList()))
    }
}

