# CUST08 persisted line-width evidence

**Date:** 2026-10-19
**Requirements:** CUST08, F32/F38, Q21, S11
**Status:** implemented_unverified — no device visual run was authorized.

## Implementation

- `ChartOptions.lineWidth` is optional and bounded to 1.0–8.0 with shared
  constants. Missing/JSON-null or out-of-range stored values fall back to the
  mark default.
- `ChartSpec.lineWidth` carries the value for Line/Area only. `ChartSpecBuilder`
  validates finite bounds.
- `VegaSpecBuilder` uses the value for the multi-series Line stroke and the Area
  outline. Defaults remain 2.0 for Line and 1.5 for Area.
- Chart settings expose Line width only for Line/Area marks; blank restores the
  default and invalid input blocks Apply. Bar/Table Apply clears any hidden
  persisted width. Settings remain presentation-only and do not rerun SQL.
- Width validation occurs only for the time-series builder; Bar results do not
  reject an unused width value.
- Bar geometry, labels, selection overlays, tooltips, exact values and export
  data are unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **42/42**; ChartSpecBuilder propagation and lower-bound rejection pass. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Line custom width, Line 2.0 default, Area 1.5 default and persistence assertions were added but **not executed**. |
| Device visual | Not performed; no thickness legibility, legend-sample or Area-edge claim. |

## Limits

- No device visual check at 1–8 dp across appearances, densities, scaling or
  export.
- Line style/interpolation/marker styling, shape/opacity/border/stacking remain
  absent.
- The legend symbol sample is not tied to the custom line width.

## Evidence files

- Code/test-only slice; no device artifact was produced.
