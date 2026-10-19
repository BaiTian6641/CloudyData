# CUST04 table row/column divider evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — persistence/selection checks passed; visual
table review remains absent.

## Implementation

- `ChartOptions.showTableRowDividers` defaults true and
  `showTableColumnDividers` defaults false, preserving the configured table
  divider state. JSON keys are `tableRowDividers`/`tableColumnDividers`, additive
  and backward-compatible.
- The shared `CloudyDivider` component now supplies `fillMaxWidth`/`fillMaxHeight`;
  before this fix horizontal dividers measured 0×1 dp and painted nothing. This
  makes row rules and other existing horizontal dividers visible app-wide. Older
  screenshots/goldens predate this change and are stale.
- Chart settings expose both toggles. Applying routes through
  `updateChartSettings`, which persists/recomposes without rerunning SQL.
- `ResultTable` conditionally draws body row rules and 1 dp column rules between
  header/cells. The header’s bottom divider remains structural.
- Row selection behavior, data, query and cell contents are unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **64/64**. |
| Emulator `emulator-5580` (API 36, x86_64) | ChartSettingsPersistenceTest **11/11** including save/reopen and defaults; TableSelectionTest **5/5** regression; VegaSpecBuilderTest **17/17** unaffected. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of column-rule alignment, scroll, selection rows, density or dark theme. No Compose-level table test. |

## Limits

- Column rules are inserted between fixed-width cells, so they add a small width
  when enabled.
- Header bottom divider is always present and is not controlled by the row toggle.
- Toggles are stored in chart options for the worksheet, not a separate table
  formatting model.
- The component-wide divider fix changes default visuals beyond the table and is
  not covered by a rendering test; existing screenshot evidence is stale.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust04-table-dividers/Persistence.txt`
- `app/build/outputs/androidTest-results/cust04-table-dividers/TableSelection.txt`
- `app/build/outputs/androidTest-results/cust04-table-dividers/Vega.txt`
