package com.example.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG = "MainActivity"
        init {
            try {
                System.loadLibrary("c++_shared")
                System.loadLibrary("onnxruntime")
                Log.d(TAG, "Bibliotecas nativas ONNX carregadas com sucesso.")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Aviso no pré-carregamento nativo: ${e.message}")
            }
        }
    }

    private val CHANNEL = "com.example.jarvis/wakeword"
    private val PERMISSION_CODE = 1001

    private var engine: OpenWakeWordEngine? = null
    private var recorder: WakeWordRecorder? = null
    private var soundManager: SoundFeedbackManager? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        soundManager = SoundFeedbackManager(applicationContext)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startListening" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
                    } else {
                        startWakeWord()
                    }
                    result.success(true)
                }
                "stopListening" -> {
                    stopAll()
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun startWakeWord() {
        if (engine == null) {
            engine = OpenWakeWordEngine(applicationContext).apply {
                initialize("hey_jarvis_v0.1.onnx")
            }
        }

        recorder = WakeWordRecorder(
            engine = engine!!,
            onWakeWord = {
                runOnUiThread {
                    soundManager?.playWakeSound()
                    notifyFlutter("onListeningCommand", null)
                }
            },
            onCommandRecorded = { wavAudio ->
                runOnUiThread {
                    notifyFlutter("onCommandAudioRecorded", wavAudio)
                }
            }
        )

        recorder?.start()
    }

    private fun notifyFlutter(method: String, argument: Any?) {
        flutterEngine?.dartExecutor?.binaryMessenger?.let { messenger ->
            MethodChannel(messenger, CHANNEL).invokeMethod(method, argument)
        }
    }

    private fun stopAll() {
        recorder?.stop()
        recorder = null
        
        // Só fecha o modelo depois da thread de áudio ter parado
        engine?.close()
        engine = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
        soundManager?.release()
    }
}