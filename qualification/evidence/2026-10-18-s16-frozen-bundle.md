# S16 frozen multi-format export evidence

**Date:** 2026-10-18
**Stage/capability:** S16 partial, F59 visual export
**Fixtures/scenarios:** Q24 same-view multi-format output; Q18 destination
completion/cancellation/provider-unavailable subset
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi, preserved
Area worksheet.
**Worker-backed S11.C0/S13.C0/S15.C0:** not involved.

## Implementation

- The product header now has one **Export** menu:
  - **All formats — SVG, PNG, PDF** freezes one scene.
  - SVG/PNG/PDF-only options retain the previous single-format flow.
- `ExportBundle` snapshots chart ID/revision and serializes one titled SVG; the
  PNG is rasterized from that exact SVG text. The host validates the SVG, PNG
  signature/IHDR and cross-checks PNG dimensions against the SVG dimensions.
  Native PDF generation consumes the same PNG, so all three formats share one
  revision, title, provenance and dimensions.
- The user selects one real SAF folder (`OpenDocumentTree`); the app writes
  basename/timestamp-matched `.svg`, `.png`, `.pdf` files. On partial failure the
  created files are deleted and the UI reports failure.
- Cancellation writes nothing and reports `Export cancelled.`
- The frozen bundle is retained in a ViewModel across the folder picker; stale
  renderer revisions are rejected by the existing revision gate.
- `ExportBundleWriterTest` invokes the real external-storage provider with an
  unknown tree URI and requires an explicit failure result with a diagnostic.

## Actual evidence

| Scenario | Observed |
| --- | --- |
| Bundle success | `Exported SVG, PNG and PDF (1705×318) to Documents.` Provider files `cloudy-chart-20261018-232430.{svg,png,pdf}`. |
| SVG | 9,512 bytes, 1704.6666×318.33333; title `Observed sales` and `Source: chart_line_series.csv · 6 rows · snapshot 5fbbe640`; no script/remote reference. |
| PNG | 34,810 bytes, 1705×318 RGBA. |
| PDF | 16,119 bytes, one page 1705×318 pt, no JavaScript; rasterized page includes title/provenance and chart. |
| Cancellation | Folder picker cancelled; status `Export cancelled.`; file count unchanged (6 pre-existing bundle files, no new set). |
| Provider unavailable | Unknown external-storage tree returns `ExportWriteResult.Failure` with a nonblank diagnostic; no completion claimed. |
| Regression suite | API 37 **41/41**, 0 failures; JVM **24/24**; lint 0 errors / 5 warnings. |

Independent readers: XML parser, Pillow, pdftoppm/pdfinfo. Artifacts and status
captures are SHA-256 bound.

## Limits

- PDF remains a raster image without a text/accessibility layer.
- The three files are separate provider documents; there is no transactional
  SAF commit. Cleanup is best-effort if a later file fails.
- Storage-full, revoked-mid-write and provider-unavailable-after-first-file
  injection were not performed; unknown-tree failure and cancellation are
  verified.
- Stale-revision rejection is covered at the revision-gate level, not by a full
  UI race test.
- Print/multi-page delivery and product reopening of exported files remain
  pending. S16/CUST14 remain partial.

## Evidence

- `2026-10-18-frozen-bundle.{svg,png,pdf}`
- `2026-10-18-frozen-bundle-pdf-raster.png`
- `2026-10-18-frozen-bundle-success.{png,ui.xml}`
- `2026-10-18-frozen-bundle-cancel-ui.xml`
- `2026-10-18-frozen-bundle-folder-{picker,confirm}-ui.xml`
- `2026-10-18-frozen-bundle-downloads.txt`
- `android-test-results/2026-10-18/emulator-5582-frozen-bundle-suite.txt`
