# WeaveMask — Agent Guide

A Magisk fork with Miuix UI (Jetpack Compose). Native (C++/Rust) + Android app (Kotlin/Compose).

## Build Commands

All real builds go through `build.py` (Python 3.8+). `build.py` imports `scripts/env.py` internally and configures the environment, toolchains, and cross-compilation flags itself — no `scripts/env.py` prefix needed.

Standalone tool invocations outside `build.py` must be prefixed with `scripts/env.py` — except app-only `gradlew` (no native rebuild), which may run plain from `app/` (see below):
- native tools (`cargo`, `rustc`, `ndk-build`): `scripts/env.py cargo ...` — on Windows, the only way ONDK DLLs get onto `PATH`
- app-only gradlew: `cd app && ./gradlew :apk:assembleDebug` — see "Direct Gradle invocation" below

```bash
./build.py ndk          # Download and install ONDK (required first)
./build.py all          # Build everything (native + app + test APK)
./build.py native       # Build native binaries only
./build.py app          # Build the Magisk app APK
./build.py test         # Build the test app
./build.py clean        # Clean all build artifacts
./build.py gen --abi arm64-v8a  # Generate IDE compilation database
./build.py clippy       # Run clippy on Rust sources
```

Flags: `-r` for release, `-v` for verbose, `-c <file>` for custom config (default: `config.prop`).

CI uses `python build.py -v -c .github/ci.prop all` (arm64-v8a only).

### Direct Gradle invocation (gotchas)

`build.py` wraps Gradle with the right environment and flags — always prefer it. If you must invoke `gradlew` directly (e.g. to verify a dependency change without rebuilding native):

- The Gradle project root is **`app/`**, not the repo root. `gradlew`/`gradlew.bat` live in `app/` and must be run with `app/` as the working directory. Running from the repo root fails with: `Directory '<repo>' does not contain a Gradle build.`
- The main app module was renamed to **`:apk`** (root project name is still `Magisk`, per `app/settings.gradle.kts`). Upstream Magisk's `:app:*` task names do **not** exist here — `:app:assembleRelease` fails with "project 'app' not found". Use `:apk:assembleRelease` / `:apk:assembleDebug`.
- Verified full combo: `cd app && ./gradlew :apk:assembleRelease :apk:assembleDebug` (both variants; release runs R8).
- Toolchain (after the v31.1 sync): Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.20, KSP 2.3.12 — declared in `app/gradle/libs.versions.toml` / `app/gradle/wrapper/gradle-wrapper.properties`.
- Signing falls back to the debug keystore when `config.prop` has no `keyStore`, so `assembleRelease` works out of the box locally.

## Prerequisites

- `ANDROID_HOME` environment variable pointing to Android SDK
- `ANDROID_STUDIO` (optional, auto-discovers bundled JDK) or JDK 21 in PATH
- Windows: enable Developer Mode for symlink support
- `./build.py ndk` must be run before any native build — it downloads ONDK r30.0 to `$ANDROID_HOME/ndk/magisk`
- `sccache` or `ccache` in PATH will be auto-detected and used for build caching

## Architecture

### It is a fork — expect divergence from upstream Magisk

Remotes:
- `origin` → github.com/Seyud/WeaveMagisk
- `upstream` → github.com/topjohnwu/Magisk
- `magisk` → local `D:/Code/src/Magisk` (a separate upstream checkout for diffing / cherry-picking)

When syncing upstream commits (`git cherry-pick magisk/<sha>`), **expect conflicts in `app/gradle/libs.versions.toml` and `app/gradle/wrapper/gradle-wrapper.properties`** — those files have diverged (see below). A dry-run in a throwaway worktree first is cheap. Port the intent, resolve by hand; most version lines auto-merge, the toolchain block and strategy-diverged lines do not.

### Native (`native/src/`)

Rust workspace + C++ via ndk-build. Workspace members: `base`, `boot`, `core`, `init`, `sepolicy`.

Build targets: `magisk`, `magiskinit`, `magiskboot`, `magiskpolicy`, `resetprop`. Rust builds first, then C++ links against Rust static libs.

Cargo config at `native/src/.cargo/config.toml`. Rust edition 2024. Clippy denies `unwrap_used`. rustfmt: `imports_granularity = "Module"`.

External dependencies are git submodules in `native/src/external/` — always clone with `--recurse-submodules`.

Generated code: `native/out/generated/flags.h` and `flags.rs` are created during build. Run `./build.py native` before IDE work on native code.

Full native subproject guide: [`native/AGENTS.md`](native/AGENTS.md).

### App (`app/`)

