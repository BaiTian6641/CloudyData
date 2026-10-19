# CUST04 table divider contrast evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — persistence/selection checks passed; visual
contrast review absent.

## Implementation

- `TableDividerContrast` offers DEFAULT (theme divider) and STRONG (theme
  outline). Missing/invalid stored values fall back to DEFAULT.
- Chart settings expose a "Strong table dividers" toggle.
- `ResultTable` selects `colors.divider` or `colors.outline` for every
  header/body/column rule; thickness and visibility settings are unchanged.
- Query/data/selection semantics and apply-without-SQL behavior are unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **64/64**. |
| Emulator `emulator-5580` (API 36, x86_64) | ChartSettingsPersistenceTest **11/11** including strong round-trip and invalid fallback; TableSelectionTest **5/5**; VegaSpecBuilderTest **17/17** unaffected. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot/contrast check in light/dark themes, selection rows or export. No Compose-level table test. |

## Limits

- Only two token-derived contrast levels; arbitrary colors are not exposed.
- Rules are decorative and carry no semantics; selection semantics are unchanged.
- The component-wide divider visibility fix predates this slice; old screenshots
  remain stale.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust04-divider-contrast/Persistence.txt`
- `app/build/outputs/androidTest-results/cust04-divider-contrast/TableSelection.txt`
- `app/build/outputs/androidTest-results/cust04-divider-contrast/Vega.txt`
