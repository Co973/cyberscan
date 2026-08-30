package com.cyberscan.app.core.shell

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface CommandExecutor {
    suspend fun start(): Boolean
    suspend fun run(command: List<String>): CommandResult
    fun shutdown()
}

class ShellExecutor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val useChroot: Boolean = true,
    private val chrootPath: String = "/data/local/nhsystem/kali-armhf",
) : CommandExecutor {
    private val commandLock = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    override suspend fun start(): Boolean = withContext(ioDispatcher) {
        commandLock.withLock {
            if (process?.isAlive == true) return@withLock true
            runCatching {
                val rootProcess = ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start()
                process = rootProcess
                writer = BufferedWriter(OutputStreamWriter(rootProcess.outputStream))
                reader = BufferedReader(InputStreamReader(rootProcess.inputStream))

                val result = runUnlocked(listOf("id"), applyChroot = false)
                result.exitCode == 0 && result.stdout.lineSequence().any { "uid=0" in it }
            }.getOrElse {
                closeProcess()
                false
            }
        }
    }

    override suspend fun run(command: List<String>): CommandResult = withContext(ioDispatcher) {
        commandLock.withLock {
            check(process?.isAlive == true) { "Root shell is not started" }
            runUnlocked(command, applyChroot = useChroot)
        }
    }

    private fun runUnlocked(command: List<String>, applyChroot: Boolean): CommandResult {
        require(command.isNotEmpty()) { "Command cannot be empty" }
        require(command.none(String::isBlank)) { "Command arguments cannot be blank" }
        val output = reader ?: error("Root shell reader is unavailable")
        val input = writer ?: error("Root shell writer is unavailable")
        val marker = "__CYBERSCAN_${UUID.randomUUID()}__"
        val payload = command.joinToString(" ", transform = ::shellWord)
        val privilegedPayload = if (applyChroot) {
            "chroot ${shellWord(chrootPath)} /bin/bash -lc ${shellWord(payload)}"
        } else {
            payload
        }

        input.write("$privilegedPayload; printf '\\n$marker:%s\\n' \"${'$'}?\"\n")
        input.flush()

        val lines = mutableListOf<String>()
        while (true) {
            val line = output.readLine() ?: error("Root shell closed before command completion")
            if (line.startsWith("$marker:")) {
                return CommandResult(
                    exitCode = line.substringAfter(':').toIntOrNull() ?: -1,
                    stdout = lines.joinToString("\n"),
                    stderr = "",
                )
            }
            lines += line
        }
    }

    override fun shutdown() {
        runCatching {
            writer?.write("exit\n")
            writer?.flush()
        }
        closeProcess()
    }

    private fun closeProcess() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { process?.destroy() }
        writer = null
        reader = null
        process = null
    }
}

internal fun shellWord(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

