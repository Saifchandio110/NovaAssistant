package com.nova.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ListenService : Service() {

    companion object {
        const val ACTION_STOP = "com.nova.assistant.STOP"
        private const val CHANNEL_ID = "nova_background"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var running: Boolean = false

        @Volatile
        var lastError: String? = null

        @Volatile
        var wakeCount: Int = 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var status = "Starting"
    private var engine: WakeWordEngine? = null
    private var tone: ToneGenerator? = null
    private var tts: TtsManager? = null
    private var runner: ActionRunner? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        if (!running) {
            try {
                createChannel()
                startedAt = SystemClock.elapsedRealtime()
                wakeCount = 0
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Starting"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
                running = true
                lastError = null
                runner = ActionRunner(applicationContext)
                tts = TtsManager(applicationContext)
                handler.postDelayed(ticker, 60_000)
                startEngine()
            } catch (e: Exception) {
                lastError = "Could not start background service: ${e.javaClass.simpleName}: ${e.message}"
                shutDown()
            }
        }
        return START_NOT_STICKY
    }

    private fun startEngine() {
        engine = WakeWordEngine(
            context = applicationContext,
            onWake = { handler.post { handleWake() } },
            onCommand = { text -> handler.post { handleCommand(text) } },
            onStatus = { text ->
                handler.post {
                    status = text
                    updateNotification()
                }
            },
            onError = { message ->
                handler.post {
                    lastError = message
                    shutDown()
                }
            }
        ).also { it.start() }
    }

    private fun beep(toneType: Int) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone?.startTone(toneType, 200)
        } catch (e: RuntimeException) {
            // Beep is optional; ignore failures.
        }
    }

    private fun handleWake() {
        wakeCount++
        status = "Heard \"hey nova\" #$wakeCount. Say your command now"
        beep(ToneGenerator.TONE_PROP_BEEP)
        updateNotification()
    }

    private fun execute(text: String): ActionOutcome {
        val r = runner ?: return ActionOutcome("Service is not ready.", false)
        return when (val result = CommandParser.parse(text)) {
            is CommandResult.Reply -> ActionOutcome(result.text, true)
            is CommandResult.OpenApp -> r.openApp(result.query)
            is CommandResult.SetAlarm -> r.setAlarm(result.hour, result.minute, result.label)
            is CommandResult.SetTimer -> r.setTimer(result.seconds, result.label)
            CommandResult.Unrecognized -> ActionOutcome(
                "I heard \"$text\" but I only know local commands: open apps, alarms, timers, time and date.",
                false
            )
        }
    }

    private fun handleCommand(text: String) {
        if (text.isBlank()) {
            status = "Didn't hear a command. Listening for \"hey nova\""
            beep(ToneGenerator.TONE_PROP_NACK)
            updateNotification()
            return
        }
        val outcome = execute(text)
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        status = "$time You: \"$text\" -> ${outcome.message.take(90)}"
        updateNotification()
        val spoken = if (outcome.message.length > 140) {
            "I can open apps, set alarms and timers, and tell the time."
        } else {
            outcome.message
        }
        tts?.speak(spoken)
    }

    private fun shutDown() {
        engine?.stop()
        engine = null
        handler.removeCallbacks(ticker)
        running = false
        tone?.release()
        tone = null
        tts?.shutdown()
        tts = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        handler.removeCallbacks(ticker)
        running = false
        tone?.release()
        tone = null
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Nova background listening", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, ListenService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPending = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Nova Assistant")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun updateNotification() {
        val minutes = (SystemClock.elapsedRealtime() - startedAt) / 60_000
        val text = "$status (up $minutes min)"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }
}
