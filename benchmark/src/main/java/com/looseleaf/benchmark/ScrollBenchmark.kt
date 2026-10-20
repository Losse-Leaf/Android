package com.looseleaf.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 滚动流畅度基准 (MOB-P1-22)
 *
 * 时间流是全 App 最重的列表（卡片 + 图片网格 + 富文本），
 * 这里用 `FrameTimingMetric` 量它的掉帧率，
 * 让「滑动是否跟手」变成 CI 里的一条曲线而不是主观感受。
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollStreamTimeline() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        // 反复上下滑动时间流，制造足够的帧样本
        device.waitForIdle()
        repeat(6) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 40)
            device.waitForIdle()
        }
        repeat(6) {
            device.swipe(device.displayWidth / 2, device.displayHeight / 4, device.displayWidth / 2, device.displayHeight * 3 / 4, 40)
            device.waitForIdle()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.looseleaf.app"
    }
}
