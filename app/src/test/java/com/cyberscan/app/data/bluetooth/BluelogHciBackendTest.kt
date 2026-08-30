package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.core.shell.CommandCapabilityProbe
import com.cyberscan.app.core.shell.CommandEnvironment
import com.cyberscan.app.core.shell.CommandEnvironmentResolver
import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.CommandResult
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
class BluelogHciBackendTest {
    @Test
    fun `active external adapter starts bluelog in resolved environment`() = runTest {
        val environment = CommandEnvironment.AndroidRoot
        val executor = FakeExecutor(
            CommandResult(0, "hci2: Type: Primary  Bus: USB\n    UP RUNNING PSCAN", ""),
        )
        val loop = FakeLoopRunner("AA:BB:CC:DD:EE:FF,Probe,Computer,RSSI -40")
        val backend = BluelogHciBackend(
            resolver = resolverFor(environment),
            executor = executor,
            loop = loop,
            clock = { 50 },
        )
        val observations = mutableListOf<BluetoothObservation>()

        val adapter = backend.detectAndStart(backgroundScope, observations::add)
        runCurrent()

        assertEquals("hci2", adapter?.name)
        assertEquals(environment, executor.environment)
        assertEquals(environment, loop.environment)
        assertEquals(listOf("bluelog", "-i", "hci2", "-a", "-v"), loop.command)
        assertEquals(BluetoothSource.EXTERNAL_HCI, observations.single().source)
    }

    @Test
    fun `missing command environment disables external backend`() = runTest {
        val backend = BluelogHciBackend(
            resolver = CommandEnvironmentResolver(CommandCapabilityProbe { _, _ -> false }),
            executor = FakeExecutor(CommandResult(0, "", "")),
            loop = FakeLoopRunner(),
        )

        assertNull(backend.detectAndStart(backgroundScope) {})
    }

    private fun resolverFor(environment: CommandEnvironment) = CommandEnvironmentResolver(
        CommandCapabilityProbe { candidate, commands ->
            candidate == environment && commands == setOf("hciconfig", "bluelog")
        },
    )

    private class FakeExecutor(
        private val result: CommandResult,
    ) : CommandExecutor {
        var environment: CommandEnvironment? = null

        override suspend fun start() = true
        override suspend fun run(command: List<String>) = result
        override suspend fun run(command: List<String>, environment: CommandEnvironment): CommandResult {
            this.environment = environment
            return result
        }
        override fun shutdown() = Unit
    }

    private class FakeLoopRunner(
        private val line: String? = null,
    ) : LoopProcessRunner {
        var command: List<String>? = null
        var environment: CommandEnvironment? = null

        override fun start(command: List<String>): Flow<String> = flow { awaitCancellation() }
        override fun start(command: List<String>, environment: CommandEnvironment): Flow<String> = flow {
            this@FakeLoopRunner.command = command
            this@FakeLoopRunner.environment = environment
            if (line != null) emit(line)
            awaitCancellation()
        }
        override fun stop() = Unit
    }
}

