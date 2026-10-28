package com.medmitra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.ui.components.CenteredFeatureIcon
import com.medmitra.app.ui.components.FrostedCard
import com.medmitra.app.ui.components.IconBadge
import com.medmitra.app.ui.components.InfoRow
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.components.PrimaryAction
import com.medmitra.app.ui.components.SectionHeading
import com.medmitra.app.ui.theme.MedMitraColors
import com.medmitra.app.ui.theme.MedMitraTheme

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(245, 249, 246)
        window.navigationBarColor = android.graphics.Color.rgb(245, 249, 246)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val openReminders = intent.getBooleanExtra(com.medmitra.app.reminders.ReminderScheduler.EXTRA_OPEN_REMINDERS, false)
        setContent { MedMitraTheme { MedMitraApp(openReminders = openReminders) } }
    }
}

@Composable
private fun MedMitraApp(openReminders: Boolean = false) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"

    Scaffold(
        containerColor = MedMitraColors.Background,
        contentColor = MedMitraColors.Text,
        bottomBar = {
            if (route in listOf("home", "history", "profile")) {
                NavigationBar(
                    containerColor = MedMitraColors.Surface,
                    contentColor = MedMitraColors.TextSecondary,
                    tonalElevation = 1.dp
                ) {
                    listOf(
                        Triple("home", "Home", Icons.Filled.Home),
                        Triple("history", "Activity", Icons.Filled.History),
                        Triple("profile", "Profile", Icons.Filled.PersonOutline)
                    ).forEach { (destination, label, icon) ->
                        NavigationBarItem(
                            selected = route == destination,
                            onClick = { nav.navigate(destination) { launchSingleTop = true } },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MedMitraColors.MintDark,
                                selectedTextColor = MedMitraColors.MintDark,
                                indicatorColor = MedMitraColors.MintPale,
                                unselectedIconColor = MedMitraColors.Sage,
                                unselectedTextColor = MedMitraColors.Sage
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = nav, startDestination = if (openReminders) "feature/reminder" else "home", modifier = Modifier.padding(innerPadding)) {
            composable("home") { HomeScreen(onOpen = { nav.navigate("feature/$it") }) }
            composable("history") { HistoryScreen() }
            composable("profile") { ProfileScreen() }
            composable("feature/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStackEntry ->
                FeatureScreen(
                    id = backStackEntry.arguments?.getString("id") ?: "reminder",
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

private data class Feature(
    val id: String,
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color = MedMitraColors.MintDark
)

private val features = listOf(
    Feature("reminder", "Medicine Reminders", "Medicines", "Your daily schedule", Icons.Filled.Medication),
    Feature("voice", "Voice Reminders", "Voice", "Personal reminders", Icons.Filled.RecordVoiceOver),
    Feature("contacts", "Emergency Contacts", "Contacts", "People you trust", Icons.Filled.PeopleAlt),
    Feature("sos", "Emergency SOS", "SOS", "Help and safety", Icons.Filled.Sos, MedMitraColors.Coral),
    Feature("fall", "Fall Detection", "Fall safety", "Explore the feature", Icons.Filled.AccessibilityNew),
    Feature("history", "Emergency History", "History", "Recent activity", Icons.Filled.History)
)

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF0FAF4), MedMitraColors.Background, Color(0xFFF8FBF9))))) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Good morning, Ananya", style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text)
                    Text("Your care, at a glance", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MedMitraColors.MintPale),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = MedMitraColors.MintDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            SectionHeading("Today’s medicines", "See schedule") { onOpen("reminder") }
            Spacer(Modifier.height(8.dp))
            FrostedCard(Modifier.fillMaxWidth()) {
                Text("Your saved medicine schedule", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text("Manage reminders and mark doses taken.", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 5.dp))
                TextButton(onClick = { onOpen("reminder") }) { Text("Open reminders", color = MedMitraColors.MintDark) }
            }
        }

        item {
            MedicinePreviewCard(onClick = { onOpen("reminder") })
        }

        item {
            SosPreview(onClick = { onOpen("sos") })
        }

        item {
            SectionHeading("Your shortcuts")
            Spacer(Modifier.height(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.chunked(2).forEach { rowFeatures ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowFeatures.forEach { feature ->
                            ShortcutCard(feature, Modifier.weight(1f)) { onOpen(feature.id) }
                        }
                        if (rowFeatures.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(
                "SOS opens a message for you to review and can place a call only after your confirmation. Voice and fall monitoring are not active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MedMitraColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
      }
    }
}

@Composable
private fun MedicinePreviewCard(onClick: () -> Unit) {
    PlainCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Medication, tint = MedMitraColors.Amber, size = 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Medicine reminders", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text("View your saved schedule", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "View medicine schedule", tint = MedMitraColors.Sage)
        }
    }
}

@Composable
private fun SosPreview(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x12A54F48), spotColor = Color(0x12A54F48))
            .clip(RoundedCornerShape(20.dp))
            .background(MedMitraColors.CoralPale)
            .border(1.dp, Color(0xFFF0D9D4), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Filled.Sos, tint = MedMitraColors.Coral, size = 40)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Need help?", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
            Text("Start a cancelable SOS flow", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
        }
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Open", color = MedMitraColors.Coral, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ShortcutCard(feature: Feature, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PlainCard(modifier.height(84.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(feature.icon, tint = feature.tint, size = 32)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.shortTitle, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Text(feature.subtitle, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, maxLines = 2, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FeatureScreen(id: String, onBack: () -> Unit) {
    if (id == "reminder") {
        com.medmitra.app.reminders.MedicineReminderScreen(onBack = onBack)
        return
    }
    if (id == "contacts" || id == "sos") {
        val context = LocalContext.current
        val database = remember(context) { MedMitraDatabase.get(context) }
        if (id == "contacts") com.medmitra.app.emergency.EmergencyContactsScreen(database, onBack)
        else com.medmitra.app.emergency.EmergencySosScreen(database, onBack)
        return
    }
    val feature = features.firstOrNull { it.id == id }
    var fallPreviewEnabled by rememberSaveable { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MedMitraColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        PageHeader(feature?.title ?: "MedMitra", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        when (id) {
            "voice" -> {
                CenteredFeatureIcon(Icons.Filled.RecordVoiceOver)
                Text("A reminder in your voice", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Add a personal message to make your routine feel more familiar.", style = MaterialTheme.typography.bodyLarge, color = MedMitraColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp, bottom = 17.dp))
                PlainCard(Modifier.fillMaxWidth()) {
                    InfoRow(Icons.Filled.GraphicEq, "Sample reminder", "“Ananya, it’s time for your evening medicine.”")
                }
                Spacer(Modifier.height(15.dp))
                PrimaryAction("Try a sample recording", icon = Icons.Filled.Mic, modifier = Modifier.fillMaxWidth()) { showInfo = true }
                PreviewNote("Voice recording and playback are not implemented.")
            }
            "contacts" -> {
                Text("People you trust", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text)
                Text("Sample contacts for an emergency plan.", style = MaterialTheme.typography.bodyLarge, color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.Person, "Meera Sharma", "Daughter · +91 98765 43210") }
                Spacer(Modifier.height(10.dp))
                PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.Person, "Arjun Sharma", "Neighbor · +91 98765 12345") }
                Spacer(Modifier.height(15.dp))
                PrimaryAction("Add a contact", icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth()) { showInfo = true }
                PreviewNote("Contacts are illustrative. No calls or messages can be sent.")
            }
            "sos" -> {
                CenteredFeatureIcon(Icons.Filled.Sos, MedMitraColors.Coral)
                Text("Emergency SOS", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("If you need immediate help, use your phone’s emergency calling feature.", style = MaterialTheme.typography.bodyLarge, color = MedMitraColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp, bottom = 18.dp))
                FrostedCard(Modifier.fillMaxWidth()) {
                    Text("Preview only", style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text)
                    Text("This demo does not place calls, send SMS, or share your location.", style = MaterialTheme.typography.bodyLarge, color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { showInfo = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedMitraColors.Coral)
                    ) {
                        Icon(Icons.Filled.Sos, contentDescription = null)
                        Spacer(Modifier.width(9.dp))
                        Text("Open SOS demo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            "fall" -> {
                CenteredFeatureIcon(Icons.Filled.AccessibilityNew)
                Text("Fall safety", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Background fall detection is not available in this version.", style = MaterialTheme.typography.bodyLarge, color = MedMitraColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp, bottom = 16.dp))
                PlainCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Preview setting", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                            Text("No sensors are monitored", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                        }
                        Switch(
                            checked = fallPreviewEnabled,
                            onCheckedChange = { fallPreviewEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MedMitraColors.Mint)
                        )
                    }
                }
                if (fallPreviewEnabled) PreviewNote("Preview only · this setting does not monitor movement.")
                Spacer(Modifier.height(14.dp))
                PrimaryAction("How it could work", icon = Icons.Filled.Info, modifier = Modifier.fillMaxWidth()) { showInfo = true }
            }
            "history" -> HistoryContent()
            else -> Unit
        }
        Spacer(Modifier.height(22.dp))
    }

    if (showInfo) PreviewDialog(id = id, onDismiss = { showInfo = false })
}

@Composable
private fun HistoryScreen() {
    Column(Modifier.fillMaxSize().background(MedMitraColors.Background).padding(horizontal = 18.dp)) {
        PageHeader("Activity")
        Spacer(Modifier.height(12.dp))
        HistoryContent()
    }
}

@Composable
private fun HistoryContent() {
    val context = LocalContext.current
    val database = remember(context) { MedMitraDatabase.get(context) }
    val events by database.history().observeAll().collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("Recent activity", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text)
        if (events.isEmpty()) PlainCard(Modifier.fillMaxWidth()) {
            Text("No activity recorded yet", color = MedMitraColors.Text)
        }
        events.forEach { event ->
            val title = when (event.eventType) {
                "MEDICINE_MARKED_TAKEN" -> "Medicine marked taken"
                "SOS_COUNTDOWN" -> "SOS started"
                "SOS_CANCELLED" -> "SOS cancelled"
                "SOS_TRIGGERED" -> "SOS actions opened"
                "SOS_COMPLETE" -> "SOS flow completed"
                else -> event.eventType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            }
            PlainCard(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.occurredAtMillis)), color = MedMitraColors.TextSecondary)
                if (event.notes.isNotBlank()) Text(event.notes, color = MedMitraColors.TextSecondary)
                if (event.eventType.startsWith("SOS")) {
                    Text("Location: ${event.locationStatus} · SMS: ${event.smsAttemptStatus} · Call: ${event.callAttemptStatus}", color = MedMitraColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen() {
    Column(Modifier.fillMaxSize().background(MedMitraColors.Background).padding(horizontal = 18.dp)) {
        PageHeader("Profile")
        Spacer(Modifier.height(12.dp))
        PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.Person, "Ananya Sharma", "Care plan · Personal") }
        Spacer(Modifier.height(11.dp))
        PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.Settings, "Preferences", "Notifications · Accessibility", tint = MedMitraColors.Sage) }
        Spacer(Modifier.height(18.dp))
        Text("MedMitra preview · Version 1.0", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
    }
}

@Composable
private fun PreviewNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 13.dp, bottom = 5.dp))
}

@Composable
private fun PreviewDialog(id: String, onDismiss: () -> Unit) {
    val sos = id == "sos"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MedMitraColors.Surface,
        titleContentColor = MedMitraColors.Text,
        textContentColor = MedMitraColors.TextSecondary,
        title = { Text(if (sos) "SOS preview only" else "Coming soon", fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                if (sos) "This preview does not contact anyone. If you need help now, use your phone’s emergency calling feature."
                else "This is a sample interaction and is not connected to a real service yet."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = MedMitraColors.MintDark) } }
    )
}
