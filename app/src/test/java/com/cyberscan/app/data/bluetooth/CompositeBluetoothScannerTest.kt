package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.DeviceClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeBluetoothScannerTest {
    @Test
    fun `native starts when external adapter is unavailable`() = runTest {
        val fixture = fixture(externalAdapter = null)

        assertTrue(fixture.subject.startScan(backgroundScope).isSuccess)
        runCurrent()

        assertEquals(1, fixture.nativePlatform.startCount)
        assertEquals("ANDROID HAL", fixture.subject.adapterLabel.value)
    }

    @Test
    fun `external adapter joins native and duplicate MAC remains one row`() = runTest {
        val fixture = fixture(externalAdapter = HciAdapter("hci2", true, true))
        fixture.subject.startScan(backgroundScope)
        runCurrent()

        fixture.nativePlatform.emit(
            NativeBluetoothEvent.DeviceFound(
                macAddress = TEST_MAC,
                name = null,
                majorDeviceClass = null,
                rssi = -60,
                transport = NativeTransport.BLE,
            ),
        )
        fixture.external.emit(
            BluetoothObservation(
                source = BluetoothSource.EXTERNAL_HCI,
                macAddress = TEST_MAC,
                name = "Probe",
                deviceClass = DeviceClass.UNKNOWN,
                rssi = null,
                observedAtMs = 20,
            ),
        )

        assertEquals(1, fixture.subject.devices.value.size)
        assertEquals("Probe", fixture.subject.devices.value.single().name)
        assertEquals("ANDROID HAL + hci2", fixture.subject.adapterLabel.value)
    }

    @Test
    fun `external failure is warning while native remains active`() = runTest {
        val fixture = fixture(externalAdapter = HciAdapter("hci1", true, true))
        fixture.subject.startScan(backgroundScope)
        runCurrent()

        fixture.external.warning.value = "bluelog exited"
        runCurrent()

        assertTrue(fixture.native.active.value)
        assertEquals("bluelog exited", fixture.subject.warning.value)
        assertEquals(null, fixture.subject.fatalError.value)
    }

    @Test
    fun `stop ends both backends exactly once`() = runTest {
        val fixture = fixture(externalAdapter = HciAdapter("hci1", true, true))
        fixture.subject.startScan(backgroundScope)
        runCurrent()

        fixture.subject.stopScan()
        fixture.subject.stopScan()

        assertEquals(1, fixture.nativePlatform.stopCount)
        assertEquals(1, fixture.external.stopCount)
    }

    private fun fixture(externalAdapter: HciAdapter?): Fixture {
        val accumulator = BluetoothDeviceAccumulator()
        val nativePlatform = FakeNativePlatform()
        val native = NativeBluetoothScanner(nativePlatform, accumulator) { 10 }
        val external = FakeExternalBackend(externalAdapter)
        return Fixture(
            subject = CompositeBluetoothScanner(native, external, accumulator),
            native = native,
            nativePlatform = nativePlatform,
            external = external,
        )
    }

    private data class Fixture(
        val subject: CompositeBluetoothScanner,
        val native: NativeBluetoothScanner,
        val nativePlatform: FakeNativePlatform,
        val external: FakeExternalBackend,
    )

    private class FakeNativePlatform : NativeBluetoothPlatform {
        override val available = true
        override val enabled = true
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

        fun emit(event: NativeBluetoothEvent) = requireNotNull(callback).invoke(event)
    }

    private class FakeExternalBackend(
        private val adapter: HciAdapter?,
    ) : OptionalHciBackend {
        override val warning = MutableStateFlow<String?>(null)
        private var callback: ((BluetoothObservation) -> Unit)? = null
        var stopCount = 0

        override suspend fun detectAndStart(
            scope: CoroutineScope,
            onObservation: (BluetoothObservation) -> Unit,
        ): HciAdapter? {
            callback = onObservation
            return adapter
        }

        override fun stop() {
            if (callback == null) return
            stopCount += 1
            callback = null
        }

        fun emit(observation: BluetoothObservation) = requireNotNull(callback).invoke(observation)
    }

    private companion object {
        const val TEST_MAC = "AA:BB:CC:DD:EE:FF"
    }
}

