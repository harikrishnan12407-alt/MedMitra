package com.medmitra.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.medmitra.app.MainActivity
import com.medmitra.app.data.EmergencyHistoryEvent
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.data.MedicineSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    if (!isSnooze) ReminderScheduler(context).scheduleDaily(medicine)
                    ReminderNotification.show(context, medicine, isSnooze)
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
                        NotificationManagerCompat.from(context).cancel(medicineId.toInt())
                    }
                    ReminderScheduler.ACTION_SNOOZE -> {
                        if (medicine != null && medicine.enabled) {
                            ReminderScheduler(context).scheduleSnooze(medicineId)
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

        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_OPEN_REMINDERS, true)
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
            .setContentText("${medicine.name} · ${medicine.dosage}")
            .setStyle(Notification.BigTextStyle().bigText("Time for ${medicine.name}: ${medicine.dosage}."))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.checkbox_on_background,
                    "Taken", actionPending(context, medicine.id, ReminderScheduler.ACTION_MARK_TAKEN)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_lock_idle_alarm,
                    "Snooze 10 min", actionPending(context, medicine.id, ReminderScheduler.ACTION_SNOOZE)
                ).build()
            )
            .build()

        try {
            manager.notify(medicine.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Notifications can be revoked. The schedule remains saved and the UI reports the permission state.
        }
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
}

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
                schedules.forEach { schedule -> scheduler.scheduleDaily(schedule) }
            } finally {
                pending.finish()
            }
        }
    }
}
