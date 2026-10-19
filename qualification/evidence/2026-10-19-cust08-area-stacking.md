# CUST08 stacked and 100% area evidence

**Date:** 2026-10-19
**Requirements:** CUST08, CH03/F34, S02/S03
**Status:** implemented_unverified — emulator spec/render checks passed; device
visual acceptance remains absent.

## Implementation

- Area marks now accept the Color shelf. `areaStackMode` persists overlay,
  stacked and 100% layouts; invalid values fall back to overlay.
- The builder computes cumulative bases per x category for stacked areas and
  rejects negative values. 100% mode divides by each category’s positive total,
  rejects non-positive totals, forces a 0–1 percent axis and keeps exact
  aggregates in `exactY`, the table, inspector and PDF.
- Vega area bands use explicit stack bases (`y`/`y2`), outlines, symbols and
  labels use the stack endpoint, and the automatic value domain uses `displayY`.
  Areas remain one mark per series with a fill-based legend.
- Area colors can be hidden from the legend/series menu; selection, tooltips and
  exact source-row identity remain intact. Stacking is gated to Sum/Count.
- Native fallback is unaffected (bars only); area charts require the offline
  renderer.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **72/72**, including stacked/100% area bases, per-x totals and percent format. |
| Vega spec | `VegaSpecBuilderTest` **20/20**: display-Y domain, area y/y2 stack signals, fill legend, percent axis and label placement. |
| Vega render probe | `VegaRendererProbeTest` **42/42**: stacked and 100% area fixtures render, percent labels 60.0%/40.0%, and distinct SVG geometry. |
| Bar query | `BarChartQueryTest` **1/1** unchanged; area query reuses the line series shape. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: PERCENT_STACKED area mode round-trips and invalid mode falls back. |
| Build/lint | Debug/test APKs build; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device inspection of band outlines, missing gaps, labels, legend, dark theme or export. |

## Limits

- Negative stacked areas are rejected rather than rendered divergently.
- 100% area charts reject zero-total categories.
- Overlapping (non-stacked) area series can obscure each other; that is the
  declared overlay mode, not a stacking substitute.
- Area selection remains point-based rather than whole-band.
- No visual/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust08-bars/Vega-area.txt`
- `app/build/outputs/androidTest-results/cust08-bars/Area-render.txt`
- `app/build/outputs/androidTest-results/cust08-bars/Persistence-area.txt`
- `app/build/outputs/androidTest-results/cust08-bars/VegaRendererProbeTest-area-final.txt`
