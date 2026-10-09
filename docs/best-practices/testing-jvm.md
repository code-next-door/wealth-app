# JVM tests (JUnit, Robolectric, coroutines)

## Workflow: test first
1. Write a failing test that describes behaviour in plain words, from the user's point of view
   (`reimportingAStatementAddsOnlyTheNewRows`, not `testImport3`).
2. Run it and see it fail **for the right reason** (not a compile error or a typo).
3. Write the smallest code that passes. Refactor while green.
4. **Mutation check** for tests written after the code: back the file up, break the real code (revert the
   actual fix, not one line of it: neighbouring code can mask a partial revert), and confirm a test goes red.
   If none does, the tests don't cover it. Restore from the backup, not `git checkout` (that also throws away
   uncommitted work).
5. Every commit leaves the suite green. Gate pushes on Gradle's **exit status**, not on filtered output.

## Where tests go
- **Pure logic** (domain maths, parsers, formatting rules): plain JUnit, no Android. Fastest, most of the suite.
- **Repositories and ViewModels:** Robolectric + an **in-memory Room database** seeded like a fresh install
  (one shared base class). Test against the real DAOs and real SQL, never mocked DAOs.
- **Database migrations:** on the JVM too (Robolectric with real SQLite and `MigrationTestHelper`), so CI
  checks every upgrade path on every push (see data-safety.md).
- SQLCipher has no JVM native library: Robolectric runs a plain `Application` (`robolectric.properties`) and
  an unencrypted in-memory DB. Encryption itself gets a device test.

## Never touch real services
- No network in tests: inject the HTTP layer (`HttpGet`, `RateSource`) and pass fakes. If a test needs the
  internet, it's wrong.
- No real clock: inject "today" (a `StateFlow<LocalDate>` or `Clock`) and move it in the test.
- **Prefer simple fakes over per-call mocks**: an in-memory implementation of the same interface that behaves
  sensibly. Deterministic fakes beat clever ones (e.g. fixed rates: 1 CHF = 100 INR = 1.25 USD).

## Coroutines and ViewModels
- Use `StandardTestDispatcher` / `runTest`; set `Dispatchers.Main` in a rule.
- **Cancel and join every ViewModel a test creates** (e.g. an extension `vm.cancelledAfterTest()` the base
  class tears down). A ViewModel left running can fail a *later* test, typically only on slower CI. Cancel
  alone isn't enough: background work (file parsing on IO) can resume onto Main while `resetMain` runs.
- **Wait for everything you then assert on.** When state combines a ViewModel's own value (a "done" message)
  with a database Flow (the list), either can arrive first: wait for both in one condition, not one then read
  the other.
- Forms should report "ready" only once their defaults are loaded, and save from the latest inputs, not a
  `StateFlow` value that may lag. Tests wait with generous timeouts (~15 s) so slow CI doesn't flake.

## Android pieces on the JVM
- `android.icu` formatting needs Robolectric, and Robolectric's ICU data can differ from the plain JVM's
  (Indian lakh grouping) and from the device's (Swiss apostrophe `’` vs `'`). Compare with a helper that
  normalises non-breaking spaces; device tests match on stable substrings.
- **DataStore allows one open store per file**: unique file names per test, or `close()` (which must
  *join* its scope) before reopening. Cancel alone raced on CI ("multiple DataStores active").
- JVM tests can't catch API-level problems (they run the JVM's `java.time`, not Android 12's). Android Lint
  can: run it over test and app code in CI.

## Speed
- Measure before tuning. Parallel test forks didn't help a ~450-test Robolectric suite (each fork pays
  Robolectric's start-up); caching Robolectric's runtime jars in CI did.
- Keep crypto in tests cheap: write test backups with few key-derivation rounds; keep one test at the real
  count.

## Test data
- Statement and file fixtures are **invented**, never copied from real files (see statement-parsing.md).
- Tests that write files use their own names and delete them.
