package com.medmitra.app.emergency

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medmitra.app.data.EmergencyContact
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.ui.components.IconBadge
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.theme.MedMitraColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EmergencyContactsScreen(database: MedMitraDatabase, onBack: () -> Unit) {
    val contacts by database.contacts().observeAll().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<EmergencyContact?>(null) }
    var autoSms by remember { mutableStateOf(EmergencyPreferences.autoSms(context)) }
    var autoCall by remember { mutableStateOf(EmergencyPreferences.autoCall(context)) }
    var shareLocation by remember { mutableStateOf(EmergencyPreferences.shareLocation(context)) }
    var permissionStatus by remember { mutableStateOf("") }
    var testingLocation by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionStatus = if (results.values.any { it }) "Permission results saved by Android. Review the current access below." else "No requested permissions were granted. Automatic actions will be unavailable until enabled in Android Settings."
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Emergency Contacts", onBack = onBack)
        Text("Contacts and emergency-action preferences stay on this device. Automatic SMS or calling can contact people without another confirmation after the SOS countdown.", color = MedMitraColors.TextSecondary)
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Emergency action setup", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(autoSms, { autoSms = it; EmergencyPreferences.save(context, sms = it) }); Text("Send SMS automatically after SOS countdown") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(autoCall, { autoCall = it; EmergencyPreferences.save(context, call = it) }); Text("Call selected contact automatically after SOS countdown") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(shareLocation, { shareLocation = it; EmergencyPreferences.save(context, location = it) }); Text("Include a fresh location in emergency SMS when available") }
            Text("Automatic SMS requires Android SEND_SMS access, cellular service, and an active subscription. Google Play requires approval for its physical-safety SMS exception; without approval, use the SMS app fallback. A successful send request does not prove delivery. Direct calls start without another confirmation and cannot be verified as connected.", color = MedMitraColors.TextSecondary)
            Text("SMS permission: ${permissionLabel(context, Manifest.permission.SEND_SMS)} · Call permission: ${permissionLabel(context, Manifest.permission.CALL_PHONE)}", color = MedMitraColors.TextSecondary)
            Text("Location permission: ${if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Off"}", color = MedMitraColors.TextSecondary)
            Button(onClick = {
                val requested = buildList {
                    if (autoSms && ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.SEND_SMS)
                    if (autoCall && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CALL_PHONE)
                    if (shareLocation && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                if (requested.isEmpty()) permissionStatus = "All permissions selected in setup are currently granted."
                else permissions.launch(requested.toTypedArray())
            }, modifier = Modifier.fillMaxWidth()) { Text("Grant selected permissions") }
            OutlinedButton(onClick = {
                val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!permitted) permissionStatus = "Grant location access to safely test a location fix. No SMS or calls were made."
                else {
                    testingLocation = true
                    permissionStatus = "Checking a fresh location. No SMS or calls will be made."
                    scope.launch {
                        val location = withContext(Dispatchers.IO) { currentLocation(context) }
                        permissionStatus = if (location == null) "No fresh location was available. Check location services and network. No SMS or calls were made." else "Location test passed (${location.latitude}, ${location.longitude}, ±${location.accuracy.toInt()} m). No SMS or calls were made."
                        testingLocation = false
                    }
                }
            }, enabled = !testingLocation, modifier = Modifier.fillMaxWidth()) { Text(if (testingLocation) "Testing location…" else "Safely test setup (no contact)") }
            if (permissionStatus.isNotBlank()) Text(permissionStatus, color = MedMitraColors.TextSecondary)
        }
        if (contacts.isEmpty()) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("No emergency contacts saved", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                Text("Add a trusted person before using the SOS flow.", color = MedMitraColors.TextSecondary)
            }
        }
        contacts.forEach { contact ->
            PlainCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Person, size = 40)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(contact.name, color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(contact.phoneE164, color = MedMitraColors.TextSecondary)
                    }
                    TextButton(onClick = { scope.launch { database.contacts().delete(contact.id) } }) {
                        androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = "Delete ${contact.name}")
                    }
                }
                TextButton(onClick = { editTarget = contact; showEditor = true }) { Text("Edit contact") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (contact.enabled) "Available for SOS" else "Disabled", Modifier.weight(1f), color = MedMitraColors.TextSecondary)
                    Switch(checked = contact.enabled, onCheckedChange = { enabled ->
                        scope.launch { database.contacts().update(contact.copy(enabled = enabled)) }
                    })
                }
            }
        }
        Button(onClick = { showEditor = true }, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add emergency contact")
        }
        if (showEditor) {
            ContactEditor(
                initial = editTarget,
                onDismiss = { showEditor = false; editTarget = null },
                onSave = { name, phone ->
                    showEditor = false
                    val target = editTarget
                    editTarget = null
                    scope.launch { if (target == null) database.contacts().save(EmergencyContact(name = name, phoneE164 = phone)) else database.contacts().update(target.copy(name = name, phoneE164 = phone)) }
                }
            )
        }
    }
}

@Composable
private fun ContactEditor(initial: EmergencyContact?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial) { mutableStateOf(initial?.phoneE164.orEmpty()) }
    val validPhone = remember(phone) { phone.matches(Regex("^\\+[1-9][0-9]{7,14}$")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add trusted contact" else "Edit trusted contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number with country code") }, singleLine = true)
                Text("Use international format, for example +14155550123.", color = MedMitraColors.TextSecondary)
                if (phone.isNotBlank() && !validPhone) Text("Enter a valid E.164 number.", color = MedMitraColors.Coral)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && validPhone, onClick = { onSave(name.trim(), phone.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun permissionLabel(context: android.content.Context, permission: String): String =
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Off"
