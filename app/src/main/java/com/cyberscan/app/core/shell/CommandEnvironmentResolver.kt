package com.cyberscan.app.core.shell

fun interface CommandCapabilityProbe {
    suspend fun hasCommands(
        environment: CommandEnvironment,
        commands: Set<String>,
    ): Boolean
}
class CommandEnvironmentResolver(
    private val probe: CommandCapabilityProbe,
) {
    private val cache = mutableMapOf<Set<String>, CommandEnvironment?>()

    suspend fun resolve(requiredCommands: Set<String>): CommandEnvironment? {
        require(requiredCommands.isNotEmpty()) { "At least one command is required" }
        require(requiredCommands.all(COMMAND_NAME::matches)) { "Invalid command name" }
        val key = requiredCommands.toSortedSet()
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }

        val resolved = CANDIDATES.firstOrNull { environment ->
            runCatching { probe.hasCommands(environment, key) }.getOrDefault(false)
        }
        synchronized(cache) { cache[key] = resolved }
        return resolved
    }

    fun clear() = synchronized(cache) { cache.clear() }

    private companion object {
        val COMMAND_NAME = Regex("^[A-Za-z0-9_.+-]+$")
        val CANDIDATES = listOf(
            CommandEnvironment.AndroidRoot,
            CommandEnvironment.Chroot("/data/local/nhsystem/kalifs"),
            CommandEnvironment.Chroot("/data/local/nhsystem/kali-arm64"),
            CommandEnvironment.Chroot("/data/local/nhsystem/kali-armhf"),
        )
    }
}

class ShellCommandCapabilityProbe(
    private val executor: CommandExecutor,
) : CommandCapabilityProbe {
    override suspend fun hasCommands(
        environment: CommandEnvironment,
        commands: Set<String>,
    ): Boolean = commands.all { command ->
        val result = executor.run(listOf("command", "-v", command), environment)
        result.exitCode == 0 && result.stdout.isNotBlank()
    }
}
