package com.medmitra.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderTime {
    fun nextOccurrence(nowMillis: Long, hour: Int, minute: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        require(hour in 0..23) { "hour must be between 0 and 23" }
        require(minute in 0..59) { "minute must be between 0 and 59" }

        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val time = LocalTime.of(hour, minute)
        var next = LocalDateTime.of(now.toLocalDate(), time).atZone(zoneId)
        if (!next.isAfter(now)) next = LocalDateTime.of(now.toLocalDate().plusDays(1), time).atZone(zoneId)
        return next.toInstant().toEpochMilli()
    }
}
