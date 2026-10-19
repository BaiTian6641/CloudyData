# Execution state

**Updated:** 2026-10-19
**Branch:** `main`
**Source:** commits through `159c4b2`. Remote push not performed/authorized.
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
  worksheet query. Worksheet tab selection commits the active sheet and requeries
  it, clearing the previous sheet's accepted result while loading.
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
- Lint: 0 errors (5 warnings). JVM: **35/35**, including set-selection reducer
  cases. Emulator renderer probe: 17/17 (pre-lasso); Vega spec builder: 4/4;
  keep/exclude query suite plus engine and line regressions: 9/9;
  table-selection plus keep/exclude/renderer/spec: 27/27; View Data plus that
  suite: 32/32. The final lasso slice was not instrumented after device handoff;
  see **Implemented but unverified**. A three-device connected run still hits a
  physical-Googlebook PNG export assertion (`expected 803`, actual `804`) and
  device timeouts; the emulator suites are clean.
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
- `qualification/evidence/2026-10-18-s11-svg-export.md`
- `qualification/evidence/2026-10-18-s16-product-export.md`
- `qualification/evidence/2026-10-18-s16-titled-export.md`
- `qualification/evidence/2026-10-18-s16-frozen-bundle.md`
- `qualification/evidence/2026-10-18-s16-print-accessible.md`
- `qualification/evidence/2026-10-18-cust13-additive-range.md`
- `qualification/evidence/2026-10-19-cust13-mark-highlight.md`
- `qualification/evidence/2026-10-19-cust13-keep-exclude.md`
- `qualification/evidence/2026-10-19-cust13-table-selection.md`
- `qualification/evidence/2026-10-19-cust13-view-data.md`
- `qualification/evidence/2026-10-19-cust13-lasso.md`
- `docs/decisions/0008-chart-ir-and-format-v2.md`
- Raw results: `qualification/evidence/android-test-results/2026-10-18/` and
  `qualification/evidence/android-test-results/2026-10-19/`
- Captures/hashes: `qualification/evidence/artifacts/`

## Implemented but unverified

- **Rectangle/lasso final qualification.** Renderer gesture, SVG-CTM hit
  testing, revision-gated set intent, non-interactive overlays and native set
  reducer compile and pass host-only checks. An earlier pre-final renderer
  probe on the API37 physical Googlebook passed **5/5** selection methods,
  including rectangle and Ctrl-drag. The final coordinate/TTL/empty-rectangle
  changes and the product-level worksheet path were not rerun: the owner
  resumed using the physical device, `emulator-5582` disappeared from `adb`, and
  the API36 emulator was not used further because it consumes laptop memory.
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
  rows; hardware arrows move in table order; Ctrl/Cmd click/arrow toggles and
  Shift click/arrow selects an inclusive range; the table highlights all exact
  rows while the endpoint drives the inspector; Escape/Tab release keyboard
  routing and stale chart/revision intents are gated. The selected set is sent
  through a chart/revision-gated `ApplySelection` message and drawn as a
  dedicated `selected` Vega dataset: Bar marks get a contrasting ring and tint,
  Line/Area marks get contrasting selected-point symbols. Area and Bar are
  verified visually on the emulator; the Line overlay is spec-tested. Keep and
  exclude operate on exact row/series identities through a persisted worksheet
  filter applied before aggregation; the source snapshot remains unchanged and
  Clear restores the accepted result. Native result-table modifier clicks now
  use the same reducer and state as the chart: Shift ranges from the anchor,
  Ctrl/Cmd toggles, plain activation replaces, and TalkBack custom actions add or
  extend. A Final-pass release observer prevents canceled gestures from leaking
  a mode into later activation; modified Enter/Space mapping is implemented but
  its row-focus path is not separately exercised. View Data resolves the
  selected marks back to bounded, exact source rows, combines the active
  worksheet filter and invalidates stale responses. Rectangle/lasso drag and
  Ctrl-drag additive selection are implemented with SVG-CTM scene hit testing,
  a revision-gated set intent, non-interactive overlays and a native set
  reducer; final device/product qualification was interrupted by a device
  handoff. Drill, compound/global filters, persisted selection, pointer hover,
  product auto-recovery, focus/TalkBack traversal, live OS-window resize and
  memory limits remain unverified. The native summary/table is the accessible
  equivalent.
