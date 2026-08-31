package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.DeviceClass
import com.cyberscan.app.domain.model.normalizeMac

enum class NativeTransport { BLE, CLASSIC }

sealed interface NativeBluetoothEvent {
    data class DeviceFound(
        val macAddress: String,
        val name: String?,
        val majorDeviceClass: Int?,
        val rssi: Int?,
        val transport: NativeTransport,
    ) : NativeBluetoothEvent

    data object ClassicCycleFinished : NativeBluetoothEvent

    data class Failure(val reason: String) : NativeBluetoothEvent
}
interface NativeBluetoothPlatform {
    val available: Boolean
    val enabled: Boolean
    fun start(onEvent: (NativeBluetoothEvent) -> Unit): Result<Unit>
    fun stop()
}

object NativeBluetoothMapper {
    fun map(
        event: NativeBluetoothEvent.DeviceFound,
        observedAtMs: Long,
    ): BluetoothObservation = BluetoothObservation(
        source = when (event.transport) {
            NativeTransport.BLE -> BluetoothSource.NATIVE_BLE
            NativeTransport.CLASSIC -> BluetoothSource.NATIVE_CLASSIC
        },
        macAddress = normalizeMac(event.macAddress),
        name = event.name?.trim()?.takeIf(String::isNotEmpty),
        deviceClass = mapMajorClass(event.majorDeviceClass),
        rssi = event.rssi,
        observedAtMs = observedAtMs,
    )

    private fun mapMajorClass(majorClass: Int?): DeviceClass = when (majorClass) {
        0x100 -> DeviceClass.COMPUTER
        0x200 -> DeviceClass.PHONE
        0x300 -> DeviceClass.NETWORKING
        0x400 -> DeviceClass.AUDIO_VIDEO
        0x500 -> DeviceClass.PERIPHERAL
        0x700 -> DeviceClass.WEARABLE
        else -> DeviceClass.UNKNOWN
    }
}
