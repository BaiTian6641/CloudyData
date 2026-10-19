# CUST03 line dash style evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `LineDashStyle` offers SOLID (default), DASHED and DOTTED. Missing/invalid
  persisted values fall back to SOLID.
- `ChartOptions.lineDashStyle` is carried into `ChartSpec`.
- Line marks and Area outlines receive Vega `encode.enter.strokeDash` as a value
  reference: `[6,4]` dashed, `[1, width+3]` dotted. Dotted additionally uses a round
  stroke cap. Solid emits no override, preserving prior output.
- Dotted uses a round cap and a width-aware gap (`width + 3`) so it remains
  separated at every allowed width.
- Interpolation, width, markers, labels, hit targets, selection and data remain
  unchanged. Area fills stay solid; only the outline is dashed.
- Chart settings expose a Line style dropdown for Line/Area. Applying is
  presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **62/62**; default/carry passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **15/15** (line + area outline; dotted gap at width 8), ChartSettingsPersistenceTest **10/10**, solid/dashed/dotted render **1/1** with differing exports and identical mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of dash phase, dots, caps, width scaling, dark theme or export pixels. |

## Limits

- Dashed pattern is fixed in CSS/Vega units and does not scale with line width.
- Dotted uses a short dash plus round cap rather than a native dot primitive; its
  gap scales so it remains visible at width 8.
- Area fill opacity/boundary remains unchanged; only the outline stroke is dashed.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust03-dash/Vega.txt`
- `app/build/outputs/androidTest-results/cust03-dash/Persistence.txt`
- `app/build/outputs/androidTest-results/cust03-dash/DashRender.txt`
