# S01.7 offline Vega renderer probe evidence

**Date:** 2026-10-18
**Stage:** S01.7 (partial)
**Status:** renderer loads and draws a trusted bar spec offline on both targets;
death recovery, selection, resize, export and integration are not verified
**Platforms:** HP Googlebook 14 (arm64, API 37, WebView 149.0.7827.5) and API 37
x86_64 emulator
**Surface:** debug-only `RendererProbeActivity` plus the worksheet bar mark; the
native chart remains the fallback

## Pinned assets

| Asset | Version/hash |
| --- | --- |
| Vega tarball | 6.4.0, SHA-256 `3521fb6ff68a94a40323954f0ba4484ba4014b157bfd37f89a3b063082320c42` |
| `vega.min.js` | SHA-256 `8f6a3587cf8d4f42c7e08120e3eb05d067e746d554e39d2dcf52acc0bd5ba28f` |
| vega-interpreter tarball | 2.3.2, SHA-256 `b83489633c1320e9d6f839781f0c84c661a5ca2666fe38cb83f7c52a531fffec` |
| generated interpreter wrapper | SHA-256 `ad3b3076d749ff1e378f4f45ff30f1dce97d3c1465c30a7d7c21f8fcbb7b15e5` |

`vega.expressionInterpreter` is an object, not a function; the initial probe
incorrectly checked `typeof === 'function'` and reported an unavailable
interpreter. Inspecting the generated bundle and correcting the check resolved
it.

## Test result

`VegaRendererProbeTest.packagedVegaRendersTrustedBarSpecOffline` passes on both
targets as part of the 12-test suite (0 failures on each device):

- Vega version `6.4.0`;
- 2 data values for A/B;
- CSP interpreter available;
- non-empty scene (`group=5, rule=5, unknown=47, text=2, rect=1`);
- `svgPaths=8`, `svgNodes=71`;
- 0 external requests during render;
- complete within 30 s.

The scene counts show Vega's SVG renderer does not emit one DOM `rect` per data
row for this spec; the visible screenshot and the scene/SVG counts are both
recorded. The test does not assert an exact pixel result.

## Security configuration

- `blockNetworkLoads=true`.
- `WebViewAssetLoader` at the dedicated app-asset origin.
- Non-asset requests refused in `shouldInterceptRequest`; navigation refused.
- CSP: `default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'
  data:; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action
  'none'`.
- `WebMessageListener` allowed origin is exactly
  `https://appassets.androidplatform.net`; listener checks main frame, source
  origin, message type/size and protocol version.
- No `addJavascriptInterface`, no SQL/file/credential bridge, no renderer-
  authored spec from imported data.
- The app manifest currently requests no network permission.

## Screenshot

`qualification/evidence/artifacts/2026-10-18-vega-probe.png` shows the rendered
two-color bar chart and the native accessible summary. The surrounding
application windows are unrelated; the probe surface is the Cloudy Data window.

## Worksheet integration

The worksheet's Bar mark now embeds this host for at most 24 nonnegative
categories, passes the observed window size in dp and the Cloudy categorical
palette, and keeps the exact-value table plus a native text summary. A native bar
chart remains the explicit fallback for renderer errors, nonnumeric/negative
values, more than 24 categories, or interpreter failure. The chart is not a
selection surface yet; table-row selection is the native equivalent.

Observed on the physical Googlebook: `Offline Vega` badge, `A = 40`, `B = 60`,
rendered two-color bars, and `Query ready`; screenshot
`2026-10-18-worksheet-vega.png`. The product chart's superseded render
completions are discarded by chart-id/revision matching; they are no longer
treated as a renderer error.

## Limits

- No renderer-process death, repeated restart or memory measurement.
- No external display; resize is exercised indirectly by the live window layout
  but not as a matched input/geometry test.
- No chart selection/hover/context menu, tooltip, export, print or focus/TalkBack
  test; table selection is the accepted native equivalent for now.
- Native summary is present, but semantic traversal of the WebView has not been
  tested with TalkBack.
- Product integration covers only the bar mark. Mark-type/table/line integration
  remains pending.
- No imported or user-authored Vega specification is accepted.
