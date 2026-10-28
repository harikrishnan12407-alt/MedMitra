package com.medmitra.app.emergency

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

@Composable
fun EmergencyContactsScreen(database: MedMitraDatabase, onBack: () -> Unit) {
    val contacts by database.contacts().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("Emergency Contacts", onBack = onBack)
        Text("Contacts stay on this device. SOS messages are reviewed in your messaging app before they can be sent.", color = MedMitraColors.TextSecondary)
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
                onDismiss = { showEditor = false },
                onSave = { name, phone ->
                    showEditor = false
                    scope.launch { database.contacts().save(EmergencyContact(name = name, phoneE164 = phone)) }
                }
            )
        }
    }
}

@Composable
private fun ContactEditor(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val validPhone = remember(phone) { phone.matches(Regex("^\\+[1-9][0-9]{7,14}$")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add trusted contact") },
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
