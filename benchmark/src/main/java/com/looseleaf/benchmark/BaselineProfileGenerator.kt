package com.looseleaf.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器 (MOB-P1-22)
 *
 * 这将生成用于预编译的 baseline-prof.txt 以显著加快冷启动和减缓运行时的 Jank（卡顿）。
 *
 * 运行方式（需要连接真机或已开机的不包含 Google Play 且版本大于 P 的模拟器）：
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 * 生成后将 benchmark/build/outputs/managed_device_android_test_additional_output/.../baseline-prof.txt
 * 复制到 app/src/main/baseline-prof.txt
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.looseleaf.app",
        includeInStartupProfile = true
    ) {
        // 冷启动应用
        startActivityAndWait()

        // 此处可以加入关键的后续交互脚本，例如等待首页的内容渲染，
        // 这样可以进一步让这部分 UI 组件被提前 AOT 编译。
    }
}
