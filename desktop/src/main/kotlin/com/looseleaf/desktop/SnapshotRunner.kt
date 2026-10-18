package com.looseleaf.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

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
        println("Starting headless UI self-check snapshots...")
        val outCompact = File("build/snapshots/profile_compact_360.png")
        renderToPng(360, 800, 2.0f, outCompact) {
            MockProfileScreen(isDarkTheme = false)
        }
        println("Compact snapshot: ${outCompact.absolutePath} (${outCompact.length()} bytes)")

        val outStandard = File("build/snapshots/profile_standard_392.png")
        renderToPng(392, 852, 2.0f, outStandard) {
            MockProfileScreen(isDarkTheme = false)
        }
        println("Standard snapshot: ${outStandard.absolutePath} (${outStandard.length()} bytes)")
    }
}
