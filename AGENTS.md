## AGENTS.md for WeaveMask

### General

- Prefix every shell command with `scripts/env.py` to ensure that commands are executed with the correct environment.
- WeaveMask is a fork of Magisk. When syncing with upstream, carefully resolve conflicts and preserve WeaveMask-specific customizations.

### App

- All application related source code lives under `app` directory. When working on the application codebase, use `app` as the working directory.
- The `app` directory is itself a Gradle project. Use `./gradlew` with corresponding tasks to build the app.
- The WeaveMask app is written in Kotlin and Java. Prefer Kotlin for all new code.
- After doing changes in `app`, make sure to build the relevant modules to ensure they build successfully.
- WeaveMask adds custom modules such as `stub-res` (stub resources). Be aware of these when modifying build configurations.

### Native

- Native code lives under `native` directory, written in Rust and C++.
- Use `build.py` in the project root to build native components. For example, `python build.py binary` builds all native binaries.
