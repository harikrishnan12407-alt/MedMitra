package com.medmitra.app.domain

import com.medmitra.app.data.MedicineDoseEvent
import com.medmitra.app.data.MedicineSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TodayPlanProgressTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 10, 29)

    @Test fun noSchedulesProducesZeroOfZero() {
        val progress = calculateTodayPlanProgress(emptyList(), emptyList(), today, zone)
        assertEquals(0, progress.completedDoses)
        assertEquals(0, progress.totalDoses)
        assertEquals(0, progress.percentage)
    }

    @Test fun onlyEnabledSchedulesAreCounted() {
        val progress = calculateTodayPlanProgress(
            listOf(schedule(1), schedule(2, enabled = false)), emptyList(), today, zone
        )
        assertEquals(0, progress.completedDoses)
        assertEquals(1, progress.totalDoses)
        assertEquals(0, progress.percentage)
    }

    @Test fun takenCountsOnceAndSnoozesAndPreviousLocalDayDoNotCount() {
        val schedules = listOf(schedule(1), schedule(2), schedule(3))
        val todayAtNoon = ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val previousDay = ZonedDateTime.of(2026, 10, 28, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val events = listOf(
            event(1, todayAtNoon, "TAKEN"),
            event(1, todayAtNoon, "TAKEN"), // duplicate history must not inflate completion
            event(2, todayAtNoon, "SNOOZED"),
            event(3, previousDay, "TAKEN")
        )

        val progress = calculateTodayPlanProgress(schedules, events, today, zone)
        assertEquals(1, progress.completedDoses)
        assertEquals(3, progress.totalDoses)
        assertEquals(33, progress.percentage)
    }

    private fun schedule(id: Long, enabled: Boolean = true) = MedicineSchedule(
        id = id, name = "Medicine $id", dosage = "1 tablet", hour = 9, minute = 0, enabled = enabled
    )

    private fun event(medicineId: Long, scheduledAt: Long, status: String) = MedicineDoseEvent(
        medicineId = medicineId, medicineName = "Medicine $medicineId", dosage = "1 tablet",
        scheduledAtMillis = scheduledAt, actionAtMillis = scheduledAt + 60_000, status = status
    )
}
