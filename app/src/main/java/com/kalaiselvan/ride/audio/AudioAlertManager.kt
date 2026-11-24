package com.kalaiselvan.ride.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.kalaiselvan.ride.logs.LogStore
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Low-latency, zero-asset audio alert synthesizer for motorcycle cockpit & helmet audio.
 * Synthesizes pure PCM sine waves in real-time or from pre-allocated buffers.
 * Zero external audio files required, ensuring zero APK bloat and instantaneous audio delivery.
 */
object AudioAlertManager {

    private const val SAMPLE_RATE = 44100
    private val audioExecutor = Executors.newSingleThreadExecutor()

    // ── Pre-synthesized Mono Buffers (Rev limit & Speed Pulses) ──────

    // Rev Limit: 3 sharp 2200 Hz beeps (60ms on, 35ms off)
    private val revLimitPcm: ShortArray by lazy {
        synthesizeMonoSequence(
            freq = 2200.0,
            pulseCount = 3,
            pulseMs = 60,
            gapMs = 35,
            volume = 0.85
        )
    }

    // 80 km/h: 2 pulse chimes (880 Hz, 100ms on, 80ms off)
    private val speed80Pcm: ShortArray by lazy {
        synthesizeMonoSequence(
            freq = 880.0,
            pulseCount = 2,
            pulseMs = 100,
            gapMs = 80,
            volume = 0.75
        )
    }

    // 150 km/h: 3 pulse alerts (1175 Hz, 90ms on, 70ms off)
    private val speed150Pcm: ShortArray by lazy {
        synthesizeMonoSequence(
            freq = 1175.0,
            pulseCount = 3,
            pulseMs = 90,
            gapMs = 70,
            volume = 0.80
        )
    }

    // 200 km/h: 4 pulse rapid tones (1480 Hz, 80ms on, 60ms off)
    private val speed200Pcm: ShortArray by lazy {
        synthesizeMonoSequence(
            freq = 1480.0,
            pulseCount = 4,
            pulseMs = 80,
            gapMs = 60,
            volume = 0.85
        )
    }

    // Lean Angle: 680 Hz gentle acoustic blip (85ms)
    private val leanBlipPcm: ShortArray by lazy {
        synthesizeMonoTone(freq = 680.0, durationMs = 85, volume = 0.70)
    }

    // ── Audio Generation Helpers ─────────────────────────────────────

    private fun synthesizeMonoTone(freq: Double, durationMs: Int, volume: Double): ShortArray {
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)
        val envLen = (SAMPLE_RATE * 0.006).toInt() // 6ms fade in/out to eliminate speaker clicks
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var amp = sin(2.0 * PI * freq * t)
            if (i < envLen) {
                amp *= (i.toDouble() / envLen)
            } else if (i > totalSamples - envLen) {
                amp *= ((totalSamples - i).toDouble() / envLen)
            }
            buffer[i] = (amp * 32767.0 * volume).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun synthesizeMonoSequence(
        freq: Double,
        pulseCount: Int,
        pulseMs: Int,
        gapMs: Int,
        volume: Double
    ): ShortArray {
        val pulse = synthesizeMonoTone(freq, pulseMs, volume)
        val gap = ShortArray((SAMPLE_RATE * gapMs) / 1000)
        val totalLen = (pulse.size * pulseCount) + (gap.size * (pulseCount - 1))
        val result = ShortArray(totalLen)
        var offset = 0
        for (p in 0 until pulseCount) {
            System.arraycopy(pulse, 0, result, offset, pulse.size)
            offset += pulse.size
            if (p < pulseCount - 1) {
                System.arraycopy(gap, 0, result, offset, gap.size)
                offset += gap.size
            }
        }
        return result
    }

    // ── Audio Track Playback ─────────────────────────────────────────

    private fun playMono(pcmData: ShortArray) {
        audioExecutor.execute {
            try {
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
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
                    .setBufferSizeInBytes(pcmData.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcmData, 0, pcmData.size)
                audioTrack.play()

                // Calculate duration and release track cleanly when finished
                val durationMs = (pcmData.size.toDouble() / SAMPLE_RATE * 1000.0).toLong() + 50L
                Thread.sleep(durationMs)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                LogStore.logEvent("AudioAlertManager: Mono playback error: ${e.message}")
            }
        }
    }

    private fun playStereo(monoPcm: ShortArray, isLeftChannel: Boolean, isRightChannel: Boolean) {
        audioExecutor.execute {
            try {
                // Interleave mono PCM into stereo [L0, R0, L1, R1, ...]
                val stereoPcm = ShortArray(monoPcm.size * 2)
                for (i in monoPcm.indices) {
                    val sample = monoPcm[i]
                    stereoPcm[i * 2] = if (isLeftChannel) sample else 0
                    stereoPcm[i * 2 + 1] = if (isRightChannel) sample else 0
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(stereoPcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(stereoPcm, 0, stereoPcm.size)
                audioTrack.play()

                val durationMs = (monoPcm.size.toDouble() / SAMPLE_RATE * 1000.0).toLong() + 50L
                Thread.sleep(durationMs)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                LogStore.logEvent("AudioAlertManager: Stereo playback error: ${e.message}")
            }
        }
    }

    // ── Public Trigger APIs ──────────────────────────────────────────

    fun playRevLimitAlert() {
        playMono(revLimitPcm)
    }

    fun playSpeed80Alert() {
        playMono(speed80Pcm)
    }

    fun playSpeed150Alert() {
        playMono(speed150Pcm)
    }

    fun playSpeed200Alert() {
        playMono(speed200Pcm)
    }

    fun playLeanAlert(isLeft: Boolean) {
        // Plays directional chime: left ear on left lean, right ear on right lean
        playStereo(leanBlipPcm, isLeftChannel = isLeft, isRightChannel = !isLeft)
    }
}
