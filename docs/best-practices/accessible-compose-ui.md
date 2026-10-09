# Accessible, calm Compose UI

## Text and layout
- Size text in `sp` and let it grow: test at **font scale 2.0**. Long rows wrap (split a row into weighted
  halves, value end-aligned) instead of clipping; chip rows use `FlowRow` rather than a sideways-scrolling
  `LazyRow` that hides choices off screen.
- Every scrollable screen stays scrollable at large text; dialogs too.
- Material's `ListItem` top-aligns the leading icon once the text wraps; use your own `Row` with the icon
  centred where that looks wrong, and test it.

## Touch and gestures
- Touch targets ≥ 48 dp (Material components do this; custom ones must too).
- **Every gesture has an alternative**: swipe between months → also "Previous/Next month" custom
  accessibility actions; drag to reorder → also "Move up/Move down" actions.
- Tapping a whole row toggles its switch (`Modifier.toggleable` on the row, not just the switch).

## Screen readers
- Icon-only buttons have a `contentDescription`. Extended FABs hide their text from accessibility: give the
  **icon** a description equal to the label.
- Hide decorative content (`contentDescription = null`); hide content behind overlays and locks
  (`clearAndSetSemantics {}` / `invisibleToUser`).
- Known Compose limitation: secure text fields keep typed text in the accessibility tree (masked on screen).

## Colour
- Colours from the theme only; support light, dark and (optionally) wallpaper colours.
- Charts: a colour-blind-safe validated palette, ≤ 6 slices (fold the rest into "N more", which opens),
  never colour alone: changes get an arrow and a sign; debts a badge, not just red.

## Messages and wording
- Plain words, no jargon. All user-visible text in `strings.xml` (Lint: a literal `%` needs
  `formatted="false"`).
- Confirmations as snackbars, shown once. Errors are a sentence with a next step.
- Destructive actions ask first, naming what will happen ("Replace all data?"), with the safe choice
  obvious.
- **Hide features that aren't ready**; don't show disabled buttons that don't explain themselves.
- Show what is calculated or estimated, and what is left out of a total ("not in net worth: X").

## Onboarding stays true
- Welcome, tour and checklist describe the app as it is: update them in the same change as the feature.
- Checklists tick themselves off from real data, so people who never saw onboarding aren't nagged.

## Testing it
- Automated: Compose accessibility checks and large-font runs in UI tests (testing-compose-ui.md).
- Manual, once per new screen: TalkBack walk-through, largest font + display size, dark theme.
