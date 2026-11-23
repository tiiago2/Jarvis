package com.example.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundFeedbackManager(context: Context) {
    private var soundPool: SoundPool? = null
    private var wakeSoundId: Int = 0
    private var isLoaded: Boolean = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, _, status ->
                    isLoaded = (status == 0)
                }
            }

        // Carrega o ficheiro colocado em res/raw/chime_1.mp3
        wakeSoundId = soundPool?.load(context, R.raw.chime_1, 1) ?: 0
    }

    fun playWakeSound() {
        if (isLoaded) {
            soundPool?.play(wakeSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}