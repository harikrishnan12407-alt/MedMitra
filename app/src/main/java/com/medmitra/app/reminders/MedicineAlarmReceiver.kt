package com.medmitra.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
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
        val doseAtMillis = intent.getLongExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, 0L)
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
                    db.medicines().setSnooze(medicineId, null, null)
                    ReminderNotification.show(context, medicine, isSnooze, doseAtMillis)
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
        val doseAtMillis = intent.getLongExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, 0L)
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
                        ReminderScheduler(context).cancelSnooze(medicineId)
                        db.withTransaction {
                            db.medicines().setSnooze(medicineId, null, null)
                            if (medicine != null) {
                                val scheduledAt = doseAtMillis.takeIf { it > 0L }
                                    ?: scheduledTimeMillis(medicine.hour, medicine.minute)
                                if (!db.medicineDoseHistory().wasTaken(medicineId, scheduledAt)) {
                                    val actionAt = System.currentTimeMillis()
                                    db.medicines().markTaken(medicineId, actionAt)
                                    db.medicineDoseHistory().insert(MedicineDoseEvent(
                                        medicineId = medicineId, medicineName = medicine.name, dosage = medicine.dosage,
                                        scheduledAtMillis = scheduledAt, actionAtMillis = actionAt, status = "TAKEN"
                                    ))
                                    db.history().insert(EmergencyHistoryEvent(
                                        occurredAtMillis = actionAt, eventType = "MEDICINE_MARKED_TAKEN", notes = medicine.name
                                    ))
                                }
                            }
                        }
                        NotificationManagerCompat.from(context).cancel(medicineId.toInt())
                    }
                    ReminderScheduler.ACTION_SNOOZE -> {
                        if (medicine != null && medicine.enabled) {
                            val scheduledAt = doseAtMillis.takeIf { it > 0L }
                                ?: scheduledTimeMillis(medicine.hour, medicine.minute)
                            ReminderScheduler(context).scheduleSnooze(medicineId, doseAtMillis = scheduledAt)
                            val until = System.currentTimeMillis() + ReminderScheduler.SNOOZE_DELAY_MILLIS
                            db.medicines().setSnooze(medicineId, until, scheduledAt)
                            db.medicineDoseHistory().insert(
                                MedicineDoseEvent(
                                    medicineId = medicineId,
                                    medicineName = medicine.name,
                                    dosage = medicine.dosage,
                                    scheduledAtMillis = scheduledAt,
                                    actionAtMillis = System.currentTimeMillis(),
                                    status = "SNOOZED"
                                )
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
    fun ensureReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "Medicine reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Scheduled reminders you create in MedMitra" }
            )
        }
    }

    fun show(context: Context, medicine: MedicineSchedule, isSnooze: Boolean, doseAtMillis: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureReminderChannel(context)

        val openIntent = Intent(context, MedicineAlarmActivity::class.java)
            .setData(Uri.parse("medmitra://alarm/${medicine.id}"))
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicine.id)
            .putExtra(ReminderScheduler.EXTRA_IS_SNOOZE, isSnooze)
            .putExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, doseAtMillis)
        val openPending = PendingIntent.getActivity(
            context, medicine.id.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPending)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_MAX)
        }
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.checkbox_on_background,
                "Taken", actionPending(context, medicine.id, ReminderScheduler.ACTION_MARK_TAKEN, doseAtMillis)
            ).build()
        ).addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_lock_idle_alarm,
                "Snooze 5 min", actionPending(context, medicine.id, ReminderScheduler.ACTION_SNOOZE, doseAtMillis)
            ).build()
        )
        if (isFullScreenIntentAllowed(context, manager)) builder.setFullScreenIntent(openPending, true)
        val notification = builder.build()

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

    private fun actionPending(context: Context, id: Long, action: String, doseAtMillis: Long): PendingIntent {
        val intent = Intent(context, MedicineActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("medmitra://medicine-action/$id/${if (action == ReminderScheduler.ACTION_SNOOZE) "snooze" else "taken"}/$doseAtMillis"))
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, id)
            .putExtra(ReminderScheduler.EXTRA_ACTION, action)
            .putExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, doseAtMillis)
        return PendingIntent.getBroadcast(
            context, (id.toInt() * 31) + action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val ALARM_SERVICE_CHANNEL = "medicine_alarm_service"

    private fun isFullScreenIntentAllowed(context: Context, manager: NotificationManager): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getNotificationChannel(ReminderScheduler.CHANNEL_ID)?.importance ?: NotificationManager.IMPORTANCE_NONE
        } else NotificationManager.IMPORTANCE_HIGH
        val specialAccess = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
        return ReminderAlarmPresentationPolicy.allowFullScreen(
            Build.VERSION.SDK_INT, specialAccess, notificationsEnabled, importance, NotificationManager.IMPORTANCE_HIGH
        )
    }
}

internal object ReminderAlarmPresentationPolicy {
    fun allowFullScreen(apiLevel: Int, fullScreenAccess: Boolean, notificationsEnabled: Boolean, channelImportance: Int, highImportance: Int): Boolean =
        notificationsEnabled && channelImportance >= highImportance && (apiLevel < 34 || fullScreenAccess)
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
                    schedule.snoozedUntilMillis?.let { deadline -> scheduler.scheduleSnooze(schedule.id, (deadline - now).coerceAtLeast(1_000L), schedule.snoozedDoseAtMillis ?: (deadline - ReminderScheduler.SNOOZE_DELAY_MILLIS)) }
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
