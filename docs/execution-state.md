# Execution state

**Updated:** 2026-10-18
**Branch:** `main`
**Source:** commits `ac94960` and `604a3a7`, plus the pending S01 probe commit;
remote push not performed/authorized in this session.
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
- Native interrupt returns `EngineException("INTERRUPT Error: Interrupted!")` and
  the connection remains usable; the worksheet has a Cancel action whose UI path
  is not yet exercised against a slow query.
- Import receipts/rollback reconcile constructed interrupted, published and
  prepared states; previous Parquet is restored when metadata was not published.
- Lint: 0 errors. JVM tests: 5/5. Instrumentation: 11/11 on each target (the 1M
  workload probe is a no-op on the emulator by design).
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

**S01.7 — bounded offline Vega renderer probe.**

- Files: new `renderer-web/` assets/HTML/JS, an Android WebView host boundary,
  and an instrumentation probe; preserve the existing native provisional chart
  until the renderer is qualified.
- Action: pin Vega and its CSP-compatible interpreter, load them only from a
  dedicated app-asset origin with no CDN/network fallback, render a trusted
  bar-chart spec from Q06 data, and capture `Ready`/`RenderComplete`/renderer
  death behavior.
- Acceptance: no network request; generated spec only; origin/message checks;
  native accessible summary/table remains available; export/selection gaps
  explicitly recorded. A compile or screenshot of a non-native surface is not a
  pass.
- Alternative if renderer work is blocked: S01.8 paged 1M-row grid, keeping the
  result-transfer limitation explicit.

## Protected context

- Do not push, publish, sign a release, change branch protections or send external
  messages without explicit authorization. Only local commits are permitted by
  this session.
- Preserve the planning documents' scope; do not import Cloudy Console terminal,
  RDP/X11/USB, host-vault or remote-session behavior.
- Do not add a Linux/JDBC DuckDB binary to the APK. Native artifacts must be built
  from the pinned commit by `tools/build_duckdb_android.sh`.
- The proposed disabled/chart tokens are provisional, not canonical.
