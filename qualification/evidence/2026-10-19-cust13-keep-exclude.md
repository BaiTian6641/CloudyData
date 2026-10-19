# CUST13 keep/exclude mark filter evidence

**Date:** 2026-10-19
**Requirements:** CUST13, F39, Q21
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi.
**Fixture:** `chart_line_series.csv`, 6 source rows, snapshot preserved.

## Implementation

- `MarkFilter` stores `KEEP`/`EXCLUDE`, the row and optional series field IDs,
  and exact result-visible `FilterMark(row, series)` identities. It stores no
  source row offsets and never mutates the Parquet snapshot.
- `WorksheetDef.filter` is optional, persisted in workbook JSON, and read
  defensively. Field replacement, series replacement, mark changes and CSV
  replacement clear filters whose identities may no longer apply.
- `WorksheetQuery` applies `MarkFilterSql.predicate` as a `WHERE` clause against
  the source dimension before aggregation. Single-dimension marks use row
  equality; line marks use `(row, series)` pairs. Values are SQL-escaped and SQL
  null is matched with `IS NULL`.
- The worksheet offers `Keep only` and `Exclude` for a non-empty selection only
  when an accepted result exists and no filter is active. Applying the action
  clears the transient selection. A filter bar names the mode and mark count and
  offers `Clear filter`. Command-palette equivalents use the same view-model
  commands.
- One filter is active at a time; further keep/exclude actions are hidden until
  the filter is cleared. This avoids silently composing ambiguous filters.

## Evidence

| Surface | Observation |
| --- | --- |
| Product Bar keep | North selected → `Kept 1 mark`, one North bar and one exact table row, source footer still reports 6 rows. |
| Product Area exclude | South selected → `Excluded 1 mark`, one North mark remains, source footer still 6 rows. |
| Clear | `Clear filter` restores both marks and both table rows. |
| Persistence | Force-stop, relaunch and reopen retained `Excluded 1 mark` and the one-row result. |
| Query tests | `MarkFilterQueryTest` 3/3, plus `EngineSmokeTest` 4/4 and `LineChartQueryTest` 2/2: **9/9**. |
| JVM | **32/32**, including escaped SQL predicates, pair construction and builder rejection. |
| Lint | 0 errors / 5 documented warnings. |

## Limits

- Drill is not implemented: this build has no dimension hierarchy to descend.
  Selecting a mark and choosing Keep is not labelled as drill.
- Filters cover the active worksheet only. Multi-sheet/global filters, compound
  keep+exclude filters and filter persistence across a data refresh are out of
  scope and explicitly cleared on replacement or field changes.
- Persisted selection, lasso, pointer hover, keep/exclude from a filtered view
  and TalkBack multi-selection remain pending.

## Evidence files

- `artifacts/2026-10-19-cust13-keep-filter.png`
- `artifacts/2026-10-19-cust13-exclude-filter.png`
- `artifacts/2026-10-19-cust13-keep-filter-ui.xml`
- `artifacts/2026-10-19-cust13-filter-reopen-ui.xml`
- `android-test-results/2026-10-19/emulator-5582-keep-exclude-query-suite.txt`
