# CUST02 subtitle and caption evidence

**Date:** 2026-10-19
**Requirements:** CUST02, F38, Q21, S11
**Status:** implemented_unverified — no device visual or host export-render run.

## Implementation

- `ChartOptions.subtitle` and `caption` are optional plain strings. The settings
  fields cap input at 160/240 characters; `ChartSpecBuilder` enforces the same
  bounds before the renderer/export path.
- The native chart header shows one bounded subtitle line under the title.
  Caption is shown below the chart with at most two lines and ellipsis; export
  and renderer controls remain in the header.
- `ApplyChartSpec` carries capped subtitle/caption fields to the offline renderer.
- SVG export now builds a variable-height provenance header with title,
  subtitle, caption (up to two width-derived lines) and source, using the same
  line-fitting helper and theme text colors; PNG is rasterized from that SVG.
  The nested chart retains its original dimensions below the header.
- The PDF text companion appends subtitle (one line) and caption (up to two
  wrapped lines) above source provenance. The raster first page inherits the
  SVG header.
- Presentation data does not rerun SQL or alter exact values; empty strings are
  omitted.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **44/44**; ChartSpecBuilder carries and truncates subtitle/caption. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Workbook persistence, probe export subtitle/caption and two-line caption assertions compile but were **not executed**. PDF subtitle/caption text rendering was not exercised. |
| Host export | SVG/PNG/PDF header rendering was **not executed** in this session. |
| Device visual | Not performed; no long-text, scaling, density, contrast or export claim. |

## Limits

- Native and exported long-text wrapping/collision were not visually inspected.
- Units and axis-title placement remain absent.
- Subtitle/caption are single workbook fields, not per-export overrides.
- PDF first-page raster text is not tagged/accessibly structured; the text
  companion supplies the searchable provenance lines.

## Evidence files

- Code/test-only slice; no device or export artifact was produced.
