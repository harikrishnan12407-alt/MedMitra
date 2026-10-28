package com.medmitra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
import com.medmitra.app.ui.components.CenteredFeatureIcon
import com.medmitra.app.ui.components.FrostedCard
import com.medmitra.app.ui.components.IconBadge
import com.medmitra.app.ui.components.InfoRow
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.components.PrimaryAction
import com.medmitra.app.ui.components.SectionHeading
import com.medmitra.app.ui.components.SoftPill
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
        setContent { MedMitraTheme { MedMitraApp() } }
    }
}

@Composable
private fun MedMitraApp() {
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
                    tonalElevation = 5.dp
                ) {
                    listOf(
                        Triple("home", "Home", Icons.Filled.Home),
                        Triple("history", "Activity", Icons.Filled.History),
                        Triple("profile", "Profile", Icons.Filled.PersonOutline)
                    ).forEach { (destination, label, icon) ->
                        NavigationBarItem(
                            selected = route == destination,
                            onClick = { nav.navigate(destination) { launchSingleTop = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
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
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
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
    var vitaminTaken by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MedMitraColors.Background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Good morning, Ananya", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text)
                    Text("Your care, at a glance", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MedMitraColors.MintPale),
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NEXT REMINDER", color = MedMitraColors.Sage, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    SoftPill("9:00 AM")
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Medication, tint = MedMitraColors.MintDark, size = 46)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vitamin D3", style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text)
                        Text("1 capsule · after breakfast", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MedMitraColors.Divider)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Today · Morning", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, modifier = Modifier.weight(1f))
                    if (vitaminTaken) {
                        SoftPill("Taken", background = MedMitraColors.MintPale)
                    } else {
                        PrimaryAction("Mark as taken", icon = Icons.Filled.Check, onClick = { vitaminTaken = true })
                    }
                }
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
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.chunked(2).forEach { rowFeatures ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                "Emergency, voice, and fall-safety tools are previews and do not contact anyone or monitor activity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MedMitraColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            )
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
                Text("Blood pressure tablet", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text("1 tablet · Today, 8:00 PM", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "View medicine schedule", tint = MedMitraColors.Sage)
        }
    }
}

@Composable
private fun SosPreview(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MedMitraColors.CoralPale)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Filled.Sos, tint = MedMitraColors.Coral, size = 40)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Need help?", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
            Text("Open the SOS preview", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
        }
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Open", color = MedMitraColors.Coral, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ShortcutCard(feature: Feature, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PlainCard(modifier.heightIn(min = 102.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(feature.icon, tint = feature.tint, size = 36)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.shortTitle, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(feature.subtitle, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, maxLines = 2, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FeatureScreen(id: String, onBack: () -> Unit) {
    val feature = features.firstOrNull { it.id == id }
    var fallPreviewEnabled by rememberSaveable { mutableStateOf(false) }
    var vitaminTaken by rememberSaveable { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MedMitraColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        PageHeader(feature?.title ?: "MedMitra", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        when (id) {
            "reminder" -> {
                Text("Today’s plan", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text)
                Text("Your sample medicine schedule", style = MaterialTheme.typography.bodyLarge, color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                MedicineScheduleCard("Vitamin D3", "1 capsule · after breakfast", "9:00 AM", !vitaminTaken) { vitaminTaken = true }
                Spacer(Modifier.height(11.dp))
                MedicineScheduleCard("Blood pressure tablet", "1 tablet · evening", "8:00 PM", false) { showInfo = true }
                Spacer(Modifier.height(14.dp))
                PrimaryAction("Add a reminder", icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth()) { showInfo = true }
                PreviewNote("Sample schedule only · reminders are not scheduled by this preview.")
            }
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
private fun MedicineScheduleCard(name: String, dosage: String, time: String, canMarkTaken: Boolean, onTaken: () -> Unit) {
    PlainCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Medication, tint = MedMitraColors.MintDark, size = 42)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text(dosage, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
            }
            SoftPill(time)
        }
        Spacer(Modifier.height(11.dp))
        if (canMarkTaken) {
            OutlinedButton(onClick = onTaken, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Mark as taken")
            }
        } else {
            Text("Sample reminder", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.Sage)
        }
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("Recent activity", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text)
        Text("A simple record of your care routine", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
        Spacer(Modifier.height(1.dp))
        PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.CheckCircle, "Medicine marked taken", "Today · 8:02 AM · Vitamin D3") }
        PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.Notifications, "Reminder preview", "Yesterday · 8:00 PM", tint = MedMitraColors.Amber) }
        PlainCard(Modifier.fillMaxWidth()) { InfoRow(Icons.Filled.Favorite, "Wellness check-in", "Monday · You were feeling good") }
        PreviewNote("Sample history only · no emergency events have been recorded.")
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
