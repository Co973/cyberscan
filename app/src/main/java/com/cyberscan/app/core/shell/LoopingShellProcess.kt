package com.cyberscan.app.core.shell

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class LoopProcessDiedUnexpectedlyException(
    tag: String,
    exitCode: Int?,
) : Exception("Loop process '$tag' terminated unexpectedly (exit code: ${exitCode ?: "unknown"})")

interface LoopProcessRunner {
    fun start(command: List<String>): Flow<String>
    fun stop()
}

class LoopingShellProcess(
    private val tag: String,
    private val processRegistry: AppProcessRegistry,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val useChroot: Boolean = true,
    private val chrootPath: String = "/data/local/nhsystem/kali-armhf",
) : LoopProcessRunner {
    @Volatile
    private var stopRequested = false
    private var process: Process? = null

    override fun start(command: List<String>): Flow<String> = callbackFlow {
        require(command.isNotEmpty()) { "Loop command cannot be empty" }
        require(command.none(String::isBlank)) { "Loop arguments cannot be blank" }
        stopRequested = false

        val payload = command.joinToString(" ", transform = ::shellWord)
        val privilegedPayload = if (useChroot) {
            "chroot ${shellWord(chrootPath)} /bin/bash -lc ${shellWord(payload)}"
        } else {
            payload
        }
        val runningProcess = try {
            ProcessBuilder("su", "-c", privilegedPayload)
                .redirectErrorStream(true)
                .start()
        } catch (exception: Exception) {
            close(exception)
            return@callbackFlow
        }

        process = runningProcess
        processRegistry.register(tag, JavaManagedProcess(runningProcess))
        val reader = BufferedReader(InputStreamReader(runningProcess.inputStream))

        try {
            while (true) {
                val line = reader.readLine() ?: break
                trySend(line)
            }
            processRegistry.release(tag)
            if (!stopRequested) {
                close(LoopProcessDiedUnexpectedlyException(tag, runningProcess.waitFor()))
            } else {
                close()
            }
        } catch (_: Exception) {
            processRegistry.release(tag)
            if (!stopRequested) close(LoopProcessDiedUnexpectedlyException(tag, null)) else close()
        }

        awaitClose { stop() }
    }.flowOn(ioDispatcher)

    override fun stop() {
        stopRequested = true
        processRegistry.unregister(tag)
        process = null
    }
}

