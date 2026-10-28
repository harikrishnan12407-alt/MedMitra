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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

private val Navy = Color(0xFF07131F)
private val NavyDeep = Color(0xFF050E18)
private val Panel = Color(0xFF102131)
private val Mint = Color(0xFF72E6C0)
private val Muted = Color(0xFF91A4B4)
private val Red = Color(0xFFFF5964)
private val Line = Color(0xFF23394A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MedMitraTheme { MedMitraApp() } }
    }
}

@Composable
private fun MedMitraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, onPrimary = NavyDeep, background = Navy,
            surface = Panel, onSurface = Color(0xFFF2F6F8), secondary = Mint
        ),
        content = content
    )
}

@Composable
private fun MedMitraApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    Scaffold(
        containerColor = Navy,
        bottomBar = {
            if (route in listOf("home", "history", "profile")) {
                NavigationBar(containerColor = NavyDeep, contentColor = Muted) {
                    listOf(
                        Triple("home", "Home", Icons.Filled.Home),
                        Triple("history", "Activity", Icons.Filled.History),
                        Triple("profile", "Profile", Icons.Filled.PersonOutline)
                    ).forEach { (dest, label, icon) ->
                        NavigationBarItem(
                            selected = route == dest,
                            onClick = { nav.navigate(dest) { launchSingleTop = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Mint, selectedTextColor = Mint,
                                indicatorColor = Panel, unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onOpen = { nav.navigate("feature/$it") }) }
            composable("history") { HistoryScreen() }
            composable("profile") { ProfileScreen() }
            composable("feature/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                FeatureScreen(id = it.arguments?.getString("id") ?: "reminder", onBack = { nav.popBackStack() })
            }
        }
    }
}

private data class Feature(val id: String, val title: String, val subtitle: String, val icon: ImageVector, val tint: Color = Mint)

private val features = listOf(
    Feature("reminder", "Medicine Reminder", "Stay on track with your meds", Icons.Filled.Medication),
    Feature("voice", "Voice Reminder", "Gentle spoken reminders", Icons.Filled.RecordVoiceOver),
    Feature("contacts", "Emergency Contacts", "Your trusted people", Icons.Filled.PeopleAlt),
    Feature("sos", "Emergency SOS", "Get help when you need it", Icons.Filled.Sos, Red),
    Feature("fall", "Fall Detection", "Understand your safety options", Icons.Filled.AccessibilityNew),
    Feature("history", "Emergency History", "Review recent activity", Icons.Filled.History)
)

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    var doseTaken by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1B29), Navy))),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Good morning,", color = Muted, fontSize = 14.sp)
                    Text("Ananya", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(Panel).border(1.dp, Line, CircleShape), contentAlignment = Alignment.Center) {
                    Text("A", color = Mint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().glassCard(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Mint.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, null, tint = Mint, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("You’re all set", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Your care plan is up to date", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Muted)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Next reminder", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("TODAY · 9:00 AM", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                }
                Text("See all", color = Mint, fontSize = 13.sp, modifier = Modifier.clickable { onOpen("reminder") })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().glassCard(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF443D31)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Medication, null, tint = Color(0xFFFFD18B))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Vitamin D3", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("1 capsule · after breakfast", color = Muted, fontSize = 12.sp)
                }
                Text(if (doseTaken) "Taken" else "Take", color = if (doseTaken) Mint else NavyDeep,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (doseTaken) Mint.copy(alpha = .14f) else Mint)
                        .clickable { doseTaken = !doseTaken }
                        .padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
        item {
            Text("YOUR TOOLS", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                features.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { feature -> FeatureCard(feature, Modifier.weight(1f)) { onOpen(feature.id) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Text("Emergency features are sample UI and do not contact emergency services.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
    }
}

@Composable
private fun FeatureCard(feature: Feature, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().height(140.dp).glassCard().clickable(onClick = onClick), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(feature.tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(feature.icon, contentDescription = null, tint = feature.tint, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(feature.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
            Spacer(Modifier.height(3.dp))
            Text(feature.subtitle, color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun Modifier.glassCard(): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .background(Brush.linearGradient(listOf(Color(0xCC172B3A), Color(0xAA102131))))
    .border(1.dp, Color(0xFF294152), RoundedCornerShape(20.dp))
    .padding(16.dp)

@Composable
private fun FeatureScreen(id: String, onBack: () -> Unit) {
    val feature = features.firstOrNull { it.id == id }
    var enabled by remember { mutableStateOf(id == "reminder") }
    var showInfo by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Navy).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Text(feature?.title ?: "MedMitra", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        when (id) {
            "reminder" -> {
                Text("Your schedule", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("A little consistency goes a long way.", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
                InfoCard(Icons.Filled.Medication, "Vitamin D3", "Today · 9:00 AM · 1 capsule")
                Spacer(Modifier.height(12.dp))
                InfoCard(Icons.Filled.Medication, "Blood pressure tablet", "Today · 8:00 PM · 1 tablet")
                Spacer(Modifier.height(18.dp))
                ActionButton("＋   Add a reminder") { showInfo = true }
                Text("Sample schedule · reminders are not scheduled yet.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
            }
            "voice" -> {
                CenterIcon(Icons.Filled.RecordVoiceOver, Mint)
                Text("A reminder in your voice", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Record a personal message to pair with your medicine routine.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
                InfoCard(Icons.Filled.GraphicEq, "Sample voice reminder", "“Ananya, it’s time for your evening medicine.”")
                Spacer(Modifier.height(18.dp))
                ActionButton("Start a sample recording") { showInfo = true }
                Text("Microphone recording is not implemented in this preview.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
            }
            "contacts" -> {
                Text("People you trust", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Sample contacts for your emergency plan.", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
                InfoCard(Icons.Filled.Person, "Meera Sharma", "Daughter · +91 98765 43210")
                Spacer(Modifier.height(12.dp))
                InfoCard(Icons.Filled.Person, "Arjun Sharma", "Neighbor · +91 98765 12345")
                Spacer(Modifier.height(18.dp))
                ActionButton("＋   Add a contact") { showInfo = true }
                Text("Contacts are illustrative. No calls or messages can be sent.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
            }
            "sos" -> {
                CenterIcon(Icons.Filled.Sos, Red)
                Text("Emergency SOS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("If you need immediate help, contact your local emergency services directly.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                Button(onClick = { showInfo = true }, colors = ButtonDefaults.buttonColors(containerColor = Red), shape = CircleShape, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Icon(Icons.Filled.Sos, null); Spacer(Modifier.width(10.dp)); Text("SOS  ·  PREVIEW", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text("This button does not place calls, send SMS, or share location.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
            }
            "fall" -> {
                CenterIcon(Icons.Filled.AccessibilityNew, Mint)
                Text("Fall detection", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Background fall detection is not available in this version.", color = Muted, modifier = Modifier.padding(top = 7.dp, bottom = 22.dp))
                Row(Modifier.fillMaxWidth().glassCard(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Learn about setup", color = Color.White, fontWeight = FontWeight.Medium)
                        Text("No sensors are being monitored", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Navy, checkedTrackColor = Mint))
                }
                if (enabled) Text("Preview only — enabling this does not monitor movement.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(18.dp))
                ActionButton("How it could work") { showInfo = true }
            }
            else -> HistoryContent()
        }
    }
    if (showInfo) AlertDialog(
        onDismissRequest = { showInfo = false },
        containerColor = Panel,
        title = { Text(if (id == "sos") "Preview only" else "Coming soon", color = Color.White) },
        text = { Text(if (id == "sos") "This demo does not contact anyone. Please use your phone’s emergency calling feature if you need help now." else "This interaction is a sample. The feature is not connected to a real service yet.", color = Muted) },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it", color = Mint) } }
    )
}

@Composable
private fun CenterIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(82.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().glassCard(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Mint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Text(text, color = Mint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryScreen() {
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp)) {
        Text("Emergency History", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))
        HistoryContent()
    }
}

@Composable
private fun HistoryContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("RECENT ACTIVITY", color = Muted, fontSize = 11.sp, letterSpacing = 1.4.sp)
        InfoCard(Icons.Filled.CheckCircle, "Medicine marked taken", "Today · 8:02 AM · Vitamin D3")
        InfoCard(Icons.Filled.Notifications, "Reminder preview", "Yesterday · 8:00 PM")
        InfoCard(Icons.Filled.Favorite, "Wellness check-in", "Monday · You were feeling good")
        Text("Sample history only. No emergency events have been recorded.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ProfileScreen() {
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp)) {
        Text("Your profile", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))
        InfoCard(Icons.Filled.Person, "Ananya Sharma", "Care plan · Personal")
        Spacer(Modifier.height(12.dp))
        InfoCard(Icons.Filled.Settings, "Preferences", "Notifications · Accessibility")
        Spacer(Modifier.height(20.dp))
        Text("MedMitra preview · Version 1.0", color = Muted, fontSize = 12.sp)
    }
}