Gradle composite build. Modules:
- **apk** — Main application (Jetpack Compose + Miuix). Package: `io.github.seyud.weave` (renamed from upstream Magisk's `:app`)
- **core** — Library module with business logic, Room DB, Retrofit, AIDL
- **test** — UI test APK (UIAutomator). Always built as release
- **build-logic** — Composite build with custom Gradle plugin (`MagiskPlugin`)

Full app subproject guide: [`app/AGENTS.md`](app/AGENTS.md).

Build conventions defined in `app/build-logic/src/main/java/Setup.kt`:
- `compileSdk` 37, `minSdk` 24, `targetSdk` 37
- Java 21 source/target compatibility
- NDK path hardcoded to `$ANDROID_HOME/ndk/magisk`
- Custom `TransformApkTask` adds EOCD comments with version metadata
- JNI libs renamed: `magisk` → `libmagisk.so`, etc.
- BusyBox downloaded at build time from GitHub releases

Version config: `app/gradle.properties` (`magisk.versionCode`) and `config.prop` (overrides). `build.py` generates `app/build/flags.prop` with `version`, `versionCode`, `abiList`.

#### Version catalog divergences from upstream

- UI deps come from a Compose BOM (`composeBom`) + Miuix — not upstream's standalone `lifecycle`/`compose-ui`/`compose-m3` pins.
- `moshix` plugin is renamed to `moshi`; no `navigation-safeargs` (this fork uses `miuix-nav`).
- `com.google.android.material:material` is still a **hard dependency**, but not for the Compose UI (that's Miuix). All Activity themes inherit `Theme.MaterialComponents.*` — the AppCompat-required shell that hosts the Compose content — and the WebUI (WebView) screens use Material widgets/styles. `MotionRevealHelper.kt` (FAB/CircularReveal) is dead code, unused.

### Navigation & UI

- `miuix-nav` (Miuix's navigation runtime) with a custom type-safe `Navigator` over a serializable `NavBackStack`; Miuix owns the transitions and predictive back, entry state/ViewModel scopes come from `NavDisplay` (replaces Fragment Navigation + SafeArgs)
- Miuix component library (not standard Material3)
- Liquid Glass effects: `CombinedBackdrop`, `InnerShadow`, `Lens`, `Vibrancy`
- 6 theme modes + Monet key colors
- Dual home layouts: Classic and Weavsk, switchable in settings
- Floating bottom bar, page scaling, app icon switcher
- Hidden app mode removed (v31.1 sync): no app hiding/repackaging, no stub modules; su blacklist (default) keeps the interactive prompt, whitelist mode uses upstream's explicit-grant model
- Whitelist mode: superuser list mode synced with Zygisk Next
- Module store browser over the KernelSU Modules registry API (`https://modules.kernelsu.org/api/v1`: index → list → detail, schema-versioned and validated, offline cache, size + SHA-256 verified downloads, one-shot `kind=script` entries) **and** legacy Magisk modules repositories (`modules.json` with `prop_url`/`zip_url`, metadata read per `module.prop`, no integrity metadata so those ZIPs download unchecked); the repository address accepts a site root or any index document URL, batch local install, and a download-and-patch boot-image install method
- WebUI theme injection: module WebUIs follow the app's Monet scheme via CSS custom properties

## Config

`config.prop` (gitignored content, sample at `config.prop.sample`): version, outdir, abiList, signing configs. All optional.

`.github/ci.prop`: `abiList=arm64-v8a` — used in CI test builds to speed up.

## Testing

- AVD tests: `scripts/avd.sh test -v <api_version> [-t <type>] [<apk>...]` — requires pre-built APKs
- Cuttlefish tests: `scripts/cuttlefish.sh` — for virtual device testing
- Test APK: `./build.py test` — builds UIAutomator test APK, always as release
- CI runs AVD tests on API 30-36 + 36.1 + 37.0 + CANARY (x86_64), API 30 (x86)
- CI test jobs only run on `workflow_dispatch` (manual trigger), not on push
- `test_common.sh` uses `$self` (TestRunner) and `$app` (AppTestRunner) as instrumentation runners
- `BaseTest.prerequisite()` must match upstream: `Shell.getShell().isRoot` + `Connection.await()`. No UI operations (launchTargetApp, grant prompts, UiDevice)
- API 23-29 removed from CI matrix: API 23 < minSdk 24; API 24-29 have RootService compatibility issues

## Code Style

- Kotlin: follow existing Compose patterns in `app/apk/src/`
- Rust: `rustfmt.toml` enforces `imports_granularity = "Module"`, edition 2024
- C++: C++23, `-Oz` optimization, `-Wall`
- No `unwrap()` in Rust — clippy denies it
- Translation strings go in `app/core/src/main/res/values/strings.xml`

## AI/Agent Guidelines

Adapted from upstream Magisk's AGENTS.md (commit cfd195b5):

1. **Git / Commit Control:** Never commit changes or amend an existing git commit without the user's explicit request or approval. When asked to commit, follow the 50/72 rule (subject ≤ 50 chars, blank line before body, wrap body at 72 chars) and include an `Assisted-by: <Friendly Name of Current Model>` trailer in the body. The trailer must name the underlying LLM model itself, not the agent framework, runner, or harness.
2. **Build Invocation:** Prefer `./build.py <command>` for real builds. Standalone native tools (`cargo`, `rustc`, `ndk-build`) must be prefixed with `scripts/env.py` (see "Build Commands" above). App-only `gradlew` may run plain from `app/` per the "Direct Gradle invocation" note.
3. **Pre-build Native Code:** Before editing code in `native/`, run `./build.py native` at least once to generate FFI bindings (`flags.h`, `flags.rs`) and headers.
4. **Subproject Context:** Refer to [`app/AGENTS.md`](app/AGENTS.md) when working inside `app/` and [`native/AGENTS.md`](native/AGENTS.md) when working inside `native/`.
5. **Verification Loop:** After changes, verify compilation and run lint/clippy for affected modules before concluding.
6. **Private Local Notes:** [`docs/private/AGENTS.md`](docs/private/AGENTS.md) — local-only agent notes (device constraints, debugging findings, commit-message conventions for upstream syncs). The directory is gitignored via `.git/info/exclude` and may not exist on other clones; read it before device work or upstream syncs, and never commit files from it.

## Windows Quirks

- ONDK cargo DLLs need to be on PATH at runtime (handled by `scripts/env.py`)
- Build uses `shell=True` for subprocess on Windows (PATHEXT support)
- Read-only file cleanup requires `chmod` before unlink

## Repository

- Branch: `master`
- CI: GitHub Actions on `macos-26` (release builds), `windows-2025` + `ubuntu-24.04` (test builds)
- License: GPLv3+
- Upstream: Magisk by topjohnwu
- First stable: v30.7.5, based on Magisk v30.7
- Bug reports are only accepted from **Debug** builds (README).
