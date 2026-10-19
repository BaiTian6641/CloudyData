# CUST03 marker shape evidence

**Date:** 2026-10-19
**Requirements:** CUST03, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator spec/render checks passed; visual
review remains absent.

## Implementation

- `MarkerShape` offers CIRCLE (default), SQUARE, DIAMOND and CROSS.
  Missing/invalid persisted values fall back to CIRCLE.
- `ChartOptions.markerShape` is carried into `MarkerSpec.shape`.
- `VegaSpecBuilder` emits symbol `shape` only for non-circle values, so default
  output remains byte-identical. Line and Area point symbols share the mapping;
  lines, hit targets, selection overlays, labels and data are untouched.
- Chart settings expose a marker-shape dropdown while markers are enabled.
  Applying it is presentation-only and does not rerun SQL.
- Marker size, visibility and the 200-point cap are unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **60/60**; default/custom shape propagation passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **12/12**, ChartSettingsPersistenceTest **10/10**, circle/diamond render **1/1** with identical line/symbol/row counts and no renderer error. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of shape legibility, size interaction, selection overlay or export appearance; exported circle/diamond SVGs are asserted different. |

## Limits

- Shape applies to Line/Area point symbols and non-circle legend swatches; it
  does not change selected-point overlays.
- Marker shape/visibility are hidden in settings for Bar and other mark types.
- No visual/TalkBack/export pixel qualification.
- Cross/diamond symbols may need size tuning; that remains user-controlled.

## Evidence files

- `app/build/outputs/androidTest-results/cust03-shapes/Vega.txt`
- `app/build/outputs/androidTest-results/cust03-shapes/Persistence.txt`
- `app/build/outputs/androidTest-results/cust03-shapes/ShapeRender.txt`
