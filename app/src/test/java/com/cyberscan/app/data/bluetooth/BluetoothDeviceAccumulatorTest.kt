package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.DeviceClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BluetoothDeviceAccumulatorTest {
    @Test
    fun `native and external observations deduplicate by normalized MAC`() {
        val accumulator = BluetoothDeviceAccumulator()

        accumulator.accept(
            observation(
                source = BluetoothSource.NATIVE_BLE,
                mac = "aa-bb-cc-dd-ee-ff",
                rssi = -70,
                at = 10,
            ),
        )
        accumulator.accept(
            observation(
                source = BluetoothSource.EXTERNAL_HCI,
                mac = "AA:BB:CC:DD:EE:FF",
                name = "Beacon",
                at = 20,
            ),
        )

        val device = accumulator.devices.value.single()
        assertEquals("AA:BB:CC:DD:EE:FF", device.macAddress)
        assertEquals("Beacon", device.name)
        assertEquals(-70, device.rssi)
        assertEquals(10, device.firstSeenAtMs)
        assertEquals(20, device.lastSeenAtMs)
        assertEquals(
            setOf(BluetoothSource.NATIVE_BLE, BluetoothSource.EXTERNAL_HCI),
            accumulator.sourcesFor(device.macAddress),
        )
    }

    @Test
    fun `null observations never erase known values`() {
        val accumulator = BluetoothDeviceAccumulator()
        accumulator.accept(
            observation(
                name = "Laptop",
                deviceClass = DeviceClass.COMPUTER,
                rssi = -41,
                at = 10,
            ),
        )

        accumulator.accept(
            observation(
                name = null,
                deviceClass = DeviceClass.UNKNOWN,
                rssi = null,
                at = 20,
            ),
        )

        val device = accumulator.devices.value.single()
        assertEquals("Laptop", device.name)
        assertEquals(DeviceClass.COMPUTER, device.deviceClass)
        assertEquals(-41, device.rssi)
    }

    @Test
    fun `clear removes devices and source provenance`() {
        val accumulator = BluetoothDeviceAccumulator()
        accumulator.accept(observation())

        accumulator.clear()

        assertEquals(emptyList<Any>(), accumulator.devices.value)
        assertEquals(emptySet<BluetoothSource>(), accumulator.sourcesFor(TEST_MAC))
    }

    private fun observation(
        source: BluetoothSource = BluetoothSource.NATIVE_CLASSIC,
        mac: String = TEST_MAC,
        name: String? = null,
        deviceClass: DeviceClass = DeviceClass.UNKNOWN,
        rssi: Int? = null,
        at: Long = 1,
    ) = BluetoothObservation(
        source = source,
        macAddress = mac,
        name = name,
        deviceClass = deviceClass,
        rssi = rssi,
        observedAtMs = at,
    )

    private companion object {
        const val TEST_MAC = "AA:BB:CC:DD:EE:FF"
    }
}
