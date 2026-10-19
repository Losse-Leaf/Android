plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.looseleaf.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "LooseLeafDeviceStudio"
            // 与 app 模块版本保持一致；这里只是设计预览工具，不参与发布
            packageVersion = "1.5.0"
        }
    }
}

tasks.register<JavaExec>("renderSnapshots") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.looseleaf.desktop.SnapshotRunner")
}
