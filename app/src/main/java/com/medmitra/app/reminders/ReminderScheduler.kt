package com.medmitra.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.medmitra.app.data.MedicineSchedule
import com.medmitra.app.domain.ReminderTime
import java.time.ZoneId

class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    fun scheduleDaily(schedule: MedicineSchedule, nowMillis: Long = System.currentTimeMillis()) {
        if (!schedule.enabled) {
            cancel(schedule.id)
            return
        }
        val triggerAt = ReminderTime.nextOccurrence(nowMillis, schedule.hour, schedule.minute, ZoneId.systemDefault())
        setAlarm(schedule.id, triggerAt, snooze = false, doseAtMillis = triggerAt)
    }

    fun scheduleSnooze(scheduleId: Long, afterMillis: Long = SNOOZE_DELAY_MILLIS, doseAtMillis: Long = System.currentTimeMillis()) {
        setAlarm(scheduleId, System.currentTimeMillis() + afterMillis, snooze = true, doseAtMillis = doseAtMillis)
    }

    fun cancel(scheduleId: Long) {
        cancelAlarm(scheduleId, snooze = false)
        cancelSnooze(scheduleId)
    }

    fun cancelSnooze(scheduleId: Long) = cancelAlarm(scheduleId, snooze = true)

    private fun cancelAlarm(scheduleId: Long, snooze: Boolean) {
        val pending = alarmPendingIntent(scheduleId, snooze, PendingIntent.FLAG_NO_CREATE, 0L)
        if (pending != null) {
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    private fun setAlarm(scheduleId: Long, atMillis: Long, snooze: Boolean, doseAtMillis: Long) {
        val pending = alarmPendingIntent(scheduleId, snooze, PendingIntent.FLAG_UPDATE_CURRENT, doseAtMillis) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (_: SecurityException) {
            // Exact-alarm access may be revoked between checking and scheduling; use an inexact alarm.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun alarmPendingIntent(scheduleId: Long, snooze: Boolean, flags: Int, doseAtMillis: Long): PendingIntent? {
        val intent = Intent(appContext, MedicineAlarmReceiver::class.java)
            .setAction(ACTION_SHOW_REMINDER)
            .setData(Uri.parse("medmitra://reminder/$scheduleId/${if (snooze) "snooze" else "daily"}"))
            .putExtra(EXTRA_MEDICINE_ID, scheduleId)
            .putExtra(EXTRA_IS_SNOOZE, snooze)
            .putExtra(EXTRA_DOSE_AT_MILLIS, doseAtMillis)
        val requestCode = requestCode(scheduleId, snooze)
        return PendingIntent.getBroadcast(appContext, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.medmitra.app.action.SHOW_MEDICINE_REMINDER"
        const val ACTION_MARK_TAKEN = "com.medmitra.app.action.MARK_MEDICINE_TAKEN"
        const val ACTION_SNOOZE = "com.medmitra.app.action.SNOOZE_MEDICINE"
        const val EXTRA_MEDICINE_ID = "medicine_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        const val EXTRA_ACTION = "reminder_action"
        const val EXTRA_DOSE_AT_MILLIS = "dose_at_millis"
        const val EXTRA_OPEN_REMINDERS = "open_reminders"
        const val CHANNEL_ID = "medicine_reminders"
        const val SNOOZE_DELAY_MILLIS = 5 * 60 * 1000L
        fun requestCode(id: Long, snooze: Boolean): Int =
            ((id xor (id ushr 32)).toInt() * 31) + if (snooze) 1 else 0
    }
}
