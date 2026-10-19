# CUST06 fixed-decimal value formatting evidence

**Date:** 2026-10-19
**Requirements:** CUST06, F36/F38, Q21, S11
**Status:** implemented_unverified — host spec/persistence passed on the physical
Googlebook; device visual review remains absent.

## Implementation

- `ChartOptions.valueDecimals` is optional and bounded to 0–6. Missing, null or
  invalid stored values keep automatic renderer formatting.
- `ChartSpecBuilder` validates the value for every chart kind and carries it into
  `ChartSpec.valueDecimals`.
- `VegaSpecBuilder` applies one d3 pattern (`",.Nf"`) to the value-axis tick
  format. Value labels use an explicit `format(datum.y, ",.Nf")` signal with an
  `isValid` guard, because Vega ignores `format` on a text mark's field
  reference. The underlying `y`, exact-value tooltip, data, scale, aggregation
  and export exact-value paths are unchanged.
- The renderer completion message now carries rendered SVG text for test
  inspection; it does not alter rendering or product behavior.
- Chart settings expose a whole-number field. Applying it is presentation-only
  and does not rerun SQL.
- Workbook JSON persists the optional value without a format-version bump.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **52/52**; carried/automatic values and -1/7 rejection pass. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **5/5**, ChartSettingsPersistenceTest **5/5**, and a focused packaged-Vega render **1/1** whose SVG contains `1,234.57` and `2,000.00`. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Device visual | Not performed; no label-width, collision, locale, negative or large-number claim. |

## Limits

- Formatting is fixed-decimal with grouping; currency and percentage patterns
  are not implemented.
- Exact tooltips and exact-value export paths intentionally keep full precision;
  display rounding never mutates results.
- Larger decimal counts can widen labels and collide; no collision policy or
  visual qualification exists.
- Locale remains the renderer pattern, not a user-selected locale.
- d3-format can fall back to exponential notation at extreme magnitudes
  (roughly ≥1e21); “fixed decimal” is not guaranteed there.
- Corrupt fractional JSON is truncated by `optInt` before bounds checking, the
  same pre-existing integer-option convention.

## Evidence files

- `app/build/outputs/androidTest-results/cust06/vega.xml`
- `app/build/outputs/androidTest-results/cust06/persistence.xml`
- `app/build/outputs/androidTest-results/cust06/format-render.xml`
