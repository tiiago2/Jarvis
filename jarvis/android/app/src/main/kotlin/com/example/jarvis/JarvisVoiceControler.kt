package com.example.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel

class JarvisVoiceControler(
    private val context: Context,
    private val channelProvider: () -> MethodChannel?
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val feedbackManager = SoundFeedbackManager(context)
    private val openWakeWord = OpenWakeWordEngine(context)

    private val recorder = WakeWordRecorder(
        engine = openWakeWord,
        onWakeWord = {
            feedbackManager.playWakeSound()
            mainHandler.post {
                channelProvider()?.invokeMethod("onWakeWordDetected", null)
            }
        },
        onCommandRecorded = { wavBytes: ByteArray ->
            mainHandler.post {
                channelProvider()?.invokeMethod("onCommandAudioRecorded", wavBytes)
            }
        }
    )

    fun start() {
        recorder.start()
    }

    fun stop() {
        recorder.stop()
    }

    fun release() {
        recorder.stop()
        openWakeWord.close()
    }
}