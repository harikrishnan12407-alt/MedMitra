package com.medmitra.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.data.MedicineSchedule
import com.medmitra.app.domain.MedicineDoseStatus
import com.medmitra.app.domain.medicineDoseStatus
import com.medmitra.app.ui.components.IconBadge
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.components.SoftPill
import com.medmitra.app.ui.theme.MedMitraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

@Composable
fun MedicineReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { MedMitraDatabase.get(context) }
    val schedules by database.medicines().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MedicineSchedule?>(null) }
    var reminderDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var alarmDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var notificationGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var fullScreenGranted by remember { mutableStateOf(fullScreenAlarmAccess(context)) }
    var alarmChannelImportance by remember { mutableStateOf(alarmChannelImportance(context)) }
    var exactAlarmsAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
    }
    LaunchedEffect(Unit) {
        ReminderNotification.ensureReminderChannel(context)
        alarmChannelImportance = alarmChannelImportance(context)
        withContext(Dispatchers.IO) { rescheduleEnabledMedicines(context, database) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                fullScreenGranted = fullScreenAlarmAccess(context)
                alarmChannelImportance = alarmChannelImportance(context)
                val exactAccessNow = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                if (exactAlarmsAllowed != exactAccessNow) {
                    exactAlarmsAllowed = exactAccessNow
                    scope.launch(Dispatchers.IO) { rescheduleEnabledMedicines(context, database) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Medicine Reminders", onBack = onBack)
        Text("Saved schedules", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
        if (schedules.isEmpty()) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("No medicine reminders yet", color = MedMitraColors.Text)
                Text("Add a medicine and its daily reminder time.", color = MedMitraColors.TextSecondary)
            }
        }
        schedules.forEach { medicine ->
            MedicineScheduleItem(
                medicine = medicine,
                onToggle = { enabled ->
                    scope.launch {
                        val updated = medicine.copy(enabled = enabled)
                        withContext(Dispatchers.IO) { database.medicines().update(updated) }
                        if (enabled) ReminderScheduler(context).scheduleDaily(updated)
                        else {
                            ReminderScheduler(context).cancel(updated.id)
                            stopMedicineAlarm(context, updated.id)
                            NotificationManagerCompat.from(context).cancel(updated.id.toInt())
                        }
                    }
                },
                onTaken = {
                    context.sendBroadcast(Intent(context, MedicineActionReceiver::class.java).setAction(ReminderScheduler.ACTION_MARK_TAKEN).putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicine.id).putExtra(ReminderScheduler.EXTRA_ACTION, ReminderScheduler.ACTION_MARK_TAKEN))
                },
                onEdit = { editTarget = medicine; showEditor = true },
                onDelete = {
                    scope.launch(Dispatchers.IO) {
                        ReminderScheduler(context).cancel(medicine.id)
                        stopMedicineAlarm(context, medicine.id)
                        NotificationManagerCompat.from(context).cancel(medicine.id.toInt())
                        database.medicines().delete(medicine.id)
                    }
                }
            )
        }
        Button(onClick = { showEditor = true }, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add medicine reminder")
        }
        ReminderExpandableSection(
            title = "Notification and alarm access",
            expanded = reminderDetailsExpanded,
            onToggle = { reminderDetailsExpanded = !reminderDetailsExpanded }
        ) {
            Text(
                "Reminders are stored on this device. Android may delay them when exact alarm access is off or battery restrictions are active.",
                color = MedMitraColors.TextSecondary
            )
            Text("Notifications", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (notificationGranted) "Allowed" else "Off · reminders may not appear",
                color = MedMitraColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!notificationGranted) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Enable notifications") }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                Text("Alarm timing", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (exactAlarmsAllowed) "Exact reminders are allowed" else "Exact access is off; reminders may be delayed",
                    color = MedMitraColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!exactAlarmsAllowed) TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("Allow exact alarms") }
            }
        }
        ReminderExpandableSection(
            title = "Alarm experience and Android settings",
            expanded = alarmDetailsExpanded,
            onToggle = { alarmDetailsExpanded = !alarmDetailsExpanded }
        ) {
            Text("Alarm experience", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Text("Alarm sound and optional speech repeat until you choose Taken or Snooze. Snooze is 5 minutes. When the phone is locked, Android can open the full-screen alarm if notifications, High channel importance, and full-screen access are enabled. When the phone is unlocked, Android 13 and later show an expanded alarm notification instead of taking over another app; this is enforced by Android.", color = MedMitraColors.TextSecondary)
            val channelReady = Build.VERSION.SDK_INT < 26 || alarmChannelImportance >= android.app.NotificationManager.IMPORTANCE_HIGH
            Text(
                when {
                    !notificationGranted -> "Full-screen alarm unavailable: notifications are disabled."
                    !fullScreenGranted -> "Full-screen access is off. Android will keep the notification as fallback."
                    !channelReady -> "Medicine notification channel is below High importance."
                    else -> "Ready for automatic full-screen launch on the lock screen. While the phone is unlocked, Android may show the expanded notification instead."
                },
                color = MedMitraColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (Build.VERSION.SDK_INT >= 34 && !fullScreenGranted) {
                TextButton(onClick = {
                    openReminderSettings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }) { Text("Allow full-screen alarms") }
            }
            if (!channelReady) {
                TextButton(onClick = { openChannelSettings(context) }) { Text("Open medicine notification settings") }
            }
            Text("Samsung phones: if alarms are delayed or suppressed, set MedMitra to Unrestricted under Settings → Apps → MedMitra → Battery, and add it to Never sleeping apps under Battery → Background usage limits. Menu names vary by One UI version. The full-screen setting is under Settings → Apps → Special access → Full screen alerts (or Full screen intents).", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        if (showEditor) {
            MedicineEditor(
                initial = editTarget,
                onDismiss = { showEditor = false; editTarget = null },
                onSave = { medicine ->
                    showEditor = false
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            database.medicines().save(medicine)
                        }
                        val saved = medicine.copy(id = if (medicine.id == 0L) id else medicine.id)
                        if (saved.enabled) ReminderScheduler(context).scheduleDaily(saved) else ReminderScheduler(context).cancel(saved.id)
                        editTarget = null
                    }
                }
            )
        }
    }
}

