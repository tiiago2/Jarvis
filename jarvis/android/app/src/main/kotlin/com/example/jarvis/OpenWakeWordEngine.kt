package com.example.jarvis

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.InputStream
import java.nio.FloatBuffer

class OpenWakeWordEngine(private val context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var sessionMel: OrtSession? = null
    private var sessionEmbedding: OrtSession? = null
    private var sessionKeyword: OrtSession? = null

    // Nomes dos inputs guardados no arranque (elimina JNI calls a cada 80ms)
    private var melInputName: String = ""
    private var embeddingInputName: String = ""
    private var keywordInputName: String = ""

    // Controlo de ciclo de vida e concorrência entre threads
    private val lock = Any()
    @Volatile private var isInitialized = false
    @Volatile private var isClosed = false

    // Buffers de acumulação e contexto temporal
    private val melBuffer = ArrayList<FloatArray>()
    private val embeddingBuffer = Array(16) { FloatArray(EMBEDDING_SIZE) }

    companion object {
        const val CHUNK_SIZE = 1280        // 80 ms a 16 kHz
        const val MEL_BINS = 32            // Bandas de frequência
        const val REQUIRED_MEL_FRAMES = 76 // Fatias necessárias para 1 embedding
        const val EMBEDDING_SIZE = 96      // Dimensão de cada embedding acústico
        const val WINDOW_STEP = 8          // Passo de avanço da janela deslizante
    }

    fun initialize(keywordModelName: String = "hey_jarvis_v0.1.onnx") {
        synchronized(lock) {
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
            }

            sessionMel = ortEnv.createSession(loadAssetBytes("melspectrogram.onnx"), sessionOptions)
            sessionEmbedding = ortEnv.createSession(loadAssetBytes("embedding_model.onnx"), sessionOptions)
            sessionKeyword = ortEnv.createSession(loadAssetBytes(keywordModelName), sessionOptions)

            // Guarda os nomes dos inputs uma única vez
            melInputName = sessionMel?.inputNames?.firstOrNull() ?: "input"
            embeddingInputName = sessionEmbedding?.inputNames?.firstOrNull() ?: "input"
            keywordInputName = sessionKeyword?.inputNames?.firstOrNull() ?: "input"

            isClosed = false
            isInitialized = true
        }
    }

    private fun loadAssetBytes(filename: String): ByteArray {
        val inputStream: InputStream = context.assets.open(filename)
        return inputStream.readBytes().also { inputStream.close() }
    }

    private fun computeMelSpectrogram(audioChunk: ShortArray): Array<FloatArray> {
        val session = sessionMel ?: return emptyArray()

        val floatAudio = FloatArray(CHUNK_SIZE) { i -> audioChunk[i].toFloat() }
        val inputBuffer = FloatBuffer.wrap(floatAudio)
        val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, longArrayOf(1, CHUNK_SIZE.toLong()))

        val results = session.run(mapOf(melInputName to inputTensor))

        @Suppress("UNCHECKED_CAST")
        val rawOutput = results[0].value as Array<Array<Array<FloatArray>>>
        val frames = rawOutput[0][0]

        inputTensor.close()
        results.close()

        // Calibração de decibéis do openWakeWord
        for (frame in frames) {
            for (i in frame.indices) {
                frame[i] = (frame[i] / 10.0f) + 2.0f
            }
        }

        return frames
    }

    private fun computeEmbedding(): FloatArray? {
        val session = sessionEmbedding ?: return null
        if (melBuffer.size < REQUIRED_MEL_FRAMES) return null

        val windowData = FloatArray(REQUIRED_MEL_FRAMES * MEL_BINS)
        var idx = 0
        for (i in 0 until REQUIRED_MEL_FRAMES) {
            val frame = melBuffer[i]
            System.arraycopy(frame, 0, windowData, idx, MEL_BINS)
            idx += MEL_BINS
        }

        val inputBuffer = FloatBuffer.wrap(windowData)
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            inputBuffer,
            longArrayOf(1, REQUIRED_MEL_FRAMES.toLong(), MEL_BINS.toLong(), 1)
        )

        val results = session.run(mapOf(embeddingInputName to inputTensor))

        @Suppress("UNCHECKED_CAST")
        val rawOutput = results[0].value as Array<Array<Array<FloatArray>>>
        val embedding = rawOutput[0][0][0]

        inputTensor.close()
        results.close()

        for (i in 0 until WINDOW_STEP) {
            if (melBuffer.isNotEmpty()) melBuffer.removeAt(0)
        }

        return embedding
    }

    fun predict(audioChunk: ShortArray): Float {
        synchronized(lock) {
            if (!isInitialized || isClosed) return 0.0f

            val session = sessionKeyword ?: return 0.0f

            val melFrames = computeMelSpectrogram(audioChunk)
            for (frame in melFrames) {
                melBuffer.add(frame)
            }

            val newEmbedding = computeEmbedding() ?: return 0.0f

            System.arraycopy(embeddingBuffer, 1, embeddingBuffer, 0, 15)
            embeddingBuffer[15] = newEmbedding

            val flatInput = FloatArray(16 * EMBEDDING_SIZE)
            var p = 0
            for (vec in embeddingBuffer) {
                System.arraycopy(vec, 0, flatInput, p, EMBEDDING_SIZE)
                p += EMBEDDING_SIZE
            }

            val inputBuffer = FloatBuffer.wrap(flatInput)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, longArrayOf(1, 16, EMBEDDING_SIZE.toLong()))

            val results = session.run(mapOf(keywordInputName to inputTensor))

            @Suppress("UNCHECKED_CAST")
            val output = results[0].value as Array<FloatArray>
            val score = output[0][0]

            inputTensor.close()
            results.close()

            return score
        }
    }

    fun close() {
        synchronized(lock) {
            isClosed = true
            isInitialized = false

            try {
                sessionMel?.close()
                sessionEmbedding?.close()
                sessionKeyword?.close()
            } catch (ignored: Exception) {}

            sessionMel = null
            sessionEmbedding = null
            sessionKeyword = null
            resetBuffers()
        }
    }

    fun resetBuffers() {
        melBuffer.clear()
        for (i in embeddingBuffer.indices) {
            embeddingBuffer[i].fill(0.0f)
        }
    }
}