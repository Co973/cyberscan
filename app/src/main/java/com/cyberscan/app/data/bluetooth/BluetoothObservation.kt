package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.DeviceClass

enum class BluetoothSource {
    NATIVE_BLE,
    NATIVE_CLASSIC,
    EXTERNAL_HCI,
}

data class BluetoothObservation(
    val source: BluetoothSource,
    val macAddress: String,
    val name: String?,
    val deviceClass: DeviceClass,
    val rssi: Int?,
    val observedAtMs: Long,
)

