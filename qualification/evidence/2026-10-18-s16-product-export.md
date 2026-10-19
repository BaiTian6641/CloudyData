# S16 product visual-export evidence

**Date:** 2026-10-18
**Stage/capability:** S16 partial, F59 visual export (worksheet subset)
**Fixtures:** Q18 (provider save completion subset), Q24 (same-view visual
output subset)
**Prerequisites verified:** committed SVG/PNG/PDF renderer exports; product
SaveDocument UI; restored API 37 userdebug emulator `emulator-5582`
(2880×1800, 240 dpi) with the preserved `chart_line_series.csv` Area
worksheet.
**Worker-backed S11.C0/S13.C0/S15.C0:** not involved in this local export task.

## Actual product flow

1. Opened the saved Area worksheet from the real Home screen.
2. Clicked the product **SVG**, **PNG**, and **PDF** actions.
3. In the real DocumentsUI CreateDocument picker, accepted the default
   `cloudy-chart.*` name and pressed **SAVE**.
4. Pulled the provider-written bytes and inspected them with independent readers
   (XML parser, Pillow, pdfinfo).
5. Repeated SVG export; the provider created `cloudy-chart (1).svg` and the app
   reported `Exported SVG (8990 bytes).`

## Results

| Output | Observed result |
| --- | --- |
| SVG | 8,972 bytes from the product flow; root SVG, 1704.67×277.67; contains `Event date`, `Sales`, all four date categories; no script or remote href. |
| PNG | 23,637 bytes; decoded 1705×278 RGBA. |
| PDF | 16,479 bytes; one page, 1705×262 pt; pdfinfo reports no JavaScript. |
| Save status | Product UI showed `Exported SVG (8972 bytes).`, `Exported PNG (23637 bytes).`, `Exported PDF (16479 bytes).` |
| Second save | Provider auto-renamed to `cloudy-chart (1).svg`; app status `Exported SVG (8990 bytes).` |
| Offline boundary | Export used the packaged renderer and local provider flow; no network dependency observed. |
| Regression suite | Full instrumentation on API 37 after the exports: **38/38**, 0 failures (`emulator-5582-api37-full-suite.txt`). |

The product files are copied under `qualification/evidence/artifacts/` as
`2026-10-18-product-export.*`. The screen/picker/UI evidence is bound in
`SHA256SUMS`.

## Scope and limits

- **Verified:** real product button → renderer export → DocumentsUI CreateDocument
  → provider-written bytes for SVG, PNG and PDF. Per-format success UI dumps and
  the Downloads listing bind the byte counts and filenames. Independent external
  readers accept all three; no script/remote reference; no export callback
  treated as success.
- **Not verified:** reopening the saved exports inside the product (there is no
  product SVG/PNG/PDF reader), cancellation, unavailable provider, revoked
  permission, storage-full failure or completion ambiguity.
- **Partial Q18:** cancellation, unavailable provider, revoked permission,
  storage-full write failure and completion ambiguity were not injected. The
  product's IO failure/status path exists but is not exercised here.
- **Partial Q24:** these are three separate export actions from the live
  worksheet, not one frozen multi-format operation. Observed order/dimensions:
  SVG 1704.67×277.67 (content bounds), PNG 1705×278, PDF page 1705×262. The PDF is
  shorter than the SVG/PNG, consistent with a live surface that is not frozen
  across exports; no single size is guaranteed. Filters, legends, pagination and
  source/snapshot notes in the exported file are not yet equivalent; the Compose
  chart title ("Observed sales") is outside the renderer SVG and is not included
  in the visual artifact.
- **Not a product reopen:** independent readers verified the bytes, but the
  product has no viewer that reopens the saved exports; the wording "save/write
  path verified" is intentional.
- Story/dashboard multi-page print (F60), PNG/SVG/PDF parity from one frozen
  export snapshot, title/provenance embedding, accessible/tagged PDF, and print
  remain pending. S16 and CUST14 remain partial.

## Raw evidence

- `2026-10-18-product-export.svg` (8,972 bytes)
- `2026-10-18-product-export.png` (23,637 bytes, 1705×278)
- `2026-10-18-product-export.pdf` (16,479 bytes, one page 1705×262)
- `2026-10-18-product-export-success.png` / `-ui.xml` (PDF success)
- `2026-10-18-product-export-picker.png` / `-ui.xml`
- `2026-10-18-product-export-svg-success-ui.xml`
- `2026-10-18-product-export-png-success-ui.xml`
- `2026-10-18-product-export-second-save-ui.xml`
- `2026-10-18-product-export-second.svg` (provider-renamed 8,990-byte file)
- `2026-10-18-product-export-downloads.txt` (provider directory listing)
- `android-test-results/2026-10-18/emulator-5582-api37-full-suite.txt`
