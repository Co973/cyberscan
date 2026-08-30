package com.cyberscan.app.core.shell

import java.util.concurrent.ConcurrentHashMap

interface ManagedProcess {
    fun terminate()
}

class JavaManagedProcess(
    private val process: Process,
) : ManagedProcess {
    override fun terminate() {
        runCatching {
            process.destroy()
            if (!process.waitFor(150, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }
    }
}

class AppProcessRegistry {
    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    val size: Int
        get() = processes.size

    fun register(tag: String, process: ManagedProcess) {
        require(tag.isNotBlank()) { "Process tag cannot be blank" }
        processes.put(tag, process)?.terminate()
    }

    fun unregister(tag: String) {
        processes.remove(tag)?.terminate()
    }

    fun release(tag: String) {
        processes.remove(tag)
    }

    fun killAll() {
        val snapshot = processes.values.toList()
        processes.clear()
        snapshot.forEach(ManagedProcess::terminate)
    }
}

