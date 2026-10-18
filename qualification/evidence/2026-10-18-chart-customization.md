# Chart customization evidence — title, gridlines, value labels

**Date:** 2026-10-18
**Owner direction:** visualization chart types and chart customization (data,
lines, grids, titles, etc.) must be explicit in design and planning; see the
addenda in all three plan files and
`docs/design/chart-grammar-and-customization.md`.
**Stage:** S06/S11 partial
**Platform:** HP Googlebook 14, arm64-v8a, Android 17/API 37
**Artifacts:** `2026-10-18-chart-settings-dialog.png`,
`2026-10-18-chart-customized.png`, `2026-10-18-chart-reopened.png`, UI dumps.

## Implemented and observed

| Control | Result |
| --- | --- |
| Chart title | Opened Chart settings, entered `Regional sales`, applied. The worksheet card title changed from the derived `Sum of sales by region` to `Regional sales`. |
| Gridlines | The value-axis grid is visible; the dialog exposes a real switch. The default is on. |
| Value labels | Toggled on; `60` and `40` render above the bars; the exact table still shows the same values. |
| Persistence | Force-stopped and relaunched; reopened the workbook. `Regional sales`, the enabled value labels and the grid state remained. |
| Query independence | The settings are presentation-only in the ViewModel: `updateChartSettings` persists and recomposes without calling the query path. The existing result/query state was retained. |
| Renderer | The Vega host receives `showGridlines` and `showValueLabels` and regenerates its spec; the offline renderer remained active. |

The chart settings are stored on the worksheet (`chartTitle`, `showGridlines`,
`showValueLabels`) with defaults for older workbooks. Automated persistence test
`ChartSettingsPersistenceTest` passes; full suite is 18 tests per device.

## Documentation added

- Product addendum §10, architecture addendum §13, runbook addendum §11.
- `docs/design/chart-grammar-and-customization.md` with chart family and
  customization contracts.
- `parity/chart-catalog.yaml` with 24 chart family rows and 17 customization
  areas, including explicit partial/not-started status.

## Limits

- Only bar/table exist; titles are native card text, not yet part of a portable
  ChartSpec export.
- Gridline styling/minor grids, axis range/log/reverse/ticks, labels positioning/
  formatting/collision, legends, line/marker styling, layers, dual axis,
  reference lines/annotations, tooltips, chart selection and export are absent.
- No query rerun was observed, but this is supported by code structure and the
  status state, not an instrumented SQL-call count.
- Chart keyboard navigation and TalkBack traversal of the WebView are not tested;
  the native table/summary remain the accessible equivalent.
