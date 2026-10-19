# Execution state

**Updated:** 2026-10-18
**Branch:** `main`
**Source:** commits through `9df8392`; remote push not performed/authorized in
this session.
**Active stage:** S00 partial; S01 partial; S02/S03 partial; early R1
vertical slice. The owner-directed chart grammar/customization contract is in
the three plan addenda, `docs/design/chart-grammar-and-customization.md` and
`parity/chart-catalog.yaml`.

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
- Rows/Values/Line-Color placements, aggregate selection (SUM verified; others
  available), Table/Bar/Line marks, chart/table, row selection, exact-value
  inspector, generated SQL display.
- Typed `ChartSpec` and multi-series line rendering with facet-separated series,
  observed-gap handling, truncation refusal, chart/X/Y/legend titles, Y
  range/zero/log/reverse, gridlines, value labels and bottom/right legend.
- Workbook format v2 with nested chart options and v1 read migration; no legacy
  dual-write, and future versions are rejected before migration.
- Home recents/favorites/all filters; favorite toggle code exists but is not yet
  tested.
- Offline Vega 6.4.0 + CSP interpreter host renders trusted bar and multi-series
  line specs from packaged assets; screen/SVG content, no external request,
  native accessible summary and origin-validated messages are observed. The
  worksheet Bar and Line marks use the host with an explicit native/table
  fallback and summary.
- System/Light/Dark and Comfortable/Standard/Compact settings persist across a
  process restart, apply to open windows through app-level state, and expose
  real selected semantics. Invalid stored values fall back without deleting
  evidence.
- Command palette (Ctrl+Shift+P, Ctrl+N, Ctrl+F), context commands, Ctrl+S and
  bounded undo/redo are real and tested; the palette runs Settings and closes
  with Escape on both devices.
- Chart/X/Y/legend titles, gridline visibility, value labels, Y range,
  zero/log/reverse and legend position are persisted per worksheet and survive
  reopen; presentation changes do not rerun SQL.
- Native interrupt returns `EngineException("INTERRUPT Error: Interrupted!")` and
  the connection remains usable; the worksheet has a Cancel action whose UI path
  is not yet exercised against a slow query.
- Import receipts/rollback reconcile constructed interrupted, published and
  prepared states; previous Parquet is restored when metadata was not published.
- Lint: 0 errors (warnings documented). JVM tests: 24/24. Emulator instrumentation:
  35/35, 0 failures. The physical Googlebook was removed by the owner; new
  lifecycle/line evidence is emulator-only.
- Window probe: physical launch bounds were ignored (observed 2880×1800);
  emulator honored 1200×900. Caption insets reported zero/unavailable.
- Physical 1M×12 import/query probe: 44.7 MB CSV, 9.3 MB Parquet, inspect 4.7 s,
  apply 2.8 s, warm grouped query 7 ms, exact totals.

### Evidence

- `qualification/evidence/2026-10-18-s00-toolchain.md`
- `qualification/evidence/2026-10-18-s01-native-engine.md`
- `qualification/evidence/2026-10-18-s01-import-and-worksheet.md`
- `qualification/evidence/2026-10-18-token-contrast.md`
- `qualification/evidence/2026-10-18-s11-line-chart.md`
- `qualification/evidence/2026-10-18-s01-renderer-lifecycle.md`
- `qualification/evidence/2026-10-18-s11-renderer-selection.md`
- `qualification/evidence/2026-10-18-s11-area-chart.md`
- `qualification/evidence/2026-10-18-s11-tooltip.md`
- `docs/decisions/0008-chart-ir-and-format-v2.md`
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
- Renderer death/recreation/replay, generation retirement, recovery exhaustion,
  stale completion/failure rejection and two exact renderer surface sizes are
  verified on the rootable emulator. The real kill run reports
  `surface=640x400 · view=640x400 · host=3 · recovered after 1 death(s)`.
- Renderer selection is verified: actual emulator taps select exact source table
  rows, hardware arrows move in table order, Escape/Tab move keyboard routing
  out of the WebView (extra arrows no longer select), and stale chart/revision
  intents are gated. Additive/range/lasso
  selection, visual mark highlight, tooltip, product auto-recovery, export,
  focus/TalkBack traversal, live OS-window resize and memory limits remain
  unverified. The native summary/table is the current accessible equivalent.

- **Current emulator caveat:** the rootable API 37 emulator disappeared during
  the session. Newer reruns ran on an API 36 x86_64 AVD with a 2880×1800/240 dpi
  display override; prior API 37/root evidence remains as recorded for those
  runs, while new evidence is API 36 and non-root.

## Not implemented / blocked

- S01.6 Arrow IPC, S01.8 million-row virtualized grid, S01.10 multiwindow,
  S01.11 16 KiB runtime. S01.4 has a native interrupt probe and UI action but no
  worker/process-death qualification; S01.5 covers constructed recovery states,
  not real process kills; S01.7 now verifies emulator renderer death/recovery,
  generation retirement, recovery exhaustion, stale completion/failure
  rejection and exact surface/view sizes. Selection and Escape focus-exit are
  verified; single-series Area is implemented and persists across reopen; a
  bounded exact-value tooltip works on selection/focus with persisted show/hide.
  Pointer hover, additive/range selection, export, TalkBack and live-window
  qualification remain.
- Query service process, Arrow transport, Room metadata, cross-window
  coordinator/conflict handling, formula parser, LOD/filters, relationships,
  totals, table calculations, dashboards, connectors, export, Tableau
  interoperability. A bounded undo/redo history exists; full command-journal
  durability does not.
- 16 KiB runtime device and licensed Tableau reference are unavailable.
- Canonical upstream token source, generator history and platform-rule-zero are
  absent. Tokens remain provisional.

## Next exact task

**CUST14 visual export (SVG first, emulator-first).**

- Files: `host.js`/`index.html`, `VegaRendererHost.kt`, `VegaChart.kt`, export
  UI/store, tests.
- Action: request the renderer's SVG for the current chart/revision, validate a
  bounded document, and save it through a user-chosen destination; keep a
  native/offline fallback if the renderer fails. Do not export a stale revision.
- Acceptance: actual export produces a valid SVG of the current chart with no
  external references; stale/failed renders are rejected; a saved file opens.
- Then: PNG/PDF/print, pointer-hover tooltip qualification and additive/range
  selection.

## Protected context

- Do not push, publish, sign a release, change branch protections or send external
  messages without explicit authorization. Only local commits are permitted by
  this session.
- Preserve the planning documents' scope; do not import Cloudy Console terminal,
  RDP/X11/USB, host-vault or remote-session behavior.
- Do not add a Linux/JDBC DuckDB binary to the APK. Native artifacts must be built
  from the pinned commit by `tools/build_duckdb_android.sh`.
- The proposed disabled/chart tokens are provisional, not canonical.
