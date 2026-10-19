# Cloudy Data execution history

Append-only record of significant experiments, failures and decisions. The
short active handoff is `docs/execution-state.md`.

## 2026-10-18 — repository and document baseline

- Read all three planning files in order, then the applicable Cloudy design
  corpus (CD01–CD18 mapping in `docs/README.md`). No repository `AGENTS.md`
  existed; added one for future agents.
- `pwd -P` = `/home/polar/CloudyData`; directory contained only `docs/` and was
  not a Git repository.
- `git ls-remote --symref` to the intended SSH remote authenticated and returned
  0 bytes of advertised refs. Interpreted as an empty remote. Initialized a local
  `main` repository and added `origin`; no force operation.
- Canonical `shared/design-system/tokens.json` and generators were absent.
  Created provisional `design-system/tokens.json`, a deterministic generator and
  a contrast checker. Recorded missing roles in ADR 0002.
- Moved the three plan files to `docs/plans/` before the first commit; moved the
  design corpus to `docs/design/`. No content rewriting.

## 2026-10-18 — toolchain and native engine

- Installed API 37 platform, build-tools 37.0.0 and NDK 28.2.13676358 with the
  Android SDK command-line tools. JDK 21.0.9; Gradle 9.8.1; AGP 9.4.1; built-in
  Kotlin 2.2.10; Compose BOM 2026.09.00.
- Built DuckDB v1.5.6 at `069cc9f9b5be802405797faecc284961b07c70ef` for
  arm64-v8a and x86_64. First attempt of the JNI adapter failed to compile on
  `DUCKDB_TYPE_JSON` and `DUCKDB_TYPE_VARINT`, which do not exist in this C API;
  removed/replaced them. The unstripped arm64 library was 519 MiB; stripping
  produced ~48 MiB.
- First device test failures:
  1. `Object[] cannot be cast to Object[][]` — JNI returned an `Object[]` of row
     arrays; Kotlin's nested-array cast failed. Fixed by casting each row
     explicitly.
  2. Test assets were read from the target application context, not the test
     APK. Fixed by using the instrumentation context.
  3. Decimal CSV values were inferred as DOUBLE (`0.1`), and the first worksheet
     test grouped each row separately. Fixed by adding exact decimal inference
     and correcting the independent expected grouping.
  4. A leading-zero probe showed that simply excluding DOUBLE from DuckDB's
     type candidates turns `00123` into a number. Kept default inference for
     initial schema and override only DOUBLE columns after a full text check.
- All 5 instrumentation tests pass on both the physical arm64 device and the
  x86_64 emulator. 5 JVM unit tests pass.

## 2026-10-18 — first real UI slice

- Home render inspected on the HP Googlebook (freeform window): no fabricated
  recents, empty state, real New workbook dialog.
- SAF picker initially hid a valid file whose provider reported
  `text/comma-separated-values`. Broadened the picker to `*/*` and retained
  importer validation; recorded the provider-variance risk.
- A second picker failure was environmental: `adb push` wrote to Android user 0
  storage while the app runs as user 10. Added a test-only MediaStore publisher
  for qualification; no production bypass.
- Created a workbook, imported the Q06-shaped synthetic CSV through the real
  picker, applied the snapshot, queried locally, rendered the provisional bar
  chart/table, selected a row, inspected exact values/SQL provenance, changed
  aggregation to Average, force-stopped the process and reopened the workbook
  with the same data and placements.
- Lint had 0 errors; removed a dead super call, added backup exclusion rules,
  fixed the adaptive icon monochrome tag and used `mutableIntStateOf`. Remaining
  warnings are an intentionally pinned Kotlin plugin and the adaptive-icon
  folder qualifier.

## 2026-10-18 — cancellation and recovery probes

- Implemented import receipts around staged data, previous-file backup, atomic
  file moves and metadata publication. Reconciliation restores the previous
  snapshot when metadata does not name the proposed snapshot and keeps committed
  data when it does.
- Added `CancellationTest`: an expensive cross-join query was interrupted after
  150 ms; DuckDB returned `INTERRUPT Error: Interrupted!` in ~2 ms and the same
  connection answered `SELECT 42` afterward.
- Added `WindowProbeTest`: physical requested launch bounds were ignored
  (observed full 2880×1800 window); emulator honored 1200×900. Caption-bar insets
  were unavailable/zero despite visible system captions. Two distinct app
  activities were alive, but no document identity/coordination is implemented.
- Added a physical-only 1M×12 import probe: 44.7 MB CSV, 9.3 MB Parquet,
  inspect 4.7 s, apply 2.8 s, grouped SUM/COUNT query 7 ms, exact totals.
- The full instrumentation suite now has 11 tests per device, 0 failures. The
  workload test is a no-op on the emulator and reports that in logcat.

## 2026-10-18 — offline Vega host probe

- Built a deterministic asset pipeline around Vega 6.4.0 and
  vega-interpreter 2.3.2, both pinned by registry tarball hash. The interpreter
  is shipped only as an ES module; transformed it into a verified UMD-style
  global without a bundler and without enabling the Function-constructor
  expression path.
- Added a restricted WebView host: packaged app-asset origin, CSP with no
  connect/object sources, `blockNetworkLoads=true`, non-asset request refusal,
  navigation refusal, origin-restricted WebMessageListener, typed message
  validation, exact chart revision checks, and renderer-death callback.
- First probe failed because `vega.expressionInterpreter` is an object, not a
  function; the readiness check rejected it. Corrected the capability check.
- Probe renders a trusted A/B bar spec on the physical arm64 Googlebook and the
  x86_64 emulator. Test observes Vega 6.4.0, two data values, non-empty scene
  and SVG, zero external requests and a native accessible summary. The product
  worksheet still uses the provisional native chart.
