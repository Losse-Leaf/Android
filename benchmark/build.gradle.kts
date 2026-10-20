plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.looseleaf.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 基准测试需要访问被测 App 的内部状态（读取 SharedPreferences 里的中枢地址等）
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("junit:junit:4.13.2")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test:rules:1.6.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}

baselineProfile {
    // 被测应用：生成冷启动 / 滚动路径的 AOT 提示
    // 只用真机（useConnectedDevices），需要连接一台已开启的设备或模拟器。
    // 若想用受管设备（Pixel 6 / API 31），在 :app 模块的
    // android.testOptions.managedDevices 中声明后再 managedDevices += "<名字>"。
    useConnectedDevices = true
}
