# Chart grammar, types and customization

**Authority:** owner direction, 2026-10-18. **Status:** design contract for the
Cloudy Data chart surface; implementation is partial. This document expands
F34/F36/F38/F40/F41 and S06/S11. It is not a claim that the chart inventory is
complete or that Tableau parity has passed.

## 1. Chart selection is analytical grammar, not a gallery

A chart is a typed specification over the current MarkTable, not a picture. The
same field may appear in several placements with different aggregation, date
level, sort or axis. The semantic layer owns:

- aggregation, grain, filtering, domain completion, totals and table
  calculations;
- stacking, binning, facets, dual-axis planning and scale domains;
- exact values and their provenance.

The renderer receives a `ChartSpec` and MarkTable. It positions marks and draws
axes/legends/labels, but it must not silently reaggregate, alter filters, change
grain or substitute a different scale domain. A visual convenience must not
change the answer.

Every chart family requires: a typed spec, data and grain fixtures, a
customization surface, selection/inspection, keyboard access, save/reopen,
export and accessibility evidence. A screenshot of a chart family alone is not
acceptance.

## 2. Chart families to implement and catalog

The catalog is atomic in `parity/chart-catalog.yaml`; this table is the design
inventory. Variants include grouped/stacked/100%, horizontal/vertical, and
single/multiple measures where applicable.

| Family | Required baseline variants | Core encodings | Notes |
| --- | --- | --- | --- |
| Bar | vertical/horizontal; grouped; stacked; 100% stacked | rows/columns, measure, color, label, detail | Zero baseline by default; negative values must render correctly. |
| Line | single/multiple; stepped; curved/linear; with markers | x (often date), y, color, path, detail | Missing values and date domains must be explicit. |
| Area | single; stacked; 100% stacked | x, y, color, detail | Area is not a substitute for a line when overlap obscures values. |
| Combo / dual axis | bar+line; dual measures; synchronized axes | separate panes, mark layers, axes | Each axis owns its scale and mark layer; no accidental shared domain. |
| Scatter | single/multiple series; sized points | x, y, color, size, shape, detail | Disaggregated row identity must survive selection. |
| Text table / crosstab | flat; row/column headers; subtotals/grand totals | row/column dimensions, measures | Exact values, alignment and copy/export scope. |
| Highlight table / heat map | categorical; sequential; diverging | row, column, color/value | Missing data is visually distinct from the lowest value. |
| Pie / donut | category share; label/legend | category, measure, color, label | Explicit “other” handling; not for negative values. |
| Treemap | hierarchical/multi-level; size/color | hierarchy, size, color, label | Label collision and tiny-cell aggregation must be declared. |
| Bubble / packed bubbles | size/color/legend; labels | category, measure, color, size | Area scaling and overlap policy must be explicit. |
| Histogram | bin count/width; color groups | bin, count/measure | Bin boundaries and bin width are persisted. |
| Box plot | whiskers/outliers; grouped | category, measure, detail | Quantile/outlier convention is declared. |
| Gantt / timeline | start/duration; color/label | start, duration, row, color | Zero/negative duration handling. |
| Bullet | target and actual; qualitative ranges | category, measure, target, thresholds | Threshold values and units are inspectable. |
| Waterfall | sequential contribution; totals | category/order, delta, subtotal | Sign and total semantics are explicit. |
| Pareto | sorted bars + cumulative line | category, measure, dual axis | Sort and cumulative denominator are explicit. |
| Funnel | stage conversion; labels | stage, value, conversion | Stages are ordered and missing stages explicit. |
| Map — symbol | lat/lon or geographic role; color/size | geometry, measure, color, size | Offline base map/coordinate role status is separate. |
| Map — filled / path / polygon | region, path, polygon; layers | geometry, measure, color | CRS, axis order and invalid geometry must be visible. |
| Reference analysis | lines, bands, distributions, trend | axis/scalar, scope, confidence | Not a mark family; it overlays without altering data. |
| Statistical / predictive | forecast, cluster, model overlays | analysis spec + mark layers | Model/version/diagnostics are persisted; visual alone is not validation. |

Facets/small multiples, density, jitter, annotation layers and page playback are
composition/layer capabilities applied to these families, not separate chart
pictures.

## 3. Customization contract

Every applicable control must persist in the workbook, have a visible effect,
support undo, and have a non-drag/keyboard route. The inspector exposes groups;
the command palette can find the same operations.

### 3.1 Data and view controls

- Aggregation, disaggregation, aggregation-level and date-part/date-value.
- Filters and filter scope; top/bottom N; measure filters; context filters.
- Sort (manual, field, aggregate, nested, tie rules); address/partition controls
  for table calculations.
- Show/hide missing values and empty rows/columns; domain completion when
  explicitly selected.
- Bins (count/width) and histograms; group/set/parameter placement.
- Measure Names/Measure Values; multiple measures; add/remove/reorder.
- Facets/small multiples and independent/shared scales.
- Pages stepping/playback and mark history settings.

