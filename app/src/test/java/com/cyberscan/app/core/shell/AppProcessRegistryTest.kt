package com.cyberscan.app.core.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppProcessRegistryTest {
    @Test
    fun `killAll is idempotent`() {
        val registry = AppProcessRegistry()
        val process = FakeManagedProcess()
        registry.register("bluelog", process)

        registry.killAll()
        registry.killAll()

        assertEquals(1, process.killCount)
        assertEquals(0, registry.size)
    }

    @Test
    fun `registering the same tag kills the replaced process`() {
        val registry = AppProcessRegistry()
        val first = FakeManagedProcess()
        val second = FakeManagedProcess()

        registry.register("bluelog", first)
        registry.register("bluelog", second)

        assertEquals(1, first.killCount)
        assertEquals(0, second.killCount)
        assertEquals(1, registry.size)
    }

    private class FakeManagedProcess : ManagedProcess {
        var killCount = 0

        override fun terminate() {
            killCount += 1
        }
    }
}

