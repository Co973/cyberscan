package com.cyberscan.app.service

import com.cyberscan.app.domain.model.BluetoothDevice
import com.cyberscan.app.domain.model.EmfReading
import com.cyberscan.app.domain.model.NetworkDevice
import com.cyberscan.app.domain.model.NetworkStatus
import com.cyberscan.app.domain.model.ScanPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanSessionControllerTest {
    @Test
    fun `network and root capability failure does not block native Bluetooth or EMF`() = runTest {
        val fixture = fixture(
            networkResult = Result.failure(IllegalStateException("Root unavailable")),
            scope = backgroundScope,
        )
        fixture.controller.start()
        runCurrent()
        assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
        assertEquals(1, fixture.bluetooth.startCount)
        assertTrue(fixture.emf.started)
        assertEquals(NetworkStatus.Unavailable, fixture.controller.state.value.networkStatus)
        assertEquals("ANDROID HAL", fixture.controller.state.value.adapterName)
    }

    @Test
    fun `native scanner startup failure is a hard failure`() = runTest {
        val fixture = fixture(
            bluetoothStart = Result.failure(IllegalStateException("Bluetooth is disabled")),
            scope = backgroundScope,
        )
        fixture.controller.start()
        runCurrent()
        assertEquals(ScanPhase.Failed("Bluetooth is disabled"), fixture.controller.state.value.phase)
        assertFalse(fixture.emf.started)
    }

    @Test
    fun `network failure soft-degrades while native Bluetooth keeps scanning`() = runTest {
        val fixture = fixture(
            networkResult = Result.failure(IllegalStateException("no wlan0")),
            scope = backgroundScope,
        )
        fixture.controller.start()
        runCurrent()
        assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
        assertEquals(NetworkStatus.Unavailable, fixture.controller.state.value.networkStatus)
        assertEquals(1, fixture.bluetooth.startCount)
    }

    @Test
    fun `external backend warning leaves native scan active`() = runTest {
        val fixture = fixture(scope = backgroundScope)
        fixture.controller.start()
        runCurrent()
        fixture.bluetooth.warning.value = "No external HCI backend"
        runCurrent()
        assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
        assertEquals("No external HCI backend", fixture.controller.state.value.warning)
        assertTrue(fixture.emf.started)
    }

    @Test
    fun `external adapter label updates while scanning`() = runTest {
        val fixture = fixture(scope = backgroundScope)
        fixture.controller.start()
        runCurrent()
        fixture.bluetooth.adapterLabel.value = "ANDROID HAL + hci1"
        runCurrent()
        assertEquals("ANDROID HAL + hci1", fixture.controller.state.value.adapterName)
    }

    @Test
    fun `duplicate start launches one composite scan`() = runTest {
        val fixture = fixture(scope = backgroundScope)
        fixture.controller.start()
        fixture.controller.start()
        runCurrent()
        assertEquals(1, fixture.bluetooth.startCount)
    }

    @Test
    fun `stop ends hardware and retains complete state`() = runTest {
        val fixture = fixture(scope = backgroundScope)
        fixture.controller.start()
        runCurrent()
        fixture.controller.stop()
        assertEquals(ScanPhase.Complete, fixture.controller.state.value.phase)
        assertEquals(1, fixture.bluetooth.stopCount)
        assertFalse(fixture.emf.started)
    }

    @Test
    fun `native runtime failure becomes hard failure`() = runTest {
        val fixture = fixture(scope = backgroundScope)
        fixture.controller.start()
        runCurrent()
        fixture.bluetooth.fatal.value = "Bluetooth permission revoked"
        runCurrent()
        assertEquals(
            ScanPhase.Failed("Bluetooth permission revoked"),
            fixture.controller.state.value.phase,
        )
    }

    @Test
    fun `retry clears native failure and starts a fresh session`() = runTest {
        val fixture = fixture(
            bluetoothStart = Result.failure(IllegalStateException("Bluetooth is disabled")),
            scope = backgroundScope,
        )
        fixture.controller.start()
        runCurrent()
        fixture.bluetooth.startResult = Result.success(Unit)
        fixture.controller.retry()
        runCurrent()
        assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
        assertEquals(2, fixture.bluetooth.startCount)
    }

    private fun fixture(
        bluetoothStart: Result<Unit> = Result.success(Unit),
        networkResult: Result<List<NetworkDevice>> = Result.success(emptyList()),
        scope: CoroutineScope,
    ): Fixture {
        val bluetooth = FakeBluetoothGateway(bluetoothStart)
        val emf = FakeEmfSource()
        val controller = ScanSessionController(
            bluetooth = bluetooth,
            network = FakeNetworkGateway(networkResult),
            emf = emf,
            scope = scope,
        )
        return Fixture(controller, bluetooth, emf)
    }

    private data class Fixture(
        val controller: ScanSessionController,
        val bluetooth: FakeBluetoothGateway,
        val emf: FakeEmfSource,
    )

    private class FakeBluetoothGateway(
        var startResult: Result<Unit>,
    ) : BluetoothScanGateway {
        override val devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
        val fatal = MutableStateFlow<String?>(null)
        override val fatalError: StateFlow<String?> = fatal
        override val warning = MutableStateFlow<String?>(null)
        override val adapterLabel = MutableStateFlow("ANDROID HAL")
        var startCount = 0
        var stopCount = 0

        override fun startScan(scope: CoroutineScope): Result<Unit> {
            startCount += 1
            return startResult
        }

        override fun stopScan() {
            stopCount += 1
        }
    }

    private class FakeNetworkGateway(
        private val result: Result<List<NetworkDevice>>,
    ) : NetworkScanGateway {
        override suspend fun scan(interfaceName: String): Result<List<NetworkDevice>> = result
    }

    private class FakeEmfSource : EmfReadingSource {
        override val readings = MutableStateFlow<EmfReading?>(null)
        var started = false
        override fun start() { started = true }
        override fun stop() { started = false }
    }
}
