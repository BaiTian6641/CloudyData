# CUST09.2 secondary aggregation, legend and labels evidence

**Date:** 2026-10-19
**Requirements:** CUST09, CH04/F32/F36/F38, S11.C0
**Status:** implemented_unverified — emulator query/spec/render checks passed;
device visual acceptance remains absent.

## Implementation

- The Secondary Values shelf now exposes an independent aggregation menu.
  `secondaryAggregation` is persisted on the worksheet and is no longer reset
  when the primary aggregation changes. Defaults still use the primary
  aggregation when the secondary field is first selected.
- The secondary axis owns a stroke-based legend scale/legend labelled
  “Secondary”, using the secondary line colour, width, dash and opacity, and the
  configured legend position/orientation. It obeys the shared legend visibility.
- Persisted `secondaryShowMarkers` and `secondaryShowLabels` controls add
  secondary point symbols and exact-value labels above the `y2` line. Labels use
  `exactY`, fall back to a plain number when absent, and clamp inside the plot.
  Markers are not clipped at the domain edge; labels/markers are non-interactive
  so the transparent hit symbol remains authoritative.
- Primary bar, stack/percent and area paths remain unchanged. Secondary labels,
  markers and legend are presentation-only; selection remains through the
  per-point secondary hit symbols.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **74/74**; secondary layer and horizontal-rejection tests still pass. |
| Dual-axis query | `DualAxisQueryTest` **1/1**: independent AVG secondary vs SUM primary on non-monotonic values, shared source rows. |
| Vega spec | `VegaSpecBuilderTest` **21/21**: secondary legend scale/legend, marker/label `y2` bindings, exact-label text, legend-off and marker/label-off behavior. |
| Vega render probe | `VegaRendererProbeTest` **43/43**: dual-axis render asserts right-axis title “Cost axis”, legend title “Secondary”, secondary label “90” and marker symbols. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: secondary markers/labels and aggregation/title/sync round-trip. |
| Build/lint | Debug/test APKs build; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device check of legend placement, label collisions, marker contrast, dark theme or export. |

## Limits

- Secondary number/currency formatting and units are not yet configurable; the
  right axis uses plain number labels and tooltips use the exact raw value.
- The secondary legend is not interactive (bar legends remain non-interactive).
- The secondary legend obeys the shared legend visibility/position/orientation
  controls; its title is currently fixed to “Secondary”.
- Secondary labels can collide in dense charts; no secondary label collision
  policy exists.
- Only one secondary LINE layer; arbitrary ordered layers/reorder remain absent.
- Automated pointer hover/lasso tests for the secondary layer remain absent.
- No visual/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust092-VegaSpecBuilderTest.txt`
- `app/build/outputs/androidTest-results/cust09-DualAxisQueryTest-final.txt`
- `app/build/outputs/androidTest-results/cust092-ChartSettingsPersistenceTest.txt`
- `app/build/outputs/androidTest-results/cust092-VegaRendererProbeTest-final.txt`
