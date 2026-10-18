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
