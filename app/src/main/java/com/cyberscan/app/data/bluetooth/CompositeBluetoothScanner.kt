package com.cyberscan.app.data.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface OptionalHciBackend {
    val warning: StateFlow<String?>

    suspend fun detectAndStart(
        scope: CoroutineScope,
        onObservation: (BluetoothObservation) -> Unit,
    ): HciAdapter?

    fun stop()
}

class CompositeBluetoothScanner(
    private val native: NativeBluetoothScanner,
    private val external: OptionalHciBackend,
    private val accumulator: BluetoothDeviceAccumulator,
) {
    private val _adapterLabel = MutableStateFlow(ANDROID_ADAPTER_LABEL)
    private val _warning = MutableStateFlow<String?>(null)
    private val sessionJobs = mutableListOf<Job>()
    private var active = false

    val devices = accumulator.devices
    val fatalError: StateFlow<String?> = native.failure
    val adapterLabel: StateFlow<String> = _adapterLabel.asStateFlow()
    val warning: StateFlow<String?> = _warning.asStateFlow()

    @Synchronized
    fun startScan(scope: CoroutineScope): Result<Unit> {
        if (active) return Result.success(Unit)
        accumulator.clear()
        _adapterLabel.value = ANDROID_ADAPTER_LABEL
        _warning.value = null

        val nativeResult = native.start()
        if (nativeResult.isFailure) return nativeResult
        active = true

        sessionJobs += scope.launch {
            external.warning.collect { message ->
                if (message != null) _warning.value = message
            }
        }
        sessionJobs += scope.launch {
            runCatching { external.detectAndStart(scope, accumulator::accept) }
                .onSuccess { adapter ->
                    if (adapter != null) {
                        _adapterLabel.value = "$ANDROID_ADAPTER_LABEL + ${adapter.name}"
                    }
                }
                .onFailure { error ->
                    _warning.value = error.message ?: "External Bluetooth backend unavailable"
                }
        }
        return Result.success(Unit)
    }

    @Synchronized
    fun stopScan() {
        if (!active) return
        sessionJobs.forEach(Job::cancel)
        sessionJobs.clear()
        external.stop()
        native.stop()
        active = false
    }

    private companion object {
        const val ANDROID_ADAPTER_LABEL = "ANDROID HAL"
    }
}

