package com.looseleaf.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * 设计预览截图器。
 *
 * 注意：这里渲染的是 Main.kt 中的 **Mock 界面**，不是 App 的真实 Composable
 * （真实界面依赖 Android Context / Room / Coil，无法在桌面 JVM 直接跑）。
 * 因此它只能用于检查版式与配色，不能当作回归测试或「UI 自检」。
 */
object SnapshotRunner {
    fun renderToPng(
        widthDp: Int,
        heightDp: Int,
        density: Float = 2.0f,
        outputFile: File,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        val pixelWidth = (widthDp * density).toInt()
        val pixelHeight = (heightDp * density).toInt()
        val scene = ImageComposeScene(
            width = pixelWidth,
            height = pixelHeight,
            density = Density(density)
        )
        scene.setContent(content)
        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
            ?: throw IllegalStateException("Failed to encode PNG")
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(data.bytes)
        scene.close()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting mock preview snapshots across all screens...")
        val outDir = File("build/snapshots")

        val p392 = File(outDir, "profile_standard_392.png")
        renderToPng(392, 852, 2.0f, p392) { MockProfileScreen(isDarkTheme = false) }
        println("Snapshot: ${p392.name} (${p392.length()} bytes)")

        val l392 = File(outDir, "ledger_standard_392.png")
        renderToPng(392, 852, 2.0f, l392) { MockLedgerScreen(isDarkTheme = false) }
        println("Snapshot: ${l392.name} (${l392.length()} bytes)")

        val s392 = File(outDir, "stream_standard_392.png")
        renderToPng(392, 852, 2.0f, s392) { MockStreamScreen(isDarkTheme = false) }
        println("Snapshot: ${s392.name} (${s392.length()} bytes)")

        val pr392 = File(outDir, "projects_standard_392.png")
        renderToPng(392, 852, 2.0f, pr392) { MockProjectsScreen(isDarkTheme = false) }
        println("Snapshot: ${pr392.name} (${pr392.length()} bytes)")

        val d392 = File(outDir, "darkroom_standard_392.png")
        renderToPng(392, 852, 2.0f, d392) { MockDarkroomScreen(isDarkTheme = false) }
        println("Snapshot: ${d392.name} (${d392.length()} bytes)")

        val outCompact = File(outDir, "profile_compact_360.png")
        renderToPng(360, 800, 2.0f, outCompact) { MockProfileScreen(isDarkTheme = false) }
        println("Snapshot: ${outCompact.name} (${outCompact.length()} bytes)")

        val editSheet = File(outDir, "edit_profile_sheet.png")
        renderToPng(392, 540, 2.0f, editSheet) { MockProfileEditSheet(isDarkTheme = false) }
        println("Snapshot: ${editSheet.name} (${editSheet.length()} bytes)")

        println("All snapshots rendered successfully.")
    }
}
