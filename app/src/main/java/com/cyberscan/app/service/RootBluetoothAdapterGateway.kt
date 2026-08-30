package com.cyberscan.app.service

import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.data.bluetooth.BluetoothAdapterDetector
import com.cyberscan.app.data.bluetooth.HciAdapter

class RootBluetoothAdapterGateway(
    private val commandExecutor: CommandExecutor,
) : BluetoothAdapterGateway {
    override suspend fun detect(): HciAdapter? {
        val result = commandExecutor.run(listOf("hciconfig", "-a"))
        if (result.exitCode != 0) return null
        return BluetoothAdapterDetector.select(BluetoothAdapterDetector.parse(result.stdout))
    }
}

