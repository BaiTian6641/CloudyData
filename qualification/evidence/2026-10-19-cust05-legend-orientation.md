# CUST05 legend orientation evidence

**Date:** 2026-10-19
**Requirements:** CUST05, F38, Q21, S11
**Status:** implemented_unverified — no device visual run was authorized.

## Implementation

- `ChartOptions.legendOrientation` is an optional `LegendOrientation`
  (`VERTICAL`/`HORIZONTAL`). Missing/JSON-null means automatic from position;
  unknown stored values fall back to automatic.
- `ChartSpec.LegendSpec` carries the optional orientation. Bar/table legends stay
  disabled; the multi-series Line legend propagates it.
- `VegaSpecBuilder` derives Automatic from position (bottom → horizontal,
  right → vertical). Explicit Vertical/Horizontal overrides it. This changes old
  bottom-legend documents from Vega's default vertical stack to the intended
  horizontal row.
- Horizontal legends use `LegendLayout`: estimated entry width from the longest
  series label, available width (full chart for bottom, 28% for right) and a
  series-count clamp. Vertical legends emit no column cap. A bounded
  `labelLimit` (60–180) caps one label's width. Right-position legends still
  extend outside the plot like the vertical form; this is inherent legend
  overhead, not eliminated by wrapping.
- Chart settings expose Automatic / Vertical / Horizontal next to legend
  position. Apply stays presentation-only and does not rerun SQL.
- Exact values, filters and series identity are untouched.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **40/40**; `ChartSpecBuilderTest` asserts orientation propagation and `LegendLayoutTest` covers long/short labels, right-pane budgeting, zero/clamp behavior and label limits. |
| Headless Vega | Bundled Vega 6.4.0: four entries at 300 px width produces a 388 px single-row legend without a cap; `columns: 2` bounds it to 188 px. `labelLimit`/columns are the final bounds. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Persistence, explicit vertical, and Automatic-bottom horizontal/columns assertions were added but **not executed**. |
| Device visual | Not performed; no legibility, wrapping, overflow or light/dark claim. |

## Limits

- No visual check of horizontal wrapping/column cap at narrow or short chart
  sizes, explicit horizontal legends on the right, or long series names.
- Interactive legend filtering and custom mark samples remain absent.
- Accessibility and saved/reopened visual state are not runtime-qualified for
  this control.

## Evidence files

- Code/test-only slice; no device artifact was produced.
