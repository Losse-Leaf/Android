# 《散页》（Loose Leaf）· Android 原生随行客户端

> **第一现场随行暗房与口袋触觉账本 (The Mobile Darkroom & Tactile Ledger)**  
> 基于 Kotlin + Jetpack Compose 构建的原生 Android 移动端应用程序。  
> 统筹规范：[`docs/mobile.md`](../docs/mobile.md) ｜ 后端契约：[`docs/backend.md`](../docs/backend.md) ｜ 生态总纲：[`docs/README.md`](../docs/README.md)

---

## 核心特性

1. **实体纸本与胶片暗房双模态 (`Paper & Darkroom`)**
   - 严格遵循跨端语义大地色盘（`Earthy Palette`），零硬编码十六进制色值。
   - **暖色棉纸模式 (`Paper`)**：温润宣纸底色 (`#F9F8F5`) 配合微噪点点阵，单手握持温润松弛。
   - **胶片暗房模式 (`Darkroom`)**：碳黑重磅相纸底色 (`#141312`) 配以暗房安全灯赤红，适合深宵微光记录。
   - **银盐装裱相纸框 (`FilmFrame`)**：还原相纸贴在手账本上的物理微隆起，底部自动标注机型、镜头、光圈与曝光等 EXIF 元数据。

2. **极速触觉记账小键盘 (`Tactile Ledger Keypad`)**
   - **单拇指热区与盲打大键位**：4x3 拟物微隆起大键位，单键高度 $\ge 60\text{dp}$。
   - **细腻物理触感反馈 (`Physical Haptics`)**：点击键帽触发轻微打字机震动，点击「盖章记下」触发木印章打击感重震。
   - **分位整型存储 (`Cents Standard`)**：货币金额严格以 `Long` 整型“分”流转，彻底告别浮点精度漂移。
   - **类目光谱横滑栏**：餐饮、交通、购物、娱乐等八大自然大地色谱标签，结合时段智能推荐常用类目。

3. **随行暗房与端侧智能压制管线 (`Image Pipeline & R2 Upload`)**
   - **EXIF 全自动提取**：自动读取相机机型、镜头、焦距、光圈、快门、ISO 及经纬度，智能匹配地级行政区代码。
   - **端侧 4K 降采样**：最长边约束至 3840px，85% JPEG/WebP 压缩，将 15MB~25MB 直拍照无损精简至 1.8MB~2.5MB。
   - **Cloudflare R2 预签名直传**：预签名凭证直传对象存储桶，零服务器应用带宽占用；无 R2 环境自动回退统一中转通道。

4. **离线优先发件箱调度引擎 (`Offline-First Outbox`)**
   - **0ms 乐观 UI 响应**：断网或弱网环境下新增记录立即写入本地 Room 数据库，附带微小的墨色“待同步”徽标。
   - **后台静默同步工作器 (`WorkManager`)**：监听系统网络状态，连通后依序自动重放队列上传，静默更新服务端权威 ID。

5. **免密设备配对与生物识别隐私保护 (`Pairing & Biometrics`)**
   - **动态二维码握手配对**：扫描 Web 展厅管理台动态二维码，一键换取独立专属 `Device Token`。
   - **KeyStore 硬件加密存储**：凭据由 `Android KeyStore` 配合 `EncryptedSharedPreferences` 托管。
   - **生物识别守护 (`BiometricPrompt`)**：支持面容、指纹与系统凭据保护，敏感财务柱状图默认模糊遮罩。

6. **主屏与锁屏小组件 (`App Widgets`)**
   - **城市印记与状态小组件 (2x2)**：动态呈现主理人常居城市、自白信条与今日收支刻度（全数据动态拉取，严禁前端写死）。
   - **桌面快捷小组件**：单触直达触觉记账键盘或随行暗房。

