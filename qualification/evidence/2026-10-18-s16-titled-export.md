# S16 titled visual-export evidence

**Date:** 2026-10-18
**Stage/capability:** S16 partial, F59 visual export subset
**Fixtures:** Q24 (same view, multiple visual formats), Q18 write-path subset
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi, preserved
Area workbook.
**Worker-backed S11.C0/S13.C0/S15.C0:** not involved.

## Change

- `ChartSpec` now carries a bounded source note; the worksheet derives it from
  the snapshot display name, row count and truncated snapshot ID.
- The host sends chart title, source note and light/dark mode to the renderer.
- The renderer builds the exported SVG as a standalone scene with a background,
  56 px title/provenance header and the chart nested below it. PNG rasterization
  and native PDF generation consume that same titled SVG, so all formats inherit
  title/provenance and dimensions.
- Tooltip/selection logic is unchanged; export remains bounded and revision-gated.

## Actual product flow

On the preserved Area worksheet, the product **SVG**, **PNG** and **PDF** actions
were each exercised through DocumentsUI CreateDocument and SAVE. The provider
auto-renamed to `cloudy-chart (7).svg`, `cloudy-chart (6).png` and
`cloudy-chart (6).pdf` on the final run. Per-format status dumps report:

- `Exported SVG (9512 bytes).`
- `Exported PNG (34810 bytes).`
- `Exported PDF (16119 bytes).`

## Results

| Output | Observed |
| --- | --- |
| SVG | 9,512 bytes; 1704.6666×318.33333; contains `Observed sales` and `Source: chart_line_series.csv · 6 rows · snapshot 5fbbe640`; no script/remote reference. |
| PNG | 34,810 bytes; 1705×318 RGBA; title/provenance visible in the raster. |
| PDF | 16,119 bytes; 1 page 1705×318 pt; no JavaScript; rasterized page visibly contains the title/provenance and chart. |
| Cross-format size | SVG 1704.6666×318.33333, PNG 1705×318, PDF 1705×318. Matching dimensions across the final product exports. |
| Reader inspection | XML parser, Pillow, pdftoppm/pdfinfo; independent viewers/readers accept the files. |
| Regression | Full API 37 suite **39/39**, 0 failures; JVM **24/24**; lint 0 errors / 5 warnings. |

## Scope and limits

- Each format is still a separate user export action. The export-status line is
  now reserved so showing feedback does not resize the chart; the final runs
  matched dimensions. This is not yet a single frozen export transaction
  covering all formats.
- PDF is a rasterized image of the SVG. `pdftotext` finds no text layer; the
  document is not tagged/accessible. F59/F60 still require an accessible
  companion/table and explicit rasterization reporting.
- Source note is provenance text, not a cryptographic snapshot hash or filter
  list. Filters/parameters are not implemented in this slice.
- Q18 failure injection (revoked permission, unavailable provider, storage full)
  and cancellation remain unverified; Q24 remains partial until one frozen
  multi-format operation and pagination/legend parity are implemented.
- Header title/source text is width-truncated with an ellipsis; tiny surfaces can
  still clip conservatively. Source IDs are display provenance, not hashes.
- Story/dashboard multi-page print (F60) is not implemented.

## Evidence

- `2026-10-18-titled-export.svg`
- `2026-10-18-titled-export.png`
- `2026-10-18-titled-export.pdf`
- `2026-10-18-titled-export-pdf-raster.png` (independent PDF raster)
- `2026-10-18-titled-export-success.png` / `-ui.xml`
- `2026-10-18-titled-export-{svg,png,pdf}-status.xml`
- `2026-10-18-titled-export-downloads.txt`
- `android-test-results/2026-10-18/emulator-5582-titled-export-suite.txt`
