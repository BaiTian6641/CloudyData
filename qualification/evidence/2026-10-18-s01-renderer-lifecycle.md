# S01.7 renderer lifecycle evidence

**Date:** 2026-10-18
**Stage:** S01.7 (partial; lifecycle subset verified)
**Requirements:** S01.7, F34/F39/T32, Q21/Q22/Q23
**Platform:** x86_64 API 37 emulator (`emulator-5580`), rootable userdebug image,
model `sdk_gphone64_x86_64`, 2880×1800 @ 240 dpi (density 1.5).
**Physical Googlebook:** unavailable for this increment.

## Cases and results

| Case | Result |
| --- | --- |
| Real WebView renderer termination | Root `kill -9` on the WebView sandboxed renderer PID (29075). Android logged `Renderer process (29075) crash detected (code -1)`. The host received `renderer_process_gone`. |
| Generation retirement | The probe increments `hostGeneration` *before* disposing the dead host, so queued callbacks from the dead generation cannot publish a completion. The recovered completion reports `host=3` (initial host 1; retirement token 2; fresh host 3). |
| Recovery/replay | The probe created a fresh WebView host and replayed the current six-point, two-series line `ChartSpec` at 640×400 dp. |
| No false completion | The recovered status includes `6 points`, `line=2`, `host=3`, and `recovered after 1 death(s)`. The kill script rejects the run unless all of these are present and the string `Renderer error` is absent. |
| Renderer surface, not just layout | JS reports `documentElement.clientWidth/Height` and `view.width()/height()`. The recovered status shows `surface=640x400 · view=640x400`, proving the CSS surface and Vega view match the requested dp, not merely the Android view layout. |
| Matched viewport test | Instrumentation renders at 360×240 dp and 640×400 dp, asserts exact Android px sizes (`dp × 1.5`), CSS surface sizes, Vega view sizes and applied dp. Both render two values. |
| Recovery bound | Instrumentation simulates three consecutive deaths: the first two recreate and replay successfully; the third publishes a terminal `renderer_process_gone` error with `rendererDeaths=3`, `recoveryAttempts=2` and the text `exhausted`. |
| Stale completion rejection | JS stamps each `RenderComplete` with the chart ID/revision captured at dispatch, not global latest state. `RendererRevisionGate` rejects an old chart/revision completion; a unit test covers old revision and different chart. |
| Stale failure rejection | JS includes chart ID/revision on apply-scoped render failures; the host drops a failure that is no longer current instead of failing the current chart. |
| Offline boundary | No external request count increased; packaged assets/CSP/origin checks remain intact. |
| Native accessible summary | The probe retains the exact text summary beneath the chart. |

## Commands and observations

```text
am instrument -w io.github...test/androidx.test.runner.AndroidJUnitRunner
OK (30 tests), 0 failures

tools/qualify_renderer_death.sh emulator-5580
killing WebView renderer pid(s): 29075
Rendered probe-line r1 · 6 points · scene={group=15, rule=5, unknown=62,
text=9, line=2, symbol=4} · svgPaths=28 · svgNodes=137 · Vega 6.4.0 ·
surface=640x400 · view=640x400 · host=3 · recovered after 1 death(s)
renderer death/recovery qualification passed; evidence written
```

Logcat markers:

```text
I CloudyRendererProbe: apply probe-line r1 640.0x400.0dp
E chromium: Renderer process (29075) crash detected (code -1).
I CloudyRendererProbe: reported renderer death 1; recreating
I CloudyRendererProbe: apply probe-line r1 640.0x400.0dp
```

JVM suite: **19 tests**, 0 failures (`ChartSpecBuilderTest` 9,
`RendererRevisionGateTest` 1, `SqlAndTypeTest` 5, `WorkbookHistoryTest` 4).

## Qualifier safety

`tools/qualify_renderer_death.sh` refuses a non-`emulator-*` serial unless
`ALLOW_NON_EMULATOR=1` is explicitly set; verifies the device state; requires
adb root; force-stops only the app and test process; records serial/API/model/UID;
clears logcat; and requires the crash, the death/recreation log, two spec
applications, the full recovered status and no renderer error. It still kills all
WebView sandboxed renderer processes on the dedicated emulator, as documented.

## Evidence

- `qualification/evidence/artifacts/2026-10-18-renderer-death-recovery.png`
- `qualification/evidence/artifacts/2026-10-18-renderer-death-recovery-ui.xml`
- `qualification/evidence/android-test-results/2026-10-18/renderer-death-logcat.txt`
- `qualification/evidence/android-test-results/2026-10-18/renderer-death-device.txt`
- `qualification/evidence/android-test-results/2026-10-18/emulator-am-instrument-full-suite.txt`
- `tools/qualify_renderer_death.sh`

## Limits

- The root-kill script is a qualification tool, not a product operation and not
  a security sandbox. It is restricted to a dedicated emulator.
- Emulator only. Physical-device renderer behavior, WebView provider versions and
  live OS-window resizing are not established by this run (the 360/640 tests
  resize the renderer surface, not the OS window).
- The product worksheet still presents a manual Retry/native fallback after
  renderer error; automatic recreation is implemented in the debug probe.
  Product lifecycle ownership is not claimed.
- Selection/hover/context-menu intents, tooltip, export/print, focus exit,
  TalkBack traversal and memory measurement remain pending. S01.7 is still
  partial.
