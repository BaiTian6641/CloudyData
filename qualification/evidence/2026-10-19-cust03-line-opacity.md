# CUST03 line opacity evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `ChartOptions.lineOpacity` is optional and bounded to 0.1–1.0. Missing/invalid
  values keep the prior opaque output.
- `ChartSpec`/builder carry the value for Line/Area.
- Vega Line marks and Area outlines receive `opacity`; Area fill opacity is
  multiplied (`0.32 × opacity`). Default Area fill remains 0.32 and default lines
  emit no opacity property.
- Markers, labels, hit targets, selected-point overlays and data are unchanged,
  preserving selection contrast and hit behavior.
- Chart settings expose a Line opacity field for Line/Area; Bar applies reset it
  to default. Applying is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **63/63**; default/carry/bounds pass. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **16/16**, ChartSettingsPersistenceTest **10/10**, opacity render **1/1** with differing exports and identical mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot/contrast check of translucent paint, markers, labels, selection ring, dark theme or export appearance. |

## Limits

- Opacity applies to line/area paint only, not markers/labels/selection overlays.
- Area fill opacity is relative to its fixed 0.32 alpha, so 0.5 yields 0.16.
- Values below 0.1 are rejected by UI/builder and fall back to opaque on load.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust03-opacity/Vega.txt`
- `app/build/outputs/androidTest-results/cust03-opacity/Persistence.txt`
- `app/build/outputs/androidTest-results/cust03-opacity/OpacityRender.txt`
