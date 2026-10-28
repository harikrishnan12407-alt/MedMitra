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
import com.medmitra.app.domain.FallDetectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.pow

class FallMonitoringService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: FallDetector
    private var sensorManager: SensorManager? = null
    private var gyroMagnitude = 0f
    private var integratedRotationDegrees = 0f
    private var lastGyroTimestampNanos = 0L
    private var orientationDegrees = 0f
    private var alertPending = false
    private var restoringAlert = false
    private var alertRestoreStarted = false
    private var alertDeadline = 0L
    private var alertWindowMillis = 30_000L
    private var alertGeneration = 0
    private var activeEventId: Long? = null
    private var demoAlert = false
    private var demoOnlyService = false
    private val alertActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_ACKNOWLEDGED) acknowledgePossibleFall()
        }
    }

    override fun onCreate() {
        super.onCreate()
        detector = FallDetector(FallDetectionConfig(sensitivity = FallPreferences.sensitivity(this)))
        running = false
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
        val isDemo = intent?.action == ACTION_DEMO
        if (intent?.action == ACTION_RECONFIGURE) {
            detector = FallDetector(FallDetectionConfig(sensitivity = FallPreferences.sensitivity(this)))
            return START_STICKY
        }
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
        } catch (_: IllegalArgumentException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val sensorManagerWasActive = sensorManager != null
        if (sensorManager == null) {
            val manager = getSystemService(SensorManager::class.java)
            val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (!isDemo && (manager == null || accelerometer == null || !manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL))) {
                notifySafely(
                    NOTIFICATION_ID,
                    statusNotification("Fall monitoring unavailable", "No accelerometer is available on this device.")
                )
                stopSelf()
                return START_NOT_STICKY
            }
            if (manager != null) {
                sensorManager = manager
                if (!isDemo) {
                    manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
                    manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
                }
            }
        }
        if (isDemo) {
            demoOnlyService = !sensorManagerWasActive
            beginPossibleFallAlert(isDemo = true)
            return START_NOT_STICKY
        }
        running = true
        sendMonitoringState()
        restorePendingAlert()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values.getOrNull(0) ?: return
            val y = event.values.getOrNull(1) ?: return
            val z = event.values.getOrNull(2) ?: return
            gyroMagnitude = sqrt(x * x + y * y + z * z)
            if (lastGyroTimestampNanos > 0L) {
                val deltaSeconds = (event.timestamp - lastGyroTimestampNanos).coerceAtLeast(0L) / 1_000_000_000f
                integratedRotationDegrees += gyroMagnitude * deltaSeconds * 57.29578f
                orientationDegrees = integratedRotationDegrees
            }
            lastGyroTimestampNanos = event.timestamp
            return
        }
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotation = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            SensorManager.getOrientation(rotation, orientation)
            orientationDegrees = sqrt(orientation[0].pow(2) + orientation[1].pow(2) + orientation[2].pow(2)) * 57.29578f
            return
        }
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values.getOrNull(0) ?: return
        val y = event.values.getOrNull(1) ?: return
        val z = event.values.getOrNull(2) ?: return
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!alertPending && !restoringAlert && detector.addSample(event.timestamp / 1_000_000L, magnitude, gyroMagnitude, orientationDegrees)) {
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
            updateActiveEvent(if (demoAlert) "FALL_DEMO_CANCELLED" else "FALL_ALERT_CANCELLED", "Fall monitoring stopped during the alert")
            NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        }
        sensorManager?.unregisterListener(this)
        sensorManager = null
        running = false
        sendMonitoringState()
        super.onDestroy()
    }

    private fun beginPossibleFallAlert(isDemo: Boolean = false) {
        if (alertPending) return
        alertPending = true
        demoAlert = isDemo
        alertWindowMillis = FallPreferences.countdownSeconds(this) * 1_000L
        alertGeneration++
        val generation = alertGeneration
        alertDeadline = SystemClock.elapsedRealtime() + alertWindowMillis
        activeEventId = null
        ioScope.launch {
            val eventId = MedMitraDatabase.get(this@FallMonitoringService).history().insert(
                EmergencyHistoryEvent(
                    occurredAtMillis = System.currentTimeMillis(),
                    eventType = if (isDemo) "FALL_DEMO_ALERT" else "FALL_ALERT_PENDING",
                    notes = if (isDemo) "Safe demo alert; no emergency action was started" else "Possible fall detected; no emergency action has been started"
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
                updateActiveEvent(if (demoAlert) "FALL_DEMO_COMPLETE" else "FALL_ALERT_UNACKNOWLEDGED", if (demoAlert) "Safe demo ended; no call or message was sent" else "No response to fall alert; no call or message was sent")
                notifySafely(ALERT_NOTIFICATION_ID, expiredAlertNotification(demoAlert))
                if (demoOnlyService) stopSelf()
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
        updateActiveEvent(if (demoAlert) "FALL_DEMO_ACKNOWLEDGED" else "FALL_ALERT_CANCELLED", "User acknowledged the possible fall alert")
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        if (demoOnlyService) stopSelf()
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
                    alertWindowMillis = FallPreferences.countdownSeconds(this@FallMonitoringService) * 1_000L
                    val remaining = (alertWindowMillis - elapsedSinceAlert).coerceAtLeast(0L)
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
        .setContentTitle(if (demoAlert) "DEMO · Possible fall alert" else "Possible fall detected")
        .setContentText(if (demoAlert) "Safe demo · tap “I’m okay” within $seconds seconds. No contact actions are available." else "Tap “I’m okay” within $seconds seconds to cancel. No call or message is sent automatically.")
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
        .build()

    private fun expiredAlertNotification(isDemo: Boolean): Notification = notificationBuilder(ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(if (isDemo) "Fall alert demo complete" else "Fall alert needs your attention")
        .setContentText(if (isDemo) "Demo only. No SMS or call was made." else "No response was received. No call or message was sent; open SOS if you need help.")
        .setCategory(Notification.CATEGORY_ALARM)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .setContentIntent(if (isDemo) openFallPendingIntent() else openSosPendingIntent())
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
        private const val ACTION_DEMO = "com.medmitra.app.action.DEMO_FALL_ALERT"
        private const val ACTION_RECONFIGURE = "com.medmitra.app.action.RECONFIGURE_FALL_MONITORING"
        private const val MONITOR_CHANNEL_ID = "fall_monitoring"
        private const val ALERT_CHANNEL_ID = "fall_alerts"
        private const val NOTIFICATION_ID = 7_001
        private const val ALERT_NOTIFICATION_ID = 7_002
        private const val REQUEST_STOP = 7_101
        private const val REQUEST_ACKNOWLEDGE = 7_102
        private const val REQUEST_OPEN_FALL = 7_103
        private const val REQUEST_OPEN_SOS = 7_104

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: android.content.Context) {
            FallPreferences.setMonitoringRequested(context, true)
            val intent = Intent(context, FallMonitoringService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun updateSensitivity(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, FallMonitoringService::class.java).setAction(ACTION_RECONFIGURE))
        }

        fun stop(context: android.content.Context) {
            FallPreferences.setMonitoringRequested(context, false)
            context.stopService(Intent(context, FallMonitoringService::class.java))
        }

        fun demo(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, FallMonitoringService::class.java).setAction(ACTION_DEMO))
        }

        private const val ACTION_START = "com.medmitra.app.action.START_FALL_MONITORING"
    }
}
