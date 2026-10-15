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

    // 预合成并进程内复用 PCM 波形缓冲区，避免每次按键重复执行数千次三角函数与指数运算
    private val shutterBuffer: ShortArray by lazy {
        val durationMs = 80
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        ShortArray(totalSamples) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            if (t in 0.0..0.02) {
                val env = exp(-t * 200.0)
                sample += sin(2.0 * PI * 1200.0 * t) * env * 0.4
            }
            if (t in 0.035..0.06) {
                val t2 = t - 0.035
                val env = exp(-t2 * 180.0)
                sample += sin(2.0 * PI * 800.0 * t2) * env * 0.35
            }
            (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private val paperFlipBuffer: ShortArray by lazy {
        val durationMs = 70
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        ShortArray(totalSamples) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / totalSamples
            val freq = 140.0 - (progress * 95.0)
            val env = (1.0 - progress) * (1.0 - progress) * 0.3
            val sample = sin(2.0 * PI * freq * t) * env
            (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private val tinkBuffer: ShortArray by lazy {
        val durationMs = 30
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        ShortArray(totalSamples) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 300.0) * 0.25
            val sample = sin(2.0 * PI * 2200.0 * t) * env
            (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * 机械快门收放声 (Mechanical Shutter Click)
     * 双正弦闭合复音：1200Hz 释放咔哒声 + 35ms 后 800Hz 帘幕闭合声
     */
    fun playShutter() {
        if (!isSoundEnabled) return
        scope.launch {
            playPcm(shutterBuffer)
        }
    }

    /**
     * 纸张翻动微扑通声 (Paper Flip)
     * 140Hz 降至 45Hz 的低频柔和扑通声
     */
    fun playPaperFlip() {
        if (!isSoundEnabled) return
        scope.launch {
            playPcm(paperFlipBuffer)
        }
    }

    /**
     * 铅字/打字机敲击声 (Tink Click)
     */
    fun playTink() {
        if (!isSoundEnabled) return
        scope.launch {
            playPcm(tinkBuffer)
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
