package com.medmitra.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.medmitra.app.data.EmergencyHistoryEvent
import com.medmitra.app.data.MedicineDoseEvent
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.data.MedicineSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

class MedicineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_SHOW_REMINDER) return
        val medicineId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1L)
        if (medicineId < 0L) return
        val isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = MedMitraDatabase.get(context)
                val medicine = db.medicines().getById(medicineId)
                if (medicine != null && medicine.enabled) {
                    if (!isSnooze) {
                        ReminderScheduler(context).cancelSnooze(medicineId)
                        ReminderScheduler(context).scheduleDaily(medicine)
                    }
                    db.medicines().setSnoozedUntil(medicineId, null)
                    ReminderNotification.show(context, medicine, isSnooze)
                    val service = Intent(context, MedicineAlarmService::class.java)
                        .setAction(MedicineAlarmService.ACTION_START)
                        .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicineId)
                        .putExtra(MedicineAlarmService.EXTRA_NAME, medicine.name)
                        .putExtra(MedicineAlarmService.EXTRA_DOSAGE, medicine.dosage)
                        .putExtra(MedicineAlarmService.EXTRA_HOUR, medicine.hour)
                        .putExtra(MedicineAlarmService.EXTRA_MINUTE, medicine.minute)
                        .putExtra(MedicineAlarmService.EXTRA_INSTRUCTIONS, medicine.instructions)
                        .putExtra(ReminderScheduler.EXTRA_IS_SNOOZE, isSnooze)
                    runCatching { ContextCompat.startForegroundService(context, service) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class MedicineActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1L)
        if (medicineId < 0L) return
        val action = intent.getStringExtra(ReminderScheduler.EXTRA_ACTION) ?: return
        if (action == ReminderScheduler.ACTION_MARK_TAKEN || action == ReminderScheduler.ACTION_SNOOZE) {
            stopMedicineAlarm(context, medicineId)
            context.sendBroadcast(Intent(MedicineAlarmActivity.ACTION_DISMISS).setPackage(context.packageName).putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicineId))
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = MedMitraDatabase.get(context)
                val medicine = db.medicines().getById(medicineId)
                when (action) {
                    ReminderScheduler.ACTION_MARK_TAKEN -> {
                        db.medicines().markTaken(medicineId, System.currentTimeMillis())
                        db.history().insert(
                            EmergencyHistoryEvent(
                                occurredAtMillis = System.currentTimeMillis(),
                                eventType = "MEDICINE_MARKED_TAKEN",
                                notes = medicine?.name.orEmpty()
                            )
                        )
                        ReminderScheduler(context).cancelSnooze(medicineId)
                        db.medicines().setSnoozedUntil(medicineId, null)
                        if (medicine != null) db.medicineDoseHistory().insert(
                            MedicineDoseEvent(
                                medicineId = medicineId, medicineName = medicine.name, dosage = medicine.dosage,
                                scheduledAtMillis = scheduledTimeMillis(medicine.hour, medicine.minute),
                                actionAtMillis = System.currentTimeMillis(), status = "TAKEN"
                            )
                        )
                        NotificationManagerCompat.from(context).cancel(medicineId.toInt())
                    }
                    ReminderScheduler.ACTION_SNOOZE -> {
                        if (medicine != null && medicine.enabled) {
                            ReminderScheduler(context).scheduleSnooze(medicineId)
                            val until = System.currentTimeMillis() + MedicineReminderPreferences.snoozeDelayMillis(context)
                            db.medicines().setSnoozedUntil(medicineId, until)
                            db.medicineDoseHistory().insert(
                                MedicineDoseEvent(medicineId, medicine.name, medicine.dosage, scheduledTimeMillis(medicine.hour, medicine.minute), System.currentTimeMillis(), "SNOOZED")
                            )
                        }
                        NotificationManagerCompat.from(context).cancel(medicineId.toInt())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object ReminderNotification {
    fun show(context: Context, medicine: MedicineSchedule, isSnooze: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "Medicine reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Scheduled reminders you create in MedMitra" }
            )
        }

        val openIntent = Intent(context, MedicineAlarmActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicine.id)
            .putExtra(ReminderScheduler.EXTRA_IS_SNOOZE, isSnooze)
        val openPending = PendingIntent.getActivity(
            context, medicine.id.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isSnooze) "Snoozed medicine reminder" else "Medicine reminder")
            .setContentText("${medicine.name} · ${medicine.dosage} · ${formatScheduledTime(medicine.hour, medicine.minute)}")
            .setStyle(Notification.BigTextStyle().bigText(buildString {
                append("Time for ${medicine.name}: ${medicine.dosage}. ${formatScheduledTime(medicine.hour, medicine.minute)}.")
                if (medicine.instructions.isNotBlank()) append(" ${medicine.instructions}")
            }))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPending)
            .setFullScreenIntent(openPending, canUseFullScreenIntent(context))
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.checkbox_on_background,
                    "Taken", actionPending(context, medicine.id, ReminderScheduler.ACTION_MARK_TAKEN)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_lock_idle_alarm,
                    "Snooze ${MedicineReminderPreferences.snoozeMinutes(context)} min", actionPending(context, medicine.id, ReminderScheduler.ACTION_SNOOZE)
                ).build()
            )
            .build()

        try {
            manager.notify(medicine.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Notifications can be revoked. The schedule remains saved and the UI reports the permission state.
        }
    }

    fun foregroundSummary(context: Context, medicine: MedicineSchedule): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(ALARM_SERVICE_CHANNEL, "Active medicine alarm", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, ALARM_SERVICE_CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        return builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Medicine alarm active")
            .setContentText("${medicine.name} · ${medicine.dosage}").setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).build()
    }

    private fun actionPending(context: Context, id: Long, action: String): PendingIntent {
        val intent = Intent(context, MedicineActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, id)
            .putExtra(ReminderScheduler.EXTRA_ACTION, action)
        return PendingIntent.getBroadcast(
            context, (id.toInt() * 31) + action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val ALARM_SERVICE_CHANNEL = "medicine_alarm_service"

    private fun canUseFullScreenIntent(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

internal fun scheduledTimeMillis(hour: Int, minute: Int): Long {
    val now = java.time.ZonedDateTime.now()
    return now.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

internal fun formatScheduledTime(hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = MedMitraDatabase.get(context).medicines().getEnabled()
                val scheduler = ReminderScheduler(context)
                val now = System.currentTimeMillis()
                schedules.forEach { schedule ->
                    scheduler.scheduleDaily(schedule)
                    schedule.snoozedUntilMillis?.let { deadline -> scheduler.scheduleSnooze(schedule.id, (deadline - now).coerceAtLeast(1_000L)) }
                }
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    MedicineAlarmService.pendingActiveIds(context).forEach { id -> scheduler.scheduleSnooze(id, 1_000L) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
