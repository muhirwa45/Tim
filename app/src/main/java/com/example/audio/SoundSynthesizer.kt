package com.example.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

class SoundSynthesizer {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Failed to initialize ToneGenerator", e)
        }
    }

    fun playFocusCompletion() {
        try {
            // High pitch ACK system tone to signify work completion
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Failed to play focus tone", e)
        }
    }

    fun playBreakCompletion() {
        try {
            // High comfort CDMA confirm tone sequence
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 350)
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Failed to play break completed tone", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Failed to release ToneGenerator", e)
        }
    }
}
