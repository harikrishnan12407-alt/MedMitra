package com.medmitra.app.reminders

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.data.MedicineSchedule
import com.medmitra.app.ui.theme.MedMitraColors
import com.medmitra.app.ui.theme.MedMitraTheme

class MedicineAlarmActivity : ComponentActivity() {
    private var intentId by mutableLongStateOf(-1L)
    private var doseAtMillis: Long = 0L
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
        sendBroadcast(Intent(this, MedicineActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, id)
            .putExtra(ReminderScheduler.EXTRA_ACTION, action)
            .putExtra(ReminderScheduler.EXTRA_DOSE_AT_MILLIS, doseAtMillis))
        finish()
    }

    @Composable
    private fun medicineState(id: Long) = androidx.compose.runtime.produceState<MedicineSchedule?>(initialValue = null, id) {
        value = if (id < 0L) null else MedMitraDatabase.get(this@MedicineAlarmActivity).medicines().getById(id)
    }

    companion object { const val ACTION_DISMISS = "com.medmitra.app.action.DISMISS_MEDICINE_ALARM" }
}

@Composable
private fun MedicineAlarmContent(medicine: MedicineSchedule?, snoozeMinutes: Int, onTaken: () -> Unit, onSnooze: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF0FAF4)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Medication, null, Modifier.size(96.dp).clip(CircleShape).background(MedMitraColors.MintPale).padding(20.dp), tint = MedMitraColors.MintDark)
        Spacer(Modifier.height(25.dp))
        Text("MEDICINE TIME", color = MedMitraColors.MintDark, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Text(medicine?.name ?: "Medicine reminder", style = MaterialTheme.typography.headlineMedium, color = MedMitraColors.Text, textAlign = TextAlign.Center)
        Text(medicine?.dosage.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        medicine?.let { item ->
            Text("Scheduled · ${formatScheduledTime(item.hour, item.minute)}", color = MedMitraColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            if (item.instructions.isNotBlank()) Text(item.instructions, color = MedMitraColors.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(36.dp))
        Button(onClick = onTaken, enabled = medicine != null, modifier = Modifier.fillMaxWidth().height(62.dp)) { Text("Taken", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onSnooze, enabled = medicine != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(58.dp)) { Text("Snooze $snoozeMinutes minutes", fontSize = 17.sp) }
        Spacer(Modifier.height(20.dp))
        Text("This alarm stays active until you choose an action.", color = MedMitraColors.TextSecondary, textAlign = TextAlign.Center)
    }
}
