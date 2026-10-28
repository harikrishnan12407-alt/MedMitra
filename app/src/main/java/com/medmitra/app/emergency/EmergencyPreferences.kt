package com.medmitra.app.emergency

import android.content.Context

object EmergencyPreferences {
    private const val FILE = "emergency_preferences"
    private const val AUTO_SMS = "auto_sms"
    private const val AUTO_CALL = "auto_call"
    private const val SHARE_LOCATION = "share_location"
    fun autoSms(context: Context) = prefs(context).getBoolean(AUTO_SMS, false)
    fun autoCall(context: Context) = prefs(context).getBoolean(AUTO_CALL, false)
    fun shareLocation(context: Context) = prefs(context).getBoolean(SHARE_LOCATION, false)
    fun save(context: Context, sms: Boolean = autoSms(context), call: Boolean = autoCall(context), location: Boolean = shareLocation(context)) {
        prefs(context).edit().putBoolean(AUTO_SMS, sms).putBoolean(AUTO_CALL, call).putBoolean(SHARE_LOCATION, location).apply()
    }
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
