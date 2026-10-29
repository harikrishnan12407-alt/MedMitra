package com.medmitra.app.domain

import com.medmitra.app.data.MedicineDoseEvent
import com.medmitra.app.data.MedicineSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class TodayPlanProgress(val completedDoses: Int, val totalDoses: Int) {
    val fraction: Float
        get() = if (totalDoses == 0) 0f else completedDoses.toFloat() / totalDoses

    val percentage: Int
        get() = (fraction * 100).roundToInt()
}

/** One enabled daily schedule represents one dose on the requested local date. */
fun calculateTodayPlanProgress(
    schedules: List<MedicineSchedule>,
    doseEvents: List<MedicineDoseEvent>,
    date: LocalDate,
    zoneId: ZoneId
): TodayPlanProgress {
    val scheduledMedicineIds = schedules.asSequence()
        .filter { it.enabled }
        .map { it.id }
        .toSet()
    if (scheduledMedicineIds.isEmpty()) return TodayPlanProgress(completedDoses = 0, totalDoses = 0)

    val takenMedicineIds = doseEvents.asSequence()
        .filter { it.status.equals("TAKEN", ignoreCase = true) }
        .filter { Instant.ofEpochMilli(it.scheduledAtMillis).atZone(zoneId).toLocalDate() == date }
        .map { it.medicineId }
        .toSet()

    return TodayPlanProgress(
        completedDoses = scheduledMedicineIds.count { it in takenMedicineIds },
        totalDoses = scheduledMedicineIds.size
    )
}
