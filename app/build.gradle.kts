import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    // MOB-P1-22：让 app 模块具备生成 Baseline Profile 的能力
    // （需要配合 :benchmark 模块与真机/模拟器）
    id("androidx.baselineprofile")
}

android {
    namespace = "com.looseleaf.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.looseleaf.app"
        // MOB-P2-56：只保留中英文，裁掉 AppCompat / Material3 带来的多语言资源
        resourceConfigurations += listOf("zh", "en")
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "1.6.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MOB-P1-62：明文 HTTP 的准入按「使用场景」切成两个发行变体。
        // 默认（公网版）只允许 https；局域网版由 productFlavor 覆盖为 true。
        manifestPlaceholders["cleartextTraffic"] = "false"
    }

    /**
     * MOB-P1-62：发行变体 —— 把「系统层是否放行明文」按场景切开。
     *
     * - `public`：中枢在公网（有域名）⇒ 只允许 https，系统层直接禁掉明文。
     * - `lan`：中枢在自家局域网，或通过 Tailscale / WireGuard 等组网工具异地访问
     *   ⇒ 允许 http，但**应用层**仍然只放行私有地址与组网网段（见 HubUrlPolicy）。
     *
     * 两个变体**用同一把签名密钥**、但**包名不同**（applicationIdSuffix）⇒
     * 可以并存、随时切换，且切换只是普通覆盖更新，不会丢本地数据。
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("public") {
            dimension = "distribution"
            manifestPlaceholders["cleartextTraffic"] = "false"
        }
        create("lan") {
            dimension = "distribution"
            manifestPlaceholders["cleartextTraffic"] = "true"
            applicationIdSuffix = ".lan"
            // 刻意**不加** versionNameSuffix：内置更新器拿本地 versionName 与远端
            // tag 比大小，带了 "-lan" 后缀会永远判成「已是最新」，局域网用户就收不到更新了。
            // 两个变体靠包名与桌面名称区分即可。
        }
    }

    signingConfigs {
        create("release") {
            // 沿用仓库内的原发布密钥（alias looseleaf），保证 1.4.x 可以直接覆盖升级
            //
            // MOB-P2-57：口令不再硬编码在构建脚本里（仓库一旦泄露，任何人都能伪造
            // 同包名的更新包）。改为从 local.properties 读取 —— 该文件已被 .gitignore 忽略。
            // 首次配置见 docs/mobile-optimization.md §附录 C。
            val localProps = Properties()
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
                localProps.load(it)
            }
            storeFile = file("release.keystore")
            // MOB-P2-57 的后续修正（CI 全红的根因）：
            // 口令缺失**不能在配置期就抛错** —— signingConfigs 是配置阶段求值的，
            // 之前用 error(...) 直接炸，导致连 testDebugUnitTest / lint 这种
            // 根本不签名的任务都跑不起来（CI 从那次改动起就一直在红）。
            // 现在改成**惰性 Provider**：只有真正要用 release 签名时才解析口令；
            // CI 的发布流水线另有「Verify signing secrets are present」前置校验，
            // 缺 Secret 会在构建之前就给出可操作的报错。
            storePassword = providers.gradleProperty("LOOSELEAF_STORE_PASSWORD").orNull
                ?: localProps.getProperty("LOOSELEAF_STORE_PASSWORD")
                ?: ""
            // MOB-P1-xx：别名也走 -P / 环境变量 ——
            // 原先只读 local.properties，而 CI 环境里根本没有这个文件，
            // 别名只能落到硬编码的 "looseleaf"。一旦换密钥时用了别的别名，
            // CI 会在签名阶段报一句难懂的 "Cannot find key with alias"，
            // 而不是在校验阶段就告诉你「别名对不上」。
            keyAlias = providers.gradleProperty("LOOSELEAF_KEY_ALIAS").orNull
                ?: providers.environmentVariable("LOOSELEAF_KEY_ALIAS").orNull
                ?: localProps.getProperty("LOOSELEAF_KEY_ALIAS")
                ?: "looseleaf"
            keyPassword = providers.gradleProperty("LOOSELEAF_KEY_PASSWORD").orNull
                ?: localProps.getProperty("LOOSELEAF_KEY_PASSWORD")
                ?: ""
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }
        }
    }
}

ksp {
    // 导出 Room schema，供迁移对比与 MigrationTestHelper 使用
    arg("room.schemaLocation", "${projectDir.absolutePath}/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // MOB-P1-20：启动闪屏（消除冷启动的「白闪 → 纸色 → 内容」三段跳变）
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // ProcessLifecycleOwner：区分「应用切后台」与「Activity 被系统相机/相册临时盖住」
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // MOB-P2-55：@Preview 只是编译期注解，运行时不需要 ——
    // 放在 release classpath 会把整包预览工具打进正式包。
    compileOnly("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // MOB-P2-54：移除 material-icons-extended。
    // 这个包会把 debug 构建的 dex 撑到 20MB+（release 经 R8 虽只剩 3MB，
    // 但构建耗时与内存占用都被它拖着走）。
    // 项目里用到的 38 个图标已全部转成本地矢量（res/drawable/ic_*.xml），
    // 路径数据直接从本依赖的原始字节码里还原，与原图标像素级一致。
    // 仍在使用的少量 core 图标（Check / Close / Delete …）由 material3 自带的
    // material-icons-core 提供，无需额外依赖。
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Kotlinx Serialization & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Local Database (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Background sync (WorkManager)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Security & Biometrics
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // 启动性能：接收云端下发的 Baseline Profile
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Camera & EXIF
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // QR Code Scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // MOB-P1-71：JVM 侧（Robolectric）直接跑 Room 的真实 SQLite ——
    // 分页查询 / 增量游标 / 迁移这类逻辑必须在 CI 里可回归，而不是靠手点。
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
