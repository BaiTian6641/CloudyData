# CUST07 categorical palette evidence

**Date:** 2026-10-19
**Requirements:** CUST07, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual/color-vision
review absent.

## Implementation

- `design-system/tokens.json` adds proposed `chart.categoricalColorblind`
  light/dark eight-color palettes. Candidates were generated from the Paul Tol
  muted family (light) and Okabe-Ito bright family (dark), then lightness-scaled
  and checked for pairwise perceptual distance under simulated protanopia,
  deuteranopia and tritanopia (Machado linear-RGB matrices) and for WCAG 3:1
  contrast against the actual light/dark canvas and surface tokens. The native
  fallback track uses the canvas token, so marks are checked against their actual
  backdrop rather than only the card surface.
- `tools/generate_tokens.py` emits `CloudyTokens.Chart.categoricalColorblind*`;
  generated output passes `--check`.
- `CategoryPalette` offers CLOUDY (default) and COLORBLIND. Missing/invalid
  persisted values fall back to CLOUDY.
- `chart/categoryPaletteColors` is the single palette resolver used by the
  preview, probes and native fallback. Category marks use first-appearance order
  modulo palette size, matching Vega's ordinal range cycling; the previous
  label-hash mapping that diverged from Vega was removed.
- Chart settings expose a categorical-palette dropdown. Applying is
  presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **67/67**, including palette mapping, eight-color uniqueness, pairwise CIE76 ≥15 under normal/protan/deutan/tritan simulation, and 3:1 contrast against token canvases/surfaces. |
| Token tools | Generator `--check` current; declared contrast pairs **40/40**. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **18/18**, ChartSettingsPersistenceTest **12/12**, eight-series palette render **1/1** in light and dark, asserting every expected hex appears and mark counts are unchanged. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot/color-vision simulation of adjacent-category distinction, legend, labels, dark theme or export. |

## Limits

- Perceptual distinctness is a simulation check, not a user study or display
  measurement. Minimum pairwise CIE76 is 15.85 (light, all three CVD types) and
  15.68 (dark, tritan); minimum contrast is 3.064 light and 3.003 dark against
  the checked tokens.
- Two categories can still share a color when more than eight appear, because
  the eight-entry range cycles (Vega does the same).
- Only two palettes; no custom color assignment or persisted category map.
- Sequential/diverging palettes remain unreachable without a continuous color
  encoding.
- Native bar fallback and Vega use the same token arrays but are not pixel-compared.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust07-palette/Vega.txt`
- `app/build/outputs/androidTest-results/cust07-palette/Persistence.txt`
- `app/build/outputs/androidTest-results/cust07-palette/PaletteRender-final.txt`
  (`colorblindPaletteEmitsAllEightExpectedColorsInBothThemes`)
