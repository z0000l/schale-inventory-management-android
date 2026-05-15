# シャーレ在库管理 · Android 版

用Claude把 [terry-u16/schale-inventory-management](https://github.com/terry-u16/schale-inventory-management)（[wx257osn2 fork](https://github.com/wx257osn2/terry-u16_schale-inventory-management)）从 Web 应用重写为现代 Android 原生应用。

游戏《碧蓝档案》活动「シャーレの総決算 with 联邦学生会」中"在库管理"翻牌玩法的概率计算器：根据已翻开的格子推断每个未翻开格子里出现各物品的概率。

## 与原 Web 版的差异

| 维度 | Web 版 | Android 版 |
|---|---|---|
| 平台 | 任意浏览器 | Android 10+ (API 29)，arm64-v8a |
| UI 框架 | React + MUI | Jetpack Compose (Material 3) |
| 计算引擎 | Rust → WASM | Kotlin 原生（移植同款 10 维 DP + 加权随机采样） |
| 屏幕方向 | 竖屏自适应 | **强制横屏**（贴合游戏中翻牌画面） |
| 交互 | 鼠标点击 + 表单 | 触屏点击 + 步进器 + 物品放置模式 |
| 安装 | 无需安装（PWA 可加） | 原生 APK |

## 应用特性

- **横屏专属设计**：三栏布局——左侧关卡选择/控制、中央 9×5 棋盘、右侧物品配置。
- **概率热力图**：8 种物品组合（2³）对应 8 张概率图，按物品颜色着色；单选物品时使用该物品色，多物品组合时使用蓝→橙渐变。
- **推荐翻开提示**：概率最大的格子带金色呼吸光晕。
- **物品放置模式**：从右侧卡片点击"放置 W×H"按钮进入模式，点击棋盘任意合法位置即放置；矩形物品有"原向 / 旋转"两个按钮。
- **沉浸全屏**：隐藏状态栏与导航栏，刘海屏延伸到 short edges，避免误触系统手势区。
- **常亮屏幕**：计算时不会自动息屏。
- **暗色 UI**：与游戏内画风协调，长时间盯屏更舒适。

## 项目结构

```
setokai-inventory/
├── app/
│   ├── build.gradle.kts            # AGP 8.5, minSdk 29, abiFilters arm64-v8a
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml     # screenOrientation=sensorLandscape, fullscreen
│       ├── kotlin/net/terryu16/schale/inventory/
│       │   ├── MainActivity.kt              # 入口 + 沉浸模式 + keep-screen-on
│       │   ├── data/
│       │   │   ├── Models.kt                # Board/Item/ItemGroup/PlacedItem
│       │   │   └── Presets.kt               # 7 个关卡预设
│       │   ├── solver/
│       │   │   ├── GameState.kt             # 求解器输入态 + 二维累积和
│       │   │   └── Solver.kt                # 10 维 DP + 全枚举/随机采样
│       │   └── ui/
│       │       ├── InventoryViewModel.kt    # 状态管理 + 后台协程计算
│       │       ├── HomeScreen.kt            # 三栏布局
│       │       ├── theme/Theme.kt
│       │       ├── board/BoardCanvas.kt     # 9×5 棋盘 Canvas
│       │       └── panel/
│       │           ├── PresetSidebar.kt     # 左侧关卡+控制
│       │           └── ItemSidePanel.kt     # 右侧物品配置
│       └── res/                              # 主题、字符串、图标
├── gradle/wrapper/                           # Gradle 8.7 wrapper
├── gradlew / gradlew.bat
├── build.gradle.kts / settings.gradle.kts
└── gradle.properties
```

## 构建

### 前置条件

| 工具 | 最低版本 | 说明 |
|---|---|---|
| JDK | 17 | 必须 17 或更高（AGP 8.5 要求） |
| Android SDK | platforms;android-34, build-tools;34.0.0 | 通过 cmdline-tools 自动安装 |
| Gradle | 8.7 | 项目自带 wrapper，无需手动安装 |

### 方式一：Android Studio（最简单）

1. 用 Android Studio Hedgehog (2023.1.1) 或更新版本"Open"本目录。
2. 等 Gradle 同步完毕（首次约 3-10 分钟，下载依赖）。
3. 顶部工具栏选择 `app` 配置 → 接上设备或开模拟器 → Run。
4. 打包发布版 APK：`Build → Generate Signed Bundle / APK → APK`。

### 方式二：命令行（无 Android Studio）

```powershell
# 1. 设置 ANDROID_HOME（指向 cmdline-tools 安装目录的上级）
$env:ANDROID_HOME = "C:\Android\sdk"
$env:Path += ";$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools"

# 2. 安装必需的 SDK 组件（首次）
sdkmanager.bat "platform-tools" "platforms;android-34" "build-tools;34.0.0"
sdkmanager.bat --licenses  # 一路 y

# 3. 在项目根目录构建 release APK
.\gradlew.bat assembleRelease

# 输出位置：
# app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

调试包：`.\gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-arm64-v8a-debug.apk`

### 安装到手机

```powershell
# 用 ADB 装到已连接的 Android 设备（先开开发者模式 + USB 调试）
adb install -r app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

或者把 APK 传到手机后用文件管理器点击安装（需要在系统设置中允许"未知来源"）。

## 概率计算说明

完全沿用原 Web 版的假设与算法：

- 物品的放置在所有可能配置中**一致随机**抽取（不考虑游戏内潜在的概率分布偏好）。
- 物品放置在翻开格子之前就已确定。
- 用 10 维动态规划 `dp[col][row][cnt0][cnt1][cnt2][w0..w4]` 统计配置总数。
- 总数 ≤ 100,000 时枚举全部配置；否则按场景数权重在 DP 表上随机回溯采样 100,000 次。
- 输出 `2^3 = 8` 张概率图（对三种物品的开关组合），UI 上通过左侧三个色块按钮选择当前展示哪几种。

算法移植自 `terry-u16/schale-inventory-management/wasm/src/solver/counter.rs`，逻辑一致，性能在 Pixel 6 级别设备上约 1–3 秒（典型关卡）；超大配置空间（如关卡 7 全部未翻开）约 5–10 秒。

## License

MIT - 与原项目保持一致。
