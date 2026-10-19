# CUST05 persisted hidden legend series evidence

**Date:** 2026-10-19
**Requirements:** CUST05, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator persistence/render checks passed;
visual and TalkBack review remain absent.

## Implementation

- `ChartOptions.hiddenSeries` is an optional bounded set (≤100 keys, each ≤256
  characters, trimmed/non-empty) persisted in workbook JSON.
- `ChartSpecBuilder` intersects persisted keys with the series produced by the
  current query. BAR has no series legend and therefore yields an empty set.
- `VegaChartCard` no longer owns hidden state: it reads effective hidden keys
  from the built spec, writes toggles/reset through `onChartOptionsChange`, and
  prunes persisted keys that no longer match current series. Mark-type changes
  clear hidden series.
- `WorksheetView`/`WorkbookScreen` route that callback to
  `WorkbookViewModel.updateChartSettings`, which persists/recomposes without
  rerunning SQL.
- Rendered marks/labels/hit targets are filtered; query rows, table, exact
  values, tooltips, scales and source-row selection are unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **59/59**; sanitization against current/absent series passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **10/10**, ChartSettingsPersistenceTest **9/9** including save/reopen of hidden keys, hidden render **1/1**, real legend taps **1/1**. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Compose product path | Code-inspected only; no Compose-level instrumentation exercises VegaChartCard wiring. |
| Visual/TalkBack | No screenshot, focus-order, hidden-label styling or TalkBack qualification. |

## Limits

- Hidden keys are view state stored in workbook chart options; they do not alter
  the query, filter, table rows or accessible summary.
- A missing series key is pruned by the next successful spec build; before that
  build it remains dormant in JSON.
- The option set is bounded; pathological series counts over 100 are truncated.
- No Compose UI test covers the product callback chain.
- Exporting while series are hidden preserves the hidden visual state.

## Evidence files

- `app/build/outputs/androidTest-results/cust05-persist-legend/Vega.txt`
- `app/build/outputs/androidTest-results/cust05-persist-legend/Persistence.txt`
- `app/build/outputs/androidTest-results/cust05-persist-legend/HiddenRender.txt`
- `app/build/outputs/androidTest-results/cust05-persist-legend/LegendClick.txt`
