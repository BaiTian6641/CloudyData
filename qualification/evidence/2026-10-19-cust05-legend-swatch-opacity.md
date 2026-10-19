# CUST05 legend swatch opacity evidence

**Date:** 2026-10-19
**Requirements:** CUST05, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator spec/render passed; visual review
absent.

## Implementation

- Legend `symbolOpacity` is emitted only when the corresponding opacity setting
  is present.
- MARKER sample uses `markerOpacity`; LINE sample uses `lineOpacity`.
- Defaults remain opaque and emit no symbol opacity property.
- Plot marks, labels, selection, hit targets, data and legend hit geometry are
  unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **64/64**. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **17/17** including marker/line opacity assertions; ChartSettingsPersistenceTest **11/11**; legend render **1/1**. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of swatch contrast, hidden-series dimming interaction, dark theme or export pixels. |

## Limits

- Marker and line samples use their own opacity settings; a custom marker shape
  does not change this mapping.
- Hidden legend labels still dim via the separate opacity/state path; combined
  effects are not visually qualified.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust05-swatch-opacity/Vega.txt`
- `app/build/outputs/androidTest-results/cust05-swatch-opacity/Persistence.txt`
- `app/build/outputs/androidTest-results/cust05-swatch-opacity/LegendRender.txt`
