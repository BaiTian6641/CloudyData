# CUST08 horizontal bar orientation evidence

**Date:** 2026-10-19
**Requirements:** CUST08, CH01/F34, S02/S03
**Status:** implemented_unverified — emulator spec/render checks passed; device
visual acceptance remains absent.

## Implementation

- `BarOrientation` persists VERTICAL/HORIZONTAL; invalid values and the default
  remain VERTICAL.
- Horizontal bars swap roles: x is the linear value scale and y is the category
  band scale. Category and value axes, gridlines (including minor), titles,
  labels, selection highlights and tooltips follow the new geometry.
- Grouped, stacked and 100%-stacked bars reuse the same semantic layout and stack
  bases; horizontal grouped segments subdivide the category band vertically and
  horizontal stacked segments share the full band.
- The native Compose fallback remains vertical-only and refuses color-series or
  horizontal bars instead of drawing wrong geometry. Exact values remain in the
  table and PDF.
- Chart settings expose a persisted Orientation control for Bar marks.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **72/72**, including existing bar normalization/stacking and palette tests. |
| Vega spec | `VegaSpecBuilderTest` **19/19**: x linear/y band scales, left category and bottom value axes, horizontal x/x2/y/height encodings, screen-direction label alignment/clamping and orientation metadata. |
| Vega render probe | `VegaRendererProbeTest` **41/41**: a horizontal stacked fixture exports two >100 px bar paths with shared y/height and contiguous x extents, while vertical/percent/grouped tests still pass. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: HORIZONTAL round-trips and invalid orientation falls back to VERTICAL. |
| Bar query | `BarChartQueryTest` **1/1** unchanged; orientation is presentation-only. |
| Build/lint | Debug/test APKs build; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device inspection of horizontal labels, axis wrapping, legend, stacked/100% geometry, dark theme or export. One full renderer run aborted with a transient `Process crashed.` after two tests; immediate reruns passed all 41. |

## Limits

- Horizontal bars require the offline renderer; the native fallback is vertical
  single-series only.
- Long category labels on the left may wrap/clip without a visual pass.
- Value-axis minor gridlines use the horizontal branch but were not visually
  inspected.
- 100% negative/zero diagnostics and detail encodings remain as before.
- No visual/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust08-bars/VegaSpecBuilderTest-horizontal.txt`
- `app/build/outputs/androidTest-results/cust08-bars/VegaRendererProbeTest-horizontal-final4.txt`
- `app/build/outputs/androidTest-results/cust08-bars/ChartSettingsPersistenceTest-horizontal.txt`
- `app/build/outputs/androidTest-results/cust08-bars/BarChartQueryTest-horizontal.txt`
