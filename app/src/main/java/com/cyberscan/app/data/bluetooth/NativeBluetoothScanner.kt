package com.cyberscan.app.data.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NativeBluetoothScanner(
    private val platform: NativeBluetoothPlatform,
    private val accumulator: BluetoothDeviceAccumulator,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _failure = MutableStateFlow<String?>(null)
    private val _active = MutableStateFlow(false)

    val failure: StateFlow<String?> = _failure.asStateFlow()
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Synchronized
    fun start(): Result<Unit> {
        if (_active.value) return Result.success(Unit)
        if (!platform.available) {
            return Result.failure(IllegalStateException("No Android Bluetooth adapter is available"))
        }
        if (!platform.enabled) {
            return Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        _failure.value = null
        return platform.start(::handleEvent).onSuccess {
            _active.value = true
        }
    }

    @Synchronized
    fun stop() {
        if (!_active.value) return
        platform.stop()
        _active.value = false
    }

    private fun handleEvent(event: NativeBluetoothEvent) {
        when (event) {
            is NativeBluetoothEvent.DeviceFound -> runCatching {
                accumulator.accept(NativeBluetoothMapper.map(event, clock()))
            }.onFailure { _failure.value = "Invalid Bluetooth result: ${it.message}" }
            is NativeBluetoothEvent.Failure -> _failure.value = event.reason
            NativeBluetoothEvent.ClassicCycleFinished -> Unit
        }
    }
}
