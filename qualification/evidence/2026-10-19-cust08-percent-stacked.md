# CUST08 100% stacked bar evidence

**Date:** 2026-10-19
**Requirements:** CUST08, CH01/F34, S02/S03
**Status:** implemented_unverified — emulator spec/render checks passed; device
visual acceptance remains absent.

## Implementation

- `BarStackMode` now persists NONE, STACKED and PERCENT_STACKED. Invalid values
  still fall back to NONE; switching to a non-additive aggregation resets the
  mode to NONE.
- For 100% stacks, `ChartSpecBuilder` sums each category, rejects negative or
  non-positive totals with a diagnostic, divides each segment by the positive
  total, and assigns cumulative normalized bases. Exact aggregate values remain
  in `exactY`, the result table, inspector and PDF table.
- The chart axis is forced to 0–1 and the presentation format to percent for this
  mode; user label decimals still apply. The underlying `ChartOptions` format is
  not mutated.
- Vega uses the same stack geometry as stacked bars, with stack endpoints driving
  the automatic Y domain. Bar labels and tooltips use the supplied normalized
  geometry while retaining exact values in their text model.
- Chart settings expose Grouped, Stacked and 100% options only for additive
  Sum/Count with Rows, Values and Color available.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **72/72**, including per-category normalization to 1.0, cumulative bases, percent axis/format, exact raw values, negative rejection and zero-total rejection. |
| Vega spec | `VegaSpecBuilderTest` **19/19**: PERCENT_STACKED metadata, 0–1 domain array, `,.1%` axis format and stacked geometry. |
| Vega render probe | `VegaRendererProbeTest` **40/40**: a 100% fixture renders 60.0% and 40.0% segment labels with a full stack. |
| Bar query | `BarChartQueryTest` **1/1** unchanged for grouped grain/order/filter/source rows. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: PERCENT_STACKED round-trips and invalid modes fall back to NONE. |
| Build/lint | Debug/test APKs build; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device inspection of percent labels, axis, legend, negative diagnostics, dark theme or export. |

## Limits

- Negative or zero-total categories are rejected rather than normalized; there is
  no divergent 100% treatment.
- Only Sum/Count are accepted as additive aggregations.
- Native fallback still refuses color-series bars.
- Percent labels may collide in narrow categories; collision policy uses the
  regular bar spacing rule.
- No visual/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust08-bars/VegaSpecBuilderTest-percent-final.txt`
- `app/build/outputs/androidTest-results/cust08-bars/VegaRendererProbeTest-percent-final.txt`
- `app/build/outputs/androidTest-results/cust08-bars/BarChartQueryTest-percent-final.txt`
- `app/build/outputs/androidTest-results/cust08-bars/ChartSettingsPersistenceTest-percent-final.txt`
