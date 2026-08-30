package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.core.shell.LoopProcessDiedUnexpectedlyException
import com.cyberscan.app.core.shell.LoopProcessRunner
import com.cyberscan.app.domain.model.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class BluetoothRepository(
    private val loopRunner: LoopProcessRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val deviceMap = linkedMapOf<String, BluetoothDevice>()
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _active = MutableStateFlow(false)
    private val _fatalError = MutableStateFlow<String?>(null)

    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()
    val active: StateFlow<Boolean> = _active.asStateFlow()
    val fatalError: StateFlow<String?> = _fatalError.asStateFlow()

    fun startScan(adapter: HciAdapter, scope: CoroutineScope) {
        if (_active.value) return
        require(ADAPTER_NAME.matches(adapter.name)) { "Invalid Bluetooth adapter name" }
        require(adapter.isUp && adapter.isRunning) { "Bluetooth adapter is not active" }

        deviceMap.clear()
        _devices.value = emptyList()
        _fatalError.value = null
        _active.value = true

        scope.launch {
            loopRunner.start(listOf("bluelog", "-i", adapter.name, "-a", "-v"))
                .catch { error ->
                    _fatalError.value = when (error) {
                        is LoopProcessDiedUnexpectedlyException -> error.message
                        else -> "Bluetooth scan failed: ${error.message ?: "unknown error"}"
                    }
                    _active.value = false
                }
                .collect { acceptLine(it, clock()) }
        }
    }

    @Synchronized
    fun acceptLine(line: String, nowMs: Long) {
        val parsed = BluelogParser.parse(line, nowMs) ?: return
        val existing = deviceMap[parsed.macAddress]
        deviceMap[parsed.macAddress] = if (existing == null) {
            parsed
        } else {
            existing.copy(
                name = parsed.name ?: existing.name,
                deviceClass = parsed.deviceClass.takeUnless { it == com.cyberscan.app.domain.model.DeviceClass.UNKNOWN }
                    ?: existing.deviceClass,
                rssi = parsed.rssi ?: existing.rssi,
                lastSeenAtMs = parsed.lastSeenAtMs,
            )
        }
        _devices.value = deviceMap.values.toList()
    }

    fun stopScan() {
        loopRunner.stop()
        _active.value = false
    }

    private companion object {
        val ADAPTER_NAME = Regex("^hci[0-9]+$")
    }
}

