# CUST09 secondary bar+line axis evidence

**Date:** 2026-10-19
**Requirements:** CUST09, CH04/F32/F34/F36, S11.C0
**Status:** implemented_unverified — emulator query/spec/render checks passed;
device visual acceptance remains absent.

## Implementation

- `WorksheetDef` persists `secondaryMeasureFieldId` and
  `secondaryAggregation`; `ChartOptions` persists the secondary axis title and
  synchronized-domain choice. Invalid/missing values fall back safely.
- For a vertical Bar with a secondary measure and no Color series, the semantic
  query returns `row`, primary `value` and `secondary_value` in one grouped
  result. Both measures share the same grouped row identity; no second source
  scan or client-side join is performed.
- `ChartSpecBuilder` emits a typed `ChartLayer` (`secondary-line`) with its own
  series, `AxisSpec` and synchronized flag. The primary bar/stack/percent path is
  unchanged; a horizontal primary bar rejects the secondary axis explicitly.
- Vega builds a separate `table2` data source, `y2` linear scale and right value
  axis; the secondary line and transparent hit target use `y2`, while the
  primary bars keep their own scale. Independent axes use the secondary value
  domain; synchronized axes copy the primary domain. Signals route secondary
  clicks/hovers to the shared source row.
- The worksheet exposes a Secondary Values shelf; chart settings expose the
  secondary axis title and synchronization toggle. The result table, inspector
  and PDF table label `secondary_value` with the secondary measure and
  aggregation. Secondary axis labels/tooltips do not inherit primary units or
  currency formatting.
- Secondary points carry an explicit `secondary` flag. Selection/hover signals
  carry it too; the host anchors secondary tooltips at `y2`, skips primary-table
  fallback/units, and uses only the per-point secondary hit symbols for tap and
  lasso hit-testing (the line mark is non-interactive to avoid path-wide bounds).
  The test fixture uses a non-monotonic secondary series and an AVG aggregation
  distinct from the primary SUM.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **74/74**, including secondary layer points, axis title/sync and horizontal rejection. |
| Dual-axis query | `DualAxisQueryTest` **1/1**: one grouped table with `row/value/secondary_value`, exact SUM/AVG values in primary sort order, and shared source rows. |
| Vega spec | `VegaSpecBuilderTest` **21/21**: `table2`, `y2` scale, right axis, primary bar on `y`, secondary line and hit symbol on `y2`, `secondary` flags, independent vs synchronized domains. |
| Vega render probe | `VegaRendererProbeTest` **43/43**: dual-axis fixture renders primary bars, secondary line, right axis title and export. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: secondary field/aggregation/title/sync round-trip. |
| Bar query | `BarChartQueryTest` **1/1** unchanged. |
| Build/lint | Debug/test APKs build; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device check of right-axis labels, line crossing, dual-axis scale readability, legend, dark theme or export pixels. |

## Limits

- One secondary LINE layer only; arbitrary ordered layers, reorder/remove and
  per-layer mark selection are absent.
- The query/persistence support an independent secondary aggregation; the
  current UI syncs it to the primary aggregation, so independent editing is not
  yet exposed.
- Secondary labels/markers, a secondary legend, color-series combination,
  horizontal dual axes, secondary units/currency formatting and manual bounds
  are absent.
- Source-row inspection is shared and exact at the grouped-row grain; the
  secondary line has no separate table result.
- Automated hover/lasso interaction tests for the secondary marks are absent;
  signal shape and mark bindings are asserted, but pointer paths were not
  exercised on device.
- No visual/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust09-DualAxisQueryTest-final.txt`
- `app/build/outputs/androidTest-results/cust09-VegaSpecBuilderTest-final.txt`
- `app/build/outputs/androidTest-results/cust09-VegaRendererProbeTest-final4.txt`
- `app/build/outputs/androidTest-results/cust09-ChartSettingsPersistenceTest-final.txt`
- `app/build/outputs/androidTest-results/cust09-BarChartQueryTest-final.txt`
