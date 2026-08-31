package com.cyberscan.app.domain.state

import com.cyberscan.app.domain.model.EmfReading
import com.cyberscan.app.domain.model.MergedDevice
import com.cyberscan.app.domain.model.NetworkStatus
import com.cyberscan.app.domain.model.ScanPhase
import com.cyberscan.app.domain.model.ScanUiState
import com.cyberscan.app.domain.model.normalizeMac

sealed interface ScanEvent {
    data object StartRequested : ScanEvent
    data class CalibrationFinished(val adapterName: String) : ScanEvent
    data class AdapterChanged(val adapterName: String) : ScanEvent
    data class WarningChanged(val warning: String?) : ScanEvent
    data class DevicesChanged(val devices: List<MergedDevice>) : ScanEvent
    data object NetworkAvailable : ScanEvent
    data object NetworkUnavailable : ScanEvent
    data class EmfChanged(val reading: EmfReading) : ScanEvent
    data class TargetSelected(val macAddress: String) : ScanEvent
    data object StopRequested : ScanEvent
    data class HardFailure(val reason: String) : ScanEvent
    data object RetryRequested : ScanEvent
}

object ScanReducer {
    fun reduce(state: ScanUiState, event: ScanEvent): ScanUiState = when (event) {
        ScanEvent.StartRequested -> when (state.phase) {
            ScanPhase.Calibrating,
            ScanPhase.Scanning,
            -> state

            else -> freshCalibrationState()
        }

        ScanEvent.RetryRequested -> freshCalibrationState()

        is ScanEvent.CalibrationFinished -> if (state.phase == ScanPhase.Calibrating) {
            state.copy(phase = ScanPhase.Scanning, adapterName = event.adapterName)
        } else {
            state
        }

        is ScanEvent.AdapterChanged -> state.copy(adapterName = event.adapterName)
        is ScanEvent.WarningChanged -> state.copy(warning = event.warning)

        is ScanEvent.DevicesChanged -> {
            val selectedStillExists = event.devices.any {
                it.bluetooth.macAddress == state.selectedMac
            }
            state.copy(
                devices = event.devices,
                selectedMac = when {
                    selectedStillExists -> state.selectedMac
                    else -> event.devices.firstOrNull()?.bluetooth?.macAddress
                },
            )
        }

        ScanEvent.NetworkAvailable -> state.copy(networkStatus = NetworkStatus.Available)
        ScanEvent.NetworkUnavailable -> state.copy(networkStatus = NetworkStatus.Unavailable)
        is ScanEvent.EmfChanged -> state.copy(emf = event.reading)
        is ScanEvent.TargetSelected -> state.copy(selectedMac = normalizeMac(event.macAddress))

        ScanEvent.StopRequested -> when (state.phase) {
            ScanPhase.Calibrating,
            ScanPhase.Scanning,
            -> state.copy(phase = ScanPhase.Complete)

            else -> state
        }

        is ScanEvent.HardFailure -> state.copy(phase = ScanPhase.Failed(event.reason))
    }

    private fun freshCalibrationState(): ScanUiState = ScanUiState(
        phase = ScanPhase.Calibrating,
        networkStatus = NetworkStatus.Pending,
    )
}
