package com.medmitra.app.domain

import kotlin.math.pow

data class FallDetectionConfig(
    val freeFallThreshold: Float = 3.0f,
    val impactThreshold: Float = 22.0f,
    val freeFallToImpactWindowMillis: Long = 1_500,
    val settleWindowMillis: Long = 3_000,
    val stableVarianceThreshold: Float = 2.5f
)

/** Conservative, configurable motion heuristic. It is not medical-grade fall detection. */
class FallDetector(private val config: FallDetectionConfig = FallDetectionConfig()) {
    private enum class Phase { WAITING, FREE_FALL, IMPACT }
    private var phase = Phase.WAITING
    private var freeFallAtMillis = 0L
    private var impactAtMillis = 0L
    private val settleSamples = ArrayList<Float>()

    fun addSample(timestampMillis: Long, accelerationMagnitude: Float): Boolean {
        if (!accelerationMagnitude.isFinite() || accelerationMagnitude < 0f) return false
        when (phase) {
            Phase.WAITING -> if (accelerationMagnitude <= config.freeFallThreshold) {
                phase = Phase.FREE_FALL
                freeFallAtMillis = timestampMillis
            }
            Phase.FREE_FALL -> {
                if (timestampMillis - freeFallAtMillis > config.freeFallToImpactWindowMillis) {
                    reset()
                    if (accelerationMagnitude <= config.freeFallThreshold) {
                        phase = Phase.FREE_FALL
                        freeFallAtMillis = timestampMillis
                    }
                } else if (accelerationMagnitude >= config.impactThreshold) {
                    phase = Phase.IMPACT
                    impactAtMillis = timestampMillis
                    settleSamples.clear()
                }
            }
            Phase.IMPACT -> {
                if (timestampMillis - impactAtMillis > config.settleWindowMillis * 2) {
                    reset()
                } else {
                    settleSamples += accelerationMagnitude
                    if (timestampMillis - impactAtMillis >= config.settleWindowMillis) {
                        val detected = variance(settleSamples) <= config.stableVarianceThreshold
                        reset()
                        return detected
                    }
                }
            }
        }
        return false
    }

    fun reset() {
        phase = Phase.WAITING
        freeFallAtMillis = 0L
        impactAtMillis = 0L
        settleSamples.clear()
    }

    private fun variance(values: List<Float>): Float {
        if (values.size < 2) return Float.POSITIVE_INFINITY
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average().toFloat()
    }
}
