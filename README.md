# Cloudy Data

Native Kotlin/Jetpack Compose analytical workbench for Googlebook Android
desktop, following the Cloudy Design Language. The long-range product target is
Tableau functional parity; this repository currently contains a verified local
authoring foundation, not a parity release.

## Current working slice

On the pinned HP Googlebook 14 (Android 17 / API 37, arm64-v8a, 4 KiB page size)
and the x86_64 API 37 emulator, the debug app can:

- create and persist a local workbook;
- choose a delimited text file through Android's document picker, stage it,
  inspect the inferred schema and a bounded preview, then apply the import;
- preserve plain decimal CSV text as exact DuckDB `DECIMAL` values, keep
  `BIGINT` exact across JNI, and preserve Unicode/timestamp values;
- write and verify a local Parquet snapshot plus the original source copy;
- place a dimension and measure on Rows/Values, choose an aggregation and a
  table/bar/line mark, and query DuckDB locally;
- add an optional Color field to a bar and choose side-by-side grouped or
  stacked segments with a category legend;
- add an optional Color series to a line, with persisted chart/axis/legend
  titles, Y range, zero/log/reverse, gridlines, value labels and a right/bottom
  legend;
- switch categorical colors between the Cloudy palette and a palette checked for
  common color-vision deficiencies, with deterministic category mapping and a
  persisted per-worksheet choice;
- inspect exact values, formatted values and the generated SQL;
- save workbook metadata atomically and reopen the workbook;
- use a native command palette (Ctrl+Shift+P), Ctrl+S, and bounded undo/redo;
- customize the bar chart's title, gridline visibility and value labels, saved
  with the worksheet and restored after reopen;
- choose persisted System/Light/Dark appearance and Comfortable/Standard/
  Compact pointer density from a real Settings screen (applied to the workbook
  bar mark as well);
- load a debug-only offline Vega 6.4.0 renderer from packaged assets, evaluate
  with the CSP interpreter, draw a trusted two-bar spec with no network access,
  and show a native accessible summary.

The worksheet Bar and multi-series Line marks use the qualified offline Vega host
with a native fallback/table and a native accessible summary. Line results refuse
silent truncation, preserve observed gaps, and never synthesize absent members.
Chart selection, renderer death, export and matched resize/input remain
unqualified; the table is the exact-value surface. The bounded offline Vega
renderer required by the architecture has **not** been integrated, so S01.7 and
the visual-grammar stages are not complete. `docs/execution-state.md` records the
exact verified/pending boundary.

## Prerequisites

- JDK 17+ (tested with JDK 21)
- Android SDK with API 37 platform and build-tools 37
- Android NDK 28.2.13676358
- CMake and Ninja
- An Android 15+ device or emulator with desktop windowing for the intended UX

The tested toolchain is pinned in `docs/decisions/0001-repository-and-toolchain.md`
and `gradle/libs.versions.toml`.

## Build

Native DuckDB artifacts are generated, not committed. Build them once for both
ABIs:

```bash
export ANDROID_NDK=$HOME/Android/Sdk/ndk/28.2.13676358
tools/build_duckdb_android.sh
```

Then build and install:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The script freezes DuckDB `v1.5.6` at commit
`069cc9f9b5be802405797faecc284961b07c70ef`, statically links `parquet`, `json`
and `core_functions`, disables extension autoload/autoinstall, strips the
library, and checks 16 KiB load alignment.

## Verify

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.baitian6641.cloudydata.EngineSmokeTest
python3 tools/generate_tokens.py --check
```

`connectedDebugAndroidTest` currently exercises both ABIs through the real
device/emulator runner. Test data is synthetic and lives under `fixtures/`.

## Repository map

| Path | Purpose |
| --- | --- |
| `docs/plans/` | The three supplied Cloudy Data planning documents, by authority order. |
| `docs/design/` | Supplied Cloudy design corpus. Scope exclusions and amendments are recorded in `docs/README.md`. |
| `docs/decisions/` | Accepted implementation decisions and evidence boundaries. |
| `docs/execution-state.md` | Short continuation record: branch, verified work, gaps, next task. |
| `docs/execution-history.md` | Append-only significant experiments and failures. |
| `parity/` | Seed feature/function/connector catalogs with statuses, not completed atomic inventories. |
| `qualification/` | Device profiles and evidence records bound to actual commands/artifacts. |
| `design-system/tokens.json` | Provisional single token source; canonical upstream `tokens.json` was not supplied. |
| `fixtures/` | Deterministic synthetic CSV inputs and independent expectations. |
| `app/` | Native Compose application and JNI adapter. |

## Known limits

- No canonical upstream `shared/design-system/tokens.json`, generator history,
  preview images or `platform-rule-zero.md` were present. Tokens are explicitly
  provisional; see `docs/decisions/0002-provisional-design-tokens.md`.
- The Vega renderer covers single, grouped and stacked bars, line and
  single-series area marks; the native offline fallback draws only single bars
  and refuses color-series bar shapes rather than showing a wrong chart. Emulator renderer death,
  generation retirement, automatic probe recreation/replay, recovery
  exhaustion, stale completion/failure rejection and exact surface/view sizes
  are qualified. Exact chart-to-table selection with arrow/Escape keyboard paths
  a bounded exact-value tooltip plus bounded SVG, PNG and one-page PDF export are
  implemented and the real product SaveDocument save/write path is verified;
  exported scenes now carry title/source provenance and matching dimensions;
  one frozen multi-format SAF bundle and explicit cancellation/provider-failure
  paths are verified. Product reopening, accessible/rasterized PDF labeling,
  print, remaining Q18 injections, pointer-hover qualification, additive/range
  selection, product auto-recovery, live OS-window resize, TalkBack and the full
  chart catalog remain incomplete. No Arrow IPC transfer; no query service process, virtualized
  million-row grid, multiwindow conflict handling,
  migrations, relationships/LOD/table calculations, dashboards, connectors or
  Tableau interoperability are implemented yet.
- The import path is bounded to 512 MiB and cannot be cancelled once staging
  starts. Decimal preservation covers plain decimal notation, not scientific
  notation or localized separators.
- A 16 KiB page-size runtime device was not available. The artifact alignment
  check is not a runtime 16 KiB qualification.
- The JNI adapter materializes a bounded result (currently 1,000 rows for a
  worksheet query, 10,000 default) rather than streaming Arrow batches. This is
  a probe/vertical-slice constraint, not the accepted final transport.

## Documentation authority

`docs/README.md` records the title-to-path mapping and precedence. The planning
documents remain unchanged in meaning; their location moved to `docs/plans/`.
Cloudy Console terminal/RDP/X11/USB behavior is out of scope for this product.
