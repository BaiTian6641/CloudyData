# CUST03 marker opacity evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `ChartOptions.markerOpacity` is optional and bounded to 0.1–1.0.
  Missing/invalid values keep markers opaque.
- `MarkerSpec`/builder carry the value.
- Vega Line and Area point symbols receive `opacity` only when set. Lines,
  labels, hit targets, selected-point overlays, data and selection are unchanged.
- Marker opacity is independent of line/area paint opacity and marker
  size/shape/visibility.
- Chart settings expose a Marker opacity field while markers are enabled;
  hiding markers applies `null`. Applying is presentation-only with no SQL rerun.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **64/64**; default/carry/bounds pass. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **17/17** (line + area symbols), ChartSettingsPersistenceTest **10/10**, marker-opacity render **1/1** with differing exports and identical mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot/contrast check of translucent markers, selection ring, dark theme or export appearance. |

## Limits

- Marker opacity applies only to point symbols; line/area paint has its own
  independent opacity setting.
- Selected-point overlays, hit targets and labels remain opaque.
- Low opacity can make small markers difficult to perceive; no contrast warning.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust03-marker-opacity/Vega.txt`
- `app/build/outputs/androidTest-results/cust03-marker-opacity/Persistence.txt`
- `app/build/outputs/androidTest-results/cust03-marker-opacity/MarkerOpacityRender.txt`
