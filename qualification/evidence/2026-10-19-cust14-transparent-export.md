# CUST14 transparent export background evidence

**Date:** 2026-10-19
**Requirements:** CUST14, F36/F38, Q21, S11
**Status:** implemented_unverified — SVG/PNG export passed on the physical
Googlebook; PDF and native visual review remain absent.

## Implementation

- `ChartOptions.transparentExportBackground` is optional and defaults off.
  Missing or invalid stored values keep the opaque background.
- `ChartSpecBuilder` carries it for Bar/Line/Area; the offline renderer receives
  it through spec user metadata.
- Export scene assembly leaves the outer background rect with `fill="none"` when
  enabled. Header text, chart content and the optional border are still drawn.
  PNG rasterization therefore keeps alpha; PDF/print paint the theme background
  before compositing that PNG.
- The on-screen chart card keeps its normal surface; the control is explicitly
  labeled "Transparent SVG/PNG"; its description says PDF/print use the chart
  theme background.
- Chart settings apply is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **56/56**; transparent/default carry passes. |
| Physical Googlebook (Android 17/API 37) | VegaSpecBuilderTest **6/6**, ChartSettingsPersistenceTest **7/7**, transparent SVG/PNG render **1/1**, default opaque bundle **1/1**, and dark transparent PDF compositing **1/1** (corner renders theme `#182427`; content remains opaque). |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Device visual | Not performed; no viewer-composited alpha, PDF background, dark theme or pixel comparison. |

## Limits

- Only the exported scene is transparent. The native card remains on the app
  surface, so the toggle does not preview transparency in place.
- PDF stays opaque and uses the chart theme background; only SVG/PNG are
  transparent.
- Transparent dark SVG/PNG text is intended for dark destinations. Pasting them
  on a light document can reduce contrast; no adaptive foreground exists.
- The border, if enabled, remains visible against transparency.
- No animated or viewer-specific background check exists.
- The dark PDF test invokes the writer with the same theme color the production
  path computes; the captured-spec color selection itself is code-inspected,
  not instrumented.

## Evidence files

- `app/build/outputs/androidTest-results/cust14-transparent/vega.xml`
- `app/build/outputs/androidTest-results/cust14-transparent/persistence.xml`
- `app/build/outputs/androidTest-results/cust14-transparent/transparentExportOmitsOpaqueBackgroundFromSvgAndPng.xml`
- `app/build/outputs/androidTest-results/cust14-transparent/frozenBundleHasMatchingSvgPngPdfAndEvidenceFiles.xml`
- `app/build/outputs/androidTest-results/cust14-transparent/transparentDarkPdfUsesThemeBackgroundForContrast.xml`
