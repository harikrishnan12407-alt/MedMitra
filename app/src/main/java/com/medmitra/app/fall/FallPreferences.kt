package com.medmitra.app.fall

import android.content.Context
import com.medmitra.app.domain.FallSensitivity

object FallPreferences {
    private const val FILE = "fall_preferences"
    private const val SENSITIVITY = "sensitivity"
    private const val COUNTDOWN = "countdown"
    private const val REQUESTED = "monitoring_requested"
    fun sensitivity(context: Context) = runCatching { FallSensitivity.valueOf(prefs(context).getString(SENSITIVITY, FallSensitivity.LOW.name)!!) }.getOrDefault(FallSensitivity.LOW)
    fun setSensitivity(context: Context, value: FallSensitivity) { prefs(context).edit().putString(SENSITIVITY, value.name).apply() }
    fun countdownSeconds(context: Context) = prefs(context).getInt(COUNTDOWN, 30).coerceIn(15, 120)
    fun setCountdownSeconds(context: Context, seconds: Int) { prefs(context).edit().putInt(COUNTDOWN, seconds.coerceIn(15, 120)).apply() }
    fun monitoringRequested(context: Context) = prefs(context).getBoolean(REQUESTED, false)
    fun setMonitoringRequested(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(REQUESTED, enabled).apply() }
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
