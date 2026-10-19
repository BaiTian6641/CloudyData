# CUST08 color-series grouped and stacked bar evidence

**Date:** 2026-10-19
**Requirements:** CUST08, CH01/F34, S02/S03, Q21
**Status:** implemented_unverified — emulator query/spec/render checks passed;
device visual acceptance and the native color-series fallback remain absent.

## Implementation

- A Bar worksheet may use the Color shelf. `WorksheetQuery` groups by the
  category and color series, orders categories by their aggregate total
  (`sortDescending`), and orders series deterministically within each category.
- `ChartSpecBuilder` fixes the category order and each segment's `stackBase`
  before lowering. Grouped is the unstacked default; `barStackMode` persists
  `NONE` or `STACKED` and falls back to `NONE` for invalid data.
- The Vega lowering emits side-by-side bars for grouped mode and rects with
  explicit stacked `y`/`y2` for stacked mode. The Y scale uses each segment's
  stack endpoint (`displayY`), so the largest category total is inside the
  automatic domain. It never runs an aggregate or stack transform; color comes
  from a series ordinal scale and a category legend.
- Stacking is gated to additive Sum/Count aggregation; non-additive stacked
  selections are rejected instead of presenting a sum of averages as a total.
  Bar legend visibility/position/orientation are available in chart settings and
  apply preserves them; bar legend toggling is explicitly disabled.
- Bar legends are not interactive, matching the implemented behavior; grouped
  and stacked marks remain presentation-only and do not change the query.
- The native Compose fallback refuses color-series bars rather than drawing a
  single incorrect bar per grouped row. Exact values remain in the table,
  inspector and PDF table.
- The debug probe gained grouped/stacked bar fixtures. A probe bug that reset
  `valueFormat` to NUMBER unless an extra was supplied was fixed, restoring the
  existing percent-format probe.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **70/70**, including grouped/stacked bases, exact Feb total, no false series without color, non-additive stacking rejection and palette tests. |
| DuckDB emulator query | `BarChartQueryTest` **1/1**: totals/order with `SUM`, grouped query shape, exact Feb total 22, stacked bases, source-row resolution and a persisted category+series filter. |
| Vega spec | `VegaSpecBuilderTest` **19/19**: series color domain, bar legend fill, grouped/stacked geometry signals, `barStackMode` metadata, non-interactive bar legend, stack base/display-Y JSON, and the automatic Y domain field. |
| Vega render probe | `VegaRendererProbeTest` **39/39**: grouped and stacked six-segment fixtures render with distinct SVG geometry; percent labels remain `12.5%` / `25.0%`. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: STACKED survives save/reopen; invalid mode falls back to NONE. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device inspection of stacked segment heights, negative values, labels, legend, dark theme, 100% or export. |

## Limits

- Grouped/stacked bars require the offline renderer; the native fallback states
  that it cannot draw the shape.
- 100% stacking, horizontal bars and detail encodings remain absent.
- Stacked labels honor TOP/INSIDE_END; no other label positions are offered for
  bars. The native fallback cannot draw the shape.
- Stacked bases are Double geometry; exact values remain in `exactY` and the
  table.
- Duplicate display labels are grouped by SQL text, so two distinct source values
  that stringify identically share a category.
- Category colors still follow first-appearance order, so changing filters/order
  can recolor categories; no persisted category→color map exists.
- No visual/large-series/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust08-bars/BarChartQueryTest-final4.txt`
- `app/build/outputs/androidTest-results/cust08-bars/VegaSpecBuilderTest-final4.txt`
- `app/build/outputs/androidTest-results/cust08-bars/VegaRendererProbeTest-final4.txt`
- `app/build/outputs/androidTest-results/cust08-bars/ChartSettingsPersistenceTest-final4.txt`
