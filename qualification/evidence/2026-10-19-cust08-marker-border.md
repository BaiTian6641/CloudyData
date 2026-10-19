# CUST08 marker border evidence

**Date:** 2026-10-19
**Requirements:** CUST08, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; visual review absent.

## Implementation

- `ChartOptions.markerBorder` defaults off; `markerBorderWidth` is optional and
  bounded to 0.5–3.0 dp. Missing/invalid widths keep the 1 dp default when the
  border is enabled.
- `MarkerSpec`/builder carry both values.
- Vega Line and Area point symbols receive `stroke = theme textColor` and the
  resolved stroke width only when enabled. Fill, shape, size, opacity, lines,
  labels, hit targets, selected overlays, data and selection are unchanged.
- Chart settings expose a Marker border toggle and width field while markers are
  enabled; hiding markers clears the persisted width.
- Applying is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **65/65**; default/carry/bounds pass. |
| Emulator `emulator-5580` (API 36, x86_64) | VegaSpecBuilderTest **18/18** (line + area), ChartSettingsPersistenceTest **12/12**, border render **1/1** with differing exports, exported stroke/width assertions and identical mark counts. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately untouched per owner instruction. |
| Visual | No screenshot check of stroke weight/contrast, dark theme, selection ring, legend swatch or export pixels. |

## Limits

- Border color follows theme text color; no custom border color.
- Legend marker swatches do not mirror the marker border.
- Marker border remains independent of line/area paint border/none.
- No visual/TalkBack/export pixel qualification.

## Evidence files

- `app/build/outputs/androidTest-results/cust08-marker-border/Vega.txt`
- `app/build/outputs/androidTest-results/cust08-marker-border/Persistence.txt`
- `app/build/outputs/androidTest-results/cust08-marker-border/BorderRender.txt`
