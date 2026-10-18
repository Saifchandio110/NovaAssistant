package com.nova.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileNotFoundException

object ModelInstaller {
    fun ensureInstalled(context: Context): File {
        val target = File(context.filesDir, "vosk-model-small-en-us")
        val marker = File(target, ".complete")
        if (marker.exists()) return target
        target.deleteRecursively()
        copyAsset(context, "model", target)
        marker.writeText("ok")
        return target
    }

    private fun copyAsset(context: Context, assetPath: String, dest: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            try {
                dest.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: FileNotFoundException) {
                dest.mkdirs()
            }
        } else {
            dest.mkdirs()
            for (child in children) {
                copyAsset(context, "$assetPath/$child", File(dest, child))
            }
        }
    }
}

class WakeWordEngine(
    private val context: Context,
    private val onWake: () -> Unit,
    private val onCommand: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({ loop() }, "nova-wake-word").start()
    }

    fun stop() {
        running = false
    }

    private fun extractText(json: String): String {
        val text = try {
            JSONObject(json).optString("text", "")
        } catch (e: Exception) {
            ""
        }
        return text.trim().removePrefix("hey nova").trim()
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        var model: Model? = null
        var wakeRec: Recognizer? = null
        var cmdRec: Recognizer? = null
        var audio: AudioRecord? = null
        try {
            onStatus("Loading speech model (first time takes a while)")
            val dir = ModelInstaller.ensureInstalled(context)
            model = Model(dir.absolutePath)
            wakeRec = Recognizer(model, 16000.0f, "[\"hey nova\", \"[unk]\"]")
            cmdRec = Recognizer(model, 16000.0f)

            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, 16384)
            audio = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException(
                    "Microphone could not be opened. Another app may be using it, or permission is missing."
                )
            }
            audio.startRecording()
            onStatus("Listening for \"hey nova\"")

            val buffer = ShortArray(4096)
            var lastWake = 0L
            var commandMode = false
            var commandStart = 0L

            while (running) {
                val read = audio.read(buffer, 0, buffer.size)
                if (read < 0) throw IllegalStateException("Microphone read error (code $read)")
                if (read == 0) continue

                if (!commandMode) {
                    val accepted = wakeRec.acceptWaveForm(buffer, read)
                    val json = if (accepted) wakeRec.result else wakeRec.partialResult
                    if (json.contains("hey nova")) {
                        wakeRec.reset()
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWake > 3000) {
                            lastWake = now
                            cmdRec.reset()
                            commandMode = true
                            commandStart = now
                            onWake()
                        }
                    }
                } else {
                    val accepted = cmdRec.acceptWaveForm(buffer, read)
                    val timedOut = SystemClock.elapsedRealtime() - commandStart > 8000
                    if (accepted || timedOut) {
                        val text = extractText(if (accepted) cmdRec.result else cmdRec.finalResult)
                        if (text.isBlank() && !timedOut) {
                            cmdRec.reset()
                        } else {
                            cmdRec.reset()
                            wakeRec.reset()
                            commandMode = false
                            lastWake = SystemClock.elapsedRealtime()
                            onCommand(text)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            if (running) {
                running = false
                onError("Wake word stopped: ${e.javaClass.simpleName}: ${e.message}")
            }
        } finally {
            try {
                audio?.stop()
            } catch (ignored: Exception) {
            }
            audio?.release()
            cmdRec?.close()
            wakeRec?.close()
            model?.close()
        }
    }
}
