package com.cyberscan.app.domain.usecase

import com.cyberscan.app.domain.model.BluetoothDevice
import com.cyberscan.app.domain.model.Confidence
import com.cyberscan.app.domain.model.DeviceClass
import com.cyberscan.app.domain.model.MergedDevice
import com.cyberscan.app.domain.model.NetworkDevice

object ClassifyDevice {
    fun baseConfidence(deviceClass: DeviceClass): Confidence = when (deviceClass) {
        DeviceClass.COMPUTER,
        DeviceClass.NETWORKING,
        DeviceClass.PHONE,
        -> Confidence.MAYBE

        DeviceClass.AUDIO_VIDEO,
        DeviceClass.PERIPHERAL,
        DeviceClass.WEARABLE,
        DeviceClass.UNKNOWN,
        -> Confidence.NONE
    }
}

object CorrelateWithNetwork {
    fun correlate(
        bluetoothDevices: List<BluetoothDevice>,
        networkDevices: List<NetworkDevice>,
    ): List<MergedDevice> = bluetoothDevices.map { bluetooth ->
        val baseConfidence = ClassifyDevice.baseConfidence(bluetooth.deviceClass)
        if (baseConfidence == Confidence.NONE) {
            return@map MergedDevice(
                bluetooth = bluetooth,
                correlatedNetwork = null,
                confidence = Confidence.NONE,
                confidenceReason = "Bluetooth class is not an IP-network candidate",
            )
        }

        val ouiMatch = networkDevices.firstOrNull {
            ouiOf(it.normalizedMacAddress) == ouiOf(bluetooth.macAddress)
        }
        if (ouiMatch != null) {
            return@map MergedDevice(
                bluetooth = bluetooth,
                correlatedNetwork = ouiMatch,
                confidence = Confidence.HIGH,
                confidenceReason = "Manufacturer prefix matched a network host",
            )
        }

        val normalizedBluetoothName = normalizeName(bluetooth.name)
        val hostnameMatch = if (normalizedBluetoothName.isEmpty()) {
            null
        } else {
            networkDevices.firstOrNull { network ->
                val normalizedHostname = normalizeName(network.hostname)
                normalizedHostname.isNotEmpty() &&
                    (normalizedHostname.contains(normalizedBluetoothName) ||
                        normalizedBluetoothName.contains(normalizedHostname))
            }
        }

        if (hostnameMatch != null) {
            MergedDevice(
                bluetooth = bluetooth,
                correlatedNetwork = hostnameMatch,
                confidence = Confidence.HIGH,
                confidenceReason = "Advertised name matched the network hostname",
            )
        } else {
            MergedDevice(
                bluetooth = bluetooth,
                correlatedNetwork = null,
                confidence = Confidence.MAYBE,
                confidenceReason = "Bluetooth class may support IP networking",
            )
        }
    }

    private fun ouiOf(macAddress: String): String = macAddress.split(':').take(3).joinToString(":")

    private fun normalizeName(value: String?): String = value
        ?.lowercase()
        ?.filter(Char::isLetterOrDigit)
        .orEmpty()
}

