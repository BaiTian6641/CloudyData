# CUST14 persisted chart padding evidence

**Date:** 2026-10-19
**Requirements:** CUST14, F38, Q21, S11
**Status:** implemented_unverified — no device visual or host export-render run.

## Implementation

- `ChartOptions.chartPadding` is a whole number in 0–32 dp with a default of 8.
  Missing/out-of-range stored values normalize to 8.
- `ChartSpec.padding` carries the value; `ChartSpecBuilder` validates it for all
  chart kinds.
- `VegaSpecBuilder` uses `spec.padding` as the root Vega padding instead of a
  hard-coded 8. Chart width/height and data/scale semantics are unchanged.
  Renderer pointer/CTM mapping already accounts for `view.padding()`, and the bar
  label clamp keeps using the inner plot height.
- Export SVG/PNG inherit the same padding; the provenance header nests the chart
  at its reported external dimensions, and PDF uses that raster plus its text
  page.
- Chart settings expose the numeric field; blank/invalid input cannot apply.
  Presentation changes do not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **47/47**; default 8, custom 0 and upper-bound rejection pass. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Vega `padding:12` and save/reopen assertions compile but were **not executed**. |
| Device/export | No chart/SVG/PNG/PDF visual or dimension verification was run. |

## Limits

- 0 dp may clip axes/labels in practice; no device pixel check exists.
- Export header/nested-chart dimensions at non-default padding were not inspected.
- Chart border/background/fit/zoom and persisted chart geometry remain absent.
- Device visual qualification remains blocked.

## Evidence files

- Code/test-only slice; no device or export artifact was produced.
