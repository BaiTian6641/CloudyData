# CUST09.3 secondary formatting and interactive legend evidence

**Date:** 2026-10-19
**Requirements:** CUST09, CH04/F36/F38, S11.C0
**Status:** implemented_unverified — emulator spec/render/persistence checks pass;
device visual and pointer interaction remain unverified.

## Implementation

- Secondary measures persist an independent number/percent/currency format,
  optional decimals and unit suffix; currency has its own code.
- The right `y2` axis uses that format, including non-USD currency label signals;
  secondary point labels use the same format. Axis title includes secondary units.
- Secondary tooltips use the secondary unit suffix instead of primary units;
  table, inspector and PDF headers use the secondary units.
- The secondary legend is interactive for dual-axis bars. Legend switching adds
  or removes only the secondary series key from `hiddenSeries`; primary bars,
  axes and data are unchanged. Hidden secondary line/markers/labels/hit targets
  are omitted while the legend remains available to restore them.
- Bar category legends remain non-interactive; secondary legend interaction is
  enabled only when a secondary layer exists.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **75/75**, including secondary decimals validation and existing bar/area/line cases. |
| Vega spec | `VegaSpecBuilderTest` **21/21**: secondary percent format/units, right-axis format/title, legend interactivity, hidden-secondary mark omission and legend retention. |
| Dual-axis query | `DualAxisQueryTest` **1/1**: independent AVG secondary vs SUM primary on non-monotonic values with shared rows. |
| Vega render probe | `VegaRendererProbeTest` **43/43**: dual-axis render asserts title, legend, labels and markers. |
| Persistence | `ChartSettingsPersistenceTest` **12/12**: secondary format/decimals/currency/units/markers/labels round-trip. |
| Build/lint | Debug/test APKs build; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No device check of secondary axis labels, unit suffix, currency glyphs, legend toggle, label collisions, dark theme or export. |

## Limits

- Secondary tooltips still show exact raw values with the unit suffix, not the
  formatted percent/currency display.
- Number-format secondary labels with blank decimals preserve `exactY`; the y2
  axis remains auto-formatted.
- Stored out-of-range secondary decimals are sanitized to null; builder-level
  invalid values are rejected.
- Legend usermeta carries primary+secondary keys, but interaction is enabled for
  BAR only when the primary bar legend is absent; color-series+secondary remains
  unsupported.
- Secondary axis manual min/max and independent tick count are absent.
- Interactive legend pointer behavior is not yet exercised by a device test.
- Arbitrary ordered layers/reorder/remove, color-series combination and
  horizontal dual axes remain absent.
- No visual/TalkBack/device performance qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust093-Vega-final2.txt`
- `app/build/outputs/androidTest-results/cust09-DualAxisQueryTest-final.txt`
- `app/build/outputs/androidTest-results/cust093-Persistence-final2.txt`
- `app/build/outputs/androidTest-results/cust093-Render-final2.txt`
- `app/build/outputs/androidTest-results/cust093-VegaRendererProbeTest-final2.txt`
