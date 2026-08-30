package com.cyberscan.app.service

import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.domain.model.NetworkDevice
import com.cyberscan.app.domain.model.ScanPhase
import com.cyberscan.app.domain.model.ScanUiState
import com.cyberscan.app.domain.state.ScanEvent
import com.cyberscan.app.domain.state.ScanReducer
import com.cyberscan.app.domain.usecase.CorrelateWithNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ScanSessionController(
    private val rootExecutor: CommandExecutor,
    private val adapterGateway: BluetoothAdapterGateway,
    private val bluetooth: BluetoothScanGateway,
    private val network: NetworkScanGateway,
    private val emf: EmfReadingSource,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ScanUiState())
    private var networkDevices: List<NetworkDevice> = emptyList()

    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        scope.launch {
            bluetooth.devices.collect {
                publishMergedDevices()
            }
        }
        scope.launch {
            bluetooth.fatalError.filterNotNull().collect { reason ->
                fail(reason)
            }
        }
        scope.launch {
            emf.readings.filterNotNull().collect { reading ->
                reduce(ScanEvent.EmfChanged(reading))
            }
        }
    }

    fun start() {
        if (_state.value.phase == ScanPhase.Calibrating || _state.value.phase == ScanPhase.Scanning) return
        networkDevices = emptyList()
        reduce(ScanEvent.StartRequested)
        launchSession()
    }

    fun retry() {
        bluetooth.stopScan()
        emf.stop()
        networkDevices = emptyList()
        reduce(ScanEvent.RetryRequested)
        launchSession()
    }

    private fun launchSession() {
        scope.launch {
            if (!rootExecutor.start()) {
                fail("Root access denied")
                return@launch
            }

            val adapter = runCatching { adapterGateway.detect() }.getOrNull()
            if (adapter == null) {
                fail("No active Bluetooth adapter detected")
                return@launch
            }

            emf.start()
            bluetooth.startScan(adapter, scope)
            reduce(ScanEvent.CalibrationFinished(adapter.name))

            network.scan("wlan0")
                .onSuccess { devices ->
                    networkDevices = devices
                    reduce(ScanEvent.NetworkAvailable)
                    publishMergedDevices()
                }
                .onFailure {
                    networkDevices = emptyList()
                    reduce(ScanEvent.NetworkUnavailable)
                    publishMergedDevices()
                }
        }
    }

    fun stop() {
        bluetooth.stopScan()
        emf.stop()
        reduce(ScanEvent.StopRequested)
    }

    fun selectTarget(macAddress: String) {
        reduce(ScanEvent.TargetSelected(macAddress))
    }

    private fun publishMergedDevices() {
        val merged = CorrelateWithNetwork.correlate(bluetooth.devices.value, networkDevices)
        reduce(ScanEvent.DevicesChanged(merged))
    }

    private fun fail(reason: String) {
        bluetooth.stopScan()
        emf.stop()
        reduce(ScanEvent.HardFailure(reason))
    }

    @Synchronized
    private fun reduce(event: ScanEvent) {
        _state.value = ScanReducer.reduce(_state.value, event)
    }
}
