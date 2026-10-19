# CUST06 currency display evidence

**Date:** 2026-10-19
**Requirements:** CUST06, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual/locale review
absent.

## Implementation

- `ValueDisplayFormat` adds CURRENCY; `CurrencyCode` offers USD, EUR, GBP and
  JPY. Missing/invalid codes fall back to USD; missing formats fall back to
  NUMBER.
- Currency formatting is fixed to two decimals. USD uses d3 `$,.2f` (including
  U+2212 negative signs); other currencies use an axis label-encode signal and
  label text signal with the symbol, matching U+2212 negative sign and
  `format(abs(value), ",.2f")`.
- Value labels use the same currency expression with the existing `isValid`
  guard. Crowded-label spacing is widened for currency (`fontSize × 7 + 6` (83 at 11sp)) to
  account for symbol/grouping/decimals. Stored values, scales, exact tooltips/exports,
  selection and accessible summaries are unchanged.
- Chart settings expose a Number/Percent/Currency value-format dropdown and a
  currency-code dropdown; decimal input is hidden for currency.
- Applying is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **65/65**; currency carry/default passes. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **18/18** (negative U+2212, currency crowded threshold), ChartSettingsPersistenceTest **12/12**, USD/EUR render **1/1** with negative `−$`/`−€` SVG texts and equal mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of symbol width, negative placement, label collisions, clip, units duplication, dark theme or export pixels. |

## Limits

- Currency formatting is fixed to two decimals and hides the decimal field;
  switching formats preserves the previously stored decimal count.
- Symbols are `$`, `€`, `£`, `¥`; no locale-aware placement, accounting
  negatives, or currency grouping conventions.
- Units remain independently appended to titles/tooltips, so choosing currency
  and units can duplicate currency context.
- JPY still displays two decimals.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust06-currency/Vega.txt`
- `app/build/outputs/androidTest-results/cust06-currency/Persistence.txt`
- `app/build/outputs/androidTest-results/cust06-currency/CurrencyRender.txt`
