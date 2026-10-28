package com.medmitra.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyWorkflowTest {
    @Test fun countdownCanBeCancelledWithoutStartingEmergencyFlow() {
        val flow = EmergencyWorkflow()
        flow.start(10_000)
        assertEquals(5, flow.remainingSeconds(10_000))
        assertEquals(2, flow.remainingSeconds(13_000))
        assertTrue(flow.cancel())
        assertFalse(flow.cancel())
        assertEquals(EmergencyStage.CANCELLED, flow.snapshot.stage)
    }

    @Test fun expiredCountdownRequiresLocationThenRecordsUnknownHandoffs() {
        val flow = EmergencyWorkflow()
        flow.start(1_000)
        assertEquals(EmergencyStage.LOCATION_LOOKUP, flow.tick(6_000).stage)
        flow.locationResolved("AVAILABLE")
        flow.smsComposerOpened(available = true)
        assertEquals(HandoffStatus.OUTCOME_UNKNOWN, flow.snapshot.smsStatus)
        flow.dialerOpened(available = true)
        assertEquals(HandoffStatus.USER_ACTION_REQUIRED, flow.snapshot.callStatus)
        assertEquals(EmergencyStage.COMPLETE, flow.snapshot.stage)
    }
}