@Composable
private fun ReminderExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    PlainCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            androidx.compose.material3.Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MedMitraColors.TextSecondary
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private suspend fun rescheduleEnabledMedicines(context: android.content.Context, database: MedMitraDatabase) {
    val scheduler = ReminderScheduler(context)
    database.medicines().getEnabled().forEach { schedule ->
        scheduler.scheduleDaily(schedule)
        schedule.snoozedUntilMillis?.let { until -> scheduler.scheduleSnooze(schedule.id, (until - System.currentTimeMillis()).coerceAtLeast(1_000L), schedule.snoozedDoseAtMillis ?: (until - ReminderScheduler.SNOOZE_DELAY_MILLIS)) }
    }
}

private fun fullScreenAlarmAccess(context: android.content.Context): Boolean = Build.VERSION.SDK_INT < 34 ||
    context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

private fun alarmChannelImportance(context: android.content.Context): Int = if (Build.VERSION.SDK_INT >= 26) {
    context.getSystemService(android.app.NotificationManager::class.java)
        .getNotificationChannel(ReminderScheduler.CHANNEL_ID)?.importance ?: android.app.NotificationManager.IMPORTANCE_NONE
} else android.app.NotificationManager.IMPORTANCE_HIGH

private fun openReminderSettings(context: android.content.Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).setData(Uri.parse("package:${context.packageName}")))
    }
}

private fun openChannelSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= 26) runCatching {
        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.CHANNEL_ID))
    }
}

@Composable
private fun MedicineScheduleItem(
    medicine: MedicineSchedule,
    onToggle: (Boolean) -> Unit,
    onTaken: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    PlainCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Medication, tint = MedMitraColors.MintDark, size = 40)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(medicine.name, color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                Text(medicine.dosage, color = MedMitraColors.TextSecondary)
            }
            SoftPill(LocalTime.of(medicine.hour, medicine.minute).format(DateTimeFormatter.ofPattern("h:mm a")))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val now = java.time.ZonedDateTime.now()
            val scheduledToday = now.toLocalDate().atTime(medicine.hour, medicine.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val startOfToday = now.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val nowMillis = System.currentTimeMillis()
            val statusState = medicineDoseStatus(medicine.enabled, nowMillis, startOfToday, scheduledToday, medicine.lastTakenAtMillis, medicine.snoozedUntilMillis)
            val status = when (statusState) {
                MedicineDoseStatus.DISABLED -> "Disabled"
                MedicineDoseStatus.SNOOZED -> "Snoozed until ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(medicine.snoozedUntilMillis!!))}"
                MedicineDoseStatus.TAKEN -> "Taken today"
                MedicineDoseStatus.MISSED -> "Missed today · daily"
                MedicineDoseStatus.UPCOMING -> "Upcoming · daily"
            }
            Text(status, Modifier.weight(1f), color = MedMitraColors.TextSecondary)
            Switch(checked = medicine.enabled, onCheckedChange = onToggle)
            TextButton(onClick = onEdit) { androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = "Edit ${medicine.name}") }
            TextButton(onClick = onDelete) {
                androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = "Delete ${medicine.name}")
            }
        }
        if (medicine.lastTakenAtMillis != null) {
            Text("Last marked taken: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(medicine.lastTakenAtMillis))}", color = MedMitraColors.TextSecondary)
        }
        if (medicine.instructions.isNotBlank()) Text(medicine.instructions, color = MedMitraColors.TextSecondary)
        OutlinedButton(onClick = onTaken, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Filled.Check, contentDescription = null)
            Text("Mark taken today", Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun MedicineEditor(initial: MedicineSchedule?, onDismiss: () -> Unit, onSave: (MedicineSchedule) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var dosage by remember(initial) { mutableStateOf(initial?.dosage.orEmpty()) }
    var instructions by remember(initial) { mutableStateOf(initial?.instructions.orEmpty()) }
    var hour by remember(initial) { mutableStateOf(initial?.hour ?: 9) }
    var minute by remember(initial) { mutableStateOf(initial?.minute ?: 0) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New medicine reminder" else "Edit medicine reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Medicine name") }, singleLine = true)
                OutlinedTextField(dosage, { dosage = it }, label = { Text("Dose, e.g. 1 tablet") }, singleLine = true)
                OutlinedTextField(instructions, { instructions = it.take(200) }, label = { Text("Instructions (optional)") }, singleLine = true)
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reminder time · ${LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a"))}")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && dosage.isNotBlank(),
                onClick = { onSave((initial ?: MedicineSchedule(name = name.trim(), dosage = dosage.trim(), hour = hour, minute = minute)).copy(name = name.trim(), dosage = dosage.trim(), hour = hour, minute = minute, instructions = instructions.trim())) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Choose reminder time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("Set time") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }
}
