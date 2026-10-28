package com.medmitra.app.domain

import kotlin.math.pow

enum class FallSensitivity(val impactThreshold: Float, val freeFallThreshold: Float, val orientationChangeDegrees: Float, val maximumPostImpactGyro: Float) {
    LOW(26f, 2.4f, 55f, 0.35f),
    MEDIUM(22f, 3.0f, 40f, 0.55f),
    HIGH(18f, 3.8f, 28f, 0.8f)
}

data class FallDetectionConfig(
    val sensitivity: FallSensitivity = FallSensitivity.LOW,
    val freeFallToImpactWindowMillis: Long = 1_500,
    val settleWindowMillis: Long = 3_000,
    val stableVarianceThreshold: Float = 2.5f
)

/** A conservative motion heuristic. It combines low acceleration, impact, orientation change and stillness. */
class FallDetector(private val config: FallDetectionConfig = FallDetectionConfig()) {
    private enum class Phase { WAITING, FREE_FALL, IMPACT }
    private var phase = Phase.WAITING
    private var freeFallAtMillis = 0L
    private var impactAtMillis = 0L
    private var impactOrientation = 0f
    private var maxPostImpactGyro = 0f
    private val settleSamples = ArrayList<Float>()

    fun addSample(timestampMillis: Long, accelerationMagnitude: Float, gyroMagnitudeRadiansPerSecond: Float = 0f, orientationDegrees: Float = 0f): Boolean {
        if (!accelerationMagnitude.isFinite() || accelerationMagnitude < 0f || !gyroMagnitudeRadiansPerSecond.isFinite() || !orientationDegrees.isFinite()) return false
        when (phase) {
            Phase.WAITING -> if (accelerationMagnitude <= config.sensitivity.freeFallThreshold) {
                phase = Phase.FREE_FALL
                freeFallAtMillis = timestampMillis
            }
            Phase.FREE_FALL -> {
                if (timestampMillis - freeFallAtMillis > config.freeFallToImpactWindowMillis || timestampMillis < freeFallAtMillis) {
                    reset()
                    if (accelerationMagnitude <= config.sensitivity.freeFallThreshold) { phase = Phase.FREE_FALL; freeFallAtMillis = timestampMillis }
                } else if (accelerationMagnitude >= config.sensitivity.impactThreshold) {
                    phase = Phase.IMPACT
                    impactAtMillis = timestampMillis
                    impactOrientation = orientationDegrees
                    maxPostImpactGyro = 0f
                    settleSamples.clear()
                }
            }
            Phase.IMPACT -> {
                if (timestampMillis - impactAtMillis > config.settleWindowMillis * 2 || timestampMillis < impactAtMillis) reset()
                else {
                    settleSamples += accelerationMagnitude
                    maxPostImpactGyro = maxOf(maxPostImpactGyro, gyroMagnitudeRadiansPerSecond)
                    if (timestampMillis - impactAtMillis >= config.settleWindowMillis) {
                        val orientationChange = kotlin.math.abs(orientationDegrees - impactOrientation)
                        val still = variance(settleSamples) <= config.stableVarianceThreshold && maxPostImpactGyro <= config.sensitivity.maximumPostImpactGyro
                        val detected = still && orientationChange >= config.sensitivity.orientationChangeDegrees
                        reset()
                        return detected
                    }
                }
            }
        }
        return false
    }

    fun reset() { phase = Phase.WAITING; freeFallAtMillis = 0L; impactAtMillis = 0L; impactOrientation = 0f; maxPostImpactGyro = 0f; settleSamples.clear() }

    private fun variance(values: List<Float>): Float {
        if (values.size < 2) return Float.POSITIVE_INFINITY
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average().toFloat()
    }
}
