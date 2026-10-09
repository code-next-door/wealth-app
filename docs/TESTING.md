# Testing Wealth

General rules live in [best-practices/](best-practices/README.md). This file covers only what's specific to
this app.

## The non-negotiables
- Test first. Tests written after the code get a mutation check (break the real code, watch a test fail).
- **No network, no real data.** Rate, price and update sources are fakes; statements in tests are invented.
- `./gradlew testDebugUnitTest` green before every push; device tests too for UI changes.
- Users' data survives every change: schema changes only through migrations covered by `MigrationTest`;
  backups of every older format still read (`BackupFormatsTest`).

## Layers
| Layer | Where | Command | What it covers |
|---|---|---|---|
| Pure logic | `app/src/test` (no Android) | `./gradlew testDebugUnitTest` | Money, net worth, loans, pensions, vesting, parsers, categorizer |
| Repositories + ViewModels | `app/src/test`, base `testutil/DatabaseTest` | same | Robolectric, in-memory Room seeded like a fresh install, fake "today" (`todayFlow`) |
| Migrations | `MigrationTest` (JVM) | same | v1 → latest with data, every step validated (schemas shipped as debug assets) |
| Backup formats | `BackupFormatsTest` + `resources/backup/format-N.json` | same | Every backup format reads exactly; `LegacyBackupReader` reads new ones |
| Device UI flows | `app/src/androidTest`, base `ui/UiTest` | see below | Compose flows, encryption, native PDF text, Keystore, StrictMode watch |
| Shrunk build | `releasetest/` (UI Automator) | `./gradlew :releasetest:connectedMinifiedAndroidTest -x :app:lintVitalMinified` | The R8 build, package `…wealth.minified` |
| Upgrade | `releasetest/upgrade-check.sh <last tag>` | the script | Data, settings and PIN survive installing over the last release |
| Lint | app + test code | `./gradlew lintDebug` | API-level crashes, string format errors |

Device tests, without uninstalling (keeps the emulator's data):
```bash
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w io.github.codenextdoor.wealth.test/io.github.codenextdoor.wealth.WealthTestRunner
```

## Project helpers
- `WealthTestRunner`: in-memory DB, `_test` preference files, fake rates (`TestRates`: 1 CHF = 100 INR =
  1.25 USD), fake updates (`TestUpdates`, version "1.0.0"), StrictMode watch (`StrictModeViolations`).
- `UiTest`: `tap()` for form buttons, tab switching, fields by label, `stubOpenDocument` /
  `stubCreateDocument`, `logScreen()` (tag `UiTest`) on wait timeouts, `onboarding.skipAll()`.
- `DatabaseTest`: `cancelledAfterTest()` for every ViewModel, `withPlainSpaces()` for formatted amounts.
- Real statements (counts only, never committed): `LocalStatementCheck` (JVM, `-PstatementFile=…`) and
  `LocalStatementDeviceCheck` (PDF on the emulator, `-e layout true` for masked text).
- README screenshots: `scripts/readme-screenshots.sh` (made-up data, `ReadmeScreenshots`).

## Known gaps (planned)
These best practices aren't followed yet; each is a separate piece of work:
1. **Navigation map**: one device test of every route's journey from Home and where Back lands, failing when
   a route in `ui/Routes.kt` has no row.
2. **Accessibility checks**: Compose's `enableAccessibilityChecks()` in `UiTest`, and the main tabs at font
   scale 2.0 and in dark theme.
3. **Recreate after save**: main flows recreate the activity after saving/deleting and check the change is
   still there.
4. **Dependabot alerts** (security only) on the GitHub repo.
