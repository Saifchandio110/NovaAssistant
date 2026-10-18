package com.nova.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
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

    @SuppressLint("MissingPermission")
    private fun loop() {
        var model: Model? = null
        var recognizer: Recognizer? = null
        var audio: AudioRecord? = null
        try {
            onStatus("Loading speech model (first time takes a while)")
            val dir = ModelInstaller.ensureInstalled(context)
            model = Model(dir.absolutePath)
            recognizer = Recognizer(model, 16000.0f, "[\"hey nova\", \"[unk]\"]")

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
            while (running) {
                val read = audio.read(buffer, 0, buffer.size)
                if (read < 0) throw IllegalStateException("Microphone read error (code $read)")
                if (read == 0) continue
                val accepted = recognizer.acceptWaveForm(buffer, read)
                val json = if (accepted) recognizer.result else recognizer.partialResult
                if (json.contains("hey nova")) {
                    recognizer.reset()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastWake > 3000) {
                        lastWake = now
                        onWake()
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
            recognizer?.close()
            model?.close()
        }
    }
}
