# CUST04 gridline contrast evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — host spec/persistence passed on the physical
Googlebook; device visual contrast review remains absent.

## Implementation

- `GridlineContrast` has two bounded choices: `DEFAULT` (theme divider) and
  `STRONG` (theme outline). `ChartOptions.gridlineContrast` defaults to DEFAULT;
  invalid or missing persisted values fall back to DEFAULT.
- `ChartSpecBuilder` carries the choice into `GridlineSpec`.
- `VegaSpecBuilder` changes only the value-axis `gridColor`. Axis domain/tick
  chrome remains the divider color, so the control styles gridlines rather than
  the whole axis. Both colors are derived from `CloudyTokens` rather than
  duplicate hex literals.
- Chart settings expose a "Strong gridlines" toggle while gridlines are visible.
  Applying it is presentation-only and does not rerun SQL.
- Workbook JSON persists the enum name without a format-version bump.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **54/54**; DEFAULT/STRONG chart-spec propagation passes. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **5/5** and ChartSettingsPersistenceTest **6/6**, including invalid/missing JSON fallback and DEFAULT grid color; strong grid color is `#667A80`, domain/tick stay `#D3DFE2` in light mode. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Device visual | Not performed; no contrast, dark-theme, export, or pixel claim. |

## Limits

- Arbitrary gridline colors are not exposed; the two choices map to provisional
  Cloudy divider/outline tokens.
- Dark mode is code-inspected (`#35515A` divider, `#79959E` outline) but not
  visually or render-tested in this slice.
- Minor gridlines and crosstab dividers remain absent.

## Evidence files

- `app/build/outputs/androidTest-results/cust04-contrast/vega.xml`
- `app/build/outputs/androidTest-results/cust04-contrast/persistence.xml`
