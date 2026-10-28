package com.medmitra.app.emergency

import android.Manifest
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.telephony.SmsManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.medmitra.app.data.EmergencyHistoryEvent
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.domain.EmergencyStage
import com.medmitra.app.domain.EmergencyWorkflow
import com.medmitra.app.domain.HandoffStatus
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private val EmergencyRed = Color(0xFFB3261E)
private val EmergencyDeepRed = Color(0xFF7F1D1D)

@Composable
fun EmergencySosScreen(database: MedMitraDatabase, onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by database.contacts().observeAll().collectAsState(initial = emptyList())
    val historyEvents by database.history().observeAll().collectAsState(initial = emptyList())
    val enabledContacts = contacts.filter { it.enabled && it.phoneE164.matches(Regex("^\\+[1-9][0-9]{7,14}$")) }
    val workflow = remember { EmergencyWorkflow(5) }
    var snapshot by remember { mutableStateOf(workflow.snapshot) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var historyId by remember { mutableStateOf<Long?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var locationLabel by remember { mutableStateOf("Not requested") }
    var smsLabel by remember { mutableStateOf("Not attempted") }
    var callLabel by remember { mutableStateOf("Not attempted") }
    val scope = rememberCoroutineScope()
    val selected = enabledContacts.firstOrNull { it.id == selectedId } ?: enabledContacts.firstOrNull()
    val activeHistory = historyEvents.firstOrNull { it.id == historyId }
    LaunchedEffect(enabledContacts) { if (enabledContacts.none { it.id == selectedId }) selectedId = enabledContacts.firstOrNull()?.id }
    LaunchedEffect(activeHistory?.smsAttemptStatus) {
        when {
            activeHistory?.smsAttemptStatus == "SMS_ACCEPTED_BY_RADIO_DELIVERY_UNKNOWN" -> smsLabel = "Accepted by radio; delivery unknown"
            activeHistory?.smsAttemptStatus?.startsWith("SMS_FAILED_NO_SERVICE") == true -> { smsLabel = "No cellular service"; statusMessage = "SMS could not be sent because cellular service is unavailable." }
            activeHistory?.smsAttemptStatus?.startsWith("SMS_FAILED_RADIO_OFF") == true -> { smsLabel = "Radio is off"; statusMessage = "SMS could not be sent because the cellular radio is off." }
            activeHistory?.smsAttemptStatus?.startsWith("SMS_SEND_FAILED") == true -> { smsLabel = "SMS send failed (${activeHistory.smsAttemptStatus})"; statusMessage = "Android reported that the SMS could not be sent. Use the messaging fallback below." }
        }
    }
    val animationsEnabled = remember { runCatching { android.animation.ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true) }
    val transition = rememberInfiniteTransition(label = "sos-pulse")
    val pulse by transition.animateFloat(1f, 1.055f, infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sos-scale")
    val idle = snapshot.stage == EmergencyStage.IDLE || snapshot.stage == EmergencyStage.CANCELLED || snapshot.stage == EmergencyStage.COMPLETE

    fun writeHistory(id: Long, type: String, notes: String) { scope.launch(Dispatchers.IO) { database.history().updateEvent(id, type, notes) } }

    fun beginSos() {
        val contact = selected ?: run { statusMessage = "Add and enable a valid trusted contact before starting SOS."; return }
        if (isBusy || snapshot.stage == EmergencyStage.COUNTDOWN || snapshot.stage == EmergencyStage.LOCATION_LOOKUP) return
        isBusy = true
        historyId = null
        statusMessage = null
        locationLabel = if (EmergencyPreferences.shareLocation(context)) "Waiting" else "Off by choice"
        smsLabel = if (EmergencyPreferences.autoSms(context)) "Waiting" else "Off by choice"
        callLabel = if (EmergencyPreferences.autoCall(context)) "Waiting" else "Off by choice"
        snapshot = workflow.start(SystemClock.elapsedRealtime())
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    database.history().insert(EmergencyHistoryEvent(occurredAtMillis = System.currentTimeMillis(), eventType = "SOS_COUNTDOWN", notes = "Selected trusted contact: ${contact.name}"))
                }
                historyId = id
                when (workflow.snapshot.stage) {
                    EmergencyStage.CANCELLED -> withContext(Dispatchers.IO) { database.history().updateEvent(id, "SOS_CANCELLED", "Cancelled during countdown; no SMS or call attempted") }
                    EmergencyStage.LOCATION_LOOKUP, EmergencyStage.COMPLETE -> withContext(Dispatchers.IO) { database.history().updateEvent(id, "SOS_ABORTED", "No actions were attempted because the history save exceeded the countdown window") }
                    else -> Unit
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                workflow.cancel()
                snapshot = workflow.snapshot
                statusMessage = "SOS was cancelled because its history could not be saved. No contact action was attempted."
            }
            finally { isBusy = false }
        }
    }

    LaunchedEffect(snapshot.stage) {
        if (snapshot.stage == EmergencyStage.COUNTDOWN) {
            while (workflow.snapshot.stage == EmergencyStage.COUNTDOWN) {
                delay(100)
                snapshot = workflow.tick(SystemClock.elapsedRealtime())
            }
            return@LaunchedEffect
        }
        if (snapshot.stage == EmergencyStage.LOCATION_LOOKUP) {
            val contact = selected
            val eventId = historyId
            if (contact == null || eventId == null) {
                statusMessage = "No emergency action was attempted because the selected contact or history record is unavailable."
                snapshot = workflow.completeAutomaticActions("NOT_AVAILABLE", HandoffStatus.UNAVAILABLE, HandoffStatus.UNAVAILABLE)
                return@LaunchedEffect
            }
            val smsEnabled = EmergencyPreferences.autoSms(context)
            val callEnabled = EmergencyPreferences.autoCall(context)
            val shareLocation = EmergencyPreferences.shareLocation(context)
            val hasLocationPermission = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            val location = if (shareLocation && hasLocationPermission) withContext(Dispatchers.IO) { currentLocation(context) } else null
            locationLabel = when {
                !shareLocation -> "Off by choice"
                !hasLocationPermission -> "Permission unavailable"
                location == null -> "Unavailable (GPS/network may be off)"
                else -> "Fresh location available"
            }
            withContext(Dispatchers.IO) {
                database.history().updateLocation(eventId, when {
                    !shareLocation -> "NOT_SHARED"
                    !hasLocationPermission -> "PERMISSION_DENIED"
                    location == null -> "UNAVAILABLE"
                    else -> "AVAILABLE"
                }, location?.latitude, location?.longitude, location?.accuracy)
            }
            var smsStatus = HandoffStatus.NOT_ATTEMPTED
            var callStatus = HandoffStatus.NOT_ATTEMPTED
            if (smsEnabled) {
                when {
                    !hasPermission(context, Manifest.permission.SEND_SMS) -> { smsStatus = HandoffStatus.UNAVAILABLE; smsLabel = "Permission missing; use fallback below" }
                    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) || !hasSmsCapability(context) -> { smsStatus = HandoffStatus.UNAVAILABLE; smsLabel = "SMS hardware or subscription unavailable" }
                    else -> {
                        val body = buildString {
                            append("I need help. Please contact me.")
                            if (location != null) append(" My current location: https://maps.google.com/?q=${location.latitude},${location.longitude}")
                        }
                        smsStatus = sendEmergencySms(context, contact.phoneE164, body, eventId)
                        smsLabel = if (smsStatus == HandoffStatus.OUTCOME_UNKNOWN) "Submitted to Android; delivery not confirmed" else "Failed to submit; use fallback below"
                    }
                }
            }
            if (callEnabled) {
                callStatus = if (!hasPermission(context, Manifest.permission.CALL_PHONE)) HandoffStatus.UNAVAILABLE else placeAutomaticCall(context, contact.phoneE164)
                callLabel = if (callStatus == HandoffStatus.OUTCOME_UNKNOWN) "Call initiated; connection unknown" else "Could not start; use fallback below"
            }
            withContext(Dispatchers.IO) {
                database.history().updateSmsAttempt(eventId, if (smsStatus == HandoffStatus.OUTCOME_UNKNOWN) "SMS_SUBMITTED_DELIVERY_UNKNOWN" else smsStatus.name)
                database.history().updateCallAttempt(eventId, if (callStatus == HandoffStatus.OUTCOME_UNKNOWN) "CALL_INITIATED_CONNECTION_UNKNOWN" else callStatus.name)
                database.history().updateEvent(eventId, "SOS_COMPLETE", "Contact ${contact.name}; location $locationLabel; SMS $smsLabel; call $callLabel")
            }
            snapshot = workflow.completeAutomaticActions(locationLabel, smsStatus, callStatus)
            statusMessage = if ((smsEnabled && smsStatus == HandoffStatus.UNAVAILABLE) || (callEnabled && callStatus == HandoffStatus.UNAVAILABLE)) {
                "One or more configured actions could not start. Check permission, SIM/service, and fallback options below."
            } else "Emergency actions were attempted. SMS delivery and call connection cannot be confirmed here."
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFF7F6)).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PageHeader("Emergency SOS", onBack = onBack)
        Text("Use your phone's emergency number for immediate danger. This SOS contacts the trusted person selected below.", color = EmergencyDeepRed, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (idle) {
            PlainCard(Modifier.fillMaxWidth()) {
                Text("Selected trusted contact", color = EmergencyDeepRed, fontWeight = FontWeight.Bold)
                if (enabledContacts.isEmpty()) Text("No enabled trusted contact. Add one in Emergency Contacts.", color = EmergencyDeepRed)
                enabledContacts.forEach { contact ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = contact.id == selected?.id, onClick = { selectedId = contact.id })
                        Column { Text(contact.name, color = Color(0xFF221A19), fontWeight = FontWeight.SemiBold); Text(contact.phoneE164, color = Color(0xFF594442)) }
                    }
                }
            }
            Box(Modifier.padding(vertical = 12.dp).scale(if (animationsEnabled) pulse else 1f), contentAlignment = Alignment.Center) {
                Button(onClick = { beginSos() }, enabled = selected != null && !isBusy, modifier = Modifier.size(204.dp).semantics { contentDescription = "Start SOS with a five second cancellation countdown" }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White)) {
                    Text(if (isBusy) "PLEASE WAIT" else "SOS", fontSize = 42.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }
            PlainCard(Modifier.fillMaxWidth()) {
                Text("After countdown", color = EmergencyDeepRed, fontWeight = FontWeight.Bold)
                StatusLine("Location sharing", if (EmergencyPreferences.shareLocation(context)) "On when permission and a fresh fix are available" else "Off")
                StatusLine("Automatic SMS", if (EmergencyPreferences.autoSms(context)) "On (may send without further confirmation)" else "Off")
                StatusLine("Automatic call", if (EmergencyPreferences.autoCall(context)) "On (will call without further confirmation)" else "Off")
            }
            Text("Press only when you want to contact your selected trusted person. Nothing starts until you press SOS.", color = Color(0xFF594442), textAlign = TextAlign.Center)
            if (snapshot.stage == EmergencyStage.CANCELLED) Text("SOS cancelled. No emergency actions were attempted.", color = EmergencyDeepRed, fontWeight = FontWeight.Bold)
            if (snapshot.stage == EmergencyStage.COMPLETE) {
                PlainCard(Modifier.fillMaxWidth()) {
                    Text("Action status", color = EmergencyDeepRed, fontWeight = FontWeight.Bold)
                    StatusLine("Location", locationLabel); StatusLine("SMS", smsLabel); StatusLine("Call", callLabel)
                    val smsFailedAfterSubmit = activeHistory?.smsAttemptStatus?.startsWith("SMS_FAILED") == true || activeHistory?.smsAttemptStatus?.startsWith("SMS_SEND_FAILED") == true
                    if (EmergencyPreferences.autoSms(context) && (snapshot.smsStatus != HandoffStatus.OUTCOME_UNKNOWN || smsFailedAfterSubmit)) OutlinedButton(onClick = { statusMessage = if (openSmsFallback(context, selected?.phoneE164.orEmpty(), locationFallbackText(activeHistory))) "SMS app opened. Review and send the message there." else "No compatible SMS app is available." }, modifier = Modifier.fillMaxWidth()) { Text("Open SMS app to send manually") }
                    if (EmergencyPreferences.autoCall(context) && snapshot.callStatus != HandoffStatus.OUTCOME_UNKNOWN) OutlinedButton(onClick = { statusMessage = if (openDialer(context, selected?.phoneE164.orEmpty())) "Phone dialer opened. Confirm the call there." else "No compatible phone dialer is available." }, modifier = Modifier.fillMaxWidth()) { Text("Open phone dialer") }
                    Button(onClick = { beginSos() }, modifier = Modifier.fillMaxWidth()) { Text("Start another SOS") }
                }
            }
        }
        if (snapshot.stage == EmergencyStage.COUNTDOWN) {
            Text("Selected contact: ${selected?.name.orEmpty()} · ${selected?.phoneE164.orEmpty()}", color = EmergencyDeepRed, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            StatusLine("Location", locationLabel); StatusLine("SMS", smsLabel); StatusLine("Call", callLabel)
            Text("${snapshot.countdownRemainingSeconds}", color = EmergencyDeepRed, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Text("Actions start when the countdown ends", color = EmergencyDeepRed, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Button(onClick = { if (workflow.cancel()) { snapshot = workflow.snapshot; historyId?.let { writeHistory(it, "SOS_CANCELLED", "Cancelled during countdown; no SMS or call attempted") }; statusMessage = "SOS cancelled. No SMS, call, or location request was made." } }, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = EmergencyDeepRed)) { Text("CANCEL SOS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        if (snapshot.stage == EmergencyStage.LOCATION_LOOKUP) {
            Text("Processing configured emergency actions…", color = EmergencyDeepRed, fontWeight = FontWeight.Bold)
            StatusLine("Location", locationLabel); StatusLine("SMS", smsLabel); StatusLine("Call", callLabel)
        }
        statusMessage?.let { Text(it, color = EmergencyDeepRed, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun StatusLine(label: String, status: String) { Text("$label · $status", color = Color(0xFF3D302F), modifier = Modifier.padding(vertical = 2.dp)) }
private fun hasPermission(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
@Suppress("DEPRECATION") private fun hasSmsCapability(context: Context): Boolean = runCatching { context.getSystemService(SmsManager::class.java) != null || SmsManager.getDefault() != null }.getOrDefault(false)

@Suppress("DEPRECATION")
private fun sendEmergencySms(context: Context, phone: String, body: String, historyId: Long): HandoffStatus {
    return try {
        val manager = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        if (manager == null) return HandoffStatus.UNAVAILABLE
        val requestCode = (historyId % Int.MAX_VALUE).toInt()
        val parts = manager.divideMessage(body)
        fun sentIntent(part: Int) = PendingIntent.getBroadcast(context, requestCode + part, Intent(context, EmergencyDeliveryReceiver::class.java).setData(Uri.parse("medmitra://sms/$historyId/$part")).putExtra(EmergencyDeliveryReceiver.EXTRA_HISTORY_ID, historyId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (parts.size == 1) manager.sendTextMessage(phone, null, body, sentIntent(0), null)
        else manager.sendMultipartTextMessage(phone, null, parts, ArrayList(List(parts.size) { sentIntent(it) }), null)
        HandoffStatus.OUTCOME_UNKNOWN
    } catch (_: SecurityException) { HandoffStatus.UNAVAILABLE }
    catch (_: UnsupportedOperationException) { HandoffStatus.UNAVAILABLE }
    catch (_: IllegalArgumentException) { HandoffStatus.UNAVAILABLE }
}

private fun placeAutomaticCall(context: Context, phone: String): HandoffStatus {
    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phone)}"))
    return try { if (intent.resolveActivity(context.packageManager) == null) HandoffStatus.UNAVAILABLE else { context.startActivity(intent); HandoffStatus.OUTCOME_UNKNOWN } }
    catch (_: ActivityNotFoundException) { HandoffStatus.UNAVAILABLE }
    catch (_: SecurityException) { HandoffStatus.UNAVAILABLE }
}

private fun openDialer(context: Context, phone: String): Boolean = if (phone.isBlank()) false else runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
private fun openSmsFallback(context: Context, phone: String, location: String): Boolean = if (phone.isBlank()) false else runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).putExtra("sms_body", "I need help. Please contact me.$location").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
private fun locationFallbackText(event: EmergencyHistoryEvent?): String = event?.latitude?.let { latitude -> event.longitude?.let { longitude -> " My current location: https://maps.google.com/?q=$latitude,$longitude" } } ?: " Location was unavailable."

@Suppress("DEPRECATION")
internal suspend fun currentLocation(context: Context): Location? = withTimeoutOrNull(12_000L) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return@withTimeoutOrNull null
    val fine = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val provider = manager.getBestProvider(Criteria().apply { accuracy = if (fine) Criteria.ACCURACY_FINE else Criteria.ACCURACY_COARSE }, true) ?: return@withTimeoutOrNull null
    suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { if (continuation.isActive) continuation.resume(location); runCatching { manager.removeUpdates(this) } }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()); continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } } }
        catch (_: SecurityException) { if (continuation.isActive) continuation.resume(null) }
        catch (_: IllegalArgumentException) { if (continuation.isActive) continuation.resume(null) }
    }
}
