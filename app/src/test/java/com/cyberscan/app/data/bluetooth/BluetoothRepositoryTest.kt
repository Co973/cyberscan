package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.core.shell.LoopProcessRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothRepositoryTest {
    @Test
    fun `duplicate MAC updates last seen without adding a row`() {
        val repository = BluetoothRepository(FakeLoopRunner(), clock = { 0L })
        val line = "AA:BB:CC:DD:EE:FF,Cyber Deck,Computer,RSSI -40"

        repository.acceptLine(line, nowMs = 100)
        repository.acceptLine(line, nowMs = 200)

        assertEquals(1, repository.devices.value.size)
        assertEquals(100, repository.devices.value.single().firstSeenAtMs)
        assertEquals(200, repository.devices.value.single().lastSeenAtMs)
    }

    @Test
    fun `duplicate start launches bluelog once with selected adapter`() = runTest {
        val loop = FakeLoopRunner()
        val repository = BluetoothRepository(loop, clock = { 1L })
        val adapter = HciAdapter("hci2", isUp = true, isRunning = true)

        repository.startScan(adapter, backgroundScope)
        repository.startScan(adapter, backgroundScope)
        runCurrent()

        assertEquals(1, loop.startCount)
        assertEquals(listOf("bluelog", "-i", "hci2", "-a", "-v"), loop.lastCommand)
        assertEquals(true, repository.active.value)
        assertNull(repository.fatalError.value)
    }

    private class FakeLoopRunner : LoopProcessRunner {
        var startCount = 0
        var lastCommand: List<String>? = null

        override fun start(command: List<String>): Flow<String> = flow {
            startCount += 1
            lastCommand = command
            awaitCancellation()
        }

        override fun stop() = Unit
    }
}
