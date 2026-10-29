package com.medmitra.app

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
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
import com.medmitra.app.ui.components.FrostedCard
import com.medmitra.app.ui.components.IconBadge
import com.medmitra.app.ui.components.InfoRow
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.components.SectionHeading
import com.medmitra.app.ui.theme.MedMitraColors
import com.medmitra.app.ui.theme.MedMitraTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val startDestination = when {
            intent.getBooleanExtra(com.medmitra.app.reminders.ReminderScheduler.EXTRA_OPEN_REMINDERS, false) -> "feature/reminder"
            intent.getBooleanExtra(com.medmitra.app.fall.FallMonitoringService.EXTRA_OPEN_SOS, false) -> "feature/sos"
            intent.getBooleanExtra(com.medmitra.app.fall.FallMonitoringService.EXTRA_OPEN_FALL, false) -> "feature/fall"
            else -> "splash"
        }
        val showingSplash = startDestination == "splash"
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val initialSystemBarColor = if (showingSplash) android.graphics.Color.rgb(6, 43, 36) else android.graphics.Color.rgb(245, 249, 246)
        window.statusBarColor = initialSystemBarColor
        window.navigationBarColor = initialSystemBarColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !showingSplash
            isAppearanceLightNavigationBars = !showingSplash
        }
        setContent { MedMitraTheme { MedMitraApp(startDestination = startDestination) } }
    }
}
@Composable
private fun MedMitraApp(startDestination: String = "home") {
    val nav = rememberNavController()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: startDestination
    val activity = context as? ComponentActivity
    SideEffect {
        val showingSplash = route == "splash"
        val barColor = if (showingSplash) android.graphics.Color.rgb(6, 43, 36) else android.graphics.Color.rgb(245, 249, 246)
        activity?.window?.let { window ->
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !showingSplash
                isAppearanceLightNavigationBars = !showingSplash
            }
        }
    }

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
        NavHost(navController = nav, startDestination = startDestination, modifier = Modifier.padding(innerPadding)) {
            composable(
                route = "splash",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) {
                MedMitraSplash {
                    nav.navigate(if (com.medmitra.app.profile.ProfilePreferences.shouldShowOnboarding(context)) "profile/setup" else "home") {
                        popUpTo("splash") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable("home") { HomeScreen(onOpen = { nav.navigate("feature/$it") }) }
            composable("history") { HistoryScreen() }
            composable("profile") { ProfileScreen(onOpen = { nav.navigate("feature/$it") }, onEdit = { nav.navigate("profile/setup") }) }
            composable("profile/setup") {
                com.medmitra.app.profile.ProfileSetupScreen(
                    onBack = if (com.medmitra.app.profile.ProfilePreferences.completed(context)) ({ nav.popBackStack() }) else null,
                    onSaved = {
                        if (nav.previousBackStackEntry?.destination?.route == "profile") nav.popBackStack()
                        else nav.navigate("home") { popUpTo("profile/setup") { inclusive = true } }
                    },
                    onSkip = if (!com.medmitra.app.profile.ProfilePreferences.completed(context)) ({ nav.navigate("home") { popUpTo("profile/setup") { inclusive = true } } }) else null
                )
            }
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
    val context = LocalContext.current
    val schedules by MedMitraDatabase.get(context).medicines().observeAll().collectAsState(initial = emptyList())
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF0FAF4), MedMitraColors.Background, Color(0xFFF8FBF9))))) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Welcome to MedMitra", style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text)
                    Text("Your care, at a glance", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MedMitraColors.MintPale),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = MedMitraColors.MintDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            SectionHeading("Today’s medicines", "See schedule") { onOpen("reminder") }
            Spacer(Modifier.height(8.dp))
            if (schedules.none { it.enabled }) {
                FrostedCard(Modifier.fillMaxWidth()) {
                    Text("No active medicine reminders", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                    Text("Add a schedule to see it here.", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                    TextButton(onClick = { onOpen("reminder") }) { Text("Add medicine", color = MedMitraColors.MintDark) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    schedules.filter { it.enabled }.take(3).forEach { medicine ->
                        FrostedCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Filled.Medication, tint = MedMitraColors.MintDark, size = 38)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(medicine.name, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                                    Text(medicine.dosage, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
                                }
                                Text(java.time.LocalTime.of(medicine.hour, medicine.minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), color = MedMitraColors.MintDark, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
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
                "SOS actions follow your saved consent settings. Fall monitoring and spoken reminders are opt-in.",
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
    if (id == "fall") {
        com.medmitra.app.fall.FallDetectionScreen(onBack = onBack)
        return
    }
    if (id == "voice") {
        com.medmitra.app.reminders.VoiceReminderScreen(onBack = onBack)
        return
    }
    val feature = features.firstOrNull { it.id == id }
    Column(
        Modifier.fillMaxSize().background(MedMitraColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        PageHeader(feature?.title ?: "MedMitra", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        when (id) {
            "history" -> HistoryContent()
            else -> Unit
        }
        Spacer(Modifier.height(22.dp))
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
    val context = LocalContext.current
    val database = remember(context) { MedMitraDatabase.get(context) }
    val events by database.history().observeAll().collectAsState(initial = emptyList())
    val doses by database.medicineDoseHistory().observeAll().collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("Recent activity", style = MaterialTheme.typography.headlineSmall, color = MedMitraColors.Text)
        if (events.isEmpty() && doses.isEmpty()) PlainCard(Modifier.fillMaxWidth()) {
            Text("No activity recorded yet", color = MedMitraColors.Text)
        }
        doses.forEach { dose ->
            PlainCard(Modifier.fillMaxWidth()) {
                Text("${dose.medicineName} · ${if (dose.status == "TAKEN") "Taken" else "Snoozed"}", style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text(dose.dosage, color = MedMitraColors.TextSecondary)
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(dose.actionAtMillis)), color = MedMitraColors.TextSecondary)
            }
        }
        events.forEach { event ->
            val title = when (event.eventType) {
                "MEDICINE_MARKED_TAKEN" -> "Medicine marked taken"
                "SOS_COUNTDOWN" -> "SOS started"
                "SOS_CANCELLED" -> "SOS cancelled"
                "SOS_TRIGGERED" -> "SOS actions opened"
                "SOS_COMPLETE" -> "SOS flow completed"
                "FALL_ALERT_PENDING" -> "Possible fall alert"
                "FALL_ALERT_CANCELLED" -> "Fall alert acknowledged"
                "FALL_ALERT_UNACKNOWLEDGED" -> "Fall alert unanswered"
                "FALL_DEMO_ALERT" -> "Safe fall demo started"
                "FALL_DEMO_ACKNOWLEDGED" -> "Safe fall demo acknowledged"
                "FALL_DEMO_CANCELLED" -> "Safe fall demo cancelled"
                "FALL_DEMO_COMPLETE" -> "Safe fall demo completed"
                else -> event.eventType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            }
            PlainCard(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.occurredAtMillis)), color = MedMitraColors.TextSecondary)
                if (event.notes.isNotBlank()) Text(event.notes, color = MedMitraColors.TextSecondary)
                if (event.eventType.startsWith("SOS") || event.eventType.startsWith("FALL")) {
                    Text("Location: ${event.locationStatus} · SMS: ${event.smsAttemptStatus} · Call: ${event.callAttemptStatus}", color = MedMitraColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(onOpen: (String) -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(com.medmitra.app.profile.ProfilePreferences.read(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) profile = com.medmitra.app.profile.ProfilePreferences.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().background(MedMitraColors.Background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Profile")
        PlainCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.medmitra.app.profile.ProfileAvatar(profile.photoUri, profile.fullName, Modifier.size(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.fullName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text)
                    Text(if (profile.age > 0) "Age ${profile.age}" else "Add your age", color = MedMitraColors.TextSecondary)
                }
            }
            Text("Preferred language · ${profile.language}", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
            if (profile.emergencyName.isNotBlank()) Text("Emergency contact · ${profile.emergencyName} · ${profile.emergencyPhone}", color = MedMitraColors.TextSecondary)
            TextButton(onClick = onEdit) { Text("Edit profile") }
        }
        Text("Your care settings", style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text)
        listOf("reminder" to "Medicine reminders", "voice" to "Voice reminder message", "contacts" to "Emergency setup", "fall" to "Fall safety").forEach { (route, label) ->
            PlainCard(Modifier.fillMaxWidth(), onClick = { onOpen(route) }) { InfoRow(Icons.Filled.Settings, label, "Manage local settings", tint = MedMitraColors.Sage) }
        }
        Text("Profile details are stored on this device.", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
    }
}

@Composable
private fun MedMitraSplash(onFinished: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    var navigationDispatched by remember { mutableStateOf(false) }
    val animationsEnabled = remember { runCatching { android.animation.ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true) }
    val transition = rememberInfiniteTransition(label = "brand-heartbeat")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animationsEnabled) 1.045f else 1f,
        animationSpec = infiniteRepeatable(tween(1_050, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "brand-pulse"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (revealed || !animationsEnabled) 1f else .76f,
        animationSpec = spring(dampingRatio = .68f, stiffness = 230f),
        label = "brand-logo-scale"
    )
    val logoAlpha by animateFloatAsState(if (revealed || !animationsEnabled) 1f else 0f, tween(360), label = "brand-logo-alpha")
    val titleAlpha by animateFloatAsState(if (revealed || !animationsEnabled) 1f else 0f, tween(430, delayMillis = 170), label = "brand-title-alpha")
    val taglineAlpha by animateFloatAsState(if (revealed || !animationsEnabled) 1f else 0f, tween(420, delayMillis = 300), label = "brand-tagline-alpha")
    val backdropAlpha by animateFloatAsState(if (revealed || !animationsEnabled) 1f else 0f, tween(520), label = "brand-backdrop-alpha")
    val ornament = rememberInfiniteTransition(label = "brand-ornaments")
    val ornamentOffset by ornament.animateFloat(-5f, 5f, infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ornament-float")
    LaunchedEffect(Unit) {
        revealed = true
        delay(if (animationsEnabled) 1_850L else 120L)
        if (!navigationDispatched) {
            navigationDispatched = true
            onFinished()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF062B24)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().alpha(backdropAlpha).background(
            Brush.verticalGradient(listOf(Color(0xFF062B24), Color(0xFF0B493B), Color(0xFF0E5A46)))
        ))
        Box(Modifier.align(Alignment.TopEnd).alpha(backdropAlpha).padding(top = 118.dp, end = 48.dp).offset(y = ornamentOffset.dp)
            .size(9.dp).background(Color(0x667EE4B5), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).alpha(backdropAlpha).padding(start = 55.dp, bottom = 142.dp).offset(y = (-ornamentOffset).dp)
            .size(6.dp).background(Color(0x667EE4B5), CircleShape))
        Box(Modifier.align(Alignment.Center).alpha(backdropAlpha).offset(x = 112.dp, y = (-104).dp).size(14.dp)
            .background(Color(0x337EE4B5), CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 30.dp)) {
            Box(
                Modifier.size(166.dp).scale(logoScale * pulse).alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(166.dp).border(1.dp, Color(0x337EE4B5), CircleShape))
                Box(Modifier.size(142.dp).shadow(24.dp, CircleShape).background(Color(0xFF174F40), CircleShape)
                    .border(1.dp, Color(0x667EE4B5), CircleShape))
                Box(Modifier.size(116.dp).background(Brush.radialGradient(listOf(Color(0xFFB5F2D1), Color(0xFF52BD8E))), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFF0A4939), modifier = Modifier.size(77.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 21.dp).size(38.dp)
                        .shadow(8.dp, CircleShape).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Medical cross", tint = Color(0xFF087657), modifier = Modifier.size(29.dp))
                    }
                }
            }
            Text(
                "MedMitra", modifier = Modifier.padding(top = 27.dp).alpha(titleAlpha),
                fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.7).sp, color = Color.White
            )
            Text(
                "Your Health, Our Care", modifier = Modifier.padding(top = 8.dp).alpha(taglineAlpha),
                style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD2EEE0), letterSpacing = .35.sp
            )
            Row(
                Modifier.padding(top = 34.dp).alpha(taglineAlpha),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    Box(Modifier.size(if (index == 1) 6.dp else 4.dp).background(Color(0xFF8CDBB5), CircleShape))
                }
            }
        }
    }
}
