# Device UI tests (Compose + Espresso)

Guiding principle: *the test should resemble how the user uses the app.* Find things by visible text,
labels and content descriptions, not test tags, and assert on what's on screen.

## Set-up
- Use the v2 Compose rule (`androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`,
  `StandardTestDispatcher`). It was stable over repeated full runs. *(time-sensitive)*
- A custom instrumentation runner gives the app test wiring: in-memory database, separate preference
  files, fake network sources. Device tests never go online.
- Run without uninstalling when the emulator holds data you want to keep: `installDebug
  installDebugAndroidTest` then `adb shell am instrument -w …`. (`connectedAndroidTest` uninstalls afterwards.)
- One shared base class with helpers: switch tab, find field by exact or partial label, stub the system file
  pickers with Espresso-Intents, wait-with-timeout that logs the screen on failure.
- Test-only code still needs compiling: run `compileDebugAndroidTestKotlin` before committing a device-test
  edit (a missing import once failed a gate's install step silently).

## Flake-free patterns
- **Tap form buttons with a real touch helper, not `performClick()` alone**: the keyboard moves the form, so
  a click computed before it settles can miss.
- **Lazy lists only compose what's on screen.** Scroll first:
  `onNode(hasScrollToNodeAction()).performScrollToNode(hasText("…"))`, then wait for the row. Dropdowns and
  long forms: `performScrollTo()` before clicking.
- Device tests often share one growing database: never assume a row is near the top or that a list is
  short. After saving, a list may stay scrolled: wait for text near the change, not a header at the top.
- **Don't depend on today's date.** Use fixed past dates in test data (a fake statement closing on day X
  broke a test on day X).
- Log the screen **one line per node** on a timeout: logcat cuts entries at ~4 KB, so `printToLog` of a whole
  screen loses almost everything.
- Repeat new UI tests a few times (e.g. 3/3) before trusting them.

## StrictMode as a test
- Debug builds enable StrictMode. In device tests, the runner records violations whose stack runs through
  *your* code and every UI test fails if one happened. Add one test proving the watch works.
- Typical catches: disk reads on Main (`context.cacheDir`/`filesDir` read the disk!), Keystore work on Main.

## Navigation map (journeys, not addresses)
- Tests that open a screen directly miss the bugs people hit: a screen nothing links to anymore, Back
  landing somewhere odd, a saved form reopening after Back.
- Keep **one navigation-map test**: a table of journeys (taps from the start screen → expected screen →
  where Back lands), driven by real taps.
- Make it **complete by construction**: compare the app's route list (e.g. the sealed `Route` subclasses via
  reflection or a hand-kept list checked against them) with the routes in the table, so a new screen without
  a journey fails the test. Screens reachable from several places get one row per entry point.
- Recreate the activity on every screen of the map: state and the back stack must survive. Tell screens apart
  by their title marked as a heading (`Modifier.semantics { heading() }`), which also helps screen-reader users.
  While the activity is recreated there is briefly no Compose content: treat that as "no title yet".
- **Back and the keyboard:** with the keyboard open, Android's first Back only closes it, and Back while the
  keyboard animates left a screen transition half done in tests. Close the keyboard the way a person would
  first (`performImeAction()` on the focused field; give search fields an IME action that clears focus),
  then press Back once. Pressing Back "again if nothing changed" overshoots on a slow screen and leaves the
  app. Don't re-focus a text field after the activity is recreated.
- Screens that open a system picker by themselves cover the app with the real picker: stub
  `ACTION_OPEN_DOCUMENT` (Espresso-Intents) in the map's set-up.

## Accessibility and large text, automatically
- The checker reports "no label" for a list row scrolled almost entirely under a bottom bar (a few pixels
  visible, its text cut off). Suppress only that check, only for elements a few pixels tall
  (`setSuppressingResultMatcher`), so real problems still fail. Such cases only show up once the test
  database is full: run the checks in the full suite, not just alone.
- **Prove the checks run** once: remove one icon's label and watch the test fail ("may not have a label
  readable by screen readers").
- Turn on Compose's accessibility checks (Accessibility Test Framework) in UI tests:
  `ui-test-junit4-accessibility` and `enableAccessibilityChecks()` on the rule, so interactions also check
  touch-target size, contrast and missing labels. *(time-sensitive: API added in Compose 1.8)*
- Run the main screens at **large font scale (e.g. 2.0)** and in dark theme (`DeviceConfigurationOverride`
  or the emulator setting): nothing important may be clipped or pushed off screen without a way to scroll.
- Automated checks miss a lot. Also try TalkBack on each new screen once.

## Data-safety checks in UI tests
- After every save or delete, **recreate the activity** (or restart the process in a release test) and check
  the change is still there. It catches "confirmed before it was really stored".
- Assert on the real end state (the row is gone), not an intermediate one (the button disappeared, which
  also happens when a confirm dialog replaces it).

## The release build is tested from outside
- Never run the in-app instrumentation suite against an R8-shrunk app: tests share classes with the app and
  R8 strips what only tests use. Test the shrunk build black-box (see release-and-upgrades.md).
