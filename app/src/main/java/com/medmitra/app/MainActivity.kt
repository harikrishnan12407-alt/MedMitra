package com.medmitra.app

import android.os.Bundle
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.domain.StartupDestination
import com.medmitra.app.domain.TodayPlanProgress
import com.medmitra.app.domain.calculateTodayPlanProgress
import com.medmitra.app.ui.components.FrostedCard
import com.medmitra.app.ui.components.IconBadge
import com.medmitra.app.ui.components.InfoRow
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.components.SectionHeading
import com.medmitra.app.ui.theme.MedMitraColors
import com.medmitra.app.ui.theme.MedMitraTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val startDestination = StartupDestination.initial(
            openReminders = intent.getBooleanExtra(com.medmitra.app.reminders.ReminderScheduler.EXTRA_OPEN_REMINDERS, false),
            openSos = intent.getBooleanExtra(com.medmitra.app.fall.FallMonitoringService.EXTRA_OPEN_SOS, false),
            openFall = intent.getBooleanExtra(com.medmitra.app.fall.FallMonitoringService.EXTRA_OPEN_FALL, false)
        )
        val showingSplash = startDestination == StartupDestination.SPLASH
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
    val context = LocalContext.current
    val startup = startDestination == StartupDestination.SPLASH
    var showSplash by rememberSaveable(startDestination) { mutableStateOf(startup) }
    var navigationStart by rememberSaveable(startDestination) {
        mutableStateOf(startDestination.takeUnless { it == StartupDestination.SPLASH })
    }
    val activity = context as? ComponentActivity
    SideEffect {
        val barColor = if (showSplash) android.graphics.Color.rgb(6, 43, 36) else android.graphics.Color.rgb(245, 249, 246)
        activity?.window?.let { window ->
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !showSplash
                isAppearanceLightNavigationBars = !showSplash
            }
        }
    }

    if (showSplash) {
        MedMitraSplash {
            if (showSplash) {
                navigationStart = StartupDestination.afterSplash(
                    showPermissionSetup = !com.medmitra.app.permissions.PermissionSetupPreferences.isComplete(context),
                    showOnboarding = com.medmitra.app.profile.ProfilePreferences.shouldShowOnboarding(context)
                )
                showSplash = false
            }
        }
    } else {
        MedMitraNavigation(startDestination = navigationStart ?: startDestination)
    }
}

@Composable
private fun MedMitraNavigation(startDestination: String) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: startDestination

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
            composable("home") { HomeScreen(onOpen = { nav.navigate("feature/$it") }) }
            composable("history") { HistoryScreen() }
            composable("profile") {
                ProfileScreen(
                    onOpen = { nav.navigate("feature/$it") },
                    onEdit = { nav.navigate("profile/setup") },
                    onPermissions = { nav.navigate("permissions") }
                )
            }
            composable("permissions") {
                com.medmitra.app.permissions.PermissionSetupScreen(
                    onDone = {
                        com.medmitra.app.permissions.PermissionSetupPreferences.markComplete(context)
                        if (nav.previousBackStackEntry?.destination?.route == "profile") nav.popBackStack()
                        else {
                            val next = if (com.medmitra.app.profile.ProfilePreferences.shouldShowOnboarding(context)) "profile/setup" else "home"
                            nav.navigate(next) { popUpTo("permissions") { inclusive = true } }
                        }
                    },
                    onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack() }) else null
                )
            }
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

private object HomeDashboardArrangement : Arrangement.Vertical {
    override val spacing = 7.dp

