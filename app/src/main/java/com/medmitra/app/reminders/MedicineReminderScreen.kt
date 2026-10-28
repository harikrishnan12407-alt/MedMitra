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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.data.MedicineSchedule
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

@Composable
fun MedicineReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { MedMitraDatabase.get(context) }
    val schedules by database.medicines().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    var notificationGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactAlarmsAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
    }
    LaunchedEffect(Unit) {
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
        Text(
            "Reminders are stored on this device. Android may delay them when exact alarm access is off.",
            color = MedMitraColors.TextSecondary
        )
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Notifications", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
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
        }
        if (Build.VERSION.SDK_INT >= 31) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("Alarm timing", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
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
                        else ReminderScheduler(context).cancel(updated.id)
                    }
                },
                onTaken = {
                    scope.launch(Dispatchers.IO) {
                        database.medicines().markTaken(medicine.id, System.currentTimeMillis())
                        ReminderScheduler(context).cancelSnooze(medicine.id)
                    }
                },
                onDelete = {
                    scope.launch(Dispatchers.IO) {
                        ReminderScheduler(context).cancel(medicine.id)
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
        if (showEditor) {
            MedicineEditor(
                onDismiss = { showEditor = false },
                onSave = { name, dosage, hour, minute ->
                    showEditor = false
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            database.medicines().save(MedicineSchedule(name = name, dosage = dosage, hour = hour, minute = minute))
                        }
                        ReminderScheduler(context).scheduleDaily(
                            MedicineSchedule(id = id, name = name, dosage = dosage, hour = hour, minute = minute)
                        )
                    }
                }
            )
        }
    }
}

private suspend fun rescheduleEnabledMedicines(context: android.content.Context, database: MedMitraDatabase) {
    val scheduler = ReminderScheduler(context)
    database.medicines().getEnabled().forEach { schedule -> scheduler.scheduleDaily(schedule) }
}

@Composable
private fun MedicineScheduleItem(
    medicine: MedicineSchedule,
    onToggle: (Boolean) -> Unit,
    onTaken: () -> Unit,
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
            Text("Daily", Modifier.weight(1f), color = MedMitraColors.TextSecondary)
            Switch(checked = medicine.enabled, onCheckedChange = onToggle)
            TextButton(onClick = onDelete) {
                androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = "Delete ${medicine.name}")
            }
        }
        if (medicine.lastTakenAtMillis != null) {
            Text("Last marked taken: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(medicine.lastTakenAtMillis))}", color = MedMitraColors.TextSecondary)
        }
        OutlinedButton(onClick = onTaken, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Filled.Check, contentDescription = null)
            Text("Mark taken today", Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun MedicineEditor(onDismiss: () -> Unit, onSave: (String, String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New medicine reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Medicine name") }, singleLine = true)
                OutlinedTextField(dosage, { dosage = it }, label = { Text("Dose, e.g. 1 tablet") }, singleLine = true)
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reminder time · ${LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a"))}")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && dosage.isNotBlank(),
                onClick = { onSave(name.trim(), dosage.trim(), hour, minute) }
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
