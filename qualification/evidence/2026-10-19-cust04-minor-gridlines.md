# CUST04 minor-gridline evidence

**Date:** 2026-10-19
**Requirements:** CUST04, F36/F38, Q21, S11
**Status:** implemented_unverified — emulator render passed; device visual review
remains absent.

## Implementation

- `ChartOptions.minorGridlines` is optional and defaults off. Missing or invalid
  stored values mean no minor grid.
- `GridlineSpec.minor` carries the choice for Bar/Line/Area. The toggle is shown
  only while major gridlines are enabled; with major grid off, minor is inert.
- `VegaSpecBuilder` inserts a secondary value axis **before** the major axis:
  `grid=true`, `domain/ticks/labels=false`, `aria=false`, no title,
  `gridWidth=0.5`, `gridOpacity=0.55`, and the same divider/outline color as
  major gridlines.
- Minor positions are explicit `axis.values` from the resolved scale domain:
  `sequence(domain('y')[0], domain('y')[1], span / 20)`, with an empty array for
  a degenerate domain. This avoids Vega's nice-tick quantization making a second
  `tickCount` identical to the major ticks. Minor lines are linear-axis only.
- Major grid/ticks/labels/domain are drawn afterwards, so major lines and axis
  chrome remain authoritative. Data, scales, formatting, selection, tooltips and
  accessible summaries are unchanged.
- Chart settings apply is presentation-only and does not rerun SQL.

## Evidence

| Surface | Observation |
| --- | --- |
| JVM | **57/57**; minor default-off/carry passes. |
| Emulator `emulator-5580` (API 36, x86_64) | Manual instrumentation: VegaSpecBuilderTest **7/7**, ChartSettingsPersistenceTest **7/7**, focused export render **1/1**. The render has ≥10 distinct minor positions and at least one position between majors, with `stroke-opacity="0.55"`; five earlier render probes also pass. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Physical Googlebook | Deliberately not used in this slice; owner requested emulator-first validation. |
| Visual | No device visual review of minor-line spacing, dark contrast, log/reverse or export appearance. |

## Limits

- Minor divisions are a fixed 20-interval linear subdivision, not directly
  user-editable.
- Log axes intentionally omit minor gridlines; the settings toggle is hidden
  while log scale is active.
- Minor lines can coincide with major tick positions and rely on draw order for
  major-line precedence.
- The secondary axis is value-only; no minor X grid exists.
- Minor opacity/0.5 width are fixed renderer values, not persisted.
- Device visual, TalkBack and pixel comparison remain absent.

## Evidence files

- `app/build/outputs/androidTest-results/cust04-minor-emulator/Vega.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/Persistence.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/MinorRender.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/FormatRender.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/CrowdedBar.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/CrowdedLine.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/Transparent.txt`
- `app/build/outputs/androidTest-results/cust04-minor-emulator/Border.txt`
