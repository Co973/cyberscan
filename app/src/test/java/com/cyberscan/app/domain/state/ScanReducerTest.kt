package com.cyberscan.app.domain.state

import com.cyberscan.app.domain.model.NetworkStatus
import com.cyberscan.app.domain.model.ScanPhase
import com.cyberscan.app.domain.model.ScanUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ScanReducerTest {
    @Test
    fun `start enters calibration with clean session state`() {
        val dirty = ScanUiState(
            phase = ScanPhase.Complete,
            selectedMac = "AA:BB:CC:11:22:33",
            networkStatus = NetworkStatus.Unavailable,
            adapterName = "hci4",
        )

        val result = ScanReducer.reduce(dirty, ScanEvent.StartRequested)

        assertEquals(ScanPhase.Calibrating, result.phase)
        assertEquals(NetworkStatus.Pending, result.networkStatus)
        assertEquals(null, result.selectedMac)
        assertEquals(null, result.adapterName)
    }

    @Test
    fun `duplicate start while active is ignored`() {
        val scanning = ScanUiState(phase = ScanPhase.Scanning, adapterName = "hci1")

        val result = ScanReducer.reduce(scanning, ScanEvent.StartRequested)

        assertSame(scanning, result)
    }

    @Test
    fun `calibration completion records adapter and begins scanning`() {
        val calibrating = ScanUiState(phase = ScanPhase.Calibrating)

        val result = ScanReducer.reduce(calibrating, ScanEvent.CalibrationFinished("hci2"))

        assertEquals(ScanPhase.Scanning, result.phase)
        assertEquals("hci2", result.adapterName)
    }

    @Test
    fun `soft network failure keeps scanning`() {
        val scanning = ScanUiState(phase = ScanPhase.Scanning)

        val result = ScanReducer.reduce(scanning, ScanEvent.NetworkUnavailable)

        assertEquals(ScanPhase.Scanning, result.phase)
        assertEquals(NetworkStatus.Unavailable, result.networkStatus)
    }

    @Test
    fun `stop retains the current session as complete`() {
        val scanning = ScanUiState(phase = ScanPhase.Scanning, adapterName = "hci1")

        val result = ScanReducer.reduce(scanning, ScanEvent.StopRequested)

        assertEquals(ScanPhase.Complete, result.phase)
        assertEquals("hci1", result.adapterName)
    }

    @Test
    fun `hard failure ends the current session`() {
        val scanning = ScanUiState(phase = ScanPhase.Scanning)

        val result = ScanReducer.reduce(scanning, ScanEvent.HardFailure("bluelog exited"))

        assertEquals(ScanPhase.Failed("bluelog exited"), result.phase)
    }

    @Test
    fun `retry resets failure and starts calibration`() {
        val failed = ScanUiState(phase = ScanPhase.Failed("root denied"))

        val result = ScanReducer.reduce(failed, ScanEvent.RetryRequested)

        assertEquals(ScanPhase.Calibrating, result.phase)
        assertEquals(NetworkStatus.Pending, result.networkStatus)
    }
}
