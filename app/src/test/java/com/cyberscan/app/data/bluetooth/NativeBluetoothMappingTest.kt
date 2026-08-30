package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.DeviceClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NativeBluetoothMappingTest {
    @Test
    fun `BLE event maps to normalized native observation`() {
        val observation = NativeBluetoothMapper.map(
            event = NativeBluetoothEvent.DeviceFound(
                macAddress = "aa-bb-cc-dd-ee-ff",
                name = "Tag",
                majorDeviceClass = null,
                rssi = -62,
                transport = NativeTransport.BLE,
            ),
            observedAtMs = 100,
        )

        assertEquals(BluetoothSource.NATIVE_BLE, observation.source)
        assertEquals("AA:BB:CC:DD:EE:FF", observation.macAddress)
        assertEquals("Tag", observation.name)
        assertEquals(-62, observation.rssi)
    }

    @Test
    fun `Classic Android major class maps to domain class`() {
        val observation = NativeBluetoothMapper.map(
            event = NativeBluetoothEvent.DeviceFound(
                macAddress = "00:11:22:33:44:55",
                name = null,
                majorDeviceClass = 0x100,
                rssi = -45,
                transport = NativeTransport.CLASSIC,
            ),
            observedAtMs = 200,
        )

        assertEquals(BluetoothSource.NATIVE_CLASSIC, observation.source)
        assertEquals(DeviceClass.COMPUTER, observation.deviceClass)
    }
}

