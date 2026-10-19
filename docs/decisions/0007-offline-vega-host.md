# ADR 0007 — Bounded offline Vega host

**Date:** 2026-10-18
**Status:** accepted as a qualified probe; product chart integration pending

## Context

The architecture selects Vega in a restricted WebView for chart rendering, with
strict offline packaging, a CSP-compatible expression interpreter, typed
messages and no renderer authority over workbook data. S01.7 requires an actual
WebView experiment, not a browser mockup.

## Decision

- Pin Vega `6.4.0` and `vega-interpreter` `2.3.2` by registry tarball SHA-256.
  License texts are stored under `licenses/`; provenance is in
  `renderer-web/PROVENANCE.md`.
- The interpreter package only ships an ES module. `tools/build_vega_assets.py`
  verifies the tarball and body, removes its import/export boundary, and wraps it
  as `vega.expressionInterpreter` with three inlined vega-util primitives. The
  Function-constructor expression path is not enabled.
- Package every renderer asset locally. No CDN, remote font, image or data
  request is permitted.
- Serve assets from `https://appassets.androidplatform.net/assets/renderer/...`
  using `WebViewAssetLoader`. `blockNetworkLoads=true`; all non-app-asset
  requests are refused by `shouldInterceptRequest` and counted as blocked.
- The HTML declares `default-src 'none'`, `script-src 'self'`, `style-src
  'self'`, `img-src 'self' data:`, `connect-src 'none'`. The document is not
  navigable.
- Use an origin-restricted `WebMessageListener` named `CloudyBridge`; validate
  main-frame, exact source origin, string type, message size and protocol
  version. Host-to-renderer messages are generated JSON only; no arbitrary
  renderer spec comes from imported data or a URL.
- Render a fixed probe bar spec from Q06-shaped data in a debug-only
  `RendererProbeActivity`. The shipped product still uses the provisional native
  chart until renderer selection/export/resize/accessibility integration passes.

## Evidence

`VegaRendererProbeTest` passes on both the arm64 physical device and x86_64
emulator. It observes Vega 6.4.0, 2 data values, a non-empty Vega scene and SVG,
no external blocked request during render, and completes within 30 s. The
screenshot shows the chart and a native accessible summary. See
`qualification/evidence/2026-10-18-s01-vega-renderer.md`.

## Not established

- Renderer death (`onRenderProcessGone`) recovery, repeated restart bounds and
  resource measurement.
- Live resize/external display, selection/hover/context-menu intents, tooltip,
  export, print, focus exit and TalkBack traversal.
- Integration with the delivered workbook surface; the native provisional bar
  chart remains the product chart for now.
- Protection against a malicious Cloudy-authored spec beyond the constrained
  generator; no imported spec is accepted in product code.
- Renderer memory/performance envelopes or a D1 dashboard scene count.

## Amendment — 2026-10-18 renderer lifecycle

A rootable API 37 emulator was used to kill the actual WebView sandboxed
renderer process. The host received `onRenderProcessGone`, the probe disposed the
dead host, created a fresh host and replayed the current six-point line spec.
Evidence: `qualification/evidence/2026-10-18-s01-renderer-lifecycle.md` and
`renderer-death-logcat.txt`.

Verified in this increment:

- real renderer termination is reported, not mistaken for completion;
- the dead host's generation is retired before disposal, so queued callbacks
  cannot publish a completion as the recovered result;
- a fresh host (generation 3 after one death) can replay the accepted
  `ChartSpec`; the recovered status and logcat identify the host/apply sequence;
- recovery is bounded to two attempts; a third death publishes a typed terminal
  error and the instrumentation suite exercises that bound;
- `RendererRevisionGate` rejects stale chart/revision completions and
  apply-scoped failures; JS stamps completions with the identity captured at
  dispatch rather than the latest global state;
- the renderer reports its CSS surface and Vega view size. The 360×240 and
  640×400 dp tests assert exact Android px, CSS surface, Vega view and applied dp
  sizes, not only WebView layout; the real death run reports
  `surface=640x400 · view=640x400`;
- offline/origin/blocked-network behavior is unchanged.

Still pending: selection/hover/context-menu intents, product auto-recovery,
tooltip, export/print, focus exit, TalkBack traversal, live OS-window resize,
memory measurement and physical-device/WebView-provider qualification. This does
not complete S01.7.

