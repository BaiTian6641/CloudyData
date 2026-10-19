# CUST05 interactive legend evidence

**Date:** 2026-10-19
**Requirements:** CUST05, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator pointer interaction passed; visual and
TalkBack qualification remain absent.

## Implementation

- `ChartSpec.hiddenSeries` is a presentation-only key set. The renderer adds a
  `visible` dataset filtered from `table`, so hidden series marks/labels/hit
  targets are absent while the full color/legend domain remains intact. Axes,
  table rows, exact values, tooltips and source-row selection identity are
  unchanged.
- Hidden keys are supplied as bounded `usermeta.hiddenSeries`. Selected-point
  overlays also respect the filter.
- `host.js` adds a transparent hit target per legend label, posts one
  revision-gated `LegendToggle` message, and dims hidden labels using stable
  series keys supplied in user metadata. Hit targets are stripped from exported
  SVG.
- Compose handles the toggle, keeps hidden state per chart instance, and exposes
  an accessible "Series" menu with per-series hide/show and "Show all series".
  The menu remains available if the legend is hidden while series are hidden, so
  state is always recoverable. State clears on mark-type changes and resets when
  the screen/chart instance is recreated; it is not written to the workbook.
- No query, aggregation, filter or data mutation occurs.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **58/58**. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **10/10**, ChartSettingsPersistenceTest **8/8**, hidden-series render **1/1** (one line mark, both legend labels retained), and a real UIAutomator pointer test **1/1** toggling both North and South with no row selection. Export contains no hit targets or cursor styling. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual/TalkBack | No screenshot/theme check of hit targets, hidden styling, focus order or TalkBack traversal. |

## Limits

- Hidden series state is ephemeral: screen recreation/reopen restores all series.
- Hidden series are still present in the query result, exact table, accessible
  summary and tooltip data; only rendered marks are filtered.
- The hit geometry is label-bbox based with minimal padding to avoid adjacent
  overlap; unusually scaled/transformed SVG or very small labels may reduce the
  touch target.
- No Compose-level instrumentation exercises the product card wiring; the
  propagation/pruning path is code-inspected while the probe exercises the
  renderer and message path.
- Legend labels are not individually exposed as controls in the SVG accessibility
  tree; the native Series menu is the keyboard/TalkBack path.
- Exporting while series are hidden preserves the hidden visual state but not an
  interactive control.

## Evidence files

- `app/build/outputs/androidTest-results/cust05-legend/Vega.txt`
- `app/build/outputs/androidTest-results/cust05-legend/Persistence.txt`
- `app/build/outputs/androidTest-results/cust05-legend/HiddenRender.txt`
- `app/build/outputs/androidTest-results/cust05-legend/LegendClick.txt`
