package com.cyberscan.app.data.network

import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.CommandResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkRepositoryTest {
    @Test
    fun `scan derives CIDR and invokes XML nmap sweep`() = runTest {
        val executor = FakeCommandExecutor(
            responses = listOf(
                CommandResult(0, "7: wlan0    inet 192.168.50.42/24 brd 192.168.50.255", ""),
                CommandResult(
                    0,
                    """<nmaprun><host><address addr="192.168.50.9" addrtype="ipv4"/><address addr="AA:BB:CC:11:22:33" addrtype="mac" vendor="Intel"/></host></nmaprun>""",
                    "",
                ),
            ),
        )
        val repository = NetworkRepository(executor)

        val devices = repository.scan().getOrThrow()

        assertEquals("192.168.50.9", devices.single().ipAddress)
        assertEquals(
            listOf(
                listOf("ip", "-o", "-4", "addr", "show", "dev", "wlan0"),
                listOf("nmap", "-sn", "-oX", "-", "192.168.50.0/24"),
            ),
            executor.commands,
        )
    }

    @Test
    fun `missing subnet soft-fails before nmap`() = runTest {
        val executor = FakeCommandExecutor(listOf(CommandResult(0, "", "")))

        val result = NetworkRepository(executor).scan()

        assertTrue(result.isFailure)
        assertEquals(1, executor.commands.size)
    }

    @Test
    fun `nmap nonzero exit returns failure`() = runTest {
        val executor = FakeCommandExecutor(
            listOf(
                CommandResult(0, "wlan0 inet 10.0.0.2/24", ""),
                CommandResult(2, "", "permission denied"),
            ),
        )

        assertTrue(NetworkRepository(executor).scan().isFailure)
    }

    private class FakeCommandExecutor(
        private val responses: List<CommandResult>,
    ) : CommandExecutor {
        val commands = mutableListOf<List<String>>()
        private var responseIndex = 0

        override suspend fun start(): Boolean = true

        override suspend fun run(command: List<String>): CommandResult {
            commands += command
            return responses[responseIndex++]
        }

        override fun shutdown() = Unit
    }
}
