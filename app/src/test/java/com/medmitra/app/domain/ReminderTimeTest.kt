package com.medmitra.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun futureTimeTodayIsSelected() {
        val now = ZonedDateTime.of(2026, 1, 2, 8, 0, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 2, 9, 30, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), ReminderTime.nextOccurrence(now.toInstant().toEpochMilli(), 9, 30, zone))
    }

    @Test fun currentOrPastTimeMovesToTomorrow() {
        val now = ZonedDateTime.of(2026, 1, 2, 9, 30, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 3, 9, 30, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), ReminderTime.nextOccurrence(now.toInstant().toEpochMilli(), 9, 30, zone))
    }

    @Test fun invalidTimeIsRejected() {
        try {
            ReminderTime.nextOccurrence(Instant.EPOCH.toEpochMilli(), 24, 0, zone)
            throw AssertionError("Expected invalid hour to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun selectedClockTimeIsPersistableAndSchedulesAtExactMinute() {
        val now = ZonedDateTime.of(2026, 1, 2, 7, 0, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 2, 13, 5, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), ReminderTime.nextOccurrence(now.toInstant().toEpochMilli(), 13, 5, zone))
    }
}
