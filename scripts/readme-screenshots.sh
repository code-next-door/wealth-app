#!/usr/bin/env bash
# Retakes the README's screenshots on the emulator, from made-up sample data in the
# test app's in-memory database (never real data), into docs/screenshots/.
# Usage (emulator running): scripts/readme-screenshots.sh
set -euo pipefail
root=$(git rev-parse --show-toplevel)
cd "$root"
app=io.github.codenextdoor.wealth
shots="overview accounts house spending hidden tour"

./gradlew -q installDebug installDebugAndroidTest
adb shell am instrument -w -e readme true \
    -e class io.github.codenextdoor.wealth.ui.ReadmeScreenshots \
    "$app.test/io.github.codenextdoor.wealth.WealthTestRunner" | tail -2
mkdir -p docs/screenshots
for name in $shots; do
    adb exec-out run-as "$app" cat "files/readme/$name.png" > "docs/screenshots/$name.png"
    sips -Z 1200 "docs/screenshots/$name.png" >/dev/null # smaller files for the repo
done
for name in $shots; do adb shell run-as "$app" rm -f "files/readme/$name.png"; done
echo "Screenshots in docs/screenshots/"