## Amendment — 2026-10-18 selection and focus exit

The renderer now lowers `sourceRowIndex` and emits typed `SelectionIntent` and
`FocusExitIntent` messages. Bars use a named top-level rect; lines use top-level,
nearly transparent hit symbols over observed points and a Vega signal, because
facet-group child marks did not reliably carry click items. The bridge validates
origin/main frame and rejects stale chart/revision messages.

The worksheet maps the validated row index to the exact result row, highlights
that table row, and keeps the inspector/table as the exact-value surface. Arrow
keys move selection in source-table order; Escape/Tab moves keyboard routing out
of the WebView (verified behaviorally, not as a complete accessibility-tree
claim). Evidence:
`qualification/evidence/2026-10-18-s11-renderer-selection.md`.

Still absent: additive/range/lasso/keep-exclude/drill selection, visual mark
highlighting, tooltip, context menu, export/print, TalkBack traversal, physical
WebView-provider qualification. CUST13 remains partial.

## Amendment — 2026-10-18 single-series area

Added a single-series Area mark family (CH03 partial). Area reuses the Line
query shape, forces a zero baseline, rejects a color series/log axis and emits
area + outline + symbols + labels + the existing selection hit target. The
workbook persists `MarkType.AREA` and reopens it. Evidence:
`qualification/evidence/2026-10-18-s11-area-chart.md`. Stacked/100%/multi-series
area, tooltip and export remain pending.

## Amendment — 2026-10-18 bounded tooltip

Added a bounded DOM tooltip with exact `exactY` values, persisted
`ChartOptions.showTooltip`, selection/focus display and a Vega hover signal.
Selection still uses the validated chart/revision path; the native table remains
authoritative. Evidence: `qualification/evidence/2026-10-18-s11-tooltip.md`.
Pointer hover, field/format choices, collision policy and TalkBack announcement
remain pending; CUST12 is partial.

## Amendment — 2026-10-18 SVG export

Added typed ExportSvg/SvgExport and ExportPng/PngExport paths. The renderer
serializes its current SVG or rasterizes it in an offscreen canvas. The host gates
chart/revision, bounds documents (2 MiB SVG, 4 MiB PNG base64, 4096 px),
validates SVG elements/attributes (no scripts, event handlers, foreignObject,
embedded objects or external URLs) and validates the PNG signature/IHDR. The
worksheet opens CreateDocument pickers for SVG, PNG and a one-page PDF generated
natively from the validated PNG.
Export errors are non-fatal, saves run on Dispatchers.IO, and the pending payload
is retained in a ViewModel. Debug export evidence:
`qualification/evidence/2026-10-18-s11-svg-export.md`. Product end-to-end
save/open, print and pixel comparison remain pending; CUST14 is partial.

## Amendment — 2026-10-18 product SaveDocument write path

The worksheet's SVG/PNG/PDF actions were exercised through the real
DocumentsUI CreateDocument picker on the API 37 device profile. Provider files
were pulled and independently parsed; the second save was auto-renamed by the
provider. This verifies the product SaveDocument write path. A subsequent titled-export
increment embeds the chart title and source/snapshot note in a standalone header
and derives PNG/PDF from that scene; a real product run produced matching
1705×318 PNG/PDF outputs. It is still not Q24's single frozen multi-format
transaction, and the PDF remains a raster image without a text layer. Evidence:
`qualification/evidence/2026-10-18-s16-product-export.md`.

### Titled export addendum

`ChartSpec.sourceNote` carries the snapshot display name, row count and truncated
snapshot ID. The renderer wraps exports in a background/header/nested-chart SVG;
PNG and native PDF inherit it. Evidence:
`qualification/evidence/2026-10-18-s16-titled-export.md`.

### Frozen export bundle addendum

The Export menu can freeze one revision into an SVG scene, rasterize that exact
scene to PNG, derive the PDF natively, and write all three to one user-selected
SAF folder with a shared timestamp. Host validation cross-checks PNG/SVG
dimensions; cancellation and unknown-tree provider failure are explicit.
Evidence: `qualification/evidence/2026-10-18-s16-frozen-bundle.md`. SAF writes
are not transactional and PDF remains raster-only.
