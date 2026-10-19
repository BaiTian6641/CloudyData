# CUST13 native table additive/range selection evidence

**Date:** 2026-10-19
**Requirements:** CUST13, F39, Q21
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi.
**Prerequisites verified:** chart pointer/keyboard selection, exact row identity,
`RowSelectionReducer`, native result table and the current keep/exclude actions.

## Implementation

- Each native result-table row observes the initial pointer press and captures
  `Shift`, `Ctrl` or `Meta` from `PointerEvent.keyboardModifiers`.
- A Final-pass release observer clears the captured mode after `clickable` has
  handled the gesture. Canceled drags, right-clicks and aborted presses cannot
  leak a range/toggle mode into a later accessibility or keyboard activation.
- The existing `clickable` action consumes that captured mode through
  `RowSelectionReducer`: Shift selects the inclusive range from the current
  anchor; Ctrl/Meta toggles one exact row; an unmodified click replaces the set.
  The table and chart share the same selection state, table highlight, endpoint
  inspector and mark overlay.
- Shift/Ctrl/Meta+Enter/Space use the same mappings for focused rows and ignore
  auto-repeat, so holding the chord applies one selection action per physical
  press.
- TalkBack exposes truthful `Add row to selection` (no-op when already selected)
  and `Select range to this row` custom actions. Unmodified activation replaces.
- No new query or source mutation is introduced.

## Evidence

| Surface | Observation |
| --- | --- |
| Product table pointer | Real worksheet with `event_date` rows. Plain click selected one (`1 selected mark`); Shift-click extended to `3 selected marks`; Ctrl-click added (`4 selected marks`); Ctrl-click toggled the anchor off (`3`); a final plain click replaced to `1`. |
| Accessibility | TalkBack `Add row to selection` on an already-selected row was a no-op, then added a second row (`2`); `Select range to this row` extended from the anchor (`3`). |
| Canceled gesture | Plain-select January, Ctrl-drag February (canceled click), accessibility-click February, then accessibility range March. The result is exactly February–March (`2 selected marks`); a leaked toggle or an ignored accessibility click would produce 3. |
| Test | `TableSelectionTest` uses real instrumentation `MotionEvent` meta state and the accessibility node tree against the native table, not reducer-only units. |
| Regression | Emulator suite `TableSelectionTest` + keep/exclude query + renderer probe + Vega spec: **27/27**. JVM **32/32**. Lint 0 errors / 5 warnings. |

## Limits

- This slice covers the native table. The native fallback bar chart still uses
  replace-only clicks; the Vega chart already supports modifiers.
- Lasso/rectangle, drill/View Data and persisted selection remain absent.
  Selection stays window/session state by default, matching the architecture
  boundary.
- Modified Enter/Space handling is implemented and repeat-guarded, but its
  row-focus path was not separately exercised; TalkBack custom actions and the
  chart's keyboard modifier path are verified.
- Touch users reach additive/range behavior through the chart's keyboard
  commands and visible table selection; no new touch-only multi-select affordance
  was added.

## Evidence files

- `android-test-results/2026-10-19/emulator-5582-table-selection-suite.txt`
