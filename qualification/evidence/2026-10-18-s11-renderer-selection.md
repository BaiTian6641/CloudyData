# Renderer selection and focus-exit evidence

**Date:** 2026-10-18
**Requirements:** S01.7, F39/T32, CUST13 (partial)
**Platform:** x86_64 API 37 emulator (`emulator-5580`), 2880×1800 @ 240 dpi
(density 1.5). Physical Googlebook unavailable.

## Implemented path

- Every lowered `ChartPoint` now carries `sourceRowIndex` into the Vega table.
- The renderer exposes two typed messages:
  - `SelectionIntent` with chart ID, revision, source row index, series and x;
  - `FocusExitIntent` with the current chart ID/revision.
- Bar marks are named `cloudyBar`; line charts add top-level, nearly transparent
  `cloudyHit` symbols over observed points. A Vega signal
  (`cloudySelection`) updates from `@cloudyBar:click` / `@cloudyHit:click` and the
  host posts the typed intent. Facet-group child marks are not used as click
  targets, so line clicks resolve to the exact top-level datum.
- The host validates main frame/origin in the existing bridge, rejects a chart
  ID/revision that is no longer current, bounds the source index and caps labels.
- The worksheet maps the index to `result.result.rows` and selects the exact
  table row; out-of-range indices are ignored. The native table/inspector remain
  the exact-value surface.
- ArrowRight/ArrowDown select the next observed point; ArrowLeft/ArrowUp select
  the previous point. Rows are sorted by `sourceRowIndex`, so movement follows
  table row order rather than Vega's series-faceted data order.
- Escape/Tab emit `FocusExitIntent`. The worksheet clears the native WebView
  focus and the Compose focus manager; the probe moves native focus to its status
  text. Instrumentation then sends another ArrowRight and asserts that no
  selection intent arrives, proving keyboard routing left the renderer. The
  product UI tree shows the WebView node `focused="false"`, but an internal
  WebView content node can still report focus, so this is not a TalkBack
  traversal claim.
- A new selection also requests native WebView focus so the arrow/Escape route is
  available immediately after a click.

## Observed evidence

| Surface | Observation |
| --- | --- |
| Debug bar probe, actual emulator tap | Status `Selected row=0 · series=Values · x=A · probe-chart r1`. |
| Debug line probe, hardware ArrowRight | Status `Selected row=0 · series=North · x=2026-01-01 · probe-line r1`. |
| Debug line probe, hardware Escape | Status `Focus exit requested; WebView released`. |
| Product worksheet, actual chart tap | First table row `2026-01-01 / North / 10` highlighted; click hit the first North point. |
| Product worksheet, ArrowRight | Selection advanced to `2026-01-01 / South / 1`, the next source row in the interleaved table. |
| Product worksheet, Escape | UI tree reports the WebView node `focused="false"`; a following ArrowRight produces no selection, proving keyboard routing left the renderer. |
| Stale results | `RendererRevisionGateTest` rejects old chart/revision completion. JS selection/error identities are captured at dispatch; the host gates both. |
| Offline boundary | No external request; native accessible summary remains below the chart. |

Automated instrumentation performs two actual `UiDevice.click`s (bar A row 0,
bar B row 1) and actual `UiDevice.pressKeyCode` events for arrows, Escape and Tab.
After Escape and Tab it asserts an extra ArrowRight does not reach the renderer.
Seven `VegaRendererProbeTest` cases pass; full emulator suite **32/32**, JVM
**19/19**, lint **0 errors / 5 documented warnings**.

## Evidence files

- `2026-10-18-renderer-selection-click.png` / `-ui.xml`
- `2026-10-18-renderer-keyboard-selection.png` / `-ui.xml`
- `2026-10-18-renderer-focus-exit.png` / `-ui.xml`
- `2026-10-18-product-selection-click.png` / `-ui.xml`
- `2026-10-18-product-selection-keyboard.png`
- `2026-10-18-product-focus-exit.png` / `-ui.xml`
- `android-test-results/2026-10-18/emulator-am-instrument-full-suite.txt`

## Limits

- This is **not** full CUST13. Additive/range/lasso selection, keep/exclude,
  drill, context menus, visual mark highlighting and tooltips remain absent.
  The selected table row and inspector are the feedback path.
- Hit targets are approximately 11 CSS px across on the line chart; small
  targets and touch precision remain a usability/test-coverage risk.
- Arrow selection follows source table order, not visual x position.
- TalkBack traversal of the WebView chart, keyboard focus indicator styling,
  chart export/print, live OS-window resize and physical-device/WebView-provider
  behavior remain unverified. An internal WebView content node can still report
  focus after Escape even though keyboard routing no longer selects.
- The debug focus screenshot shows both the status text and an internal WebView
  node as focused; the behavioral no-selection assertion, not that tree alone,
  is the release evidence.
- The emulator-only evidence does not establish physical Googlebook behavior.
