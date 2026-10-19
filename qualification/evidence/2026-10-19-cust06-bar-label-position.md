# CUST06 robust bar-label position evidence

**Date:** 2026-10-19
**Requirements:** CUST06, F32/F38, Q21, S11
**Status:** implemented_unverified — no device visual run was authorized.

## Implementation

- `ChartOptions.labelPosition` persists TOP (default) or INSIDE_END and is
  applied **only to Bar marks**; Line/Area force TOP because inside placement is
  not meaningful for those marks in this build. Unknown stored values fall back
  to TOP.
- Bar label geometry is computed in Vega signals from the actual Y scale:
  - `delta = scale('y',0) - scale('y',datum.y)` determines whether zero is
    below or above the endpoint, covering negative values and reversed axes.
  - INSIDE_END is active only for non-zero marks whose endpoint-to-zero distance
    is at least `fontSize + 18`; the label moves 6 px toward zero and uses a
    top/bottom baseline matching the direction.
  - Other labels fall outward from zero. The final position is clamped
    symmetrically inside plot bounds using a `cloudyPlotHeight` signal, so
    short/zero bars and endpoints beyond explicit Y bounds do not disappear.
  - Labels are `interactive:false`, so selection and hover continue to reach the
    bar rather than the value text.
  - Foreground and a 1.25 px inverse halo switch to the on-fill pair whenever
    the final anchor lies within the endpoint→zero bar span, including labels
    that clamping moved over the fill. White-on-light-palette measures
    4.92–7.38:1; `#101A1D`-on-dark-palette measures 8.10–10.46:1.
- Chart settings expose position only for Bar. Apply is presentation-only and
  does not rerun SQL. Exact values and aggregation are unchanged.

## Evidence

| Surface | Observation |
| --- | --- |
| Headless Vega 6.4.0 | Positive, negative, zero, short (1 px) and reversed bars; explicit `-200..-100` with `-250` and reverse `100..200` with `250`. Labels remained within the view; tall/active or clamped-over-fill labels used the contrast pair; off-fill labels kept canvas text color. |
| JVM | **43/43**; Bar position propagation, Line forced-TOP, font-size validation and width cases pass. |
| Build/lint | Debug and test APKs compile; lint 0 errors / 5 warnings. |
| Instrumentation | Signal/fill assertions, `interactive:false`, Line-forced-TOP and persistence assertions were added but **not executed**. |
| Device visual | Not performed; no native contrast, density, scaling, long-category or export claim. |

## Limits

- No device visual check at 1–8/8–16 font sizes, all densities, light/dark,
  narrow/short charts, long category labels or negative/reversed product data.
- Collision policy, label field selection and number/date formatting remain
  absent.
- Line/Area label position is intentionally not exposed.
- Headless scene checks are host evidence; they do not certify WebView pixels,
  accessibility or export output.

## Evidence files

- Code/test-only slice; no device artifact was produced.
