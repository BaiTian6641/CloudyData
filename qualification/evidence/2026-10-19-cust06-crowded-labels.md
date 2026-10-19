# CUST06 bounded crowded-label policy evidence

**Date:** 2026-10-19
**Requirements:** CUST06, F36/F38, Q21, S11
**Status:** implemented_unverified — renderer behavior passed on the physical
Googlebook; device visual review remains absent.

## Implementation

- `LabelCollisionPolicy` offers `SHOW_ALL` (default) and `HIDE_CROWDED`.
  Missing/invalid persisted values fall back to `SHOW_ALL`.
- `ChartSpecBuilder` carries the policy for Bar, Line and Area.
- `VegaSpecBuilder` places the decision in the text mark's `text` signal, not
  `defined`: a text mark's `defined` is ignored by Vega. Crowded labels emit an
  empty string, so they draw no text.
- Bar, Line and Area labels compare the band's actual `bandwidth('x')` with
  `fontSize + 36`. Using the band width also keeps multi-series lines from
  hiding labels that share the same category across series. Default `SHOW_ALL`
  keeps the previous field-based text encoding when no decimal format is active.
- Chart settings expose "Hide crowded labels" only while value labels are on.
  Applying it is presentation-only and does not rerun SQL.
- Values, scales, selection, exact table, tooltip and accessible summary are
  unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **55/55**; policy carried/default passes. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **6/6**, ChartSettingsPersistenceTest **7/7**, a 60-category bar render **1/1** with no value labels, a 40-category three-series line render **1/1** with no labels, and a wide 2-category formatted render **1/1** still showing labels. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Device visual | Not performed; no overlap, clipping, rotation, multiple-series or accessibility claim. |

## Limits

- The policy is a bounded density heuristic, not text-measured collision
  detection; long labels can still overlap at spacing above the threshold.
- It applies only to displayed value labels, not axis labels.
- The threshold does not depend on units/decimal width.
- Dark theme, exports and native fallback have no extra visual qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust06-collision/vega.xml`
- `app/build/outputs/androidTest-results/cust06-collision/persistence.xml`
- `app/build/outputs/androidTest-results/cust06-collision/crowded-render.xml`
- `app/build/outputs/androidTest-results/cust06-collision/crowded-line-render.xml`
- `app/build/outputs/androidTest-results/cust06-collision/format-render.xml`
