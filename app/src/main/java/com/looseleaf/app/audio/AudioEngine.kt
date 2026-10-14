package com.looseleaf.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 散页拟真拟物声学引擎 (AudioEngine)
 * 严格遵循 docs/ecosystem.md §4.4 与 web/src/utils/audio.ts 物理声学算法
 * 基于 AudioTrack 程序化实时合成 PCM 波形，零依赖、零体积
 */
object AudioEngine {

    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)
    var isSoundEnabled: Boolean = true

    /**
     * 机械快门收放声 (Mechanical Shutter Click)
     * 双正弦闭合复音：1200Hz 释放咔哒声 + 35ms 后 800Hz 帘幕闭合声
     */
    fun playShutter() {
        if (!isSoundEnabled) return
        scope.launch {
            val durationMs = 80
            val totalSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                var sample = 0.0

                // 第 1 声：0ms 处 1200Hz
                if (t in 0.0..0.02) {
                    val env = exp(-t * 200.0)
                    sample += sin(2.0 * PI * 1200.0 * t) * env * 0.4
                }

                // 第 2 声：35ms 处 800Hz
                if (t >= 0.035 && t <= 0.06) {
                    val t2 = t - 0.035
                    val env = exp(-t2 * 180.0)
                    sample += sin(2.0 * PI * 800.0 * t2) * env * 0.35
                }

                buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            playPcm(buffer)
        }
    }

    /**
     * 纸张翻动微扑通声 (Paper Flip)
     * 140Hz 降至 45Hz 的低频柔和扑通声
     */
    fun playPaperFlip() {
        if (!isSoundEnabled) return
        scope.launch {
            val durationMs = 70
            val totalSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / totalSamples
                val freq = 140.0 - (progress * 95.0) // 140 -> 45 Hz
                val env = (1.0 - progress) * (1.0 - progress) * 0.3
                val sample = sin(2.0 * PI * freq * t) * env
                buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            playPcm(buffer)
        }
    }

    /**
     * 铅字/打字机敲击声 (Tink Click)
     */
    fun playTink() {
        if (!isSoundEnabled) return
        scope.launch {
            val durationMs = 30
            val totalSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-t * 300.0) * 0.25
                val sample = sin(2.0 * PI * 2200.0 * t) * env
                buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            playPcm(buffer)
        }
    }

    private fun playPcm(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(buffer.size * 1000L / SAMPLE_RATE + 20)
        } catch (_: Exception) {
            // ignore
        } finally {
            track?.release()
        }
    }
}
