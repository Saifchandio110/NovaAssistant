package com.nova.assistant

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    val messages = mutableStateListOf<ChatMessage>()

    var isListening by mutableStateOf(false)
        private set

    var speakReplies by mutableStateOf(true)

    private var ttsWarned = false
    private val tts = TtsManager(application)
    private val runner = ActionRunner(application)
    private val speech = SpeechInput(
        context = application,
        onResult = { submit(it) },
        onError = { reply(it, isError = true, speak = false) },
        onListening = { isListening = it }
    )

    init {
        messages.add(
            ChatMessage(
                "Hi, I'm Nova. AI chat is NOT configured yet, so I only run local commands " +
                    "(open apps, alarms, timers, time and date). Say or type \"help\" for the list.",
                fromUser = false
            )
        )
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        messages.add(ChatMessage(text, fromUser = true))
        when (val result = CommandParser.parse(text)) {
            is CommandResult.Reply -> reply(result.text)
            is CommandResult.OpenApp -> outcome(runner.openApp(result.query))
            is CommandResult.SetAlarm -> outcome(runner.setAlarm(result.hour, result.minute, result.label))
            is CommandResult.SetTimer -> outcome(runner.setTimer(result.seconds, result.label))
            CommandResult.Unrecognized -> reply(
                "I don't have an AI model connected, so I can't answer open-ended questions yet.\n\n" +
                    CommandParser.HELP_TEXT
            )
        }
    }

    fun startListening() {
        tts.stop()
        speech.start()
    }

    fun stopListening() {
        speech.stop()
    }

    fun onMicPermissionDenied() {
        reply(
            "Microphone permission was denied, so voice input is off. Typing still works. " +
                "To enable it: Settings > Apps > Nova Assistant > Permissions > Microphone.",
            isError = true,
            speak = false
        )
    }

    private fun outcome(o: ActionOutcome) = reply(o.message, isError = !o.success)

    private fun reply(text: String, isError: Boolean = false, speak: Boolean = true) {
        messages.add(ChatMessage(text, fromUser = false, isError = isError))
        if (speak && speakReplies && !tts.speak(text) && !ttsWarned) {
            ttsWarned = true
            messages.add(
                ChatMessage(
                    "Voice output isn't available: " +
                        (tts.errorMessage ?: "the text-to-speech engine isn't ready yet") + ".",
                    fromUser = false,
                    isError = true
                )
            )
        }
    }

    override fun onCleared() {
        speech.stop()
        tts.shutdown()
        super.onCleared()
    }
}
