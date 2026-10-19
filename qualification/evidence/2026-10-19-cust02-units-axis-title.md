# CUST02 units and axis-title alignment evidence

**Date:** 2026-10-19
**Requirements:** CUST02, F16/F38, Q21, S11
**Status:** implemented_unverified — no device visual or host export run.

## Implementation

- `ChartOptions.units` is optional plain text, trimmed and capped at 16
  characters. Missing/blank values preserve prior titles.
- `ChartSpecBuilder` appends ` (units)` to the Y-axis title unless that exact
  suffix is already present, and stores units on the spec for other surfaces.
- `ChartOptions.axisTitleAnchor` persists AUTO/START/END; both X and Y axis specs
  carry it. Vega receives `titleAnchor: start|end` only when explicit, leaving
  AUTO output unchanged.
- Units appear in:
  - the native result-table and inspector headers (`Sum of value (USD)`) via
    one shared suffix helper that avoids double-appending;
  - the native chart accessible summary and renderer tooltip;
  - the Y-axis title in screen/SVG/PNG rendering;
  - PDF table column labels.
- Chart settings expose the units field and alignment dropdown. Apply is
  presentation-only and does not rerun SQL; exact values and aggregation are
  unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **46/46**; units suffix/16-char bound, anchor propagation and single-suffix table labels pass. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Vega `titleAnchor`/`usermeta.units` and save/reopen assertions compile but were **not executed**. |
| Device/export | Native table/tooltip/axis rendering and SVG/PNG/PDF verification were not run. |

## Limits

- No device visual check of start/end title placement, clipping, long units or
  axis/title collisions.
- Units are a single worksheet suffix, not a typed unit/currency model or
  per-measure format; number/date formatting remains absent.
- Subtitle/caption formatting controls remain absent.
- Tooltip/export behavior is code-reviewed but not rendered in this session.

## Evidence files

- Code/test-only slice; no device or export artifact was produced.
