package com.kalaiselvan.ride.crash

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.kalaiselvan.ride.logs.LogStore

object EmergencyBuzzer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var stopHandler: android.os.Handler? = null
    private var stopRunnable: Runnable? = null

    private const val SAMPLE_RATE = 44100
    private const val DURATION_MS = 1200
    private const val NUM_SAMPLES = (SAMPLE_RATE * DURATION_MS) / 1000

    // Pre-computed cached PCM samples so play() never wastes CPU cycles in hot crash path
    private val cachedSamples: ShortArray by lazy {
        val samples = ShortArray(NUM_SAMPLES)
        for (i in 0 until NUM_SAMPLES) {
            val timeMs = (i.toDouble() / SAMPLE_RATE) * 1000.0
            val freq = when {
                timeMs in 0.0..160.0    -> 1000.0
                timeMs in 220.0..380.0  -> 1000.0
                timeMs in 440.0..640.0  -> 1500.0
                timeMs in 700.0..900.0  -> 1500.0
                timeMs in 960.0..1120.0 -> 2000.0
                else -> 0.0
            }

            if (freq > 0.0) {
                val period = SAMPLE_RATE / freq
                val phase = (i % period.toInt()) / period
                val value = if (phase < 0.5) 30000 else -30000
                samples[i] = value.toShort()
            } else {
                samples[i] = 0
            }
        }
        samples
    }

    @Synchronized
    fun play() {
        if (isPlaying) return
        try {
            val samples = cachedSamples
            val numSamples = samples.size
            val bufferSize = numSamples * 2

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(samples, 0, numSamples)
            audioTrack?.setLoopPoints(0, numSamples, -1) // Continuous loop while active
            audioTrack?.setVolume(1.0f)
            audioTrack?.play()
            isPlaying = true
            LogStore.logEvent("EmergencyBuzzer: Loud emergency buzzer started")

            // 60-second safety watchdog timeout: auto-stop if UI or countdown dies
            if (stopHandler == null) {
                stopHandler = android.os.Handler(android.os.Looper.getMainLooper())
            }
            stopRunnable?.let { stopHandler?.removeCallbacks(it) }
            val runnable = Runnable {
                LogStore.logEvent("EmergencyBuzzer: 60s safety timeout reached, stopping buzzer")
                stop()
            }
            stopRunnable = runnable
            stopHandler?.postDelayed(runnable, 60_000L)
        } catch (e: Exception) {
            LogStore.logEvent("EmergencyBuzzer: Failed to play buzzer: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        stopRunnable?.let { stopHandler?.removeCallbacks(it) }
        stopRunnable = null
        if (!isPlaying && audioTrack == null) return
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        isPlaying = false
        LogStore.logEvent("EmergencyBuzzer: Loud emergency buzzer stopped")
    }
}
