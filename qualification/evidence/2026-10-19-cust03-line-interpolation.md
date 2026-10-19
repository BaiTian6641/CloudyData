# CUST03 line interpolation evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `LineInterpolation` offers LINEAR (default), STEP and MONOTONE.
  Missing/invalid persisted values fall back to LINEAR.
- `ChartOptions.lineInterpolation` is carried into `ChartSpec`.
- Vega line marks receive `encode.enter.interpolate` only for non-linear values.
  The same value is applied to Area fill boundaries and their outlines. Default
  linear marks emit no interpolation override, preserving prior output.
- Observed gaps (`defined`), markers, labels, axes, hit targets, selection and
  data remain unchanged.
- Chart settings expose a Line interpolation dropdown for Line/Area. Applying is
  presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **61/61**; default/carry passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **14/14** (line + area/outline), ChartSettingsPersistenceTest **10/10**, linear/step render **1/1** with differing exports and identical mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of curve shape, step alignment, gap boundaries, markers or export appearance. |

## Limits

- STEP uses Vega’s midpoint `step` curve, not step-before/after; the transition
  position is not configurable.
- Monotone can flatten local variation; no visual qualification exists.
- Interpolation applies to Line and Area joining only, not to table/query values.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust03-interpolation/Vega.txt`
- `app/build/outputs/androidTest-results/cust03-interpolation/Persistence.txt`
- `app/build/outputs/androidTest-results/cust03-interpolation/InterpolationRender.txt`
