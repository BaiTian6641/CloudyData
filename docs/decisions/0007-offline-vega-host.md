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
