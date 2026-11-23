package com.example.jarvis

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class WakeWordRecorder(
    private val engine: OpenWakeWordEngine,
    private val onWakeWord: () -> Unit = {},
    private val onCommandRecorded: (ByteArray) -> Unit
) {
    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var lastDetectionTime: Long = 0L

    companion object {
        private const val TAG = "WakeWordRecorder"
        const val SAMPLE_RATE = 16000
        const val CHUNK_SIZE = 1280
        const val ACTIVATION_THRESHOLD = 0.4f
        const val COOLDOWN_MS = 2000L
        const val COMMAND_DURATION_MS = 5000L
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, CHUNK_SIZE * 2)
        )

        isRecording = true
        audioRecord?.startRecording()

        audioThread = thread(name = "OpenWakeWordAudioThread") {
            val audioBuffer = ShortArray(CHUNK_SIZE)

            while (isRecording) {
                val readSamples = audioRecord?.read(audioBuffer, 0, CHUNK_SIZE) ?: 0
                if (readSamples == CHUNK_SIZE && isRecording) {
                    try {
                        val score = engine.predict(audioBuffer)
                        val currentTime = System.currentTimeMillis()

                        if (score >= ACTIVATION_THRESHOLD && (currentTime - lastDetectionTime > COOLDOWN_MS)) {
                            onWakeWord()
                            val rawPcm = recordRawCommand(COMMAND_DURATION_MS)
                            val wavAudio = pcmToWav(rawPcm)

                            engine.resetBuffers()
                            lastDetectionTime = System.currentTimeMillis()

                            if (wavAudio.isNotEmpty()) {
                                onCommandRecorded(wavAudio)
                            }
                        }
                    } catch (e: IllegalStateException) {
                        // Apanha o fecho da OrtSession sem matar o processo do Android
                        Log.w(TAG, "Sessão ONNX encerrada durante o ciclo de áudio: ${e.message}")
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro na inferência do wake word: ${e.message}")
                    }
                }
            }
        }
    }

    private fun recordRawCommand(maxDurationMs: Long = 4000L): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        val startTime = System.currentTimeMillis()
        val stopTime = startTime + maxDurationMs
        
        var silenceStartTime = 0L
        var hasSpoken = false
        val silenceThreshold = 1500 // Amplitude média de corte (ajustável)

        while (isRecording && System.currentTimeMillis() < stopTime) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                outputStream.write(buffer, 0, read)

                // Calcula a amplitude média do buffer para detetar voz
                var sum = 0L
                for (i in 0 until read step 2) {
                    val sample = (buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                    sum += Math.abs(sample.toShort().toInt())
                }
                val amplitude = sum / (read / 2)

                if (amplitude > silenceThreshold) {
                    hasSpoken = true
                    silenceStartTime = 0L // Reinicia contagem de silêncio
                } else if (hasSpoken) {
                    // Se já falou e agora está calado
                    if (silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - silenceStartTime > 800L) {
                        // 800ms de silêncio após falar: encerra a gravação de imediato
                        break
                    }
                }
            }
        }
        return outputStream.toByteArray()
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[20] = 1; header[22] = 1
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[32] = 2; header[34] = 16
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }

    fun stop() {
        isRecording = false
        try {
            // Aguarda até 300ms para a thread terminar o ciclo atual
            audioThread?.join(300)
        } catch (ignored: InterruptedException) {}
        audioThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}