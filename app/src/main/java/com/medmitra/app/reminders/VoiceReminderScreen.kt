package com.medmitra.app.reminders

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.medmitra.app.ui.components.PageHeader
import com.medmitra.app.ui.components.PlainCard
import com.medmitra.app.ui.theme.MedMitraColors
import java.util.Locale
import android.os.Handler
import android.os.Looper

object VoiceReminderPreferences {
    private const val FILE = "voice_reminders"
    private const val MESSAGE = "message"
    private const val ENABLED = "enabled"
    fun message(context: android.content.Context) = context.getSharedPreferences(FILE, 0).getString(MESSAGE, "It's time to take your medicine.") ?: "It's time to take your medicine."
    fun enabled(context: android.content.Context) = context.getSharedPreferences(FILE, 0).getBoolean(ENABLED, false)
    fun save(context: android.content.Context, message: String, enabled: Boolean) {
        context.getSharedPreferences(FILE, 0).edit().putString(MESSAGE, message).putBoolean(ENABLED, enabled).apply()
    }
}

object VoiceReminderSpeaker {
    private val main = Handler(Looper.getMainLooper())
    private var active: TextToSpeech? = null
    private var activeOwner: String? = null
    private var request = 0

    fun speak(context: android.content.Context, message: String, owner: String, onResult: (Boolean) -> Unit = {}, onFinished: () -> Unit = {}) {
        if (message.isBlank()) { onResult(false); return }
        main.post {
            stopNow()
            val token = ++request
            activeOwner = owner
            val engine = runCatching {
                TextToSpeech(context.applicationContext) { result ->
                    main.post {
                        if (token != request) return@post
                        val speaker = active ?: return@post
                        val language = if (result == TextToSpeech.SUCCESS) speaker.setLanguage(Locale.getDefault()) else TextToSpeech.LANG_MISSING_DATA
                        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                            release(token)
                            onResult(false)
                        } else {
                            speaker.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit
                                override fun onDone(utteranceId: String?) { main.post { release(token); onFinished() } }
                                @Deprecated("Deprecated in Android") override fun onError(utteranceId: String?) { main.post { release(token); onResult(false); onFinished() } }
                                override fun onError(utteranceId: String?, errorCode: Int) { main.post { release(token); onResult(false); onFinished() } }
                            })
                            val status = speaker.speak(message, TextToSpeech.QUEUE_FLUSH, null, "medmitra-$owner-$token")
                            onResult(status == TextToSpeech.SUCCESS)
                            if (status != TextToSpeech.SUCCESS) { release(token); onFinished() }
                        }
                    }
                }
            }.getOrNull()
            if (engine == null) { activeOwner = null; onResult(false) } else active = engine
        }
    }

    fun stop(owner: String? = null) {
        main.post { if (owner == null || activeOwner == owner) stopNow() }
    }

    private fun stopNow() {
        request++
        activeOwner = null
        val old = active
        active = null
        runCatching { old?.stop() }
        runCatching { old?.shutdown() }
    }

    private fun release(token: Int) {
        if (token != request) return
        activeOwner = null
        val old = active
        active = null
        runCatching { old?.shutdown() }
    }
}

@Composable
fun VoiceReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf(VoiceReminderPreferences.message(context)) }
    var enabled by remember { mutableStateOf(VoiceReminderPreferences.enabled(context)) }
    var speaking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Speech starts when you preview a message.") }
    DisposableEffect(Unit) { onDispose { VoiceReminderSpeaker.stop("preview") } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Voice Reminders", onBack = onBack)
        Text("Enter a message for Android Text to Speech to read aloud. This does not record your voice.", color = MedMitraColors.TextSecondary)
        PlainCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(message, { value -> message = value.take(300); VoiceReminderPreferences.save(context, value.take(300), enabled) }, modifier = Modifier.fillMaxWidth(), label = { Text("Personal reminder message") }, minLines = 3)
            Button(onClick = {
                VoiceReminderPreferences.save(context, message, enabled)
                if (message.isBlank()) status = "Enter a message to preview."
                else {
                    speaking = true
                    VoiceReminderSpeaker.speak(context, message, "preview", onResult = { success ->
                        speaking = success
                        status = if (success) "Speaking preview in ${Locale.getDefault().displayName}." else "Speech could not start. Check the installed speech engine and language data."
                    }, onFinished = { speaking = false })
                }
            }, enabled = message.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Play preview") }
            OutlinedButton(onClick = { VoiceReminderSpeaker.stop("preview"); speaking = false; status = "Speech stopped." }, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
        }
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Speak with medicine notifications", color = MedMitraColors.Text)
            Switch(checked = enabled, onCheckedChange = { enabled = it; VoiceReminderPreferences.save(context, message, enabled) })
            Text("When enabled, the saved message is spoken when a scheduled medicine reminder fires. Android may limit speech when the app is in the background or another audio session is active.", color = MedMitraColors.TextSecondary)
        }
        Text(status, color = MedMitraColors.TextSecondary)
        if (!speaking) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { status = "No speech data installer is available." } }) { Text("Check speech language data") }
    }
}
