package com.nova.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            errorMessage = "The text-to-speech engine failed to start."
            return
        }
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = engine.setLanguage(Locale.US)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                errorMessage = "No installed text-to-speech voice data for your language."
                return
            }
        }
        isReady = true
    }

    fun speak(text: String): Boolean {
        if (!isReady) return false
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-utterance")
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
