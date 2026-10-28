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

    @Test fun callCanBeSkippedWithoutReportingAnAttempt() {
        val flow = EmergencyWorkflow()
        flow.start(1_000)
        flow.tick(6_000)
        flow.locationResolved("NOT_SHARED")
        flow.smsComposerOpened(available = false)
        assertEquals(HandoffStatus.UNAVAILABLE, flow.snapshot.smsStatus)
        flow.skipCall()
        assertEquals(HandoffStatus.NOT_ATTEMPTED, flow.snapshot.callStatus)
        assertEquals(EmergencyStage.COMPLETE, flow.snapshot.stage)
    }

    @Test fun skippingSmsAndCallLeavesBothAttemptsUnchanged() {
        val flow = EmergencyWorkflow()
        flow.start(1_000)
        flow.tick(6_000)
        flow.locationResolved("NOT_SHARED")
        flow.skipSms()
        flow.skipCall()
        assertEquals(HandoffStatus.NOT_ATTEMPTED, flow.snapshot.smsStatus)
        assertEquals(HandoffStatus.NOT_ATTEMPTED, flow.snapshot.callStatus)
    }

    @Test fun directCallRequestRecordsUnknownConnectionOutcome() {
        val flow = EmergencyWorkflow()
        flow.start(1_000)
        flow.tick(6_000)
        flow.locationResolved("NOT_SHARED")
        flow.skipSms()
        flow.callRequested(available = true)
        assertEquals(HandoffStatus.OUTCOME_UNKNOWN, flow.snapshot.callStatus)
        assertEquals(EmergencyStage.COMPLETE, flow.snapshot.stage)
    }

    @Test fun automaticActionsCompleteOnlyAfterCountdownAndKeepOutcomesUnknown() {
        val flow = EmergencyWorkflow()
        flow.start(2_000)
        assertEquals(EmergencyStage.LOCATION_LOOKUP, flow.tick(7_000).stage)
        val done = flow.completeAutomaticActions("AVAILABLE", HandoffStatus.OUTCOME_UNKNOWN, HandoffStatus.OUTCOME_UNKNOWN)
        assertEquals(EmergencyStage.COMPLETE, done.stage)
        assertEquals(HandoffStatus.OUTCOME_UNKNOWN, done.smsStatus)
        assertEquals(HandoffStatus.OUTCOME_UNKNOWN, done.callStatus)
        assertEquals("AVAILABLE", done.locationStatus)
    }

    @Test fun automaticActionsCannotCompleteBeforeCountdownEnds() {
        val flow = EmergencyWorkflow()
        flow.start(0)
        try {
            flow.completeAutomaticActions("NOT_SHARED", HandoffStatus.NOT_ATTEMPTED, HandoffStatus.NOT_ATTEMPTED)
            throw AssertionError("Expected early emergency actions to be rejected")
        } catch (_: IllegalStateException) { }
        assertTrue(flow.cancel())
        assertEquals(EmergencyStage.CANCELLED, flow.snapshot.stage)
    }
}
