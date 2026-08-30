package com.cyberscan.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

@SuppressLint("MissingPermission")
class AndroidNativeBluetoothPlatform(
    private val context: Context,
) : NativeBluetoothPlatform {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter?
        get() = manager?.adapter

    private var active = false
    private var receiver: BroadcastReceiver? = null
    private var scanCallback: ScanCallback? = null

    override val available: Boolean
        get() = adapter != null

    override val enabled: Boolean
        get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    override fun start(onEvent: (NativeBluetoothEvent) -> Unit): Result<Unit> = runCatching {
        check(!active) { "Native Bluetooth scanning is already active" }
        val bluetoothAdapter = checkNotNull(adapter) { "No Android Bluetooth adapter is available" }
        check(bluetoothAdapter.isEnabled) { "Bluetooth is disabled" }

        active = true
        val discoveryReceiver = createDiscoveryReceiver(bluetoothAdapter, onEvent)
        receiver = discoveryReceiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(discoveryReceiver, filter)
        }

        val callback = createScanCallback(onEvent)
        scanCallback = callback
        bluetoothAdapter.bluetoothLeScanner?.startScan(callback)

        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        check(bluetoothAdapter.startDiscovery()) { "Classic Bluetooth discovery could not start" }
    }.onFailure {
        stop()
    }

    override fun stop() {
        val bluetoothAdapter = adapter
        scanCallback?.let { callback ->
            runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback) }
        }
        runCatching {
            if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter.cancelDiscovery()
        }
        receiver?.let { registered -> runCatching { context.unregisterReceiver(registered) } }
        scanCallback = null
        receiver = null
        active = false
    }

    private fun createScanCallback(onEvent: (NativeBluetoothEvent) -> Unit) =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onEvent(
                    NativeBluetoothEvent.DeviceFound(
                        macAddress = result.device.address,
                        name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull(),
                        majorDeviceClass = runCatching {
                            result.device.bluetoothClass?.majorDeviceClass
                        }.getOrNull(),
                        rssi = result.rssi,
                        transport = NativeTransport.BLE,
                    ),
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                onEvent(NativeBluetoothEvent.Failure("BLE scan failed ($errorCode)"))
            }
        }

    private fun createDiscoveryReceiver(
        bluetoothAdapter: BluetoothAdapter,
        onEvent: (NativeBluetoothEvent) -> Unit,
    ) = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.bluetoothDeviceExtra() ?: return
                    val rawRssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    onEvent(
                        NativeBluetoothEvent.DeviceFound(
                            macAddress = device.address,
                            name = runCatching { device.name }.getOrNull(),
                            majorDeviceClass = runCatching {
                                device.bluetoothClass?.majorDeviceClass
                            }.getOrNull(),
                            rssi = rawRssi.takeUnless { it == Short.MIN_VALUE }?.toInt(),
                            transport = NativeTransport.CLASSIC,
                        ),
                    )
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    onEvent(NativeBluetoothEvent.ClassicCycleFinished)
                    if (active && !bluetoothAdapter.startDiscovery()) {
                        onEvent(NativeBluetoothEvent.Failure("Classic Bluetooth discovery could not restart"))
                    }
                }
            }
        }
    }
}

private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
    getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}

