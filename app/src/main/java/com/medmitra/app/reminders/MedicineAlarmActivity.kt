package com.medmitra.app.reminders

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.data.MedicineSchedule
import com.medmitra.app.ui.theme.MedMitraTheme

class MedicineAlarmActivity : ComponentActivity() {
    private var intentId by mutableLongStateOf(-1L)
    private var doseAtMillis: Long = 0L
    private var actionSubmitted = false
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1L) == intentId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = if (darkMode) android.graphics.Color.rgb(7, 30, 26) else android.graphics.Color.rgb(247, 252, 248)
        window.navigationBarColor = if (darkMode) android.graphics.Color.rgb(11, 48, 39) else android.graphics.Color.rgb(230, 245, 236)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
        updateAlarmIntent(intent)
        setContent {
            MedMitraTheme {
                val medicineId = intentId
                val medicine by medicineState(medicineId)
                BackHandler(enabled = true) { }
                MedicineAlarmContent(
                    medicine = medicine,
                    snoozeMinutes = 5,
                    onTaken = { finishWithAction(medicineId, ReminderScheduler.ACTION_MARK_TAKEN) },
                    onSnooze = { finishWithAction(medicineId, ReminderScheduler.ACTION_SNOOZE) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        actionSubmitted = false
        updateAlarmIntent(intent)
    }

    private fun updateAlarmIntent(intent: Intent) {
        intentId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1L)
        doseAtMillis = intent.getLongExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, 0L)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_DISMISS)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dismissReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(dismissReceiver, filter)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onStop()
    }

    private fun finishWithAction(id: Long, action: String) {
        if (actionSubmitted) return
        actionSubmitted = true
        sendBroadcast(Intent(this, MedicineActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, id)
            .putExtra(ReminderScheduler.EXTRA_ACTION, action)
            .putExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, doseAtMillis))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Composable
    private fun medicineState(id: Long) = androidx.compose.runtime.produceState<MedicineSchedule?>(initialValue = null, id) {
        value = if (id < 0L) null else MedMitraDatabase.get(this@MedicineAlarmActivity).medicines().getById(id)
    }

    companion object { const val ACTION_DISMISS = "com.medmitra.app.action.DISMISS_MEDICINE_ALARM" }
}

