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