### 3.2 Titles, captions and text

- Chart title, subtitle and caption; left/center/right alignment; visibility.
- Sheet/worksheet title independent of the dashboard title.
- Axis titles and units; legend title; parameter/filter control labels.
- Descriptive text and annotations attached to a mark, point, region or pane.
- Accessible summary text generated from the same result, not a separate
  calculated answer.

### 3.3 Axes, scales and grids

- Continuous/categorical axes; fixed/automatic ranges; zero baseline toggle
  (bars default on); log scale with invalid-domain diagnostics; reversed axis.
- Major/minor ticks, tick count, tick label format, tick label rotation.
- Gridlines: show/hide, major/minor, color/width/opacity, above/below marks.
- Axis domain padding and reference domains; independent or synchronized axes.
- Dual axes; per-axis title, range and formatting; axis parity is explicit.
- Long labels, null/unknown categories and dense categorical axes must reflow or
  be labeled, not silently clipped.

### 3.4 Lines, marks, grids and tables

- Line width/style/color; markers; interpolation (linear/step/curve where
  supported); path order; missing-value gaps.
- Mark size and shape; opacity; border/stroke; stacking/normalization.
- Mark layers (add, reorder, remove, per-layer marks/axes) with stable layer
  identity.
- Crosstab row/column dividers, banding, header size, alignment, wrapping;
  subtotals and grand totals with separate plan nodes.
- Data grid virtualization and sticky headers; selection and clipboard scope.

### 3.5 Color, legends and labels

- Palette type: categorical, sequential, diverging, custom; palette version and
  category-key mapping persisted.
- Category identity stable across filters, sort, reopen and appearance; missing
  values use a distinct pattern/symbol/label.
- Legend visibility, position, orientation, title, mark sample and interactive
  filtering/highlighting where declared.
- Data labels: field/value, position, font, number/date format, collision policy,
  show-on-hover vs always; labels never replace exact tooltip/table values.
- Highlighting changes presentation; it must not silently change filters or
  totals.

### 3.6 Reference, annotation and statistical overlays

- Reference lines/bands/distributions with scope, axis, value/aggregation,
  label, confidence and styling.
- Trend lines and model overlays with type, domain, equation/diagnostics,
  confidence options and execution location.
- Box/whisker, forecast intervals and cluster overlays retain their own
  AnalysisSpec and model/version.
- Annotations persist with anchor identity and are not raw dataset HTML.

### 3.7 Tooltips, interaction and accessibility

- Tooltip field selection and formats; bounded values; sanitized text only.
- Selection, additive/range/lasso where appropriate, keep/exclude, drill,
  highlight and View Data.
- Hover is supplemental; every action is reachable by keyboard or a visible
  control. Chart selection has a native table/accessible equivalent.
- Focus visible above selection; a focus exit from the canvas/WebView; no trap.
- Accessible summary includes chart type, fields, filters, units, snapshot and
  exact result table availability.

### 3.8 Layout, formatting and export

- Chart size, fit width/height/entire view, zoom/pan and declared overflow.
- Padding, borders, background, rounded bounded surfaces only where the Cloudy
  shape roles allow.
- Number/date/unit formatting independent of stored precision.
- Export PNG/SVG/PDF/print with the same frozen result, filters, theme, fonts,
  legends, labels and page breaks; raster/unsupported elements are labeled.
- Theme/appearance changes must not change category meaning or recompute data.

## 4. Required controls and evidence

For each implemented chart family, the UI must expose its applicable:
data/aggregation, title/caption, axis titles/ranges/ticks/gridlines, legend,
labels, color/palette, size/shape, layers/dual axis, reference/annotation,
tooltip, sort/filter, layout, selection and export. Controls without their real
operation are not counted as completed.

Evidence must include the actual native surface, a saved/reopened workbook, exact
values against an independent fixture, a selection/inspection path, and an
accessible alternative. The initial implementation may expose a subset, but the
catalog must list the missing controls explicitly.

## 5. Current implementation boundary

The current worksheet supports a table mark, a bar mark, and a multi-series line
mark. The bar/line workbook options now include chart title, X/Y-axis and legend
titles, Y range, zero baseline, logarithmic/reversed Y, gridline visibility,
value labels, legend visibility/position and a stable Color series. Line rows are
grouped by the semantic query before rendering, observed NULLs are gaps rather
than zeros, absent members are not synthesized, and a truncated result is never
drawn as a partial line. The renderer is still a bounded offline Vega surface;
the native summary/table remains the exact-value and accessible equivalent.

The following remain absent or partial: horizontal bar variants, other chart
families, rich subtitle/caption formatting and
typed unit models, tick/grid styling, custom/interactive legend behavior, line
style/interpolation controls, temporal x scales, layers/dual axes, reference
analysis, annotations, tooltip field controls, chart selection, export and the
full responsive/input matrix. Those rows remain
`not_started` or `partial` in `parity/chart-catalog.yaml`; this document does not
promote them.
