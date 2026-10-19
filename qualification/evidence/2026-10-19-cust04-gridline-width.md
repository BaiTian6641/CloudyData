# CUST04 major-gridline width evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — host spec/persistence passed on the physical
Googlebook; device visual review remains absent.

## Implementation

- `ChartOptions.gridlineWidth` is an optional whole/decimal value in 0.5–3.0 dp.
  A blank value emits no override and preserves the renderer default.
- `ChartSpecBuilder` carries it in `GridlineSpec` and rejects non-finite or
  out-of-range values before building any chart kind.
- `VegaSpecBuilder` emits `gridWidth` on the value axis only when major gridlines
  are visible. No data, scale, tick or dimension changes.
- Chart settings expose a decimal field while gridlines are enabled. Validation
  and application are gated by the toggle, so an invalid hidden field cannot
  block Apply; applying is presentation-only and does not rerun SQL.
- Workbook JSON persists the optional value; missing, null and invalid stored
  values fall back to the automatic renderer width.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **51/51**; 2.5 dp carried, 0/3.5/NaN rejected. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **5/5** and ChartSettingsPersistenceTest **5/5**, including `gridWidth`, default omission, hidden-grid omission and reopened value. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Device visual | Not performed; no line-weight, high-contrast, export or narrow-window claim. |

## Limits

- Width is not a color or dash style; gridline color remains renderer-palette
  controlled.
- `gridWidth` applies to the visible major value-axis grid only.
- 0.5 dp may rasterize inconsistently on displays without visual qualification;
  no pixel or contrast claim is made.
- Export scenes inherit the setting but were not rendered in this slice.

## Evidence files

- `app/build/outputs/androidTest-results/cust04/vega.xml`
- `app/build/outputs/androidTest-results/cust04/persistence.xml`
