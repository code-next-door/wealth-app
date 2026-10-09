# Dev environment and workflow

## Machine
- Keep projects **out of iCloud-synced folders** (Desktop/Documents): sync creates "file 2" duplicates in build
  folders and breaks builds.
- JDK 17 (or what AGP requires), Android SDK with the compile SDK installed, `ANDROID_HOME` in the shell profile.
- One emulator image matching the target SDK; don't wipe one that holds hand-entered test data.

## Gradle
- All versions in `gradle/libs.versions.toml`.
- Configuration cache on: read env vars and `-P` values via `providers`, not `System.getenv` at config time.
- Gradle runs one build per project at a time: queue work instead of racing two commands.
- Bigger heaps / other GCs made builds *slower* here; measure before changing `gradle.properties`.
- Pin versions tests use that AndroidX bundles older copies of (coroutines, serialization, fragment, Espresso),
  or device tests hit `NoSuchMethodError`/`AbstractMethodError` at runtime.
- Check a library's AAR `minCompileSdk` before bumping it.
- Lint catches API-level crashes JVM tests can't (`LocalDate.ofInstant` is API 33+; use
  `atZone(…).toLocalDate()`).

## Running long checks in the background
- Full suites, device tests, release tests and screenshots run in the background while other work continues.
- Run them in a **git worktree of the commit** (`git worktree add <tmp>/check-<sha> <sha>`), not the folder
  being edited: a build reads the folder, not the commit.
- Commit locally when done; **push only after its checks pass**; then remove the worktree.
- The emulator is shared: while device tests run, don't install or launch anything else on it.
- Keep the Mac awake for long device runs (`caffeinate -i <command>`): if it sleeps, the emulator stalls,
  tests "hang" for the length of the sleep, and timing-sensitive ones fail.
- In check scripts, run each check as its own line under `set -e`. A failure inside `a && b` does **not**
  stop a `set -e` script (a failed Lint once let a push through).

## adb
- `adb shell am start -n` stacks a second activity; launch like the launcher does:
  `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`.
- `ProcessLifecycleOwner` reports "stopped" ~700 ms after leaving the app.

## Git and GitHub
- Small, focused commits with imperative messages; every commit green.
- If GitHub blocks pushes that expose your email, use the noreply address
  (`gh api user --jq '"\(.id)+\(.login)@users.noreply.github.com"'`).
- Keep private notes (AI-assistant files, local logs) in `.gitignore`.