- Renderer death, live resize, selection/export, focus/TalkBack and memory limits
  remain open.

## 2026-10-18 — worksheet Vega integration

- Embedded `VegaRendererHost` in the worksheet Bar mark with a live size in dp,
  Cloudy categorical colors, chart-id/revision matching and a native fallback for
  renderer failure, nonnumeric/negative values or more than 24 categories.
- Superseded render completions are now ignored rather than surfaced as
  renderer errors. Table selection remains the exact-value access path.
- Physical device run shows the `Offline Vega` badge, two-color A/B bars and the
  native summary alongside the exact-value table. No renderer console errors
  were observed.

## 2026-10-18 — settings and persisted presentation

- Replaced the Home placeholder with a real Settings route. Implemented
  app-level System/Light/Dark and Comfortable/Standard/Compact preferences with
  explicit SharedPreferences commit results, safe fallback for invalid values,
  and one observable state stream for open windows.
- Bound the app theme and density to the store. Switching to Dark/Compact
  recomposed the UI without reloading the workbook or rerunning a query.
- Two preference tests cover persistence across store instances and non-
  destructive invalid-value fallback. Full instrumentation suite: 14 tests per
  device, 0 failures.
- Settings radio rows expose `checkable/checked` semantics; physical TalkBack,
  keyboard traversal, large text and cross-window preference changes remain open.

## 2026-10-18 — chart customization, palette and command history

- Added the owner-directed chart-family/customization contract to all three plan
  files plus `docs/design/chart-grammar-and-customization.md` and
  `parity/chart-catalog.yaml` (24 families, 17 customization areas).
- Implemented persisted chart title, major-gridline visibility and bar value
  labels; the Vega spec regenerates from the saved worksheet state without
  rerunning SQL. Manual device walkthrough and reopen verified the behavior.
- Added a context command palette with Ctrl+Shift+P on Home and Workbook,
  Ctrl+N/Ctrl+F on Home, Ctrl+S on Workbook, and context-specific commands.
  UiAutomator tests run Settings and verify Escape dismissal on both devices.
- Added bounded pure `WorkbookHistory` with undo/redo, redo invalidation and
  import reset; toolbar/palette expose real enabled states. Manual Sum→Average
  undo/redo restored the expected results.
- The current chart inventory remains bar/table only; every other family and
  most customization areas are explicitly `not_started`/`partial`.

## 2026-10-18 — line chart, typed IR and emulator-first qualification

- The owner requested emulator-first build/validation and removed the physical
  Googlebook for the remainder of the session. All new line-chart validation was
  performed on `emulator-5580`; no new physical-only claim is made.
- Added typed `ChartSpec` and `ChartOptions` (nested workbook format v2), a
  multi-series line query, series-aware table/inspector mapping, and persisted
  chart/X/Y/legend titles, Y range, zero/log/reverse, gridlines, labels and
  legend position.
- First full emulator `am instrument` run failed when UiAutomation was not
  connected in CommandPaletteTest. CommandPaletteTest alone then passed (2/2)
  and a full rerun passed 24/24, so the failure is retained as a transient
  automation-environment event rather than a product result.
- First faceted line render showed axes/legend but no lines: the Vega group
  facet had `clip:true` with no width/height, clipping all child marks. Adding
  explicit width/height signals produced two visible series paths.
- Independent scout review prompted: v2 migration without legacy dual write,
  2001-row truncation probe/refusal, defined/null gaps, facet grouping (not
  `detail`), removal of clamp, bar log/legend restrictions, payload limits,
  series-column table/inspector labels, and mark-switch cleanup.
- Emulator manual flow: imported six-row two-series fixture, set event_date on
  Rows, value on Values, series on Color, Line mark; set titles, Y 0–40, right
  legend; observed North 10/20/30 and South 1/2/4, no March South point; saved,
  force-stopped, reopened and confirmed the same chart and exact table.
- Independent reviewer findings were fixed: explicit Y range now disables Vega
zero/nice expansion; series-aware table/inspector mapping uses the actual query
shape; line-only log/legend controls are gated/normalized for bars; the probe
asserts real line marks; 2000/2001 boundary and document-level v1 migration are
tested in the final run.
Final emulator suite: 27 tests, 0 failures; JVM suite: 18 tests, 0 failures.

## 2026-10-18 — renderer lifecycle and stale-result gate

- Verified real renderer termination on the rootable API 37 emulator by killing
  the WebView sandboxed renderer PID for the probe. The host received
  `onRenderProcessGone`, the probe recreated the host and replayed the six-point
  line spec; the final status reports "recovered after 1 death(s)".
- Added bounded recovery (two attempts), a debug-only simulated-death trigger for
  deterministic instrumentation, fixed viewport extras for surface-size tests,
  and `RendererRevisionGate` for stale chart/revision completions.
- Independent review found and we fixed: queued callbacks from the dead host
  generation could publish a stale completion; JS stamped completions with the
  latest global chart/revision rather than the dispatched identity; viewport
  tests proved layout but not renderer surface; the kill script was not scoped
  and accepted weak evidence.
- The recovered status now records `surface=640x400 · view=640x400 · host=3`;
  the script requires the crash, death/recreation log, two applies, the full
  recovered status and no renderer error.
- Full emulator suite: 30/30; JVM: 19/19. The root-kill tool refuses
  non-emulator serials unless explicitly opted in and is documented as a
  qualification operation, not a sandbox.
- Still open: selection/hover/context-menu intents, product auto-recovery,
  export/print, focus/TalkBack, live OS-window resize and memory limits.
