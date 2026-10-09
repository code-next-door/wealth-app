# Best practices (reusable)

Research and lessons collected while building a local-first Android app (Kotlin, Jetpack Compose, Room +
SQLCipher, no server) that holds personal financial data. Written to be **project-neutral**: copy this
folder into another Android repo as-is. What's specific to this app lives in [../TESTING.md](../TESTING.md).

| Doc | Covers |
|---|---|
| [testing-jvm.md](testing-jvm.md) | Test-first workflow, mutation check, Robolectric + in-memory Room, ViewModels and coroutines, fakes, flake-free CI |
| [testing-compose-ui.md](testing-compose-ui.md) | Device UI tests with Compose + Espresso: stable taps, lazy lists, StrictMode, navigation map, accessibility and large-text checks |
| [data-safety.md](data-safety.md) | Keeping users' data through every update: migrations, backup formats, seeds, stored meanings, data-safety tests |
| [money.md](money.md) | Amounts, currencies, exchange rates and number formatting without rounding bugs |
| [security-local-data.md](security-local-data.md) | Encrypted database, Keystore, app lock and PIN, screenshots, logging, network use, in-app updates |
| [accessible-compose-ui.md](accessible-compose-ui.md) | Large text, touch targets, screen readers, gestures with alternatives, colour, calm and clear messages |
| [statement-parsing.md](statement-parsing.md) | Reading bank/card statements (PDF, CSV, .xlsx) reliably without ever handling real data in the repo |
| [release-and-upgrades.md](release-and-upgrades.md) | Testing the shrunk (R8) build, upgrade checks from the last release, signing, release checklist |
| [dev-environment.md](dev-environment.md) | JDK/SDK, Gradle speed and gotchas, running long checks in the background, git hygiene |

**Freshness:** written 2026-10-09 (AGP 9.4, Kotlin 2.4, Compose BOM 2026.09, compile/target SDK 37). Library
behaviour and APIs change: re-check anything marked *(time-sensitive)* before relying on it.

**How to use in another project:** copy the folder, then point your project's contributor guide (or AI
assistant notes) at it, e.g. "Follow docs/best-practices/ for testing, data safety and security."

**Keep it alive:** when a bug teaches something general, add it to the right doc in the same commit as the
fix (one or two lines: the rule and why). Project-specific details go to `docs/TESTING.md` instead.
