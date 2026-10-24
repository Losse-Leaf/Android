plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // MOB-P1-22：Baseline Profile 生成（app 模块启用后即可跑 generateBaselineProfile）
    id("androidx.baselineprofile") version "1.4.1" apply false
}
