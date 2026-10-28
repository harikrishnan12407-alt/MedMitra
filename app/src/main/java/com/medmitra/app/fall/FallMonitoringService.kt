package com.medmitra.app.fall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medmitra.app.MainActivity
import com.medmitra.app.data.EmergencyHistoryEvent
import com.medmitra.app.data.MedMitraDatabase
import com.medmitra.app.domain.FallDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.sqrt

class FallMonitoringService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val detector = FallDetector()
    private var sensorManager: SensorManager? = null
    private var alertPending = false
    private var restoringAlert = false
    private var alertRestoreStarted = false
    private var alertDeadline = 0L
    private var alertGeneration = 0
    private var activeEventId: Long? = null
    private val alertActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_ACKNOWLEDGED) acknowledgePossibleFall()
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannels()
        ContextCompat.registerReceiver(
            this,
            alertActionReceiver,
            android.content.IntentFilter(ACTION_ACKNOWLEDGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sendMonitoringState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACKNOWLEDGE -> {
                if (sensorManager == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                acknowledgePossibleFall()
                return START_STICKY
            }
        }

        try {
            val notification = monitoringNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (sensorManager == null) {
            val manager = getSystemService(SensorManager::class.java)
            val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (manager == null || accelerometer == null || !manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)) {
                notifySafely(
                    NOTIFICATION_ID,
                    statusNotification("Fall monitoring unavailable", "No accelerometer is available on this device.")
                )
                stopSelf()
                return START_NOT_STICKY
            }
            sensorManager = manager
        }
        restorePendingAlert()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values.getOrNull(0) ?: return
        val y = event.values.getOrNull(1) ?: return
        val z = event.values.getOrNull(2) ?: return
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!alertPending && !restoringAlert && detector.addSample(event.timestamp / 1_000_000L, magnitude)) {
            beginPossibleFallAlert()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(alertTicker)
        runCatching { unregisterReceiver(alertActionReceiver) }
        alertGeneration++
        if (alertPending) {
            alertPending = false
            updateActiveEvent("FALL_ALERT_CANCELLED", "Fall monitoring stopped during the alert")
            NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        }
        sensorManager?.unregisterListener(this)
        sensorManager = null
        running = false
        sendMonitoringState()
        super.onDestroy()
    }

    private fun beginPossibleFallAlert() {
        if (alertPending) return
        alertPending = true
        alertGeneration++
        val generation = alertGeneration
        alertDeadline = SystemClock.elapsedRealtime() + ALERT_WINDOW_MILLIS
        activeEventId = null
        ioScope.launch {
            val eventId = MedMitraDatabase.get(this@FallMonitoringService).history().insert(
                EmergencyHistoryEvent(
                    occurredAtMillis = System.currentTimeMillis(),
                    eventType = "FALL_ALERT_PENDING",
                    notes = "Possible fall detected; no emergency action has been started"
                )
            )
            withContext(Dispatchers.Main) {
                if (generation == alertGeneration) activeEventId = eventId
                else ioScope.launch {
                    MedMitraDatabase.get(this@FallMonitoringService).history()
                        .updateEvent(eventId, "FALL_ALERT_CANCELLED", "Alert acknowledged before history save completed")
                }
            }
        }
        handler.removeCallbacks(alertTicker)
        handler.post(alertTicker)
    }

    private val alertTicker = object : Runnable {
        override fun run() {
            if (!alertPending) return
            val remaining = ceil((alertDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1_000.0).toInt()
            if (remaining <= 0) {
                alertPending = false
                updateActiveEvent("FALL_ALERT_UNACKNOWLEDGED", "No response to fall alert; no call or message was sent")
                notifySafely(
                    ALERT_NOTIFICATION_ID,
                    expiredAlertNotification()
                )
                return
            }
            notifySafely(
                ALERT_NOTIFICATION_ID,
                possibleFallNotification(remaining)
            )
            handler.postDelayed(this, 1_000L)
        }
    }

    private fun acknowledgePossibleFall() {
        if (!alertPending) {
            detector.reset()
            NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
            ioScope.launch {
                MedMitraDatabase.get(this@FallMonitoringService).history().resolveLatestPendingFallAlert(
                    "FALL_ALERT_CANCELLED",
                    "User acknowledged the possible fall alert"
                )
            }
            return
        }
        alertPending = false
        alertGeneration++
        handler.removeCallbacks(alertTicker)
        detector.reset()
        updateActiveEvent("FALL_ALERT_CANCELLED", "User acknowledged the possible fall alert")
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
    }

    private fun restorePendingAlert() {
        if (alertRestoreStarted) return
        alertRestoreStarted = true
        restoringAlert = true
        ioScope.launch {
            val event = MedMitraDatabase.get(this@FallMonitoringService).history().latestPendingFallAlert()
            withContext(Dispatchers.Main) {
                restoringAlert = false
                if (event != null && sensorManager != null && !alertPending) {
                    activeEventId = event.id
                    alertGeneration++
                    alertPending = true
                    val elapsedSinceAlert = (System.currentTimeMillis() - event.occurredAtMillis).coerceAtLeast(0L)
                    val remaining = (ALERT_WINDOW_MILLIS - elapsedSinceAlert).coerceAtLeast(0L)
                    alertDeadline = SystemClock.elapsedRealtime() + remaining
                    handler.removeCallbacks(alertTicker)
                    handler.post(alertTicker)
                }
            }
        }
    }

    private fun updateActiveEvent(type: String, notes: String) {
        val id = activeEventId
        activeEventId = null
        if (id != null) ioScope.launch {
            MedMitraDatabase.get(this@FallMonitoringService).history().updateEvent(id, type, notes)
        }
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {
            // If alerts can no longer be shown, stop sensor monitoring rather than hide its state.
            stopSelf()
        }
    }

    private fun monitoringNotification(): Notification = notificationBuilder(MONITOR_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Fall monitoring is active")
        .setContentText("Motion is monitored on this device. Tap to manage.")
        .setCategory(Notification.CATEGORY_SERVICE)
        .setOngoing(true)
        .setContentIntent(openFallPendingIntent())
        .addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Stop monitoring",
                actionPendingIntent(ACTION_STOP, REQUEST_STOP)
            ).build()
        )
        .build()

    private fun possibleFallNotification(seconds: Int): Notification = notificationBuilder(ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Possible fall detected")
        .setContentText("Tap “I’m okay” within $seconds seconds to cancel. No call or message is sent automatically.")
        .setCategory(Notification.CATEGORY_ALARM)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openFallPendingIntent())
        .addAction(
            Notification.Action.Builder(
                android.R.drawable.checkbox_on_background,
                "I'm okay",
                actionPendingIntent(ACTION_ACKNOWLEDGE, REQUEST_ACKNOWLEDGE)
            ).build()
        )
        .addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_menu_call,
                "Open SOS",
                openSosPendingIntent()
            ).build()
        )
        .build()

    private fun expiredAlertNotification(): Notification = notificationBuilder(ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Fall alert needs your attention")
        .setContentText("No response was received. No call or message was sent; open SOS if you need help.")
        .setCategory(Notification.CATEGORY_ALARM)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .setContentIntent(openSosPendingIntent())
        .build()

    private fun statusNotification(title: String, message: String): Notification = notificationBuilder(MONITOR_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .build()

    private fun notificationBuilder(channelId: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channelId)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        this,
        requestCode,
        Intent(this, FallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openFallPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_FALL,
        Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_FALL, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openSosPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_SOS,
        Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_SOS, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(MONITOR_CHANNEL_ID, "Fall monitoring", NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Fall alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun sendMonitoringState() {
        sendBroadcast(
            Intent(ACTION_MONITORING_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_IS_RUNNING, running)
        )
    }

    companion object {
        const val EXTRA_OPEN_FALL = "open_fall_safety"
        const val EXTRA_OPEN_SOS = "open_emergency_sos"
        const val ACTION_MONITORING_STATE = "com.medmitra.app.action.FALL_MONITORING_STATE"
        const val EXTRA_IS_RUNNING = "fall_monitoring_running"
        const val ACTION_STOP = "com.medmitra.app.action.STOP_FALL_MONITORING"
        const val ACTION_ACKNOWLEDGE = "com.medmitra.app.action.ACKNOWLEDGE_FALL_ALERT"
        const val ACTION_ACKNOWLEDGED = "com.medmitra.app.action.FALL_ALERT_ACKNOWLEDGED"
        private const val MONITOR_CHANNEL_ID = "fall_monitoring"
        private const val ALERT_CHANNEL_ID = "fall_alerts"
        private const val NOTIFICATION_ID = 7_001
        private const val ALERT_NOTIFICATION_ID = 7_002
        private const val ALERT_WINDOW_MILLIS = 30_000L
        private const val REQUEST_STOP = 7_101
        private const val REQUEST_ACKNOWLEDGE = 7_102
        private const val REQUEST_OPEN_FALL = 7_103
        private const val REQUEST_OPEN_SOS = 7_104

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: android.content.Context) {
            val intent = Intent(context, FallMonitoringService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, FallMonitoringService::class.java))
        }

        private const val ACTION_START = "com.medmitra.app.action.START_FALL_MONITORING"
    }
}
