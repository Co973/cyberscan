package com.cyberscan.app.service

import com.cyberscan.app.data.bluetooth.HciAdapter
import com.cyberscan.app.domain.model.BluetoothDevice
import com.cyberscan.app.domain.model.EmfReading
import com.cyberscan.app.domain.model.NetworkDevice
import com.cyberscan.app.domain.model.ScanUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

fun interface BluetoothAdapterGateway {
    suspend fun detect(): HciAdapter?
}

interface BluetoothScanGateway {
    val devices: StateFlow<List<BluetoothDevice>>
    val fatalError: StateFlow<String?>
    fun startScan(adapter: HciAdapter, scope: CoroutineScope)
    fun stopScan()
}

interface NetworkScanGateway {
    suspend fun scan(interfaceName: String = "wlan0"): Result<List<NetworkDevice>>
}

interface EmfReadingSource {
    val readings: StateFlow<EmfReading?>
    fun start()
    fun stop()
}

interface ScanController {
    val state: StateFlow<ScanUiState>
    fun start()
    fun stop()
    fun retry()
    fun selectTarget(macAddress: String)
}

interface ScanServiceLauncher {
    fun start()
    fun stop()
}
