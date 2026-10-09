# Releases and upgrades

## Test what you ship
- Have a `minified` build type: R8 exactly like release, debug-signed, its own `applicationIdSuffix` so it
  never touches real data on the device.
- Test it **black-box** from a separate test module (UI Automator, its own process): open the app, add data,
  reopen saved items (routes with ids), restore a backup. That's what catches missing R8 keep rules.
- Run it before every release and after adding a library.
- UI Automator + Compose: a selected segmented button reports `checked`, not `selected`; scrolling can't get
  through one very tall list item.

## Upgrade check
- Before any release that changes stored data or settings: build the **last released tag**, create data
  through its UI (an entry, a setting, a PIN), install the new build over it, and check it all survived. Script
  it (`upgrade-check.sh <tag>`) so it's cheap enough to always run.
- Same signing key forever: a new key means users must uninstall (and lose data without a backup).

## Pipeline
- CI on every push: unit tests, release build, Lint over app + test code, in parallel jobs.
- Release on a version tag: signed APK + its SHA-256 on a GitHub Release, key from CI secrets.
- `versionCode` increases every release; in-app update checks compare numeric version parts.
- Lint vital runs inside `assembleRelease` (~1/3 of its time); CI's lint job covers it, so skip it in CI
  builds (`-x lintVitalRelease`) but keep it in real releases.

## Checklist
1. All JVM, device and black-box tests green on the release commit.
2. Upgrade check from the last tag (if data/settings changed).
3. README "What's new", supported files, privacy and install sections true; screenshots retaken if a shown
   screen changed (made-up data only).
4. Release notes say if backups become unreadable by older versions.
5. Tag, let the workflow build, check the published checksum.
