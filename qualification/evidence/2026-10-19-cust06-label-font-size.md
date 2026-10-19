# CUST06 label font-size evidence

**Date:** 2026-10-19
**Requirements:** CUST06, F32/F38, Q21, S11
**Status:** implemented_unverified — no device visual run was authorized.

## Implementation

- `ChartOptions.labelFontSize` is optional and bounded to 8–16 with shared model
  constants. Missing or out-of-range stored values fall back to the mark default.
- `ChartSpec.LabelSpec` carries show/font size for Bar, Line and Area;
  `ChartSpecBuilder` validates the bound.
- Vega label marks keep their existing top/outside offsets and baselines:
  Bar offset -6, baseline bottom, default 11; Line/Area offset -8, baseline
  bottom, default 10. An explicit font size replaces only the font value.
- Chart settings expose the font-size field; invalid values block Apply.
  Settings remain presentation-only and do not rerun SQL.
- Label **position** was removed from this slice. Independent review showed
  inside-end placement is wrong for negative/reversed values and short bars,
  and theme text does not meet contrast on categorical fills. Shipping a
  position control without those rules would be a fake capability.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **41/41**; `ChartSpecBuilderTest` asserts font-size propagation and lower-bound rejection. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Bar-label font/baseline/offset JSON and persistence assertions were added but **not executed**. |
| Device visual | Not performed; no font legibility, clipping, scaling or light/dark claim. |

## Limits

- Labels remain top/outside only; position, field selection, number/date
  formatting and collision policy remain absent.
- Font size applies per worksheet chart, not per label field.
- Negative bars, small heights, long labels, export and TalkBack were not
  visually exercised.

## Evidence files

- Code/test-only slice; no device artifact was produced.
