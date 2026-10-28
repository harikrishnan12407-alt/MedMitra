package com.medmitra.app.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medmitra.app.data.EmergencyContact
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.theme.MedMitraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

data class UserProfile(
    val fullName: String,
    val age: Int,
    val photoUri: String,
    val language: String,
    val emergencyName: String,
    val emergencyPhone: String
)

object ProfilePreferences {
    private const val FILE = "user_profile"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun completed(context: Context) = prefs(context).getBoolean("completed", false)
    fun shouldShowOnboarding(context: Context): Boolean {
        if (completed(context)) return false
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val isExistingInstall = packageInfo != null && packageInfo.lastUpdateTime - packageInfo.firstInstallTime > 60_000L
        if (isExistingInstall) prefs(context).edit().putBoolean("completed", true).putBoolean("legacy_profile", true).apply()
        return !isExistingInstall
    }
    fun read(context: Context) = UserProfile(
        prefs(context).getString("name", "").orEmpty(), prefs(context).getInt("age", 0),
        prefs(context).getString("photo", "").orEmpty(), prefs(context).getString("language", "English").orEmpty(),
        prefs(context).getString("emergency_name", "").orEmpty(), prefs(context).getString("emergency_phone", "").orEmpty()
    )
    fun save(context: Context, profile: UserProfile) {
        prefs(context).edit().putBoolean("completed", true).putString("name", profile.fullName)
            .putInt("age", profile.age).putString("photo", profile.photoUri).putString("language", profile.language)
            .putString("emergency_name", profile.emergencyName).putString("emergency_phone", profile.emergencyPhone).apply()
    }
}

@Composable
fun ProfileSetupScreen(onBack: (() -> Unit)?, onSaved: () -> Unit, onSkip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val database = remember(context) { MedMitraDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val initial = remember { ProfilePreferences.read(context) }
    var name by remember { mutableStateOf(initial.fullName) }
    var age by remember { mutableStateOf(if (initial.age == 0) "" else initial.age.toString()) }
    var photo by remember { mutableStateOf(initial.photoUri) }
    var language by remember { mutableStateOf(initial.language.ifBlank { "English" }) }
    var emergencyName by remember { mutableStateOf(initial.emergencyName) }
    var emergencyPhone by remember { mutableStateOf(initial.emergencyPhone) }
    var contactConsent by remember { mutableStateOf(initial.emergencyName.isNotBlank() && initial.emergencyPhone.isNotBlank()) }
    var languageMenu by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photo = uri.toString()
        }
    }
    val ageValue = age.toIntOrNull()
    val phoneValid = emergencyPhone.isBlank() || emergencyPhone.matches(Regex("^\\+[1-9][0-9]{7,14}$"))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(if (ProfilePreferences.completed(context)) "Edit Profile" else "Welcome to MedMitra", onBack = onBack)
        Text(if (ProfilePreferences.completed(context)) "Update your local profile details." else "A few details help personalize your reminders. Your information stays on this device.", color = MedMitraColors.TextSecondary)
        Text("Step ${step + 1} of 2", color = MedMitraColors.MintDark, fontWeight = FontWeight.SemiBold)
        if (step == 0) PlainCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProfileAvatar(photo, name, Modifier.size(68.dp))
                OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    androidx.compose.material3.Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Text("Optional photo", Modifier.padding(start = 6.dp))
                }
            }
            OutlinedTextField(name, { name = it.take(80); error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3); error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Age") }, singleLine = true)
            Column {
                OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Preferred language · $language") }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    listOf("English", "हिन्दी", "தமிழ்", "తెలుగు", "ಕನ್ನಡ", "മലയാളം", "বাংলা", "मराठी").forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = { language = item; languageMenu = false })
                    }
                }
            }
        }
        if (step == 1) PlainCard(Modifier.fillMaxWidth()) {
            Text("Emergency contact (optional)", color = MedMitraColors.Text, fontWeight = FontWeight.SemiBold)
            Text("Only save this person as a trusted contact if they have agreed to receive emergency messages or calls.", color = MedMitraColors.TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(contactConsent, { contactConsent = it })
                Text("I have consent to save and contact this person", color = MedMitraColors.TextSecondary)
            }
            OutlinedTextField(emergencyName, { emergencyName = it.take(80); error = null }, modifier = Modifier.fillMaxWidth(), enabled = contactConsent, label = { Text("Contact name") }, singleLine = true)
            OutlinedTextField(emergencyPhone, { emergencyPhone = it.take(20); error = null }, modifier = Modifier.fillMaxWidth(), enabled = contactConsent, label = { Text("Phone number with country code") }, singleLine = true)
            if (!phoneValid) Text("Use international format, e.g. +14155550123.", color = MedMitraColors.Coral)
        }
        error?.let { Text(it, color = MedMitraColors.Coral) }
        Button(onClick = {
            if (name.isBlank()) { error = "Enter your name to continue."; return@Button }
            if (ageValue == null || ageValue !in 1..120) { error = "Enter an age from 1 to 120."; return@Button }
            if (step == 0) { step = 1; error = null; return@Button }
            if (!phoneValid) { error = "Enter a valid international phone number or leave the number blank."; return@Button }
            if (contactConsent && (emergencyName.isBlank() || emergencyPhone.isBlank())) { error = "Enter the contact name and phone number, or leave contact consent off."; return@Button }
            scope.launch {
                saving = true
                try {
                    withContextSafe {
                        if (contactConsent) {
                            val existing = database.contacts().getByPhone(emergencyPhone.trim())
                            if (existing == null) database.contacts().save(EmergencyContact(name = emergencyName.trim(), phoneE164 = emergencyPhone.trim()))
                        }
                        ProfilePreferences.save(context, UserProfile(name.trim(), ageValue, photo, language, if (contactConsent) emergencyName.trim() else "", if (contactConsent) emergencyPhone.trim() else ""))
                    }
                    Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                    onSaved()
                } catch (_: Exception) {
                    error = "Could not save your profile. Your existing data has not been cleared."
                } finally {
                    saving = false
                }
            }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Saving…" else if (step == 0) "Continue" else "Save profile") }
        if (step == 1) OutlinedButton(onClick = { step = 0; error = null }, modifier = Modifier.fillMaxWidth()) { Text("Back to profile details") }
        if (onSkip != null && step == 0) TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Set up profile later") }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
fun ProfileAvatar(uri: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = if (uri.isBlank()) null else withContextSafe { runCatching { decodeProfileBitmap(context, Uri.parse(uri)) }.getOrNull() }
    }
    androidx.compose.foundation.layout.Box(modifier.clip(CircleShape).background(MedMitraColors.MintPale), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Profile photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) {
                Text(name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").ifBlank { "M" }.uppercase(), color = MedMitraColors.MintDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun decodeProfileBitmap(context: Context, uri: Uri): android.graphics.Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / sample > 512) sample *= 2
    options.inSampleSize = sample
    options.inJustDecodeBounds = false
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private suspend fun <T> withContextSafe(block: suspend () -> T): T = kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
