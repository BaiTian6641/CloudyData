# CUST13 visual mark highlight evidence

**Date:** 2026-10-19
**Requirements:** CUST13, F39, Q21
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi.
**Prerequisites verified:** exact source-row selection, additive/range modes,
chart-revision gate and native table selection.

## Implementation

- `VegaRendererHost.applySelection(chartId, revision, rows)` is chart/revision
  gated, bounded to the renderer point limit and 0-based source-row indices. It
  is a presentation message only; no query is rerun.
- `VegaChartCard` sends the current row set after a successful render and when
  the set changes, so clear, replace, additive and range updates reach the same
  spec revision.
- `VegaSpecBuilder` adds a second Vega dataset named `selected`. The initial
  dataset is empty; selection messages replace it with exact table rows.
- Selected marks are overlays, not query changes:
  - Bar: a `cloudySelectedBar` rect with a contrasting 3 px ring and translucent
    foreground fill over the exact category.
  - Line/Area: `cloudySelectedPoints` symbols with a contrasting ring and a
    240 px² size. The transparent hit target stays above them.
- Selected rows remain exact in the native table and endpoint inspector. The
  overlay is cleared by an empty replacement payload and rebuilt after a spec
  re-render.

## Evidence

| Surface | Observation |
| --- | --- |
| Product Area | Three additive/range rows selected: dark ringed symbols appear at the three selected points; unselected points keep their series mark. |
| Product Bar | North selected: white contrasting ring and tint identify the North bar; South keeps its plain fill. |
| Renderer probe | `VegaRendererProbeTest` **17/17** on the emulator, including click, Ctrl-toggle and Shift-range selection tests. |
| Spec | `VegaSpecBuilderTest` **4/4**: the `selected` dataset and `cloudySelectedBar`/`cloudySelectedPoints` overlays are present in Bar and Area/Line specs. |

## Limits

- Line visual selection shares the same overlay code but was not separately
  screenshotted; the line spec test asserts the overlay binding.
- Selection is not persisted into the workbook and is cleared on worksheet tab
  change. Lasso/rectangle, keep/exclude, drill, pointer hover and TalkBack
  multi-selection remain unverified.
- A connected run that included the physical Googlebook hit an unrelated PNG
  export assertion (`expected 803`, actual `804`); the emulator probe suite is
  17/17 and the selection tests are unaffected.

## Evidence files

- `artifacts/2026-10-19-cust13-mark-highlight-area.png`
- `artifacts/2026-10-19-cust13-mark-highlight-bar.png`
- `android-test-results/2026-10-19/emulator-5582-vega-renderer-probe-suite.txt`
- `android-test-results/2026-10-19/emulator-5582-vega-spec-suite.txt`
