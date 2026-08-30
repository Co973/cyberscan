package com.cyberscan.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScanModelsTest {
    @Test
    fun `normalized MAC identity is uppercase`() {
        val device = BluetoothDevice(
            macAddress = "aa:bb:cc:01:02:03",
            name = "node",
            deviceClass = DeviceClass.COMPUTER,
            rssi = null,
            firstSeenAtMs = 1,
            lastSeenAtMs = 2,
        )

        assertEquals("AA:BB:CC:01:02:03", device.macAddress)
    }

    @Test
    fun `failed state retains an actionable reason`() {
        val state = ScanUiState(phase = ScanPhase.Failed("No active Bluetooth adapter"))

        assertEquals("No active Bluetooth adapter", (state.phase as ScanPhase.Failed).reason)
    }
}
