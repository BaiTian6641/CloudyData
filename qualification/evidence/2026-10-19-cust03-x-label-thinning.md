# CUST03 categorical X-label thinning evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — host spec/persistence passed on the physical
Googlebook; device visual review remains absent.

## Implementation

- `ChartOptions.xLabelCount` is optional, whole-number 2–50. Missing/out-of-range
  stored values mean automatic/all labels.
- `AxisLabelSelection` chooses nearest-index, evenly spaced domain labels and
  always retains the first and last. The domain, rows, categories, scales and
  selection identity are unchanged.
- `ChartSpec.AxisSpec.labelValues` carries the subset only when the request is
  smaller than the X domain. Bar, Line and Area builders use it.
- `VegaSpecBuilder` emits the subset as axis `values`. This is a real band-axis
  operation, unlike Vega `tickCount` which band scales ignore.
- Chart settings expose X label count; blank/all is automatic. Changes are
  presentation-only and do not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **50/50**; nearest-index subsets, first/last retention, automatic/oversize behavior and builder bounds pass. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **5/5** and ChartSettingsPersistenceTest **5/5**, including axis `values` and reopened `xLabelCount`. |
| Device visual | Not performed; no label collision, rotation, narrow-window or accessibility claim. |

## Limits

- Only label/ticks are thinned; all categories remain in data, selection and the
  exact table.
- A request equal to or larger than the domain keeps all labels.
- No device visual check of label collisions at 2–50 or with angled labels.
- Thinned axes request Vega `labelOverlap: greedy`; the visible subset, not the
  full domain, selects the angled-label threshold. Neither is visually qualified.
- Temporal continuous-axis scaling remains absent.

## Evidence files

- `app/build/outputs/androidTest-results/connected/debug/TEST-HP Googlebook 14 - 17.xml`
  (focused runs; each run overwrote this file).
