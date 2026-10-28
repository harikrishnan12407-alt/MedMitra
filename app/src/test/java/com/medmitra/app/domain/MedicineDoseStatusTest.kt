package com.medmitra.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineDoseStatusTest {
    @Test fun snoozeTakesPrecedenceUntilItsDeadline() {
        assertEquals(MedicineDoseStatus.SNOOZED, medicineDoseStatus(true, 1_000, 0, 500, null, 1_001))
        assertEquals(MedicineDoseStatus.MISSED, medicineDoseStatus(true, 1_001, 0, 500, null, 1_001))
    }

    @Test fun takenTodayIncludesDosesRecordedBeforeScheduledTime() {
        assertEquals(MedicineDoseStatus.TAKEN, medicineDoseStatus(true, 700, 0, 900, 650, null))
        assertEquals(MedicineDoseStatus.UPCOMING, medicineDoseStatus(true, 700, 0, 900, null, null))
    }

    @Test fun disabledDoseIsNeverShownAsMissed() {
        assertEquals(MedicineDoseStatus.DISABLED, medicineDoseStatus(false, 1_000, 0, 500, null, null))
    }

    @Test fun snoozeIntervalsAreLimitedToSupportedChoicesAndDefaultToFiveMinutes() {
        assertEquals(300_000L, SnoozeIntervals.delayMillis(5))
        assertEquals(5, SnoozeIntervals.normalize(7))
    }
}
