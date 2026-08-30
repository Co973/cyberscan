package com.cyberscan.app.core.shell

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandEnvironmentResolverTest {
    @Test
    fun `Android root wins when all commands exist there`() = runTest {
        val probe = FakeProbe(
            available = mapOf(CommandEnvironment.AndroidRoot to setOf("ip", "nmap")),
        )

        val result = CommandEnvironmentResolver(probe).resolve(setOf("ip", "nmap"))

        assertEquals(CommandEnvironment.AndroidRoot, result)
        assertEquals(listOf(CommandEnvironment.AndroidRoot), probe.visited)
    }

    @Test
    fun `resolver selects existing NetHunter kalifs`() = runTest {
        val kalifs = CommandEnvironment.Chroot("/data/local/nhsystem/kalifs")
        val probe = FakeProbe(
            available = mapOf(kalifs to setOf("hciconfig", "bluelog")),
        )

        val result = CommandEnvironmentResolver(probe).resolve(setOf("hciconfig", "bluelog"))

        assertEquals(kalifs, result)
    }

    @Test
    fun `missing capability resolves to null and result is cached`() = runTest {
        val probe = FakeProbe(emptyMap())
        val resolver = CommandEnvironmentResolver(probe)

        assertNull(resolver.resolve(setOf("nmap")))
        val visitsAfterFirstResolution = probe.visited.size
        assertNull(resolver.resolve(setOf("nmap")))

        assertEquals(visitsAfterFirstResolution, probe.visited.size)
    }

    @Test
    fun `invalid command name is rejected before probing`() = runTest {
        val probe = FakeProbe(emptyMap())

        val result = runCatching {
            CommandEnvironmentResolver(probe).resolve(setOf("nmap; reboot"))
        }

        assertEquals("Invalid command name", result.exceptionOrNull()?.message)
        assertEquals(emptyList<CommandEnvironment>(), probe.visited)
    }

    private class FakeProbe(
        private val available: Map<CommandEnvironment, Set<String>>,
    ) : CommandCapabilityProbe {
        val visited = mutableListOf<CommandEnvironment>()

        override suspend fun hasCommands(
            environment: CommandEnvironment,
            commands: Set<String>,
        ): Boolean {
            visited += environment
            return available[environment]?.containsAll(commands) == true
        }
    }
}

