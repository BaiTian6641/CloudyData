# CUST05 legend mark sample evidence

**Date:** 2026-10-19
**Requirements:** CUST05, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `LegendMarkSample` offers MARKER (default) and LINE. Missing/invalid persisted
  values fall back to MARKER.
- `ChartOptions.legendMarkSample` is carried into `LegendSpec`.
- MARKER preserves prior legend behavior, including custom marker shapes.
- LINE emits Vega `symbolType: "stroke"`, `symbolSize: 200`, the current line
  width and current dash pattern (width-aware dotted gap) in the legend only.
- Plot marks, data, selection, hidden-series filtering and legend hit targets
  remain unchanged; the setting is shown for Line charts only.
- Applying is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **64/64**; default/carry passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **17/17** (including dotted width-8 swatch), ChartSettingsPersistenceTest **10/10**, legend-sample render **1/1** with differing exports and identical mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of swatch length, dash alignment, label spacing, hit targets or export appearance. |

## Limits

- Legend line swatch mirrors width/dash but not opacity or the plot's dotted
  round cap; Vega's legend stroke symbol exposes no cap channel.
- Marker sample remains the only mode where custom marker shape is shown.
- Bottom and right legends share the same sample; no per-orientation tuning.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust05-legend-sample/Vega.txt`
- `app/build/outputs/androidTest-results/cust05-legend-sample/Persistence.txt`
- `app/build/outputs/androidTest-results/cust05-legend-sample/LegendSampleRender.txt`
