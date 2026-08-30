package com.cyberscan.app.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cyberscan.app.domain.model.EmfReading
import com.cyberscan.app.service.EmfReadingSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmfFilter(
    private val calibrationDurationMs: Long = 2_000,
    private val alpha: Float = 0.2f,
) {
    private var calibrationStartedAtMs: Long? = null
    private var baselineTotal = 0f
    private var baselineSamples = 0
    private var baseline: Float? = null
    private var smoothedMagnitude: Float? = null

    init {
        require(calibrationDurationMs > 0) { "Calibration duration must be positive" }
        require(alpha in 0f..1f && alpha > 0f) { "EMA alpha must be in (0, 1]" }
    }

    fun accept(magnitude: Float, timestampMs: Long): EmfReading? {
        require(magnitude.isFinite() && magnitude >= 0f) { "Magnitude must be finite and non-negative" }
        val startedAt = calibrationStartedAtMs ?: timestampMs.also { calibrationStartedAtMs = it }
        val currentBaseline = baseline
        if (currentBaseline == null && timestampMs - startedAt < calibrationDurationMs) {
            baselineTotal += magnitude
            baselineSamples += 1
            smoothedMagnitude = magnitude
            return null
        }

        val resolvedBaseline = currentBaseline ?: (baselineTotal / baselineSamples.coerceAtLeast(1)).also {
            baseline = it
        }
        val previousMagnitude = smoothedMagnitude ?: resolvedBaseline
        val smoothed = previousMagnitude + alpha * (magnitude - previousMagnitude)
        smoothedMagnitude = smoothed
        return EmfReading(
            magnitudeMicroTesla = smoothed,
            baselineMicroTesla = resolvedBaseline,
            anomalyMicroTesla = smoothed - resolvedBaseline,
            timestampMs = timestampMs,
        )
    }

    fun reset() {
        calibrationStartedAtMs = null
        baselineTotal = 0f
        baselineSamples = 0
        baseline = null
        smoothedMagnitude = null
    }
}

class EmfSensorManager(
    @ApplicationContext context: Context,
) : EmfReadingSource, SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val filter = EmfFilter()
    private val _readings = MutableStateFlow<EmfReading?>(null)
    private var lastPublishedAtMs = 0L

    override val readings: StateFlow<EmfReading?> = _readings.asStateFlow()

    override fun start() {
        filter.reset()
        lastPublishedAtMs = 0L
        _readings.value = null
        magnetometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD || event.values.size < 3) return
        val nowMs = System.currentTimeMillis()
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        val reading = filter.accept(magnitude, nowMs) ?: return
        if (nowMs - lastPublishedAtMs >= PUBLISH_INTERVAL_MS) {
            _readings.value = reading
            lastPublishedAtMs = nowMs
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val PUBLISH_INTERVAL_MS = 83L
    }
}

