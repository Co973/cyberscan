package com.cyberscan.app.data.bluetooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeBluetoothScannerTest {
    @Test
    fun `disabled adapter fails without registering callbacks`() {
        val platform = FakeNativePlatform(available = true, enabled = false)
        val scanner = NativeBluetoothScanner(platform, BluetoothDeviceAccumulator())

        val result = scanner.start()

        assertTrue(result.isFailure)
        assertEquals("Bluetooth is disabled", result.exceptionOrNull()?.message)
        assertEquals(0, platform.startCount)
    }

    @Test
    fun `missing adapter has a descriptive failure`() {
        val platform = FakeNativePlatform(available = false)
        val scanner = NativeBluetoothScanner(platform, BluetoothDeviceAccumulator())

        val result = scanner.start()

        assertTrue(result.isFailure)
        assertEquals("No Android Bluetooth adapter is available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `device event is accumulated and failure is published`() {
        var now = 100L
        val platform = FakeNativePlatform()
        val accumulator = BluetoothDeviceAccumulator()
        val scanner = NativeBluetoothScanner(platform, accumulator) { now }
        assertTrue(scanner.start().isSuccess)

        platform.emit(
            NativeBluetoothEvent.DeviceFound(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Phone",
                majorDeviceClass = 0x200,
                rssi = -51,
                transport = NativeTransport.CLASSIC,
            ),
        )
        now = 200
        platform.emit(NativeBluetoothEvent.Failure("BLE scan failed (2)"))

        assertEquals("Phone", accumulator.devices.value.single().name)
        assertEquals("BLE scan failed (2)", scanner.failure.value)
    }

    @Test
    fun `stop delegates exactly once after start`() {
        val platform = FakeNativePlatform()
        val scanner = NativeBluetoothScanner(platform, BluetoothDeviceAccumulator())
        scanner.start()

        scanner.stop()
        scanner.stop()

        assertEquals(1, platform.stopCount)
        assertFalse(scanner.active.value)
    }

    private class FakeNativePlatform(
        override val available: Boolean = true,
        override val enabled: Boolean = true,
    ) : NativeBluetoothPlatform {
        private var callback: ((NativeBluetoothEvent) -> Unit)? = null
        var startCount = 0
        var stopCount = 0

        override fun start(onEvent: (NativeBluetoothEvent) -> Unit): Result<Unit> {
            startCount += 1
            callback = onEvent
            return Result.success(Unit)
        }

        override fun stop() {
            stopCount += 1
            callback = null
        }

        fun emit(event: NativeBluetoothEvent) {
            callback?.invoke(event)
        }
    }
}
