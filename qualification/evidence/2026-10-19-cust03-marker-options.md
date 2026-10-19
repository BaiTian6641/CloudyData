# CUST03 marker visibility and size evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render/spec checks passed; visual
review remains absent.

## Implementation

- `ChartOptions.showMarkers` defaults true; `markerSize` is optional and bounded
  to 2–12 px diameter. Missing/invalid stored sizes keep automatic defaults.
- `ChartSpec` carries `MarkerSpec(show, size)`; `ChartSpecBuilder` validates and
  propagates it for Line/Area.
- `VegaSpecBuilder` omits point symbols when markers are off and preserves the
  200-point cap. Default symbol areas remain 30 (Line) and 24 (Area); a custom
  size uses `diameter²`. Lines, hit targets, selection overlays, labels and
  tooltips are unchanged.
- Chart settings expose a Markers toggle and marker-size field for Line/Area.
  Validation and application are gated by the toggle; applying is
  presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **60/60**; carry/default/bounds pass. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **11/11**, ChartSettingsPersistenceTest **10/10**, hidden-marker render **1/1**: symbols decrease, lines and six table rows remain. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot/theme check of marker size, shape, overlap or selection-ring appearance. |

## Limits

- Marker shape is not configurable yet; Vega default symbol shape is used.
- The 200-point cap still disables markers entirely beyond the cap.
- Size is a diameter in CSS px/Vega units, not device dp.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust03-markers/Vega.txt`
- `app/build/outputs/androidTest-results/cust03-markers/Persistence.txt`
- `app/build/outputs/androidTest-results/cust03-markers/MarkersRender.txt`
