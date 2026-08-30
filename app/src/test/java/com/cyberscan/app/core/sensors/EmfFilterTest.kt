package com.cyberscan.app.core.sensors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmfFilterTest {
    @Test
    fun `baseline average and EMA yield stable anomaly`() {
        val filter = EmfFilter(calibrationDurationMs = 2_000, alpha = 0.2f)

        repeat(20) { index ->
            assertNull(filter.accept(magnitude = 50f, timestampMs = index * 100L))
        }
        val reading = filter.accept(magnitude = 60f, timestampMs = 2_100)!!

        assertEquals(50f, reading.baselineMicroTesla, 0.01f)
        assertEquals(52f, reading.magnitudeMicroTesla, 0.01f)
        assertTrue(reading.anomalyMicroTesla in 1.99f..2.01f)
    }

    @Test
    fun `reset requires a fresh baseline`() {
        val filter = EmfFilter(calibrationDurationMs = 100, alpha = 0.5f)
        filter.accept(40f, 0)
        filter.accept(40f, 100)
        filter.reset()

        assertNull(filter.accept(70f, 1_000))
        assertNull(filter.accept(70f, 1_050))
        assertEquals(70f, filter.accept(70f, 1_100)!!.baselineMicroTesla, 0.01f)
    }
}

