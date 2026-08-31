package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.core.shell.CommandEnvironmentResolver
import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.LoopProcessDiedUnexpectedlyException
import com.cyberscan.app.core.shell.LoopProcessRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class BluelogHciBackend(
    private val resolver: CommandEnvironmentResolver,
    private val executor: CommandExecutor,
    private val loop: LoopProcessRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) : OptionalHciBackend {
    private val _warning = MutableStateFlow<String?>(null)
    private var active = false

    override val warning: StateFlow<String?> = _warning.asStateFlow()

    override suspend fun detectAndStart(
        scope: CoroutineScope,
        onObservation: (BluetoothObservation) -> Unit,
    ): HciAdapter? {
        if (active) return null
        _warning.value = null
        if (!executor.start()) return null
        val environment = resolver.resolve(setOf("hciconfig", "bluelog")) ?: return null
        val result = executor.run(listOf("hciconfig", "-a"), environment)
        if (result.exitCode != 0) return null
        val adapter = BluetoothAdapterDetector.select(BluetoothAdapterDetector.parse(result.stdout))
            ?: return null
        active = true
        scope.launch {
            loop.start(listOf("bluelog", "-i", adapter.name, "-a", "-v"), environment)
                .catch { error ->
                    _warning.value = when (error) {
                        is LoopProcessDiedUnexpectedlyException -> error.message
                        else -> "External Bluetooth scan failed: ${error.message ?: "unknown error"}"
                    }
                    active = false
                }
                .collect { line ->
                    BluelogParser.parse(line, clock())?.let { device ->
                        onObservation(
                            BluetoothObservation(
                                source = BluetoothSource.EXTERNAL_HCI,
                                macAddress = device.macAddress,
                                name = device.name,
                                deviceClass = device.deviceClass,
                                rssi = device.rssi,
                                observedAtMs = device.lastSeenAtMs,
                            ),
                        )
                    }
                }
        }
        return adapter
    }

    override fun stop() {
        if (!active) return
        loop.stop()
        active = false
    }
}
