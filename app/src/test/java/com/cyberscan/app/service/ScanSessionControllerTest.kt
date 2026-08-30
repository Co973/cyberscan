package com.cyberscan.app.service

import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.CommandResult
import com.cyberscan.app.data.bluetooth.HciAdapter
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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanSessionControllerTest {
    @Test
    fun `root denial is a hard failure and starts no hardware`() = runTest {
        val fixture = fixture(rootGranted = false, scope = backgroundScope)

        fixture.controller.start()
        runCurrent()

        assertEquals(ScanPhase.Failed("Root access denied"), fixture.controller.state.value.phase)
        assertEquals(0, fixture.bluetooth.startCount)
        assertFalse(fixture.emf.started)
    }

    @Test
    fun `missing adapter is a hard failure`() = runTest {
        val fixture = fixture(adapter = null, scope = backgroundScope)

        fixture.controller.start()
        runCurrent()

        assertEquals(
            ScanPhase.Failed("No active Bluetooth adapter detected"),
            fixture.controller.state.value.phase,
        )
    }

    @Test
    fun `network failure soft-degrades while Bluetooth keeps scanning`() = runTest {
        val fixture = fixture(
            networkResult = Result.failure(IllegalStateException("no wlan0")),
            scope = backgroundScope,
        )

        fixture.controller.start()
        runCurrent()

        assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
        assertEquals(NetworkStatus.Unavailable, fixture.controller.state.value.networkStatus)
        assertEquals(1, fixture.bluetooth.startCount)
        assertEquals(true, fixture.emf.started)
    }

    @Test
    fun `duplicate start launches one Bluetooth loop`() = runTest {
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
    fun `unexpected Bluetooth loop death becomes hard failure`() = runTest {
        val fixture = fixture(scope = backgroundScope)
        fixture.controller.start()
        runCurrent()

        fixture.bluetooth.fatal.value = "bluelog exited"
        runCurrent()

        assertEquals(ScanPhase.Failed("bluelog exited"), fixture.controller.state.value.phase)
    }

    @Test
    fun `retry clears failure and starts a fresh session`() = runTest {
        val fixture = fixture(rootGranted = false, scope = backgroundScope)
        fixture.controller.start()
        runCurrent()
        fixture.root.granted = true

        fixture.controller.retry()
        runCurrent()

        assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
        assertEquals(1, fixture.bluetooth.startCount)
    }

    private fun fixture(
        rootGranted: Boolean = true,
        adapter: HciAdapter? = HciAdapter("hci1", true, true),
        networkResult: Result<List<NetworkDevice>> = Result.success(emptyList()),
        scope: CoroutineScope,
    ): Fixture {
        val root = FakeRootExecutor(rootGranted)
        val bluetooth = FakeBluetoothGateway()
        val emf = FakeEmfSource()
        val controller = ScanSessionController(
            rootExecutor = root,
            adapterGateway = BluetoothAdapterGateway { adapter },
            bluetooth = bluetooth,
            network = FakeNetworkGateway(networkResult),
            emf = emf,
            scope = scope,
        )
        return Fixture(controller, root, bluetooth, emf)
    }

    private data class Fixture(
        val controller: ScanSessionController,
        val root: FakeRootExecutor,
        val bluetooth: FakeBluetoothGateway,
        val emf: FakeEmfSource,
    )

    private class FakeRootExecutor(
        var granted: Boolean,
    ) : CommandExecutor {
        override suspend fun start(): Boolean = granted
        override suspend fun run(command: List<String>): CommandResult = error("Not used")
        override fun shutdown() = Unit
    }

    private class FakeBluetoothGateway : BluetoothScanGateway {
        override val devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
        val fatal = MutableStateFlow<String?>(null)
        override val fatalError: StateFlow<String?> = fatal
        var startCount = 0
        var stopCount = 0

        override fun startScan(adapter: HciAdapter, scope: CoroutineScope) {
            startCount += 1
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

        override fun start() {
            started = true
        }

        override fun stop() {
            started = false
        }
    }
}
