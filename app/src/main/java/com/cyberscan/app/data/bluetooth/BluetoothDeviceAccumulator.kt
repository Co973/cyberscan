package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.BluetoothDevice
import com.cyberscan.app.domain.model.DeviceClass
import com.cyberscan.app.domain.model.normalizeMac
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothDeviceAccumulator {
    private val entries = linkedMapOf<String, BluetoothDevice>()
    private val sources = mutableMapOf<String, MutableSet<BluetoothSource>>()
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    @Synchronized
    fun accept(observation: BluetoothObservation) {
        val mac = normalizeMac(observation.macAddress)
        val current = entries[mac]
        entries[mac] = BluetoothDevice(
            macAddress = mac,
            name = observation.name?.takeIf(String::isNotBlank) ?: current?.name,
            deviceClass = observation.deviceClass.takeUnless { it == DeviceClass.UNKNOWN }
                ?: current?.deviceClass
                ?: DeviceClass.UNKNOWN,
            rssi = observation.rssi ?: current?.rssi,
            firstSeenAtMs = minOf(
                current?.firstSeenAtMs ?: observation.observedAtMs,
                observation.observedAtMs,
            ),
            lastSeenAtMs = maxOf(
                current?.lastSeenAtMs ?: observation.observedAtMs,
                observation.observedAtMs,
            ),
        )
        sources.getOrPut(mac, ::mutableSetOf).add(observation.source)
        _devices.value = entries.values.toList()
    }

    @Synchronized
    fun sourcesFor(macAddress: String): Set<BluetoothSource> =
        runCatching { normalizeMac(macAddress) }
            .getOrNull()
            ?.let { sources[it]?.toSet() }
            .orEmpty()

    @Synchronized
    fun clear() {
        entries.clear()
        sources.clear()
        _devices.value = emptyList()
    }
}

