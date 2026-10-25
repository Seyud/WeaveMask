# AGENTS.md (app subproject)

Guidelines for AI models operating inside the `app/` subproject. Always include and follow the top-level [`AGENTS.md`](../AGENTS.md).

## 1. Environment & Gradle Setup

- **General Guidelines:** Always follow the top-level [`AGENTS.md`](../AGENTS.md) for general repository rules, environment execution setup, and commit control policies.
- **Working Directory:** Set working directory to `app/` when working on app code.
- **Build Orchestrator:** For real builds, run `./build.py app` from the repo root — it configures the ONDK toolchain and environment automatically.
- **Direct Gradle:** If you must invoke `gradlew` directly (e.g. to verify a dependency change without rebuilding native), run it **from `app/`** (the Gradle project root; running from the repo root fails with `Directory '<repo>' does not contain a Gradle build.`). Verified combo: `./gradlew :apk:assembleRelease :apk:assembleDebug`.

## 2. Architecture & Submodules

Multi-module Gradle project structure:
- **`:apk`** (`apk/`): **Main application APK** (Jetpack Compose + Miuix UI). Primary target for new UI/app features. Package: `io.github.seyud.weave`. Renamed from upstream Magisk's `:app` (root project is still named `Magisk`).
- **`:core`** (`core/`): Core domain logic, resources, Room DB, Retrofit, AIDL services. Primary target for core feature development.
- **`:test`** (`test/`): Application testing target (UIAutomator, always built as release).
- **`:build-logic`** (`build-logic/`): Composite build with custom Gradle plugin (`MagiskPlugin`).

> **Fork divergence from upstream:** upstream Magisk's `:apk-ng` module does **not** exist in this fork. Here `:apk` is the actively developed main app — **not** upstream's maintenance-mode legacy APK. Do not add new features to a nonexistent `apk-ng`.

> **Hidden mode removed (v31.1 sync):** following upstream v31.1, the app-hiding (repackaging) feature and the `:stub`/`:stub-res`/`:shared` modules were removed. Su authorization is explicit in whitelist mode; the interactive `SuRequestActivity` prompt only fires in blacklist (default) mode.

## 3. Development Guidelines

- **Language & UI:** Written in Kotlin/Java. **Prefer Kotlin for all new code.** Uses Jetpack Compose + Miuix for UI (not stock Material3).
- **String Resources:** Default strings in `core/src/main/res/values/strings.xml`. Translations go in `values-[lang]/strings.xml`.
- **Data Stack:** Room, KSP, Wire (Protocol Buffers), Retrofit, Moshi. AIDL for cross-process services.
- **Build Conventions:** Defined in `build-logic/src/main/java/Setup.kt` — compileSdk 37, minSdk 24, targetSdk 37, Java 21, NDK at `$ANDROID_HOME/ndk/magisk`. `TransformApkTask` appends EOCD version metadata; JNI libs renamed (`magisk` → `libmagisk.so`); BusyBox downloaded at build time.

## 4. Workflows & Verification (from `app/`)

Run from the `app/` directory (or use `./build.py <...>` from the repo root):
- **Build Main APK (Debug):** `./gradlew :apk:assembleDebug`
- **Build Main APK (Release, R8):** `./gradlew :apk:assembleRelease`
- **Run Lint:** `./gradlew lint`
- **Run Unit Tests:** `./gradlew test`
- **Clean Artifacts:** `./gradlew clean`

> **Mandatory lint check:** after **any** change under `app/` (Kotlin/Java/resources/Gradle files), run Android lint for the affected modules before concluding — e.g. `./gradlew :apk:lint :core:lint`, or full `./gradlew lint` when multiple/unspecified modules are touched. A successful compile alone is not sufficient verification.