    override fun androidx.compose.ui.unit.Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray
    ) {
        val gapCount = (sizes.size - 1).coerceAtLeast(0)
        val baseGap = spacing.roundToPx()
        val extraSpace = (totalSize - sizes.sum() - baseGap * gapCount).coerceAtLeast(0)
        val extraPerGap = if (gapCount == 0) 0 else extraSpace / gapCount
        val extraRemainder = if (gapCount == 0) 0 else extraSpace % gapCount
        var position = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = position
            position += size
            if (index < gapCount) {
                position += baseGap + extraPerGap + (if (index < extraRemainder) 1 else 0)
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val profile by com.medmitra.app.profile.ProfilePreferences.observe(context)
        .collectAsState(initial = com.medmitra.app.profile.ProfilePreferences.read(context))
    val database = remember(context) { MedMitraDatabase.get(context) }
    val schedules by database.medicines().observeAll().collectAsState(initial = emptyList())
    val doseEvents by database.medicineDoseHistory().observeAll().collectAsState(initial = emptyList())
    var today by remember { mutableStateOf(LocalDate.now()) }
    var localZone by remember { mutableStateOf(ZoneId.systemDefault()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val refreshDate = {
        today = LocalDate.now()
        localZone = ZoneId.systemDefault()
    }
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refreshDate() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    androidx.compose.runtime.LaunchedEffect(today, localZone) {
        val nextDay = ZonedDateTime.now(localZone).toLocalDate().plusDays(1).atStartOfDay(localZone)
        val waitMillis = (nextDay.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1L)
        delay(waitMillis)
        refreshDate()
    }
    val todayProgress = remember(schedules, doseEvents, today, localZone) {
        calculateTodayPlanProgress(schedules, doseEvents, today, localZone)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF0FAF4), MedMitraColors.Background, Color(0xFFF8FBF9))))) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 8.dp),
        verticalArrangement = HomeDashboardArrangement
      ) {
        item {
            HomeWelcomeCard(profile)
        }

        item {
            SectionHeading("Today’s medicines", "See schedule") { onOpen("reminder") }
            Spacer(Modifier.height(3.dp))
            TodayPlanCard(todayProgress, onAddMedicine = { onOpen("reminder") })
        }

        item {
            SosPreview(onClick = { onOpen("sos") })
        }

        item {
            SectionHeading("Your shortcuts")
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.chunked(2).forEach { rowFeatures ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowFeatures.forEach { feature ->
                            ShortcutCard(feature, Modifier.weight(1f)) { onOpen(feature.id) }
                        }
                        if (rowFeatures.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "SOS actions follow your saved consent settings. Fall monitoring and spoken reminders are opt-in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MedMitraColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
      }
    }
}

@Composable
private fun HomeWelcomeCard(profile: com.medmitra.app.profile.UserProfile) {
    val firstName = profile.fullName.trim().substringBefore(' ').ifBlank { "friend" }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(360)) + slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 12 }
    ) {
        Box(
            Modifier.fillMaxWidth()
                .shadow(5.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x24329A79), spotColor = Color(0x24329A79))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(MedMitraColors.Emerald, MedMitraColors.EmeraldMid, MedMitraColors.MintDark)))
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(20.dp))
        ) {
            Box(
                Modifier.matchParentSize()
            ) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-46).dp).size(138.dp)
                        .background(Color.White.copy(alpha = .045f), CircleShape)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "YOUR DAILY CARE",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = MedMitraColors.MintGlow,
                        letterSpacing = .9.sp
                    )
                    Text(
                        if (profile.fullName.isBlank()) "Welcome to MedMitra" else "Welcome back, $firstName",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 20.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Your health, at a glance",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 15.sp),
                        color = Color(0xFFD7F0E2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(48.dp).border(1.dp, Color.White.copy(alpha = .68f), CircleShape).padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    com.medmitra.app.profile.ProfileAvatar(profile.photoUri, profile.fullName, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun TodayPlanCard(progress: TodayPlanProgress, onAddMedicine: () -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "todayPlanProgress"
    )
    val animatedPercentage by animateIntAsState(
        targetValue = progress.percentage,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "todayPlanPercentage"
    )
    val empty = progress.totalDoses == 0
    val ringSize = 88.dp
    Row(
        Modifier.fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x18329A79), spotColor = Color(0x18329A79))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEAF7EF), Color(0xFFF7FCF8))))
            .border(1.dp, Color(0xFFD8EBDF), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(ringSize).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f)
            },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val strokeWidth = 7.dp.toPx()
                drawArc(
                    color = Color(0xFFDCEDE2), startAngle = -90f, sweepAngle = 360f,
                    useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                if (animatedProgress > 0f) drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF64C69B), MedMitraColors.MintDark, Color(0xFF64C69B)), center = center),
                    startAngle = -90f, sweepAngle = 360f * animatedProgress,
                    useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$animatedPercentage%", style = MaterialTheme.typography.titleLarge, color = MedMitraColors.MintDark, fontWeight = FontWeight.Bold)
                Text("${progress.completedDoses}/${progress.totalDoses} doses", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("TODAY’S PLAN", style = MaterialTheme.typography.labelLarge, color = MedMitraColors.MintDark, letterSpacing = .9.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                if (empty) "No medicines scheduled today." else "${progress.completedDoses} of ${progress.totalDoses} doses completed",
                style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text
            )
            if (empty) {
                TextButton(onClick = onAddMedicine, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add medicine", color = MedMitraColors.MintDark)
                }
            } else {
                Spacer(Modifier.height(2.dp))
                Text("Keep up your healthy routine", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SosPreview(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x12A54F48), spotColor = Color(0x12A54F48))
            .clip(RoundedCornerShape(18.dp))
            .background(MedMitraColors.CoralPale)
            .border(1.dp, Color(0xFFF0D9D4), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Filled.Sos, tint = MedMitraColors.Coral, size = 34)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("Need help?", style = MaterialTheme.typography.titleSmall, color = MedMitraColors.Text)
            Text("Start a cancelable SOS flow", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, fontSize = 12.sp)
        }
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Open", color = MedMitraColors.Coral, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ShortcutCard(feature: Feature, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PlainCard(modifier.height(72.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(feature.icon, tint = feature.tint, size = 29)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.shortTitle, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 16.sp)
                Text(feature.subtitle, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, lineHeight = 14.sp)
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
private fun ProfileScreen(onOpen: (String) -> Unit, onEdit: () -> Unit, onPermissions: () -> Unit) {
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
        PlainCard(Modifier.fillMaxWidth(), onClick = onPermissions) {
            InfoRow(Icons.Filled.Security, "Permission setup", "Review Android access and current status", tint = MedMitraColors.MintDark)
        }
        Text("Profile details are stored on this device.", style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
    }
}

@Composable
private fun MedMitraSplash(onFinished: () -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    var navigationDispatched by rememberSaveable { mutableStateOf(false) }
    var splashStartedAt by rememberSaveable { mutableLongStateOf(0L) }
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
        val startedAt = splashStartedAt.takeIf { it > 0L } ?: SystemClock.elapsedRealtime().also { splashStartedAt = it }
        revealed = true
        val minimumDuration = if (animationsEnabled) 1_850L else 120L
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val remaining = (minimumDuration - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        if (!navigationDispatched) {
            navigationDispatched = true
            onFinished()
        }
    }

    Box(
        Modifier.fillMaxSize().background(MedMitraColors.Emerald),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().alpha(backdropAlpha).background(
            Brush.verticalGradient(listOf(MedMitraColors.Emerald, MedMitraColors.EmeraldMid, Color(0xFF0E5A46)))
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
                Box(Modifier.size(116.dp).background(Brush.radialGradient(listOf(Color(0xFFB5F2D1), MedMitraColors.MintGlow)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MedMitraColors.EmeraldMid, modifier = Modifier.size(77.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 21.dp).size(38.dp)
                        .shadow(8.dp, CircleShape).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Medical cross", tint = MedMitraColors.MintDark, modifier = Modifier.size(29.dp))
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
                    Box(Modifier.size(if (index == 1) 6.dp else 4.dp).background(MedMitraColors.MintGlow, CircleShape))
                }
            }
        }
    }
}
