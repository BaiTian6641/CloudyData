# CUST13 View Data evidence

**Date:** 2026-10-19
**Requirements:** CUST13, F39, Q21
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi.
**Fixture:** `chart_line_series.csv`, 6 source rows.
**Prerequisites verified:** exact row/series mark identity, selection reducer,
persisted keep/exclude filters and bounded DuckDB source access.

## Implementation

- `UnderlyingRowsQuery` rebuilds a KEEP predicate from the selected marks' exact
  row and optional series identities, combines it with the active worksheet
  filter, and executes a read-only `SELECT *` against the Parquet snapshot with
  `LIMIT 201`. At most 200 rows are displayed; truncation is explicit.
- The query never reads the aggregated chart result as raw data and never
  mutates the snapshot, workbook or selection.
- `WorkbookViewModel.ViewDataState` scopes loading/ready/failed results to a
  generation. Field, mark, filter and worksheet query changes invalidate and
  close stale view-data responses. The request carries the visible sheet ID and
  is rejected unless that sheet is the active worksheet and the accepted
  result's mark/series shape matches it.
- Worksheet tab selection now commits the active sheet and requeries it; a
  different sheet never displays the previous sheet's result while loading.
- `WorksheetResult.hasSeriesColumn` is mark-type aware, so a legacy TABLE/BAR
  sheet with a stale series field cannot misread its aggregate value column as a
  series identity.
- `WorksheetView` exposes `View data` whenever selected marks exist, including
  while a keep/exclude filter is active. Keep/exclude actions remain hidden in
  that filtered state.
- `ViewDataDialog` is a native accessible table: selected-mark count, source
  name, row count/truncation, worksheet-filter notice, typed source columns,
  exact-value row content descriptions and Close. The dialog does not import
  renderer content.

## Evidence

| Surface | Observation |
| --- | --- |
| Product View Data | With the `event_date` table, January's aggregate was 11 (visible in the worksheet). `View data` showed exactly two source rows: North Jan 1 = 10 and South Jan 1 = 1; footer still reported 6 source rows. |
| Filter context | Query test selects South under an EXCLUDE-North worksheet filter and receives exactly South's three source rows; `worksheetFilterApplied` is true. |
| Identity | Query test resolves the January aggregate row and an exact `(date, series)` line mark to their source rows, and verifies no invented rows without a dimension identity. |
| Cross-sheet | Product test switches from an `event_date` sheet to a `series` sheet; the visible series rows replace the old date result rather than rendering stale data or querying the first sheet. |
| Regression | `ViewDataQueryTest` 3/3; combined emulator suite (table selection, View Data, keep/exclude, renderer, spec) **32/32**; JVM **32/32**; lint 0 errors / 5 warnings. |

## Limits

- The 200-row display cap has no paging/load-more yet; truncation is labelled.
- View Data is selection-driven. There is no dialog for all rows when selection
  is empty, no column chooser, copy/export, or underlying-row filter editor.
- The cross-sheet fix makes visible tabs requery, but no dedicated multiwindow or
  cross-window conflict test exists.
- Exact values are preserved in row accessibility descriptions; visible cells
  use the worksheet display formatter.
- Lasso/rectangle, drill (no hierarchy yet) and persisted selection remain
  absent.

## Evidence files

- `artifacts/2026-10-19-cust13-view-data.png`
- `artifacts/2026-10-19-cust13-view-data-ui.xml`
- `android-test-results/2026-10-19/emulator-5582-view-data-suite.txt`
