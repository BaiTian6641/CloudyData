# S16 printable / accessible worksheet evidence

**Date:** 2026-10-18
**Stage/capability:** S16 partial, F60 print and accessible delivery (worksheet subset)
**Fixtures:** Q18 print cancellation/output; Q24 visual output parity subset
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi, preserved
Area worksheet. Android print spooler with the `Save as PDF` destination.
**Worker-backed S11.C0/S13.C0/S15.C0:** not involved.

## Implementation

- `PdfExportWriter` now appends paginated A4 text pages to the raster chart page:
  title, source/snapshot note, chart summary, an explicit "Page 1 is a
  rasterized chart image" notice, column headers, exact rows and page numbers.
- The frozen bundle's PDF uses this accessible table, so SVG/PNG/PDF folder
  exports and print share one frozen scene.
- `PdfPrintAdapter` serves the already-frozen PDF bytes and declared page count
  to Android's print spooler; no renderer/file authority is exposed to the
  spooler.
- Product **Export → Print worksheet…** opens the native Android print flow.
  The status says “Print dialog opened; printing is not yet confirmed” because no
  spooler completion callback is claimed.

## Observed evidence

| Scenario | Observation |
| --- | --- |
| Native print | Print dialog opened with 2 pages; selected the real `Save as PDF` destination; output saved to Downloads. |
| Printed PDF | 55,772 bytes, 2 pages, A4 595×841 pt, no JavaScript, `Tagged: no`; producer Skia/PDF m148. |
| Text extraction | `pdftotext` returns `Observed sales`, source/snapshot provenance, the accessible-data notice and exact rows `2026-01-01 11`, `2026-02-01 22`, `2026-03-01 30`, `2026-04-01 4`. |
| Frozen bundle PDF | Product folder bundle PDF is 55,082 bytes, 2 pages; same searchable title/source/table text. SVG/PNG remain 9,504/34,810 bytes at 1705×318. |
| Accessible pagination test | 80-row table with long category labels produced 3 pages; page 2 is A4; `pdftotext` returns title/source/summary/rows and the complete wrapped labels. A single row taller than a page continued across pages with its end marker preserved. |
| PDF-only export | PDF-only product export produced the same 55,082-byte, 2-page accessible PDF (text title/source/table), not a raster-only page. |
| Print cancellation | Cancelled the native print dialog; PDF count in Downloads stayed unchanged; status remained the truthful "printing is not yet confirmed." |
| Regression | API 37 **43/43**, 0 failures; JVM **24/24**; lint 0 errors / 5 warnings. |

## Limits

- The PDF is **not tagged**; it has a searchable companion text layer but no
  tagged structure tree or reading order. It is not a fully accessible PDF
  claim. Page 1 remains a raster image with an explicit notice.
- Only the current worksheet is printed. Multi-page dashboard/story print,
  page ranges, scale/fit controls, headers/footers and PowerPoint output remain
  absent.
- Android print-spooler completion is not observed by the app; a saved print
  file is verified externally. The status is only "dialog opened" and is
  branched on a null `PrintManager.print` result.
- Print adapter writes in cancellable chunks off the main thread; page-range
  subsets are not regenerated independently.
- Print-spooler failure, storage-full and revoked-mid-write injection are not
  tested. Q18 cancellation is verified.
- F60/S16 remain partial.

## Evidence

- `2026-10-18-print-output.pdf`, `-info.txt`, `-txt`
- `2026-10-18-print-pdf-only.pdf`, `-txt`
- `2026-10-18-print-{dialog,printer-list,ready,after,cancel-dialog,cancel-after}*`
- `2026-10-18-accessible-bundle.{svg,png,pdf}`, `-raster.png`, `-success-ui.xml`
- `2026-10-18-accessible-pdf-test.pdf`, `-txt`
- `android-test-results/2026-10-18/emulator-5582-print-suite.txt`
