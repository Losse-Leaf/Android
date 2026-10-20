package com.looseleaf.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动性能基准 (MOB-P1-22)
 *
 * 目标：把「冷启动 < 700ms」变成**可回归的数字**，而不是靠手感。
 * 每次发版跑一次 `Pixel6`（或真机）的冷启动，
 * 若启动时间劣化超过阈值，CI 就会失败。
 *
 * 运行方式（需要连接真机或已开机的模拟器）：
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ./gradlew :app:generateBaselineProfile      # 生成并安装 Baseline Profile
 * ```
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = {
            // 回到桌面，模拟「用户从桌面点开」
            pressHome()
        }
    ) {
        // 冷启动：拉起进程并等到首帧
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            // 先冷启动一次，再测「切回前台」
            pressHome()
            startActivityAndWait()
        }
    ) {
        startActivityAndWait()
    }

    companion object {
        const val TARGET_PACKAGE = "com.looseleaf.app"
    }
}
