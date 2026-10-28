package com.medmitra.app.domain

enum class MedicineDoseStatus { DISABLED, SNOOZED, TAKEN, MISSED, UPCOMING }

fun medicineDoseStatus(
    enabled: Boolean,
    nowMillis: Long,
    startOfTodayMillis: Long,
    scheduledTodayMillis: Long,
    lastTakenAtMillis: Long?,
    snoozedUntilMillis: Long?
): MedicineDoseStatus = when {
    !enabled -> MedicineDoseStatus.DISABLED
    snoozedUntilMillis != null && snoozedUntilMillis > nowMillis -> MedicineDoseStatus.SNOOZED
    lastTakenAtMillis != null && lastTakenAtMillis >= startOfTodayMillis && lastTakenAtMillis <= nowMillis -> MedicineDoseStatus.TAKEN
    scheduledTodayMillis <= nowMillis -> MedicineDoseStatus.MISSED
    else -> MedicineDoseStatus.UPCOMING
}

object SnoozeIntervals {
    val optionsMinutes = listOf(5, 10, 15, 30, 60)
    const val DEFAULT_MINUTES = 5
    fun normalize(minutes: Int): Int = minutes.takeIf { it in optionsMinutes } ?: DEFAULT_MINUTES
    fun delayMillis(minutes: Int): Long = normalize(minutes) * 60_000L
}
