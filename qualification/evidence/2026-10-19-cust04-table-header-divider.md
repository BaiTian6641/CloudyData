# CUST04 table header divider evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — persistence/selection checks passed; visual
review absent.

## Implementation

- `ChartOptions.showTableHeaderDivider` defaults true and persists as
  `tableHeaderDivider`.
- Chart settings expose a toggle; `ResultTable` draws the header rule only when
  enabled, using the shared table divider width/contrast.
- Header content, body row/column toggles, query, data and selection semantics
  are unchanged.
- Applying is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **65/65**. |
| Emulator `emulator-5580` (API 36, x86_64) | ChartSettingsPersistenceTest **12/12** including false round-trip and default true; TableSelectionTest **5/5**; VegaSpecBuilderTest **18/18** unaffected. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot/Compose table test of header-rule removal, empty-table path, width/contrast or dark theme. |

## Limits

- Header content spacing remains unchanged; only the rule is removed.
- Header rule shares the global table divider width/contrast, with no independent
  styling.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust04-header-divider/Persistence.txt`
- `app/build/outputs/androidTest-results/cust04-header-divider/TableSelection.txt`
- `app/build/outputs/androidTest-results/cust04-header-divider/Vega.txt`
