package com.medmitra.app.fall

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.theme.MedMitraColors
import com.medmitra.app.domain.FallSensitivity

@Composable
fun FallDetectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var monitoring by remember { mutableStateOf(FallMonitoringService.running) }
    var monitoringRequested by remember { mutableStateOf(FallPreferences.monitoringRequested(context)) }
    var sensitivity by remember { mutableStateOf(FallPreferences.sensitivity(context)) }
    var countdownSeconds by remember { mutableStateOf(FallPreferences.countdownSeconds(context)) }
    var sensitivityMenu by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun launchMonitoring() {
        try {
            FallMonitoringService.start(context)
            monitoringRequested = true
            statusMessage = "Starting foreground monitoring. The active state will appear after the service registers its sensors."
        } catch (_: SecurityException) {
            monitoring = false
            statusMessage = "Android blocked fall monitoring. Check notification and activity permissions, then try again."
        } catch (_: IllegalStateException) {
            monitoring = false
            statusMessage = "Fall monitoring can only start while MedMitra is visible. Return to this screen and try again."
        }
    }

    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchMonitoring()
        else statusMessage = "Activity recognition permission is needed to start the health foreground service."
    }
    fun checkActivityPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else launchMonitoring()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notificationsEnabled) checkActivityPermissionAndStart()
        else statusMessage = "Enable notifications before starting visible fall monitoring."
    }
    val demoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { FallMonitoringService.demo(context); statusMessage = "Requesting a safe demo alert. No SMS or call will be made." }
            .onFailure { statusMessage = "Android could not start the demo foreground service." }
        else statusMessage = "Activity Recognition permission is required for the health foreground service."
    }

    DisposableEffect(lifecycleOwner, context) {
        val stateObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                monitoring = FallMonitoringService.running
                monitoringRequested = FallPreferences.monitoringRequested(context)
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                monitoring = intent.getBooleanExtra(FallMonitoringService.EXTRA_IS_RUNNING, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(stateObserver)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(FallMonitoringService.ACTION_MONITORING_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(stateObserver)
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Fall Safety", onBack = onBack)
        Text("Monitoring is opt-in and uses a motion heuristic. It can miss falls or flag ordinary movement; it is not medical-grade and cannot replace emergency services.", color = MedMitraColors.TextSecondary)
        Text("Android may ask for Activity Recognition permission to run this health foreground service. Sensor readings stay on this device.", color = MedMitraColors.TextSecondary)
        PlainCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Foreground monitoring", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(if (monitoring) "Active while its ongoing notification is present" else if (monitoringRequested) "Previously enabled; service stopped. Restart manually." else "Off", color = MedMitraColors.TextSecondary)
                }
                Switch(checked = monitoring, onCheckedChange = { enable ->
                    if (!enable) {
                        FallMonitoringService.stop(context)
                        monitoring = false
                        monitoringRequested = false
                        statusMessage = "Fall monitoring stopped."
                    } else if (!notificationsEnabled) {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            statusMessage = "Enable notifications, return here, then turn monitoring on."
                        }
                    } else checkActivityPermissionAndStart()
                })
            }
        }
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Detection sensitivity", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Text("Lower sensitivity reduces false alarms but may miss events. Higher sensitivity may trigger more often. New settings apply immediately if monitoring is active.", color = MedMitraColors.TextSecondary)
            Button(onClick = { sensitivityMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(sensitivity.name.lowercase().replaceFirstChar { it.uppercase() }) }
            DropdownMenu(expanded = sensitivityMenu, onDismissRequest = { sensitivityMenu = false }) {
                FallSensitivity.entries.forEach { option -> DropdownMenuItem(text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = {
                    sensitivity = option; FallPreferences.setSensitivity(context, option); sensitivityMenu = false
                    if (monitoring) FallMonitoringService.updateSensitivity(context)
                }) }
            }
            Text("False-alarm countdown · $countdownSeconds seconds", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { seconds -> Button(onClick = { countdownSeconds = seconds; FallPreferences.setCountdownSeconds(context, seconds) }) { Text("${seconds}s") } }
            }
            Text("A motion candidate shows a notification with an “I’m okay” cancel action. If it expires, MedMitra records it and offers SOS. It never sends a message or places a call automatically.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Android background limits", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Text("Start monitoring while this screen is visible. Android keeps it in a foreground service with a permanent notification. MedMitra does not restart sensor monitoring after reboot; turn it on again after a restart.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Text("Accelerometer, gyroscope, and fused orientation data are processed locally when those sensors are available. MedMitra does not request background location or body-sensor access for this feature.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            val batteryUnrestricted = runCatching { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }.getOrDefault(false)
            Text(if (batteryUnrestricted) "Battery optimization exemption: allowed" else "Battery optimization: Android or the phone maker may pause the service under heavy restrictions. Review system battery settings if monitoring stops." , color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Button(onClick = {
            val activityAllowed = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            if (!notificationsEnabled) statusMessage = "Enable notifications before testing the alert."
            else if (!activityAllowed && Build.VERSION.SDK_INT >= 29) demoPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else try {
                statusMessage = "Requesting a safe demo alert. No SMS or call will be made."
                FallMonitoringService.demo(context)
            } catch (_: Exception) { statusMessage = "Android could not start the demo foreground service." }
        }, modifier = Modifier.fillMaxWidth()) { Text("Test alert safely (no contact)") }
        statusMessage?.let { Text(it, color = MedMitraColors.Coral) }
        if (!notificationsEnabled) Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth()) { Text("Enable notifications") }
    }
}