7. **程序化拟真声学引擎 (`AudioEngine`)**
   - 基于 `AudioTrack` 纯算法实时合成机械快门声、纸张翻转声与铅字击打声，零音频文件依赖。

---

## 项目工程架构

```text
android/
├── app/
│   ├── build.gradle.kts                   # 模块构建配置 (Compose, Room, WorkManager, OkHttp)
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml        # 清单声明、权限与小组件 Receiver
│   │   │   ├── java/com/looseleaf/app/
│   │   │   │   ├── LooseLeafApp.kt        # Application 根实例与后台工作器配置
│   │   │   │   ├── MainActivity.kt        # FragmentActivity、Edge-to-Edge 沉浸与底部导航
│   │   │   │   ├── audio/                 # 程序化声学合成引擎 (AudioEngine)
│   │   │   │   ├── darkroom/              # 端侧图片压制管线与 EXIF 提取 (ImagePipeline)
│   │   │   │   ├── data/
│   │   │   │   │   ├── api/               # OkHttp 4 + Kotlinx Serialization 网络客户端 (APIClient)
│   │   │   │   │   ├── local/             # Room 数据库、实体类与 DAO
│   │   │   │   │   ├── security/          # KeyStore 凭据存储与生物识别守护
│   │   │   │   │   └── sync/              # 离线发件箱 WorkManager 与同步管理器 (SyncManager)
│   │   │   │   ├── haptics/               # 物理触觉马达驱动 (HapticsEngine)
│   │   │   │   ├── model/                 # 领域模型与 DTO 信封
│   │   │   │   ├── theme/                 # 大地色盘 (Color.kt)、排版 (Typography.kt) 与主题
│   │   │   │   ├── ui/                    # Jetpack Compose 视图层
│   │   │   │   │   ├── components/        # FilmFrame, NoiseBackground, PaperCard, 岁月留白
│   │   │   │   │   ├── darkroom/          # 随行暗房视图 (DarkroomScreen)
│   │   │   │   │   ├── ledger/            # 极速触觉记账小键盘 (LedgerScreen, TactileKeypad)
│   │   │   │   │   ├── pairing/           # 扫码与凭据入网视图 (PairingScreen)
│   │   │   │   │   ├── profile/           # 主理人展台与设置 (ProfileScreen)
│   │   │   │   │   ├── projectswishes/    # 造物、心愿与山川足迹 (ProjectsWishesScreen)
│   │   │   │   │   └── stream/            # 时光手账时间流 (StreamScreen)
│   │   │   │   ├── utils/                 # FlexTime 弹性时间、分位整型转换与 375 城市字典
│   │   │   │   └── widget/                # Android 桌面小组件提供器
│   │   │   └── res/                       # 布局 XML、小组件配置与 Mipmap 图标
│   │   └── test/                          # 单元测试 (FormatUtilsTest, CityDictionaryTest 等)
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts                       # 根工程构建插件
├── settings.gradle.kts                    # 模块引入与仓库源配置
├── gradle.properties
├── gradlew / gradlew.bat
└── README.md
```

---

## 本地编译与运行

### 1. 环境需求
- **JDK**：OpenJDK 17 或 21
- **Android SDK**：API Level 35 (Compile), API Level 26+ (Min)
- **开发工具**：Android Studio Koala / Ladybug / Meerkat 或 IntelliJ IDEA

### 2. 命令行构建

```bash
# 赋予执行权限
chmod +x gradlew

# 运行单元测试
./gradlew testDebugUnitTest

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

生成安装包路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 3. 连接后端数据中枢
- **Android 模拟器**：默认访问宿主机地址为 `http://10.0.2.2:8088`。
- **真机局域网调试**：在 App 首次启动的「设备入网」界面，将中枢 Hub 地址改为局域网 IP（例如 `http://192.168.1.100:8088`），使用相机扫描 Web 管理后台的配对二维码即可一键入网。

---

## 许可证 (License)

Loose Leaf Project © 2026. All rights reserved.
