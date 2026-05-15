# Schale Inventory Management · Android

A native Android rewrite of [terry-u16/schale-inventory-management](https://github.com/terry-u16/schale-inventory-management) ([wx257osn2 fork](https://github.com/wx257osn2/terry-u16_schale-inventory-management)), originally a web app.

It is a probability calculator for the "Inventory Management" tile-flipping minigame in the *Blue Archive* event *Schale's Year-End Settlement with the Federal Student Council*. Given the cells you've already revealed, it infers the probability of each item appearing in every unrevealed cell.

## Differences from the web version

| Aspect | Web version | Android version |
|---|---|---|
| Platform | Any browser | Android 10+ (API 29), arm64-v8a |
| UI framework | React + MUI | Jetpack Compose (Material 3) |
| Solver | Rust → WASM | Native Kotlin (same 10-dimensional DP + weighted random sampling, ported) |
| Orientation | Portrait, responsive | **Landscape-locked** (matches the in-game board view) |
| Interaction | Mouse + forms | Touch + steppers + item-placement mode |
| Install | None (PWA optional) | Native APK |

## Features

- **Landscape-first layout**: three columns — level picker / controls on the left, 9×5 board in the center, item configuration on the right.
- **Probability heatmap**: 8 maps for the 2³ item combinations, colored by item type. Single item uses that item's color; combinations use a blue→orange gradient.
- **Recommended-flip hint**: the cell with the highest probability pulses with a golden glow.
- **Item placement mode**: tap a "Place W×H" button on a right-side card to enter placement mode, then tap any legal spot on the board to drop the item. Rectangular items get both an "Original" and a "Rotated" button.
- **Immersive fullscreen**: status bar and navigation bar hidden, content extended into the display cutout via `short edges`, avoiding accidental gesture triggers.
- **Keep screen on**: the screen will not auto-sleep while calculating.
- **Dark UI**: aligned with the in-game art style, easier on the eyes during long sessions.

## Project structure

```
setokai-inventory/
├── app/
│   ├── build.gradle.kts            # AGP 8.5, minSdk 29, abiFilters arm64-v8a
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml     # screenOrientation=sensorLandscape, fullscreen
│       ├── kotlin/net/terryu16/schale/inventory/
│       │   ├── MainActivity.kt              # Entry point + immersive mode + keep-screen-on
│       │   ├── data/
│       │   │   ├── Models.kt                # Board / Item / ItemGroup / PlacedItem
│       │   │   └── Presets.kt               # 7 level presets
│       │   ├── solver/
│       │   │   ├── GameState.kt             # Solver input state + 2D prefix sums
│       │   │   └── Solver.kt                # 10-D DP + full enumeration / random sampling
│       │   └── ui/
│       │       ├── InventoryViewModel.kt    # State management + background coroutine compute
│       │       ├── HomeScreen.kt            # Three-column layout
│       │       ├── theme/Theme.kt
│       │       ├── board/BoardCanvas.kt     # 9×5 board Canvas
│       │       └── panel/
│       │           ├── PresetSidebar.kt     # Left: levels + controls
│       │           └── ItemSidePanel.kt     # Right: item configuration
│       └── res/                              # Theme, strings, icons
├── gradle/wrapper/                           # Gradle 8.7 wrapper
├── gradlew / gradlew.bat
├── build.gradle.kts / settings.gradle.kts
└── gradle.properties
```

## Build

### Prerequisites

| Tool | Minimum version | Notes |
|---|---|---|
| JDK | 17 | Must be 17 or higher (required by AGP 8.5) |
| Android SDK | `platforms;android-34`, `build-tools;34.0.0` | Installed automatically via `cmdline-tools` |
| Gradle | 8.7 | Project ships a wrapper — no manual install needed |

### Option 1: Android Studio (easiest)

1. Open this directory with Android Studio Hedgehog (2023.1.1) or newer.
2. Wait for Gradle sync to complete (3–10 minutes on first run while dependencies download).
3. In the top toolbar, pick the `app` configuration → connect a device or start an emulator → Run.
4. To produce a release APK: `Build → Generate Signed Bundle / APK → APK`.

### Option 2: Command line (no Android Studio)

```powershell
# 1. Set ANDROID_HOME (point to the parent of cmdline-tools)
$env:ANDROID_HOME = "C:\Android\sdk"
$env:Path += ";$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools"

# 2. Install required SDK components (first time only)
sdkmanager.bat "platform-tools" "platforms;android-34" "build-tools;34.0.0"
sdkmanager.bat --licenses  # accept all with y

# 3. Build a release APK from the project root
.\gradlew.bat assembleRelease

# Output:
# app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

Debug build: `.\gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-arm64-v8a-debug.apk`

### Install on a device

```powershell
# Install via ADB to a connected Android device
# (enable Developer Options + USB debugging first)
adb install -r app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

Alternatively, transfer the APK to the phone and install it through a file manager (you'll need to allow installs from unknown sources in system settings).

## Probability calculation

The assumptions and algorithm are identical to the original web version:

- Item placements are sampled **uniformly at random** across all valid configurations (the in-game distribution may have biases, which are not modeled).
- Item placements are determined before any cell is revealed.
- Configuration counts are tallied with a 10-dimensional dynamic program: `dp[col][row][cnt0][cnt1][cnt2][w0..w4]`.
- When the total number of configurations is ≤ 100,000, all configurations are enumerated. Otherwise, the DP table is sampled via weighted random backtracking, 100,000 times.
- The output is `2³ = 8` probability maps (one per on/off combination of the three item types). Three color-coded buttons on the left panel switch the active view.

The algorithm is ported from `terry-u16/schale-inventory-management/wasm/src/solver/counter.rs`. Behavior matches; performance on Pixel 6-class hardware is roughly 1–3 seconds for typical levels, and 5–10 seconds for very large configuration spaces (e.g., level 7 with no cells revealed).

## License

MIT — same as the upstream project.