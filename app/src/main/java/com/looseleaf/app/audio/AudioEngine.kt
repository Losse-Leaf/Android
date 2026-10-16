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
 * 深度优化：采用预载入静态 AudioTrack 零延迟重放机制，杜绝频繁动态建构 AudioTrack 与 Thread.sleep 造成的线程饥饿与底层崩溃
 */
object AudioEngine {

    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)
    var isSoundEnabled: Boolean = true

    // 1. 机械快门波形
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

    // 2. 纸张翻动波形
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

    // 3. 铅字打字机敲击波形
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

    // 常驻静态重放轨道，进程生命周期复用
    private val shutterTrack: AudioTrack? by lazy { createStaticTrack(shutterBuffer) }
    private val paperFlipTrack: AudioTrack? by lazy { createStaticTrack(paperFlipBuffer) }
    private val tinkTrack: AudioTrack? by lazy { createStaticTrack(tinkBuffer) }

    private fun createStaticTrack(buffer: ShortArray): AudioTrack? {
        return try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 机械快门收放声 (Mechanical Shutter Click)
     */
    fun playShutter() {
        if (!isSoundEnabled) return
        scope.launch {
            playTrack(shutterTrack, shutterBuffer)
        }
    }

    /**
     * 纸张翻动微扑通声 (Paper Flip)
     */
    fun playPaperFlip() {
        if (!isSoundEnabled) return
        scope.launch {
            playTrack(paperFlipTrack, paperFlipBuffer)
        }
    }

    /**
     * 铅字/打字机敲击声 (Tink Click)
     */
    fun playTink() {
        if (!isSoundEnabled) return
        scope.launch {
            playTrack(tinkTrack, tinkBuffer)
        }
    }

    private fun playTrack(track: AudioTrack?, fallbackBuffer: ShortArray) {
        if (track != null) {
            try {
                synchronized(track) {
                    track.pause()
                    track.reloadStaticData()
                    track.play()
                }
                return
            } catch (_: Exception) {
                // 异常时走一次性兜底
            }
        }

        // 兜底降级：单次轻量级播放
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
            val tempTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(fallbackBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            tempTrack.write(fallbackBuffer, 0, fallbackBuffer.size)
            tempTrack.play()
        } catch (_: Exception) {
            // ignore
        }
    }
}
