package com.medmitra.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallDetectorTest {
    @Test fun impactNeedsPriorFreeFallStablePeriodAndOrientationChange() {
        val detector = FallDetector()
        assertFalse(detector.addSample(0, 1f, orientationDegrees = 0f))
        assertFalse(detector.addSample(300, 27f, orientationDegrees = 0f))
        assertFalse(detector.addSample(800, 9.8f, 0.1f, 5f))
        assertFalse(detector.addSample(1_300, 9.7f, 0.1f, 20f))
        assertFalse(detector.addSample(1_800, 9.8f, 0.1f, 35f))
        assertTrue(detector.addSample(3_300, 9.8f, 0.1f, 60f))
        assertFalse(detector.addSample(3_500, 9.8f, 0.1f, 60f))
    }

    @Test fun stableImpactWithoutOrientationChangeDoesNotTrigger() {
        val detector = FallDetector()
        detector.addSample(0, 1f)
        detector.addSample(300, 27f)
        detector.addSample(700, 9.8f, 0.1f, 2f)
        detector.addSample(1_200, 9.7f, 0.1f, 2f)
        detector.addSample(1_700, 9.8f, 0.1f, 2f)
        assertFalse(detector.addSample(3_300, 9.8f, 0.1f, 2f))
    }

    @Test fun unstableMovementAndLateImpactDoNotTrigger() {
        val detector = FallDetector()
        detector.addSample(0, 1f)
        assertFalse(detector.addSample(1_700, 27f, 0.1f, 60f))
        detector.addSample(2_000, 1f)
        detector.addSample(2_100, 27f, 0.1f, 60f)
        detector.addSample(2_700, 4f, 1.2f, 80f)
        detector.addSample(3_200, 18f, 1.2f, 90f)
        detector.addSample(3_700, 2f, 1.2f, 100f)
        assertFalse(detector.addSample(5_100, 13f, 1.2f, 110f))
    }

    @Test fun sensitivityProfilesAreOrderedForImpactAndOrientation() {
        assertTrue(FallSensitivity.LOW.impactThreshold > FallSensitivity.MEDIUM.impactThreshold)
        assertTrue(FallSensitivity.MEDIUM.impactThreshold > FallSensitivity.HIGH.impactThreshold)
        assertTrue(FallSensitivity.LOW.orientationChangeDegrees > FallSensitivity.HIGH.orientationChangeDegrees)
        assertEquals(FallSensitivity.LOW, FallDetectionConfig().sensitivity)
    }
}
