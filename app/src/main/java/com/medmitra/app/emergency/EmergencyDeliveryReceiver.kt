package com.medmitra.app.emergency

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.medmitra.app.data.MedMitraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmergencyDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        if (id < 0) return
        val status = when (resultCode) {
            Activity.RESULT_OK -> "SMS_ACCEPTED_BY_RADIO_DELIVERY_UNKNOWN"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "SMS_FAILED_NO_SERVICE"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "SMS_FAILED_RADIO_OFF"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS_SEND_FAILED_LIMIT_EXCEEDED"
            else -> "SMS_SEND_FAILED_RESULT_$resultCode"
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { MedMitraDatabase.get(context).history().updateSmsAttempt(id, status) }
            finally { pending.finish() }
        }
    }
    companion object { const val EXTRA_HISTORY_ID = "emergency_history_id" }
}