- Product visual export is verified for a live worksheet: individual and frozen
  multi-format actions use real DocumentsUI/SAF destinations and provider-owned
  bytes. The frozen bundle produces basename/timestamp-matched SVG/PNG/PDF from
  one validated scene; the verified set is 9,512-byte SVG,
  34,810-byte 1705×318 PNG and 16,119-byte one-page 1705×318 PDF with embedded
  title/source provenance. Cancellation writes nothing; an unknown SAF tree
  returns an explicit provider failure. PDF is still raster-only, and storage-full/
  mid-write failure injection, accessible PDF and print remain partial.
- **Current device state:** the owner is using the API37 physical Googlebook
  (`C1L63708PZ`); no further interaction is permitted. The rootable
  `emulator-5582` disappeared from `adb` during the renderer suite. Only the
  API36 `emulator-5580` remains, and it was not started/used further because it
  consumes laptop memory. Final lasso/product instrumentation is blocked until
  an available device is provided.

## Not implemented / blocked

- S01.6 Arrow IPC, S01.8 million-row virtualized grid, S01.10 multiwindow,
  S01.11 16 KiB runtime. S01.4 has a native interrupt probe and UI action but no
  worker/process-death qualification; S01.5 covers constructed recovery states,
  not real process kills; S01.7 now verifies emulator renderer death/recovery,
  generation retirement, recovery exhaustion, stale completion/failure
  rejection and exact surface/view sizes. Selection and Escape focus-exit are
  verified; single-series Area is implemented and persists across reopen; a
  bounded exact-value tooltip works on selection/focus with persisted show/hide;
  bounded SVG, PNG and one-page PDF export are implemented; all decode/parse and
  match their probe evidence files; a real product SaveDocument save/write path is
  verified for all three; reopening them in the product remains unverified.
  Title/source/snapshot provenance and matching dimensions are embedded in the
  latest exports; one frozen multi-format export into a SAF folder is verified.
  The bundle PDF now includes a searchable A4 table and the product exposes a
  native Print worksheet flow; a real Save-as-PDF print produced a two-page,
  text-extractable PDF. PDF tagging, print-spooler completion, pointer hover,
  remaining Q18 failures, final lasso/rectangle device and product qualification,
  drill/hierarchy, compound or cross-sheet filters, persisted selection, TalkBack
  and live-window qualification remain.
- Query service process, Arrow transport, Room metadata, cross-window
  coordinator/conflict handling, formula parser, LOD/filters, relationships,
  totals, table calculations, dashboards, connectors, export, Tableau
  interoperability. A bounded undo/redo history exists; full command-journal
  durability does not.
- 16 KiB runtime device and licensed Tableau reference are unavailable.
- Canonical upstream token source, generator history and platform-rule-zero are
  absent. Tokens remain provisional.

## Next exact task

**CUST13 final lasso/product qualification — blocked on an available device.**

- Files: none planned; fix `renderer/host.js`, `VegaChart.kt`,
  `WorkbookScreen.kt`, tests/evidence only if qualification finds a defect.
- Action: with an explicitly available device, run the rectangle/Ctrl-drag probe
  (including slow drag and empty rectangle), run the full selection suite, then
  perform a product worksheet rectangle/Ctrl-drag and open View Data on the same
  selected set.
- Acceptance: exact intersecting marks appear in the table, inspector, overlay
  and View Data; empty replace clears; Ctrl-drag adds; keyboard/table remain the
  accessible alternative; stale chart/revision intents are rejected.
- Then: drill once an explicit dimension hierarchy exists; persisted selection
  remains an explicit action, not automatic.

## Protected context

- Do not push, publish, sign a release, change branch protections or send external
  messages without explicit authorization. Only local commits are permitted by
  this session.
- Preserve the planning documents' scope; do not import Cloudy Console terminal,
  RDP/X11/USB, host-vault or remote-session behavior.
- Do not add a Linux/JDBC DuckDB binary to the APK. Native artifacts must be built
  from the pinned commit by `tools/build_duckdb_android.sh`.
- The proposed disabled/chart tokens are provisional, not canonical.
