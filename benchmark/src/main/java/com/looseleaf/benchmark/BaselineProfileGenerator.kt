package com.looseleaf.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器 (MOB-P1-22 / AND-1)
 *
 * 生成用于预编译的 baseline-prof.txt，以加快冷启动并减少首屏 Jank。
 *
 * 运行方式（需要 API 33+ 的真机或**不含 Google Play** 的模拟器）：
 * ```
 * ./gradlew :app:generatePublicReleaseBaselineProfile
 * # 若设备上已配对的是局域网变体：
 * ./gradlew :app:generateLanReleaseBaselineProfile
 * ```
 * 生成后把 `app/src/<flavor>Release/generated/baselineProfiles/baseline-prof.txt`
 * 复制到 `app/src/main/baseline-prof.txt`。
 *
 * 交互脚本覆盖（不再只有 `startActivityAndWait()`）：
 * 冷启动 → 时间流首屏 → 滚动 3 页 → 打开详情 → 返回 → 切账本 → 滚动 → 回时间流。
 * 未配对时脚本会优雅退化为「只采集配对页」，不会让生成任务失败。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        // 被测包名随变体变化（public = com.looseleaf.app，lan = com.looseleaf.app.lan）。
        // 关键：独立 test 模块里 targetContext.packageName 是 **benchmark 自己**，
        // 直接拿来用会让 BaselineProfileRule 去 force-stop 正在跑测试的进程 ⇒ Process crashed。
        val packageName = BuildConfig.TESTED_PACKAGE

        baselineProfileRule.collect(
            packageName = packageName,
            includeInStartupProfile = true
        ) {
            // 冷启动
            startActivityAndWait()
            device.waitForIdle()

            // 等首屏出现：已配对时是时间流，未配对时是入网页
            val streamVisible = device.wait(
                Until.hasObject(By.textContains("时光生活刻度")),
                15_000
            )
            if (!streamVisible) {
                device.wait(Until.hasObject(By.textContains("随行入网")), 5_000)
                // 未配对：只采集启动 + 入网页即可，后续交互没有意义
                return@collect
            }

            // 时间流滚动 3 页（触发分页加载、行模型装配、图片解码）
            repeat(3) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
                device.waitForIdle()
            }

            // 打开详情：点击列表区域中部（比依赖具体文案稳定），再返回
            val width = device.displayWidth
            val height = device.displayHeight
            device.click(width / 2, (height * 0.45f).toInt())
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            // 切换账本：触发账本首屏（分类 / 信贷 / 资产并行拉取）与月度列表
            device.findObject(By.text("账本"))?.click()
            device.waitForIdle()
            repeat(2) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
                device.waitForIdle()
            }

            // 回到时间流，让滚动位置与首屏路径也进入 profile
            device.findObject(By.text("时间流"))?.click()
            device.waitForIdle()
        }
    }
}
