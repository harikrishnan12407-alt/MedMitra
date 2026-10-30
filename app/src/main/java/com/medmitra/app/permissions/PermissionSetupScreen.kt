package com.medmitra.app.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.medmitra.app.ui.localization.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.theme.MedMitraColors

object PermissionSetupPreferences {
    private const val FILE = "permission_setup"
    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("complete", false)

    fun markComplete(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("complete", true).apply()
    }
}

private data class PermissionSnapshot(
    val notifications: Boolean,
    val exactAlarms: Boolean?,
    val fullScreenAlarms: Boolean?,
    val location: String,
    val sms: Boolean,
    val calls: Boolean,
    val activityRecognition: Boolean?
)

@Composable
fun PermissionSetupScreen(onDone: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember(context) { mutableStateOf(readPermissionSnapshot(context)) }
    fun refresh() { snapshot = readPermissionSnapshot(context) }

    val notificationsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val smsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val callsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val activityRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFEAF7EF), MedMitraColors.Background, Color.White))
        ).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        PageHeader("Permission setup", onBack = onBack)
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(MedMitraColors.Emerald, MedMitraColors.EmeraldMid, MedMitraColors.MintDark)),
                RoundedCornerShape(24.dp)
            ).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(24.dp)).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(48.dp).background(Color.White.copy(alpha = .13f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = .24f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Security, contentDescription = null, tint = MedMitraColors.MintGlow, modifier = Modifier.size(25.dp))
                }
                Column {
                    Text("Your care, your choice", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "Review what MedMitra uses. You can skip any item and change access later in Android Settings.",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD7F0E2)
                    )
                }
            }
        }

        PermissionSectionHeading("MEDICINE REMINDERS", "CORE ACCESS")
        PermissionRow(
            "Notifications", "Show scheduled reminders and alarm controls.",
            accessStatus(snapshot.notifications), !snapshot.notifications,
            if (snapshot.notifications) "Enabled" else "Allow",
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) notificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openAppNotificationSettings(context)
            }
        )
        PermissionRow(
            "Exact alarms", "Lets medicine reminders fire close to their scheduled time.",
            snapshot.exactAlarms?.let(::accessStatus) ?: "Not required on this Android version",
            snapshot.exactAlarms == false,
            if (snapshot.exactAlarms == false) "Open Settings" else "Available",
            onAction = { if (Build.VERSION.SDK_INT >= 31) openExactAlarmSettings(context) }
        )
        PermissionRow(
            "Full-screen alarms", "Allows a medicine alarm to take over the screen when Android permits it, such as while locked.",
            when (snapshot.fullScreenAlarms) {
                true -> "Allowed"
                false -> "Not allowed"
                null -> "Check notification settings"
            },
            snapshot.fullScreenAlarms != true,
            if (snapshot.fullScreenAlarms == true) "Allowed" else "Review access",
            onAction = { openFullScreenSettings(context) }
        )

        PermissionSectionHeading("EMERGENCY SOS", "OPTIONAL")
        Text("These permissions are used only for actions you enable and explicitly start in SOS. This checklist never sends a message or places a call.", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
        PermissionRow(
            "Location", "Adds a current location to an SOS message when a fresh fix is available.", snapshot.location,
            snapshot.location == "Not granted", if (snapshot.location == "Not granted") "Allow" else "Review",
            onAction = { locationRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
        )
        PermissionRow(
            "Send SMS", "Needed only if you enable automatic SOS text messages to your saved trusted contact.", accessStatus(snapshot.sms), !snapshot.sms,
            if (snapshot.sms) "Enabled" else "Allow", onAction = { smsRequest.launch(Manifest.permission.SEND_SMS) }
        )
        PermissionRow(
            "Place calls", "Needed only if you enable automatic SOS calling to your saved trusted contact.", accessStatus(snapshot.calls), !snapshot.calls,
            if (snapshot.calls) "Enabled" else "Allow", onAction = { callsRequest.launch(Manifest.permission.CALL_PHONE) }
        )

        PermissionSectionHeading("FALL SAFETY AND VOICE", "OPTIONAL")
        PermissionRow(
            "Activity recognition", "Lets the opt-in Fall Safety foreground service use motion activity on Android 10 and later.",
            snapshot.activityRecognition?.let(::accessStatus) ?: "Not required on this Android version",
            snapshot.activityRecognition == false,
            if (snapshot.activityRecognition == false) "Allow" else "Available",
            onAction = { if (Build.VERSION.SDK_INT >= 29) activityRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
        )
        PermissionRow(
            "Emergency contacts", "Trusted contacts are entered in MedMitra; the device address book is not read.",
            "Contacts permission not needed", false, "Not needed", onAction = {}
        )
        PermissionRow(
            "Voice reminders", "Spoken reminders use Android Text-to-Speech and do not record audio.",
            "Microphone permission not needed", false, "Not needed", onAction = {}
        )

        if (!snapshot.notifications || snapshot.location == "Not granted" || !snapshot.sms || !snapshot.calls ||
            snapshot.activityRecognition == false
        ) {
            OutlinedButton(onClick = { openAppPermissionsSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Android app permission settings")
            }
        }

        Text("If Android no longer shows a permission prompt, open Settings → Apps → MedMitra → Permissions. Special alarm access opens its own Android settings page. Status updates when you return.", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (onBack == null) "Continue" else "Done") }
        if (onBack == null) {
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    explanation: String,
    status: String,
    needsAttention: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    PlainCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(end = 12.dp).size(42.dp).background(MedMitraColors.SurfaceSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = when (title) {
                        "Notifications" -> Icons.Filled.NotificationsActive
                        "Exact alarms", "Full-screen alarms" -> Icons.Filled.Alarm
                        "Location" -> Icons.Filled.LocationOn
                        "Send SMS" -> Icons.Filled.Sms
                        "Place calls" -> Icons.Filled.Call
                        "Activity recognition" -> Icons.Filled.DirectionsRun
                        "Emergency contacts" -> Icons.Filled.People
                        else -> Icons.Filled.RecordVoiceOver
                    },
                    contentDescription = null, tint = MedMitraColors.MintDark, modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                Surface(
                    color = if (needsAttention) Color(0xFFFFF3DB) else MedMitraColors.SurfaceSoft,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        status, Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (needsAttention) MedMitraColors.Amber else MedMitraColors.MintDark
                    )
                }
            }
            if (needsAttention) {
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PermissionSectionHeading(title: String, tag: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MedMitraColors.MintDark, modifier = Modifier.weight(1f))
        Surface(color = MedMitraColors.SurfaceSoft, shape = RoundedCornerShape(50)) {
            Text(tag, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MedMitraColors.MintDark)
        }
    }
}

private fun accessStatus(granted: Boolean): String = if (granted) "Granted" else "Not granted"

private fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
    val exact = if (Build.VERSION.SDK_INT < 31) null else context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    } else null
    val locationStatus = when {
        fine -> "Precise location granted"
        coarse -> "Approximate location granted"
        else -> "Not granted"
    }
    return PermissionSnapshot(
        notifications = notificationAllowed,
        exactAlarms = exact,
        fullScreenAlarms = fullScreen,
        location = locationStatus,
        sms = granted(Manifest.permission.SEND_SMS),
        calls = granted(Manifest.permission.CALL_PHONE),
        activityRecognition = if (Build.VERSION.SDK_INT < 29) null else granted(Manifest.permission.ACTIVITY_RECOGNITION)
    )
}

private fun openAppNotificationSettings(context: Context) = launchSettings(context,
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
)

private fun openAppPermissionsSettings(context: Context) = launchSettings(context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
)

private fun openExactAlarmSettings(context: Context) = launchSettings(context,
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))
)

private fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        launchSettings(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:${context.packageName}")))
    } else {
        openAppNotificationSettings(context)
    }
}

private fun launchSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
        }
    }
}
