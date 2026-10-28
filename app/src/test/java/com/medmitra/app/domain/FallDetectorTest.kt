package com.medmitra.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallDetectorTest {
    @Test fun freeFallImpactAndStablePeriodRaisesOneCandidate() {
        val detector = FallDetector()
        assertFalse(detector.addSample(0, 1.0f))
        assertFalse(detector.addSample(300, 25.0f))
        assertFalse(detector.addSample(800, 9.8f))
        assertFalse(detector.addSample(1_300, 9.7f))
        assertFalse(detector.addSample(1_800, 9.8f))
        assertTrue(detector.addSample(3_300, 9.8f))
        assertFalse(detector.addSample(3_500, 9.8f))
    }

    @Test fun impactWithoutPriorFreeFallDoesNotTrigger() {
        val detector = FallDetector()
        assertFalse(detector.addSample(0, 25.0f))
        assertFalse(detector.addSample(3_500, 9.8f))
    }

    @Test fun unstablePostImpactMotionDoesNotTrigger() {
        val detector = FallDetector()
        detector.addSample(0, 1.0f)
        detector.addSample(300, 25.0f)
        detector.addSample(700, 4f)
        detector.addSample(1_200, 18f)
        detector.addSample(1_700, 2f)
        assertFalse(detector.addSample(3_300, 13f))
    }
}