@Composable
private fun MedicineAlarmContent(medicine: MedicineSchedule?, snoozeMinutes: Int, onTaken: () -> Unit, onSnooze: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) AlarmPalette(
        backgroundTop = Color(0xFF071E1A), backgroundBottom = Color(0xFF0B3027),
        glow = Color(0xFF1B7556), panel = Color(0xFF123B30), panelBorder = Color(0xFF285C4B),
        text = Color(0xFFF1FBF5), secondary = Color(0xFFB1CEC0), accent = Color(0xFF8FE0B5),
        accentInk = Color(0xFF073D2D), ring = Color(0xFF78D6A5)
    ) else AlarmPalette(
        backgroundTop = Color(0xFFF7FCF8), backgroundBottom = Color(0xFFE6F5EC),
        glow = Color(0xFFB2E8CA), panel = Color(0xFFFFFFFF), panelBorder = Color(0xFFD8EADF),
        text = Color(0xFF14372B), secondary = Color(0xFF597568), accent = Color(0xFF176B50),
        accentInk = Color.White, ring = Color(0xFF4CBA83)
    )
    var entrance by remember { mutableStateOf(false) }
    val entranceScale by animateFloatAsState(if (entrance) 1f else .97f, tween(420, easing = FastOutSlowInEasing), label = "alarm-entrance")
    val infinite = rememberInfiniteTransition(label = "alarm-heartbeat")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = if (androidAnimationsEnabled()) 1.035f else 1f,
        animationSpec = infiniteRepeatable(tween(1_350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "medicine-pulse"
    )
    val haloScale by infinite.animateFloat(
        initialValue = .91f, targetValue = if (androidAnimationsEnabled()) 1.12f else .91f,
        animationSpec = infiniteRepeatable(tween(2_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "medicine-halo"
    )
    LaunchedEffect(medicine?.id) {
        entrance = false
        kotlinx.coroutines.delay(35)
        entrance = true
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
    ) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 8.dp).size(186.dp)
            .background(palette.glow.copy(alpha = if (dark) .12f else .28f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 2.dp, bottom = 30.dp).size(228.dp)
            .background(palette.glow.copy(alpha = if (dark) .10f else .22f), CircleShape))

        AnimatedVisibility(
            visible = entrance,
            enter = fadeIn(tween(420)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 22 },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier.fillMaxSize().scale(entranceScale).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = palette.accent, modifier = Modifier.size(17.dp))
                    Text("  MEDICINE REMINDER", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                }

                Box(Modifier.padding(top = 1.dp).size(224.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(205.dp).scale(haloScale).border(1.dp, palette.ring.copy(alpha = .24f), CircleShape))
                    Box(Modifier.size(174.dp).border(1.dp, palette.ring.copy(alpha = .27f), CircleShape))
                    Box(
                        Modifier.size(136.dp).shadow(20.dp, CircleShape).background(
                            Brush.linearGradient(listOf(if (dark) Color(0xFF205B45) else Color.White, if (dark) Color(0xFF123F32) else Color(0xFFDDF4E6))),
                            CircleShape
                        ).border(1.dp, palette.ring.copy(alpha = .32f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Medication, contentDescription = "Medicine", tint = palette.accent, modifier = Modifier.size(65.dp).scale(pulse))
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 26.dp).size(42.dp)
                            .shadow(8.dp, CircleShape).background(palette.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
                    }
                }

                Text("It’s time for", color = palette.secondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 1.dp))
                Text(
                    medicine?.name ?: "Medicine reminder", color = palette.text,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 31.sp, lineHeight = 37.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    shape = RoundedCornerShape(24.dp), color = palette.panel,
                    shadowElevation = if (dark) 0.dp else 5.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.panelBorder)
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AlarmDetail(label = "DOSAGE", value = medicine?.dosage?.ifBlank { "As prescribed" } ?: "", palette = palette, modifier = Modifier.weight(1f))
                            Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(38.dp).background(palette.panelBorder))
                            AlarmDetail(
                                label = "SCHEDULED",
                                value = medicine?.let { formatScheduledTime(it.hour, it.minute) } ?: "",
                                palette = palette, modifier = Modifier.weight(1f)
                            )
                        }
                        if (!medicine?.instructions.isNullOrBlank()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(palette.panelBorder))
                            Text(medicine?.instructions.orEmpty(), color = palette.secondary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                AlarmActionButton(
                    title = "Taken", supporting = "I’ve taken this dose", icon = Icons.Filled.Check,
                    onClick = onTaken, enabled = medicine != null, primary = true, palette = palette,
                    modifier = Modifier.fillMaxWidth().padding(top = 23.dp)
                )
                AlarmActionButton(
                    title = "Snooze $snoozeMinutes min", supporting = "Remind me again in $snoozeMinutes minutes", icon = Icons.Filled.Snooze,
                    onClick = onSnooze, enabled = medicine != null, primary = false, palette = palette,
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp)
                )
                Text(
                    "Your reminder will stay active until you choose an action.",
                    color = palette.secondary, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 17.dp, bottom = 8.dp)
                )
            }
        }
    }
}

private data class AlarmPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glow: Color,
    val panel: Color,
    val panelBorder: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val accentInk: Color,
    val ring: Color
)

@Composable
private fun AlarmDetail(label: String, value: String, palette: AlarmPalette, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = palette.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(value, color = palette.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun AlarmActionButton(
    title: String,
    supporting: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean,
    palette: AlarmPalette,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, tween(110), label = "alarm-button-press")
    val shape = RoundedCornerShape(22.dp)
    val fill = if (primary) Brush.linearGradient(listOf(if (palette.accentInk == Color.White) Color(0xFF21845D) else Color(0xFF9DE9BD), palette.accent)) else Brush.linearGradient(listOf(palette.panel, palette.panel))
    Surface(
        modifier = modifier.scale(scale).height(72.dp)
            .then(if (!primary) Modifier.border(1.dp, palette.panelBorder, shape) else Modifier),
        shape = shape,
        color = Color.Transparent,
        enabled = enabled,
        onClick = onClick,
        interactionSource = interaction,
        tonalElevation = 0.dp,
        shadowElevation = if (primary) 8.dp else 1.dp
    ) {
        Row(
            Modifier.fillMaxSize().background(fill).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) palette.accentInk else palette.accent, modifier = Modifier.size(23.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = if (primary) palette.accentInk else palette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(supporting, color = if (primary) palette.accentInk.copy(alpha = .78f) else palette.secondary, fontSize = 11.sp)
            }
        }
    }
}

private fun androidAnimationsEnabled(): Boolean =
    runCatching { android.animation.ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
