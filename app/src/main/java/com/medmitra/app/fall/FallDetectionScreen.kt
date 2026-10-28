package com.medmitra.app.fall

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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

@Composable
fun FallDetectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var monitoring by remember { mutableStateOf(FallMonitoringService.running) }
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun launchMonitoring() {
        try {
            FallMonitoringService.start(context)
            monitoring = true
            statusMessage = null
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

    DisposableEffect(lifecycleOwner, context) {
        val stateObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                monitoring = FallMonitoringService.running
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
                    Text(if (monitoring) "Active while its ongoing notification is present" else "Off", color = MedMitraColors.TextSecondary)
                }
                Switch(checked = monitoring, onCheckedChange = { enable ->
                    if (!enable) {
                        FallMonitoringService.stop(context)
                        monitoring = false
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
            Text("What happens after a possible fall", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Text("MedMitra shows a notification with a 30-second “I’m okay” action. If you do not respond, it records the alert and offers to open SOS. It never sends a message or places a call automatically.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Android background limits", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Text("Start monitoring while this screen is visible. Android keeps it in a foreground service with a permanent notification. MedMitra does not restart sensor monitoring after reboot; turn it on again after a restart.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Text("The accelerometer is read locally. MedMitra does not request background location or body-sensor access for this feature.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        statusMessage?.let { Text(it, color = MedMitraColors.Coral) }
        if (!notificationsEnabled) Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth()) { Text("Enable notifications") }
    }
}
