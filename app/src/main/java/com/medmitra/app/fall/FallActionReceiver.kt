package com.medmitra.app.fall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.medmitra.app.data.MedMitraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FallMonitoringService.ACTION_STOP -> FallMonitoringService.stop(context)
            FallMonitoringService.ACTION_ACKNOWLEDGE -> {
                NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
                context.sendBroadcast(
                    Intent(FallMonitoringService.ACTION_ACKNOWLEDGED).setPackage(context.packageName)
                )
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        MedMitraDatabase.get(context).history().resolveLatestPendingFallAlert(
                            "FALL_ALERT_CANCELLED",
                            "User acknowledged the possible fall alert"
                        )
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val ALERT_NOTIFICATION_ID = 7_002
    }
}
