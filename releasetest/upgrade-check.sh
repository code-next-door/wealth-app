#!/usr/bin/env bash
# Checks that updating keeps everything: installs an older release's shrunk build,
# makes data with it (expense, dark theme, PIN), installs this checkout's shrunk build
# over it, like an update from GitHub, and checks the data is all there.
# Touches only the separate ".minified" test app, never the real one.
#
# Usage (emulator running): releasetest/upgrade-check.sh v0.2.2
set -euo pipefail
old=${1:?"usage: $0 <older release tag, e.g. v0.2.2>"}
root=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'git -C "$root" worktree remove --force "$work/old" >/dev/null 2>&1 || true; rm -rf "$work"' EXIT
app=io.github.codenextdoor.wealth.minified
tests=io.github.codenextdoor.wealth.releasetest/androidx.test.runner.AndroidJUnitRunner

echo "Building $old…"
git -C "$root" worktree add --detach "$work/old" "$old" >/dev/null 2>&1
# Where the Android SDK is (not in Git).
if [ -f "$root/local.properties" ]; then cp "$root/local.properties" "$work/old/"; fi
# Lint's vital checks don't change the APK; skipping them saves about a minute per build.
(cd "$work/old" && ./gradlew -q :app:assembleMinified -x :app:lintVitalMinified)
echo "Building this version and the tests…"
(cd "$root" && ./gradlew -q :app:assembleMinified :releasetest:assembleMinified -x :app:lintVitalMinified)

step() {
    local out
    out=$(adb shell am instrument -w -e upgradeStep "$1" \
        -e class "io.github.codenextdoor.wealth.releasetest.UpgradeTest#$2" "$tests")
    echo "$out" | tail -2
    echo "$out" | grep -q "^OK (1 test)"
}

adb uninstall "$app" >/dev/null 2>&1 || true
adb install "$work"/old/app/build/outputs/apk/minified/*.apk >/dev/null
adb install -r "$root"/releasetest/build/outputs/apk/minified/*.apk >/dev/null
echo "Making data with $old…"
step before makeDataInTheOldVersion
echo "Updating to this version…"
adb install -r "$root"/app/build/outputs/apk/minified/*.apk >/dev/null
step after findItAfterTheUpdate
echo "Updating from $old keeps the data."
