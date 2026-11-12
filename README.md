# 《散页》（Loose Leaf）· Android 随行端

> Kotlin + Jetpack Compose 原生 Android 应用：第一现场速记、口袋触觉账本、随行影像装裱。
> 工程规范：[`docs/mobile.md`](../docs/mobile.md) ｜ 后端契约：[`docs/backend.md`](../docs/backend.md) ｜ 跨端约定：[`docs/README.md`](../docs/README.md) §四

---

## 核心特性

1. **纸本双模态**
   - 暖色棉纸（`paper`）/ 暗房安全灯（`darkroom`）/ 跟随系统（`system`）三态外观，首帧前读取，冷启动不闪白。
   - 全部颜色走 `LooseLeafTheme.colors` 语义令牌，代码零硬编码色值；纸面微噪点为 128×128 确定性瓦片平铺。

2. **极速触觉记账**
   - 4×3 大键位键盘，单键 ≥ 60dp，按下变色 + 触觉反馈；金额全程 `Long` 整型「分」，不用浮点。
   - 备注支持自然语义快记（`42.5 午餐手冲咖啡` → 金额 + 分类 + 标题），金额未敲时实时提示「识别到 ¥42.50 · 餐饮」，盖章自动采纳。
   - 通知监听自动记账：白名单微信 / 支付宝 / 银联 / 主流银行，5 分钟去重窗口，过滤验证码与营销文案。

3. **随行影像**
   - 选图即读 EXIF（机型 / 镜头 / 光圈 / 快门 / ISO / GPS），按坐标反查城市。
   - 端侧压到最长边 2048px、JPEG 82，再走预签名直传；离线时先落 `filesDir/outbox_media/` 补传。

4. **离线优先**
   - 写入先落 Room + 发件箱，界面 0 等待上屏，`WorkManager` 联网补传。
   - 游标增量拉取、墓碑对齐删除；失败事件不静默丢弃，顶栏邮戳与全局横幅给「同步异常 N 项」入口。

5. **配对与门禁**
   - 扫码或粘贴链接两通道，`HubUrlPolicy` 校验中枢地址（公网强制 https，局域网放行 http）。
   - 设备令牌存 `EncryptedSharedPreferences`（KeyStore），不可用时**拒绝配对**而非明文降级；生物识别按「应用整体切后台」重锁。

6. **无音频**
   - 不申请 `VIBRATE`、不持有音频焦点、无音效资源；触觉走 `LocalHapticFeedback`（零权限）。

---

## 工程结构

| 模块 | 职责 |
| :--- | :--- |
| `core/` | 实体模型、主题令牌、通用组件、`APIClient`、`CredentialStore`、`FormatUtils`、城市字典 |
| `feature-ledger/` | 账本域全部数据与 UI：独立 `looseleaf_ledger.db`、记账键盘、资产/分期/信贷、通知监听 |
| `app/` | 应用壳：单 Activity、路由、时间流 / 工坊 / 展台 / 设置 / 配对、`SyncManager`、更新器 |
| `benchmark/` | Baseline Profile 生成（需真机，手动跑，不进 CI 门禁） |

依赖方向严格单向：`app → feature-ledger → core`。

```
app/src/main/java/com/looseleaf/app/
├── MainActivity.kt          # 单 Activity、路由栈、生物识别门禁、全局离线横幅
├── LooseLeafApp.kt          # Application：token 注入、后台预热、发件箱调度
├── data/{api,local,security,sync}/   # OkHttp 客户端、Room、KeyStore、同步中枢
├── darkroom/ImagePipeline.kt         # EXIF 提取、降采样、编码、直传
├── ui/{stream,projectswishes,profile,pairing,components}/
└── theme/ utils/ updater/ reminder/
core/ 与 feature-ledger/ 各自独立成模块，结构同理。
```

---

## 本地构建

```bash
# 本机自带 JDK 17（系统默认 java 版本跑不了 AGP）
export JAVA_HOME="$PWD/.jdk17/Contents/Home"

./gradlew :app:assemblePublicDebug      # 公网版（只允许 https）
./gradlew :app:assembleLanDebug         # 局域网版（包名带 .lan）
./gradlew :app:testPublicDebugUnitTest  # 单测
./gradlew :app:lintPublicDebug          # 静态检查（0 error 为门禁）
```

- 环境：JDK 17、Android SDK **API 35**（minSdk 26）、AGP 8.7+。
- 产物：`app/build/outputs/apk/public/debug/`。
- 包名：`public` → `com.looseleaf.app`；`lan` → `com.looseleaf.app.lan`。两者同一把签名密钥，可并存覆盖升级。

---

## 连接后端中枢

- **模拟器**：宿主机地址固定 `http://10.0.2.2:8088`。
- **真机局域网**：入网页填局域网 IP（如 `http://192.168.1.100:8088`）或 Tailscale 节点，扫 Web 管理台二维码即可入网。
- 公网部署必须 https；`lan` 变体也只在私有网段 / 回环 / 组网段放行 http。

---

## 发布签名

发布包使用仓库内 `app/release.keystore`（alias `looseleaf`），CI 以它签名，`assembleRelease` 产物可直接覆盖安装。

应用内更新会比对下载 APK 与已装应用的签名，不一致时提示「需卸载重装」，避免系统安装器抛裸错误。

---

## 许可证

Loose Leaf Project © 2026. All rights reserved.
