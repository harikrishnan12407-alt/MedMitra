package com.medmitra.app.reminders

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine
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
import java.util.concurrent.atomic.AtomicReference
import android.os.Handler

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
    private val activeSpeaker = AtomicReference<TextToSpeech?>()
    fun speak(context: android.content.Context, message: String) {
        if (message.isBlank()) return
        activeSpeaker.getAndSet(null)?.let { it.stop(); it.shutdown() }
        val reference = AtomicReference<TextToSpeech?>()
        val tts = runCatching {
            TextToSpeech(context.applicationContext) { result ->
                Handler(context.mainLooper).post {
                    val speaker = reference.get()
                    if (speaker != null && activeSpeaker.get() === speaker) {
                        if (result == TextToSpeech.SUCCESS) {
                            val language = speaker.setLanguage(Locale.getDefault())
                            if (language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED) {
                                speaker.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) = Unit
                                    override fun onDone(utteranceId: String?) { activeSpeaker.compareAndSet(speaker, null); speaker.shutdown() }
                                    @Deprecated("Deprecated in Android")
                                    override fun onError(utteranceId: String?) { activeSpeaker.compareAndSet(speaker, null); speaker.shutdown() }
                                    override fun onError(utteranceId: String?, errorCode: Int) { activeSpeaker.compareAndSet(speaker, null); speaker.shutdown() }
                                })
                                if (speaker.speak(message, TextToSpeech.QUEUE_FLUSH, null, "medicine-reminder") != TextToSpeech.SUCCESS) speaker.shutdown()
                            } else speaker.shutdown()
                        } else speaker.shutdown()
                    }
                }
            }
        }.getOrNull()
        if (tts != null) {
            reference.set(tts)
            activeSpeaker.set(tts)
            Handler(context.mainLooper).postDelayed({
                if (activeSpeaker.compareAndSet(tts, null)) { tts.stop(); tts.shutdown() }
            }, 60_000L)
        }
    }
}

@Composable
fun VoiceReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf(VoiceReminderPreferences.message(context)) }
    var enabled by remember { mutableStateOf(VoiceReminderPreferences.enabled(context)) }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparing speech engine…") }
    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val speech = engine
                if (speech != null) {
                    val language = speech.setLanguage(Locale.getDefault())
                    ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
                    status = if (ready) "Speech is ready in ${Locale.getDefault().displayName}." else "Speech data for this language is unavailable. Install language data or choose another system voice."
                } else status = "Speech engine is unavailable."
            } else {
                ready = false
                status = "Could not initialize the speech engine. Check that a text-to-speech engine is installed."
            }
        }
        engine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            engine = null
            ready = false
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Voice Reminders", onBack = onBack)
        Text("Enter a message for Android Text to Speech to read aloud. This does not record your voice.", color = MedMitraColors.TextSecondary)
        PlainCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(message, { value -> message = value.take(300); VoiceReminderPreferences.save(context, value.take(300), enabled) }, modifier = Modifier.fillMaxWidth(), label = { Text("Personal reminder message") }, minLines = 3)
            Button(onClick = {
                VoiceReminderPreferences.save(context, message, enabled)
                val tts = engine
                if (!ready || tts == null) status = "Speech is not ready. Check language data and the installed speech engine."
                else if (message.isBlank()) status = "Enter a message to preview."
                else {
                    val result = tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "medmitra-preview")
                    status = if (result == TextToSpeech.SUCCESS) "Speaking preview…" else "The speech engine could not speak this message."
                }
            }, enabled = ready && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Play preview") }
            OutlinedButton(onClick = { engine?.stop(); status = "Speech stopped." }, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
        }
        PlainCard(Modifier.fillMaxWidth()) {
            Text("Speak with medicine notifications", color = MedMitraColors.Text)
            Switch(checked = enabled, onCheckedChange = { enabled = it; VoiceReminderPreferences.save(context, message, enabled) })
            Text("When enabled, the saved message is spoken when a scheduled medicine reminder fires. Android may limit speech when the app is in the background or another audio session is active.", color = MedMitraColors.TextSecondary)
        }
        Text(status, color = MedMitraColors.TextSecondary)
        if (!ready) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { status = "No speech data installer is available." } }) { Text("Check speech language data") }
    }
}
