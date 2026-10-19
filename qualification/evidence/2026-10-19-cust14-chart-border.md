# CUST14 chart border evidence

**Date:** 2026-10-19
**Requirements:** CUST14, F36/F38, Q21, S11
**Status:** implemented_unverified — export rendering passed on the physical
Googlebook; native visual review remains absent.

## Implementation

- `ChartOptions.showChartBorder` is optional and defaults off. Missing or invalid
  stored values mean no border.
- `ChartSpecBuilder` carries it for Bar/Line/Area; `ChartSpec.chartBorder` is
  renderer-independent display intent.
- The native chart card draws a 1 dp `colors.divider` outline; fallback content
  is covered by that same parent card, with no duplicate inner border.
- `VegaSpecBuilder` puts the flag in spec user metadata. Export scene assembly
  reads it and draws a 1 px outline around the full exported frame, including any
  title/provenance header. PNG and PDF export use that same scene.
- Chart settings expose a toggle. Applying it is presentation-only and does not
  rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **53/53**; border carried and default-off pass. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **5/5**, ChartSettingsPersistenceTest **5/5**, and a focused exported-SVG border render **1/1** (validated, outer rect present). |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Device visual | Native border and fallback were not visually reviewed; no contrast, density, rounded-clipping or PNG/PDF pixel comparison. |

## Limits

- Border color follows the theme divider/grid color; custom colors are absent.
- Native width is 1 dp while exported width is 1 CSS px; no pixel-equivalence
  claim is made.
- The PDF page is rasterized from the same PNG scene, so border inheritance is
  structural, not PDF-vector-border evidence.
- Background, fit/zoom and persisted geometry remain absent.

## Evidence files

- `app/build/outputs/androidTest-results/cust14/vega.xml`
- `app/build/outputs/androidTest-results/cust14/persistence.xml`
- `app/build/outputs/androidTest-results/cust14/border-render.xml`
