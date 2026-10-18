# Execution state

**Updated:** 2026-10-18
**Branch:** `main`
**Source:** commits through `b6d4bb3`, plus the pending command-history/chart-customization
commit; remote push not performed/authorized in this session.
**Active stage:** S00 partial; S01 partial; early R1 vertical slice.

## What works (verified)

All claims below are bound to the evidence paths and the pinned device/emulator
unless stated otherwise.

- Buildable native Compose app; debug APK builds and installs on the HP Googlebook
  14 (Android 17/API 37, arm64-v8a, 4 KiB pages) and the x86_64 API 37 emulator.
- Pinned DuckDB v1.5.6 native engine for both ABIs; 16 KiB-aligned ELF segments;
  typed JNI values: Int64, DECIMAL scale, timestamp microseconds, Unicode.
- CSV import through the real SAF picker with bounded preview, exact plain-decimal
  import, Parquet snapshot publication, original source preservation and verified
  row count.
- Workbook create, atomic metadata save, process-restart reopen, and local
  worksheet query.
- Rows/Values placements, aggregate selection (SUM verified; others available),
  provisional Table/Bar marks, chart/table, row selection, exact-value inspector,
  generated SQL display.
- Home recents/favorites/all filters; favorite toggle code exists but is not yet
  tested.
- Offline Vega 6.4.0 + CSP interpreter host renders a trusted two-bar spec from
  packaged assets on both targets; screen/SVG content, no external request,
  native accessible summary and origin-validated messages are observed. The
  worksheet Bar mark uses the host with an explicit native fallback and summary.
- System/Light/Dark and Comfortable/Standard/Compact settings persist across a
  process restart, apply to open windows through app-level state, and expose
  real selected semantics. Invalid stored values fall back without deleting
  evidence.
- Command palette (Ctrl+Shift+P, Ctrl+N, Ctrl+F), context commands, Ctrl+S and
  bounded undo/redo are real and tested; the palette runs Settings and closes
  with Escape on both devices.
- Chart title, gridline visibility and value labels are persisted per worksheet
  and survive reopen; the bar renderer receives the presentation flags without
  rerunning SQL.
- Native interrupt returns `EngineException("INTERRUPT Error: Interrupted!")` and
  the connection remains usable; the worksheet has a Cancel action whose UI path
  is not yet exercised against a slow query.
- Import receipts/rollback reconcile constructed interrupted, published and
  prepared states; previous Parquet is restored when metadata was not published.
- Lint: 0 errors (warnings documented). JVM tests: 8/8. Instrumentation: 18/18
  on each target (the 1M workload probe is a no-op on the emulator by design).
- Window probe: physical launch bounds were ignored (observed 2880×1800);
  emulator honored 1200×900. Caption insets reported zero/unavailable.
- Physical 1M×12 import/query probe: 44.7 MB CSV, 9.3 MB Parquet, inspect 4.7 s,
  apply 2.8 s, warm grouped query 7 ms, exact totals.

### Evidence

- `qualification/evidence/2026-10-18-s00-toolchain.md`
- `qualification/evidence/2026-10-18-s01-native-engine.md`
- `qualification/evidence/2026-10-18-s01-import-and-worksheet.md`
- `qualification/evidence/2026-10-18-token-contrast.md`
- Raw results: `qualification/evidence/android-test-results/2026-10-18/`
- Captures/hashes: `qualification/evidence/artifacts/`

## Implemented but unverified

- Native visual/interaction acceptance. Captures were leader-inspected only; no
  independent reviewer has accepted them. Keyboard traversal, IME, TalkBack,
  focus return, shortcuts, density modes and light/dark require their own runs.
- Favorites toggle, workbook rename, new worksheet, aggregation choices other
  than SUM and the replacement-import path have code but no dedicated tests.
- The worksheet Cancel action's UI wiring is not exercised against a slow query;
  only the native interrupt and query-generation invalidation code exist.
- Save mutex prevents an older save from replacing newer state in the same
  process; cross-window/conflict behavior is not implemented or tested.
- The chart's label-hash category colors are stable only for unchanged labels;
  no persisted palette map exists.
- The Vega host renders the worksheet Bar mark with a native fallback and table
  selection equivalent; renderer death, matched resize/input runs, chart
  selection, export, focus/TalkBack and memory limits are unverified.

## Not implemented / blocked

- S01.4 cancellation/interruption/worker death, S01.5 receipt recovery, S01.6
  Arrow IPC, S01.7 offline Vega, S01.8 million-row virtualized grid, S01.10
  multiwindow, S01.11 16 KiB runtime.
- Vega/renderer host, query service process, Arrow transport, room metadata,
  coordinator/history/undo, formula parser, LOD/filters, relationships, totals,
  table calculations, dashboards, connectors, export, Tableau interoperability.
- 16 KiB runtime device and licensed Tableau reference are unavailable.
- Canonical upstream token source, generator history and platform-rule-zero are
  absent. Tokens remain provisional.

## Next exact task

**S11.C0 incremental — line mark and axis/legend customization.**

- Files: `WorksheetView.kt`, `VegaChart.kt`, `VegaRendererHost.kt`, chart
  catalog/model, renderer assets, tests.
- Action: add a Line mark with date/categorical x, multiple series by color,
  missing-value policy, axis title/range/zero/log/reverse controls, gridline
  styling and a legend. Persist every control; do not let the renderer
  reaggregate. Add a per-family fixture and save/reopen/selection evidence.
- Acceptance: exact data/grain fixture, visible axis/legend/grid customization,
  unsupported states explained, native summary/table, renderer offline/typed
  boundary unchanged, and catalog rows moved only with evidence.
- Then: S01.7 renderer-lifecycle completion and S03 cross-window coordinator.

- Files: `renderer-web/` host/bridge, `VegaRendererHost.kt` and a bounded
  instrumentation probe.
- Action: add a repeated renderer-death/recreate probe (or force renderer
  termination through a supported mechanism), live resize/pixel mapping,
  selection-intent round trip with stale-revision rejection, and an explicit
  export/availability decision. Keep `blockNetworkLoads` and origin checks.
- Acceptance: no false completion after renderer death; a fresh renderer can
  replay accepted workbook state; late/old intents are rejected; actual
  screenshot/asset evidence at two window sizes; unchanged native accessible
  summary remains available.
- If renderer death cannot be forced safely, record the blocker and move to S06
  native worksheet integration only after the missing gate is explicit.

## Protected context

- Do not push, publish, sign a release, change branch protections or send external
  messages without explicit authorization. Only local commits are permitted by
  this session.
- Preserve the planning documents' scope; do not import Cloudy Console terminal,
  RDP/X11/USB, host-vault or remote-session behavior.
- Do not add a Linux/JDBC DuckDB binary to the APK. Native artifacts must be built
  from the pinned commit by `tools/build_duckdb_android.sh`.
- The proposed disabled/chart tokens are provisional, not canonical.
