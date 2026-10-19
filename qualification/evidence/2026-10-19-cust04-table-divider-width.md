# CUST04 table divider width evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — persistence/selection checks passed; visual
review absent.

## Implementation

- `ChartOptions.tableDividerWidth` is optional and bounded to 0.5–3.0 dp.
  Missing/invalid values keep the previous 1 dp rules.
- Chart settings expose a decimal width field alongside row/column toggles.
- `ResultTable` uses the resolved thickness for header/body horizontal rules and
  column rules. Default remains 1 dp.
- Query/data/selection semantics and row content heights are unchanged; each
  table row's pitch grows by `thickness − 1 dp` because the rule is a sibling.
- Applying is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **64/64**. |
| Emulator `emulator-5580` (API 36, x86_64) | ChartSettingsPersistenceTest **11/11** including width/default/bounds; TableSelectionTest **5/5**; VegaSpecBuilderTest **17/17** unaffected. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of thick-rule geometry, scroll alignment, selection rows, density or dark theme. No Compose-level table test. |

## Limits

- Width applies uniformly to header/body/column rules; per-direction width is not
  configurable.
- Column rules add the selected width per boundary when enabled.
- No contrast/color control for table rules yet.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust04-divider-width/Persistence.txt`
- `app/build/outputs/androidTest-results/cust04-divider-width/TableSelection.txt`
- `app/build/outputs/androidTest-results/cust04-divider-width/Vega.txt`
