# CUST06 percent value display evidence

**Date:** 2026-10-19
**Requirements:** CUST06, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `ValueDisplayFormat` offers `NUMBER` (default) and `PERCENT`. Missing/invalid
  persisted values fall back to NUMBER.
- `ChartSpecBuilder` carries the format for Bar/Line/Area.
- `VegaSpecBuilder` uses one d3 pattern for the value axis and labels:
  NUMBER → `,.Nf` only when decimals are set; PERCENT → `,.N%` with decimals
  defaulting to 1. Percent display multiplies by 100 and appends `%`; stored
  `y`, exact tooltips/exports, scale domain, selection and accessible summaries
  are unchanged.
- Crowded-label hiding still guards percent labels; formatting only changes text.
- Chart settings expose "Percent values" as a toggle. Applying it is
  presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **58/58**; format carry/default passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **8/8**, ChartSettingsPersistenceTest **8/8**, percent render **1/1** with SVG texts `12.5%`, `25.0%` and an additional axis-only percent tick. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device check of percent-label width, collision, clipping or dark theme. |

## Limits

- No currency/percent-with-unit composition; units remain an axis-title/tooltip
  suffix and are not automatically suppressed for percent.
- Blank decimals default to 1 in percent mode, stated in the field's supporting
  text when percent is active.
- Very large percentages can widen labels and collide despite the density
  policy; no text measurement exists.
- d3 percent formatting is display-only; users must supply ratios (0.25 → 25%).
- TalkBack, export pixel and device visual qualification remain absent.

## Evidence files

- `app/build/outputs/androidTest-results/cust06-percent/Vega.txt`
- `app/build/outputs/androidTest-results/cust06-percent/Persistence.txt`
- `app/build/outputs/androidTest-results/cust06-percent/PercentRender.txt`
