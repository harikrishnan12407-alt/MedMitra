package com.medmitra.app.reminders

import android.app.Service
import android.content.Intent
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.medmitra.app.data.MedicineSchedule
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.ui.localization.AppText
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps an audible alarm alive while the screen is locked. Individual notification actions stop each dose. */
class MedicineAlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val active = linkedMapOf<Long, MedicineSchedule>()
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1L) ?: -1L
        if (intent == null) {
            val savedIds = savedActiveIds()
            if (savedIds.isEmpty()) { stopSelf(startId); return START_NOT_STICKY }
            startForeground(SUMMARY_ID, ReminderNotification.foregroundSummary(this, MedicineSchedule(name = "Medicine", dosage = "", hour = 0, minute = 0)))
            savedIds.forEach { savedId -> scope.launch {
                val medicine = MedMitraDatabase.get(this@MedicineAlarmService).medicines().getById(savedId)
                handler.post { if (medicine != null && medicine.enabled) activate(medicine) else stopDose(savedId) }
            } }
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_START -> if (id >= 0L) {
                val medicine = MedicineSchedule(
                    id = id,
                    name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
                    dosage = intent.getStringExtra(EXTRA_DOSAGE).orEmpty(),
                    hour = intent.getIntExtra(EXTRA_HOUR, 9),
                    minute = intent.getIntExtra(EXTRA_MINUTE, 0),
                    instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS).orEmpty()
                )
                if (medicine.name.isNotBlank()) activate(medicine) else stopSelf(startId)
            }
            ACTION_STOP -> if (id >= 0L) {
                if (active.isNotEmpty()) {
                    startForeground(SUMMARY_ID, ReminderNotification.foregroundSummary(this, active.values.first()))
                    handler.post { stopDose(id) }
                } else {
                    val remaining = savedActiveIds().filter { it != id }
                    saveActiveIdList(remaining)
                    if (remaining.isEmpty()) stopSelf(startId)
                    else {
                        startForeground(SUMMARY_ID, ReminderNotification.foregroundSummary(this, MedicineSchedule(name = "Medicine", dosage = "", hour = 0, minute = 0)))
                        scope.launch {
                            val db = MedMitraDatabase.get(this@MedicineAlarmService)
                            val restored = remaining.mapNotNull { db.medicines().getById(it) }.filter { it.enabled }
                            saveActiveIdList(restored.map { it.id })
                            if (restored.isEmpty()) handler.post {
                                removeForegroundNotification()
                                stopSelf()
                            } else restored.forEach { medicine -> handler.post { activate(medicine) } }
                        }
                    }
                }
            }
        }
        return if (active.isNotEmpty()) START_STICKY else START_NOT_STICKY
    }

    private fun activate(medicine: MedicineSchedule) {
        active[medicine.id] = medicine
        saveActiveIds()
        if (active.size == 1) {
            startForeground(SUMMARY_ID, ReminderNotification.foregroundSummary(this, medicine))
            acquireWakeLock()
            startAlarmSound()
        } else {
            getSystemService(android.app.NotificationManager::class.java).notify(SUMMARY_ID, ReminderNotification.foregroundSummary(this, medicine))
        }
        speakFor(medicine)
    }

    private fun stopDose(id: Long) {
        active.remove(id)
        saveActiveIds()
        VoiceReminderSpeaker.stop("alarm")
        if (active.isEmpty()) {
            handler.removeCallbacks(repeatSpeech)
            stopAlarmSound()
            releaseWakeLock()
            removeForegroundNotification()
            stopSelf()
        } else {
            active.values.firstOrNull()?.let(::speakFor)
            getSystemService(android.app.NotificationManager::class.java).notify(SUMMARY_ID, ReminderNotification.foregroundSummary(this, active.values.first()))
        }
    }

    private fun acquireWakeLock() {
        val manager = getSystemService(PowerManager::class.java) ?: return
        if (wakeLock == null) wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MedMitra:MedicineAlarm").apply { setReferenceCounted(false) }
        if (wakeLock?.isHeld != true) runCatching { wakeLock?.acquire() }
    }

    private fun releaseWakeLock() { runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }; wakeLock = null }

    private fun startAlarmSound() {
        if (mediaPlayer?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@MedicineAlarmService, uri)
                isLooping = true
                setOnPreparedListener { player -> player.start() }
                setOnErrorListener { player, _, _ -> player.release(); if (mediaPlayer === player) mediaPlayer = null; true }
                prepareAsync()
                mediaPlayer = this
            }
        }.onFailure { mediaPlayer = null }
    }

    private fun stopAlarmSound() { runCatching { mediaPlayer?.stop() }; runCatching { mediaPlayer?.release() }; mediaPlayer = null }

    @Suppress("DEPRECATION")
    private fun removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    private fun speakFor(medicine: MedicineSchedule) {
        if (!VoiceReminderPreferences.enabled(this)) return
        val text = buildString {
            val savedMessage = VoiceReminderPreferences.message(this@MedicineAlarmService)
            append(if (savedMessage == "It's time to take your medicine.") AppText.translate(this@MedicineAlarmService, savedMessage) else savedMessage)
            append(" ${medicine.name}, ${medicine.dosage}.")
            if (medicine.instructions.isNotBlank()) append(" ${medicine.instructions}")
        }
        VoiceReminderSpeaker.speak(this, text, "alarm") { }
        handler.removeCallbacks(repeatSpeech)
        handler.postDelayed(repeatSpeech, SPEECH_REPEAT_MILLIS)
    }

    private val repeatSpeech = object : Runnable {
        override fun run() {
            if (active.isEmpty()) return
            active.values.firstOrNull()?.let(::speakFor)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopAlarmSound()
        VoiceReminderSpeaker.stop("alarm")
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun savedActiveIds(): List<Long> = pendingActiveIds(this)

    private fun saveActiveIds() {
        saveActiveIdList(active.keys.toList())
    }

    private fun saveActiveIdList(ids: List<Long>) {
        getSharedPreferences(ACTIVE_STATE_FILE, MODE_PRIVATE).edit()
            .putStringSet(ACTIVE_IDS_KEY, ids.map { it.toString() }.toSet()).apply()
    }

    companion object {
        const val ACTION_START = "com.medmitra.app.action.START_MEDICINE_ALARM"
        const val ACTION_STOP = "com.medmitra.app.action.STOP_MEDICINE_ALARM"
        const val EXTRA_NAME = "alarm_medicine_name"
        const val EXTRA_DOSAGE = "alarm_medicine_dosage"
        const val EXTRA_HOUR = "alarm_medicine_hour"
        const val EXTRA_MINUTE = "alarm_medicine_minute"
        const val EXTRA_INSTRUCTIONS = "alarm_medicine_instructions"
        private const val SUMMARY_ID = 818_100
        private const val SPEECH_REPEAT_MILLIS = 60_000L
        private const val ACTIVE_STATE_FILE = "active_medicine_alarm"
        private const val ACTIVE_IDS_KEY = "active_ids"

        fun pendingActiveIds(context: android.content.Context): List<Long> = context.getSharedPreferences(ACTIVE_STATE_FILE, android.content.Context.MODE_PRIVATE)
            .getStringSet(ACTIVE_IDS_KEY, emptySet()).orEmpty().mapNotNull(String::toLongOrNull)
    }
}

internal fun stopMedicineAlarm(context: Context, id: Long) {
    runCatching {
        ContextCompat.startForegroundService(context, Intent(context, MedicineAlarmService::class.java)
            .setAction(MedicineAlarmService.ACTION_STOP)
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, id))
    }
}
