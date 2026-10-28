package com.medmitra.app.emergency

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.medmitra.app.data.EmergencyHistoryEvent
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.domain.EmergencyStage
import com.medmitra.app.domain.EmergencyWorkflow
import com.medmitra.app.domain.HandoffStatus
import com.medmitra.app.ui.components.CenteredFeatureIcon
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.theme.MedMitraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Composable
fun EmergencySosScreen(database: MedMitraDatabase, onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by database.contacts().observeAll().collectAsState(initial = emptyList())
    val enabledContacts = contacts.filter { it.enabled }
    val workflow = remember { EmergencyWorkflow(countdownSeconds = 5) }
    var snapshot by remember { mutableStateOf(workflow.snapshot) }
    var selectedContactId by remember { mutableStateOf<Long?>(null) }
    var historyId by remember { mutableStateOf<Long?>(null) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var locationLookupInProgress by remember { mutableStateOf(false) }
    var includeLocationInMessage by remember { mutableStateOf(false) }
    var showCallConfirmation by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var startingSos by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabledContacts) {
        if (enabledContacts.none { it.id == selectedContactId }) {
            selectedContactId = enabledContacts.firstOrNull()?.id
        }
    }
    val selectedContact = enabledContacts.firstOrNull { it.id == selectedContactId }

    fun recordLocation(result: Location?, status: String) {
        currentLocation = result
        locationLookupInProgress = false
        snapshot = workflow.locationResolved(status)
        historyId?.let { id ->
            scope.launch(Dispatchers.IO) {
                database.history().updateLocation(
                    id = id,
                    status = status,
                    latitude = result?.latitude,
                    longitude = result?.longitude,
                    accuracyMeters = result?.accuracy
                )
            }
        }
    }

    fun lookupLocation() {
        locationLookupInProgress = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { currentLocation(context) }
            recordLocation(result, if (result == null) "UNAVAILABLE" else "AVAILABLE")
        }
    }

    fun startSos() {
        val contact = selectedContact ?: return
        if (startingSos) return
        startingSos = true
        statusMessage = null
        currentLocation = null
        includeLocationInMessage = false
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    database.history().insert(
                        EmergencyHistoryEvent(
                            occurredAtMillis = System.currentTimeMillis(),
                            eventType = "SOS_COUNTDOWN",
                            notes = "Contact selected: ${contact.name}"
                        )
                    )
                }
                historyId = id
                snapshot = workflow.start(SystemClock.elapsedRealtime())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                statusMessage = "SOS could not be started because its history could not be saved."
            } finally {
                startingSos = false
            }
        }
    }

    fun finishWithoutCall() {
        snapshot = workflow.skipCall()
        historyId?.let { id ->
            scope.launch(Dispatchers.IO) {
                database.history().updateCallAttempt(id, HandoffStatus.NOT_ATTEMPTED.name)
                database.history().updateEvent(id, "SOS_COMPLETE", "Contact: ${selectedContact?.name.orEmpty()}; no call placed")
            }
        }
    }

    fun recordCallResult(status: HandoffStatus) {
        snapshot = when (status) {
            HandoffStatus.OUTCOME_UNKNOWN -> workflow.callRequested(available = true)
            HandoffStatus.USER_ACTION_REQUIRED -> workflow.dialerOpened(available = true)
            else -> workflow.callRequested(available = false)
        }
        historyId?.let { id ->
            scope.launch(Dispatchers.IO) {
                database.history().updateCallAttempt(id, status.name)
                database.history().updateEvent(id, "SOS_COMPLETE", "Contact: ${selectedContact?.name.orEmpty()}; call status: ${status.name}")
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) lookupLocation()
        else recordLocation(null, "PERMISSION_DENIED")
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCallNumber
        if (granted && number != null) recordCallResult(placeCall(context, number, direct = true))
        else if (number != null) recordCallResult(placeCall(context, number, direct = false))
    }

    LaunchedEffect(snapshot.stage) {
        if (snapshot.stage == EmergencyStage.COUNTDOWN) {
            while (workflow.snapshot.stage == EmergencyStage.COUNTDOWN) {
                delay(250)
                val updated = workflow.tick(SystemClock.elapsedRealtime())
                if (updated.stage != EmergencyStage.COUNTDOWN) {
                    try {
                        historyId?.let { id ->
                            withContext(Dispatchers.IO) {
                                database.history().updateEvent(id, "SOS_TRIGGERED", "Contact: ${selectedContact?.name.orEmpty()}; countdown completed")
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        statusMessage = "SOS started, but its history could not be updated."
                    } finally {
                        snapshot = updated
                    }
                    break
                }
                snapshot = updated
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Emergency SOS", onBack = onBack)
        CenteredFeatureIcon(Icons.Filled.Sos, MedMitraColors.Coral)
        Text("SOS only prepares actions for contacts you choose. Messages open in your SMS app for review; calls require a separate confirmation.", color = MedMitraColors.TextSecondary)

        if (snapshot.stage == EmergencyStage.IDLE || snapshot.stage == EmergencyStage.CANCELLED || snapshot.stage == EmergencyStage.COMPLETE) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("Choose a trusted contact", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                if (enabledContacts.isEmpty()) {
                    Text("Add and enable an emergency contact before starting SOS.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                } else {
                    enabledContacts.forEach { contact ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedContactId == contact.id, onClick = { selectedContactId = contact.id })
                            Column {
                                Text(contact.name, color = MedMitraColors.Text)
                                Text(contact.phoneE164, color = MedMitraColors.TextSecondary)
                            }
                        }
                    }
                }
            }
            when (snapshot.stage) {
                EmergencyStage.IDLE -> Button(
                    enabled = selectedContact != null && !startingSos,
                    onClick = { startSos() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (startingSos) "Preparing SOS…" else "Start SOS · 5 second cancel window") }
                EmergencyStage.CANCELLED -> {
                    PlainCard(Modifier.fillMaxWidth()) { Text("SOS cancelled. No message or call was started.", color = MedMitraColors.Text) }
                    Button(onClick = { startSos() }, modifier = Modifier.fillMaxWidth()) { Text("Start again") }
                }
                EmergencyStage.COMPLETE -> {
                    PlainCard(Modifier.fillMaxWidth()) {
                        Text("SOS actions finished", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                        Text("SMS status: ${snapshot.smsStatus.displayName()}", color = MedMitraColors.TextSecondary)
                        Text("Call status: ${snapshot.callStatus.displayName()}", color = MedMitraColors.TextSecondary)
                        Text("Delivery and call connection cannot be confirmed by MedMitra.", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Button(onClick = { startSos() }, modifier = Modifier.fillMaxWidth()) { Text("Start a new SOS") }
                }
                else -> Unit
            }
        }

        if (snapshot.stage == EmergencyStage.COUNTDOWN) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("SOS starting in ${snapshot.countdownRemainingSeconds} seconds", color = MedMitraColors.Coral, fontWeight = FontWeight.Bold)
                Text("Cancel now to stop before any contact or location action.", color = MedMitraColors.TextSecondary)
            }
            OutlinedButton(
                onClick = {
                    if (workflow.cancel()) {
                        snapshot = workflow.snapshot
                        historyId?.let { id -> scope.launch(Dispatchers.IO) { database.history().updateEvent(id, "SOS_CANCELLED", "Cancelled during countdown") } }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel SOS") }
        }

        if (snapshot.stage == EmergencyStage.LOCATION_LOOKUP) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("Location sharing is optional", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                Text("MedMitra will request foreground location access only after you choose to look up your current location for this SOS.", color = MedMitraColors.TextSecondary)
            }
            Button(
                enabled = !locationLookupInProgress,
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) lookupLocation()
                    else {
                        locationLookupInProgress = true
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (locationLookupInProgress) "Looking up location…" else "Get current location for this SOS") }
            TextButton(enabled = !locationLookupInProgress, onClick = {
                recordLocation(null, "NOT_SHARED")
            }, modifier = Modifier.fillMaxWidth()) { Text("Continue without location") }
        }

        if (snapshot.stage == EmergencyStage.SMS_REVIEW) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("Review emergency message", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
                Text("To: ${selectedContact?.name ?: "No contact selected"}", color = MedMitraColors.TextSecondary)
                Text("Location status: ${snapshot.locationStatus}", color = MedMitraColors.TextSecondary)
                if (currentLocation != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeLocationInMessage, onCheckedChange = { includeLocationInMessage = it })
                        Text("Include my current location in the message", color = MedMitraColors.TextSecondary)
                    }
                }
                Text("Your messaging app must be used to send the message. MedMitra cannot see whether it was sent.", color = MedMitraColors.TextSecondary)
            }
            Button(onClick = {
                val contact = selectedContact
                if (contact == null) {
                    statusMessage = "Choose an enabled emergency contact first."
                } else {
                    val locationText = if (includeLocationInMessage) currentLocation?.let {
                        " My current location: https://maps.google.com/?q=${it.latitude},${it.longitude}"
                    }.orEmpty() else ""
                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phoneE164}"))
                        .putExtra("sms_body", "I need help. Please contact me.$locationText")
                    val canOpen = smsIntent.resolveActivity(context.packageManager) != null
                    val opened = if (canOpen) try {
                        context.startActivity(smsIntent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    } else false
                    snapshot = workflow.smsComposerOpened(opened)
                    val smsStatus = if (opened) HandoffStatus.OUTCOME_UNKNOWN else HandoffStatus.UNAVAILABLE
                    scope.launch(Dispatchers.IO) {
                        historyId?.let { id -> database.history().updateSmsAttempt(id, smsStatus.name) }
                    }
                    if (!opened) statusMessage = "No messaging app is available."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open SMS composer") }
            TextButton(onClick = {
                workflow.skipSms()
                finishWithoutCall()
                historyId?.let { id -> scope.launch(Dispatchers.IO) { database.history().updateSmsAttempt(id, HandoffStatus.NOT_ATTEMPTED.name) } }
            }, modifier = Modifier.fillMaxWidth()) { Text("Skip SMS and finish") }
        }

        if (snapshot.stage == EmergencyStage.CALL_REVIEW) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text(
                    when (snapshot.smsStatus) {
                        HandoffStatus.NOT_ATTEMPTED -> "SMS skipped"
                        HandoffStatus.UNAVAILABLE -> "No messaging app available"
                        else -> "SMS composer opened"
                    },
                    color = MedMitraColors.Text,
                    fontWeight = FontWeight.SemiBold
                )
                Text("SMS status: ${snapshot.smsStatus.displayName()}. Sending is up to you in your messaging app.", color = MedMitraColors.TextSecondary)
                Text("You may place one direct call to the selected contact, or finish without calling.", color = MedMitraColors.TextSecondary)
            }
            Button(enabled = selectedContact != null, onClick = { showCallConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Review direct call")
            }
            TextButton(onClick = {
                finishWithoutCall()
            }, modifier = Modifier.fillMaxWidth()) { Text("Finish without calling") }
        }

        statusMessage?.let { Text(it, color = MedMitraColors.Coral) }
        Spacer(Modifier.height(4.dp))
    }

    if (showCallConfirmation && selectedContact != null) {
        AlertDialog(
            onDismissRequest = { showCallConfirmation = false },
            title = { Text("Place a direct call?") },
            text = { Text("If you confirm and grant phone permission, MedMitra will immediately call ${selectedContact.name} (${selectedContact.phoneE164}). The call may not connect, and you can end it from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCallNumber = selectedContact.phoneE164
                    showCallConfirmation = false
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        recordCallResult(placeCall(context, selectedContact.phoneE164, direct = true))
                    } else callPermission.launch(Manifest.permission.CALL_PHONE)
                }) { Text("Place call") }
            },
            dismissButton = { TextButton(onClick = { showCallConfirmation = false }) { Text("Cancel") } }
        )
    }
}

private fun HandoffStatus.displayName(): String = when (this) {
    HandoffStatus.NOT_ATTEMPTED -> "Not attempted"
    HandoffStatus.OUTCOME_UNKNOWN -> "Opened or requested; outcome unknown"
    HandoffStatus.USER_ACTION_REQUIRED -> "Phone action requires your confirmation"
    HandoffStatus.UNAVAILABLE -> "Unavailable"
}

private fun placeCall(context: Context, phone: String, direct: Boolean): HandoffStatus {
    val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent = Intent(action, Uri.parse("tel:${Uri.encode(phone)}"))
    val available = intent.resolveActivity(context.packageManager) != null
    val callStatus = when {
        !available -> HandoffStatus.UNAVAILABLE
        direct -> HandoffStatus.OUTCOME_UNKNOWN
        else -> HandoffStatus.USER_ACTION_REQUIRED
    }
    if (available) try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        return HandoffStatus.UNAVAILABLE
    } catch (_: SecurityException) {
        return HandoffStatus.UNAVAILABLE
    }
    return callStatus
}

@Suppress("DEPRECATION")
private suspend fun currentLocation(context: Context): Location? = withTimeoutOrNull(12_000L) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return@withTimeoutOrNull null
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val criteria = Criteria().apply { accuracy = if (fine) Criteria.ACCURACY_FINE else Criteria.ACCURACY_COARSE }
    val provider = manager.getBestProvider(criteria, true) ?: return@withTimeoutOrNull null
    suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (continuation.isActive) continuation.resume(location)
                runCatching { manager.removeUpdates(this) }
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        } catch (_: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
