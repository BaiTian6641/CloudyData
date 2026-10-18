# ADR 0008 — Typed chart IR, line marks and workbook format v2

**Date:** 2026-10-18
**Status:** accepted for the current bar/line slice

## Context

The initial chart surface built raw Vega JSON directly in the Android host from
undifferentiated label/value pairs. It could not represent multiple series,
observed NULLs, truncation, axis ownership, or persisted chart options without
dual-writing legacy keys. An independent scout review identified these as
material risks before a line mark could be considered complete.

## Decision

- Introduce a renderer-independent `ChartSpec`/`ChartSeries`/`ChartPoint` IR in
  `chart/`. It carries kind, title, axis/scale semantics, gridlines, labels,
  legend, exact value text, defined/gap state, source row index and a spec
  version. The semantic layer builds it; the renderer lowers it.
- Lower the IR in `renderer/VegaSpecBuilder.kt`. It emits no analytical
  aggregate/bin/stack/filter transform. Multi-series line marks use a Vega group
  facet by `series`; each facet group has explicit width/height so clipping does
  not erase marks.
- Represent observed NULLs as `y=null`/`defined=false` and a `defined` Vega
  signal. Absent members are not synthesized. Current missing policy is
  connect-observed-neighbors; this is declared in the IR and docs.
- Refuse a truncated result before drawing. Line queries request up to 2001
  grouped rows so exactly 2000 acceptable rows can be distinguished from a
  truncated result; bar queries are also refused when the engine reports
  truncation.
- Bump the workbook format to v2. New writes use only nested `chart` options;
  v1 top-level `chartTitle/showGridlines/showValueLabels` are read and normalized
  on load. Nested options win over legacy fields. A future format version is
  rejected before migration. This prevents an older reader from silently
  dropping axis/legend/series state that it does not understand.
- Bar log scales are rejected in this slice (zero-baseline bars are not valid on
  a log domain); line log scales require positive values and positive explicit
  bounds. Bar category legends are suppressed until grouped/stacked bar
  semantics exist.
- Switching Table/Bar clears the line-only Color series rather than silently
  ignoring it.

## Consequences

- The chart surface now has a testable typed contract and a validated offline
  lowering path.
- Exact values remain in the table/inspector and `exactY`; chart geometry uses a
  separate Double coordinate.
- X remains a categorical band over ISO date labels; proportional temporal
  spacing, user-selected missing policy, mark selection, export, layers/dual
  axes and the remaining chart families are still open.
- Renderer death, matched resize and TalkBack traversal remain pending; this ADR
  does not complete S11.C0 or S11.
