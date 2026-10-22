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

## 2026-10-18 — renderer selection and focus exit

- Lowered `sourceRowIndex` into the Vega table and added typed
  `SelectionIntent`/`FocusExitIntent` messages with chart/revision gating.
- Added named bar rects and top-level line hit symbols driven by a Vega signal;
  facet child click items were not reliable.
- Product worksheet now maps a validated click/arrow selection to the exact
  table row, advances in source-table order and releases native WebView focus on
  Escape/Tab.
- Actual emulator tap, hardware arrows and Escape verified on the debug probe
  and the product worksheet. Full suite: 32/32; JVM 19/19; lint 0 errors.
- CUST13 remains partial: additive/range/lasso selection, visual mark highlight,
  tooltip, context menu and export are still absent.

## 2026-10-18 — single-series Area chart

- Added persisted `MarkType.AREA`/`ChartKind.AREA`, line-shaped aggregate query,
  zero-baseline area/outline/symbols/labels and the shared selection hit target.
- Product worksheet exposes Mark → Area; switching clears color-series/log
  options; chart/table survive app restart.
- Verified 11/22/30/4 aggregate values, area scene mark and offline rendering.
  JVM 23/23; emulator 35/35; lint 0 errors.
- CH03 is partial; stacked/100%/multi-series area and tooltip/export remain.

## 2026-10-18 — bounded chart tooltip

- Added persisted `showTooltip`, exact-value serialization, a bounded DOM
  tooltip on selection/focus, and a Vega hover signal.
- Keyboard-selection tooltip evidence on the API 36 emulator: `North
  2026-01-01 = 10.0`; show/hide persistence tested.
- Pointer hover did not qualify through adb mouse injection and remains open.
  JVM 24/24; emulator 35/35; lint 0 errors. CUST12 partial.

## 2026-10-18 — SVG chart export

- Added typed ExportSvg/SvgExport, bounded/validated serialization and a
  SaveDocument SVG path; debug export produced a valid 7.6 KiB document with no
  scripts or remote references.
- Added a stale-revision check and 2 MiB limit; product save/open was not
  exercised end-to-end after the AVD data reset.
- JVM 24/24; emulator 37/37; lint 0 errors. CUST14 remains partial.

## 2026-10-18 — product visual-export write path

- On the restored API 37 userdebug emulator, exported the live Area worksheet
  through the real SVG/PNG/PDF product actions and DocumentsUI CreateDocument
  picker; provider files parsed as SVG, 1705×278 PNG and one-page PDF.
- A second SVG save was provider-renamed to `cloudy-chart (1).svg`.
- Confirmed UI success statuses and no script/remote references. Q18/Q24 remain
  partial: failure injection, one frozen multi-format output, embedded
  title/provenance and print are outstanding.

## 2026-10-18 — titled/provenance visual export

- Added bounded source notes to `ChartSpec`, sent title/source/theme to the
  renderer, and wrapped SVG exports with a 56 px title/provenance header that
  PNG/PDF inherit.
- Real product SVG/PNG/PDF exports matched at 1705×318 (SVG intrinsic
  1704.67×318.33) and visibly included `Observed sales` and
  `Source: chart_line_series.csv · 6 rows · snapshot 5fbbe640`.
- PDF remains raster-only; separate export actions and Q18 failure injection
  remain. API37 suite 39/39; JVM 24/24; lint 0/5.

## 2026-10-18 — frozen multi-format export bundle

- Added `ExportBundle`: one revision produces a titled SVG and a PNG raster from
  that exact SVG, plus a native PDF; the host cross-checks dimensions and writes
  all three to a real SAF folder with one timestamp.
- Product evidence: SVG 9512 B, PNG 34810 B 1705×318, PDF 16119 B one page
  1705×318, same basename; cancellation reported and wrote nothing; unknown SAF
  tree produced an explicit failure.
- API37 suite 41/41; JVM 24/24; lint 0/5. PDF remains raster-only; accessible/
  tagged output, print and storage-full/mid-write injection remain pending.

## 2026-10-18 — printable accessible worksheet PDF

- Added paginated A4 text table pages to the frozen PDF and a native Android
  print adapter/action. Real Save-as-PDF output: 2 pages, 55,772 bytes, A4, no
  JavaScript, searchable title/source/summary/rows; print cancellation created
  no file.
- Accessible bundle and PDF-only exports: 2 pages, 55,082 bytes, same searchable
  text. 80-row writer test with wrapped labels: 3 pages. Print adapter writes in
  cancellable chunks off the main thread; print action is serialized and checks
  null PrintManager results. API37 suite 43/43; JVM 24/24; lint 0/5.
- PDF remains untagged; worksheet-only print and spooler completion remain
  partial. The MediaStore qualification helper now removes all prior
  `cloudy_ui_q06*` copies to prevent unique-name exhaustion on repeated runs.

## 2026-10-18 — additive/range chart selection

- Added a pure row-selection reducer (replace/toggle/range + anchor), renderer
  mode transport, native pointer-modifier hint, multi-row table highlight and
  endpoint inspector.
- Actual emulator pointer Ctrl/Shift and hardware Shift/Ctrl keyboard tests pass;
  product table highlights rows 0–2 after replace/range/add. API37 47/47; JVM
  28/28; lint 0/5.
- Lasso, keep/exclude, drill, persisted selection and visual mark highlight
  remain pending.

## 2026-10-19 — visual chart mark highlight for row selection

- Added a chart/revision-gated `ApplySelection` renderer message bounded by the
  renderer point limit and source-row index. The worksheet sends the current row
  set after each successful render and whenever selection changes.
- `VegaSpecBuilder` now adds an empty `selected` dataset and uses it for overlay
  marks: Bar gets a contrasting 3 px ring with a translucent foreground tint;
  Line/Area get contrasting selected-point symbols. Query meaning and the native
  table/inspector remain unchanged.
- Emulator product evidence: three additive/range Area rows show three ringed
  markers; a selected North bar is ringed while South stays plain.
- Emulator `VegaRendererProbeTest` 17/17 and `VegaSpecBuilderTest` 4/4. A
  three-device connected run still hits an unrelated physical-device PNG
  assertion (`expected 803`, actual `804`) and device timeouts.
- Persisted selection, lasso, keep/exclude/drill, pointer hover and TalkBack
  multi-selection remain pending. Next: keep/exclude/drill semantics.

## 2026-10-19 — keep/exclude mark filters

- Added `MarkFilter`/`FilterMark` with optional worksheet persistence, exact
  row/series identities and defensive JSON read. Field, series, mark and source
  replacement clear filters that no longer describe the view.
- `WorksheetQuery` now emits a validated, SQL-escaped `WHERE` predicate before
  aggregation. Single-dimension marks use row equality; line marks use
  `(row, series)` pairs; null identities use `IS NULL`. The Parquet snapshot is
  untouched.
- Product worksheet actions expose `Keep only`, `Exclude` and a labeled filter
  bar with `Clear filter`; command palette mirrors them. Filters can be applied
  only from an accepted unfiltered result, so no ambiguous compound filter is
  attempted.
- Emulator evidence: Bar keep leaves one North bar/table row at 60 while the
  source footer remains 6 rows; Area exclude hides South and leaves North;
  Clear restores both; force-stop/reopen retains `Excluded 1 mark`.
- JVM 32/32, lint 0 errors/5 warnings, emulator filter suite plus engine/line
  regressions 9/9. Drill remains blocked by the absent dimension hierarchy;
  compound/cross-sheet filters and persisted selection remain pending.

## 2026-10-19 — native table additive and range selection

- Result-table rows now capture Shift/Ctrl/Meta from the initial Compose pointer
  press and route the existing selection reducer: Shift extends from the anchor,
  Ctrl/Meta toggles, plain click replaces. A Final-pass release observer clears
  canceled gestures, and Shift/Ctrl/Meta+Enter/Space plus TalkBack custom
  actions expose the same modes. Modified key chords ignore auto-repeat, and
  the TalkBack Add action no-ops on an already-selected row. Chart and table
  share one selected set, inspector endpoint and mark overlay.
- Added `TableSelectionTest`, which imports the real fixture, opens the product
  worksheet, injects `MotionEvent` meta state, and verifies 1 → 3 (Shift range) →
  4 (Ctrl add) → 3 (Ctrl toggle) → 1 (plain replace); it also exercises TalkBack
  add/range custom actions and a canceled Ctrl-drag followed by an accessibility
  replace and range, proving the anchor moved and no stale mode leaked. Keyboard
  Enter/Space mapping compiles and ignores repeats but its row-focus path was
  not separately exercised.
- Emulator table-selection/keep-exclude/renderer/spec suite 27/27; JVM 32/32;
  lint 0 errors/5 warnings. Corrected the keep/exclude evidence label from
  CUST14 to CUST13 and refreshed the capability catalog. View Data is the next
  ready CUST13 slice; drill still waits on an explicit hierarchy.

## 2026-10-19 — CUST13 View Data

- Added `UnderlyingRowsQuery`: selected aggregate marks are converted back to
  exact `(row[, series])` identities and queried read-only against the Parquet
  snapshot with the active worksheet filter. Results are capped at 200 rows with
  an explicit 201-row truncation probe; the snapshot and worksheet are untouched.
- Added generation-guarded `ViewDataState`, a native accessible source-row dialog
  with exact-value content descriptions, and a `View data` action available for
  selected marks even while a keep/exclude filter is active.
- Fixed the pre-existing sheet-tab mismatch: tab selection now commits
  `activeWorksheetId`, requeries the visible sheet and clears the previous
  sheet's accepted result while loading. `View data` also rejects a sheet/result
  mismatch, and `hasSeriesColumn` is mark-type aware so stale series fields on
  TABLE/BAR sheets cannot corrupt identities.
- Product evidence: January aggregated to 11 in the worksheet; View Data showed
  exactly North Jan 1 = 10 and South Jan 1 = 1 while the footer stayed at 6 source
  rows. Filter-context query test returns only South's three rows under
  EXCLUDE-North; a line-pair test resolves only North Jan 1.
- `ViewDataQueryTest` 3/3; combined emulator suite 32/32; JVM 32/32; lint 0
  errors/5 warnings. Lasso/rectangle is the next ready selection slice; drill
  still waits on an explicit hierarchy.

## 2026-10-19 — CUST13 rectangle/lasso selection (implemented_unverified)

- Added a pointer rectangle over the offline Vega surface, exact scenegraph hit
  testing for `cloudyBar`/`cloudyHit`, SVG-CTM coordinate inversion (origin and
  padding), a visible lasso overlay, and a revision-gated `LassoSelectionIntent`
  carrying bounded exact source-row identities plus additive state. Ctrl/Meta
  drag adds; an empty non-additive rectangle clears. Selection overlays are
  `interactive:false`, and pointer-up hit testing now posts SelectionIntent
  directly so a click immediately after a lasso cannot be lost to browser click
  suppression.
- Added `RowSelectionReducer.selectMany`, native callback wiring, probe lasso
  mode, tests for set replace/union/clear, slow Ctrl-drag, empty rectangle and
  overlay non-interactivity. Independent review corrected the coordinate
  transform (SVG CTM minus origin and padding) and removed the modifier TTL.
- Host-only checks: JVM **35/35**, lint 0 errors/5 warnings, debug/test APKs
  compile. Before the final geometry/TTL changes, the API37 physical device ran
  the renderer selection subset **5/5**, including rectangle and Ctrl-drag.
- Final device/product qualification is blocked: the owner resumed using the
  physical device, `emulator-5582` disappeared, and the API36 emulator was not
  used because of laptop memory pressure. No raw final device log exists.

## 2026-10-19 — CUST03 persisted Y tick count

- Added optional `ChartOptions.yTickCount` with shared 2–50 bounds, defensive
  old-document loading, `AxisSpec.tickCount`, chart-settings input/validation and
  Vega Y-axis `tickCount` plus `labelOverlap: greedy`. Settings remain
  presentation-only and do not rerun SQL.
- Independent review found X `tickCount` is a no-op on the current band scale;
  the X control/field was removed rather than shipping a control without its
  real operation. Y ticks, invalid-input blocking and Area zero-baseline
  preservation are covered by JVM/spec tests.
- JVM 36/36; lint 0 errors/5 warnings; debug/test APKs compile. Persistence and
  Vega-output instrumentation compiled but were not run; no device visual
  evidence exists.

## 2026-10-19 — CUST05 persisted legend orientation

- Added optional `LegendOrientation` (vertical/horizontal/automatic) to
  `ChartOptions`/`LegendSpec`, a chart-settings dropdown, persistence with
  defensive old-document loading, and Vega `direction` output. Automatic now
  means bottom→horizontal and right→vertical; explicit values override it.
- Horizontal legends use a width-derived `columns` cap so long series lists wrap
  instead of generating an unbounded single-row legend. Bottom legends change
  from Vega's vertical default to horizontal, matching the visible label.
- A width/label-aware `LegendLayout` bounds horizontal columns and legend
  `labelLimit`, preventing an unbounded single-row width and capping individual
  label width. Right-position legends still use outside-plot width like the
  vertical form.
- JVM 40/40; lint 0 errors/5 warnings; debug/test APKs compile. Persistence and
  Vega JSON instrumentation compile but were not run; no device visual evidence.

## 2026-10-19 — CUST06 label font size

- Added optional persisted `labelFontSize` (8–16) through `ChartOptions`,
  `LabelSpec`, settings UI and Bar/Line/Area label lowering. Existing top
  offsets, baselines and mark-default font sizes are preserved.
- An initial inside-end position control was removed after independent review:
  it misplaces labels for negative/reversed values and short bars and fails
  contrast on categorical fills. Position remains absent pending those rules.
- JVM 41/41; lint 0 errors/5 warnings; debug/test APKs compile. Vega JSON and
  persistence instrumentation compile but were not run; no device legibility,
  clipping, collision or export evidence.

## 2026-10-19 — CUST08 line width

- Added optional persisted `lineWidth` (1–8) for Line/Area, chart-settings input,
  spec validation and Vega stroke lowering. Defaults remain 2.0 for Line and
  1.5 for the Area outline; Bar and selection overlays are untouched. Bar/Table
  Apply clears hidden widths, and validation is time-series only.
- JVM 42/42; lint 0 errors/5 warnings; debug/test APKs compile. Vega JSON and
  persistence instrumentation compile but were not run; no device thickness,
  legend-sample or export evidence.

## 2026-10-19 — CUST06 robust bar label position

- Reintroduced persisted TOP/INSIDE_END position for Bar labels only. Vega uses
  Y-scale-derived signals for sign/reversal-aware offsets, a non-zero/short-bar
  threshold, symmetric plot-bound clamping via `cloudyPlotHeight`, and an
  on-fill foreground/inverse halo keyed to the final anchor actually over the
  bar. Labels are non-interactive so bar click/hover still works; Line/Area
  labels remain TOP.
- Headless bundled Vega 6.4.0 checks covered positive, negative, zero, 1 px and
  reversed bars; labels stayed in view and active inside labels remained inside
  tall bars.
- JVM 43/43; lint 0 errors/5 warnings; debug/test APKs compile. Instrumentation
  and device contrast/clipping/export visuals were not run.

## 2026-10-19 — CUST02 subtitle and caption

- Added optional bounded subtitle (160) and caption (240) plain text through
  chart options, typed spec, native chart header/footer, renderer export header,
  SVG/PNG provenance and PDF text companion. Presentation changes do not rerun
  SQL.
- Export captions wrap to up to two width-derived lines. The renderer probe now
  carries subtitle/caption extras and the narrow-export test asserts subtitle
  presence plus two caption nodes.
- JVM 44/44; lint 0 errors/5 warnings; debug/test APKs compile, `node --check`
  passes. Host export rendering, persistence/Vega instrumentation and device
  visual/scaling/accessibility checks were not run.

## 2026-10-19 — CUST02 units and axis-title alignment

- Added optional 16-character plain-text units and persisted AUTO/START/END axis
  title alignment. Units append once to the Y title, native table/inspector/PDF
  labels and renderer tooltip; axis title anchors reach Vega for X/Y axes.
- A shared header-label helper prevents duplicate suffixes when the measure name
  already contains the units text. Design-contract gap wording was refreshed.
- JVM 46/46; lint 0 errors/5 warnings; debug/test APKs compile. Instrumentation,
  export rendering and device placement/clipping checks were not run.

## 2026-10-19 — CUST14 chart padding

- Added persisted 0–32 dp chart padding (default 8), validated through chart
  options/spec and consumed by the Vega root layout. Export SVG/PNG/PDF inherit
  the same layout while chart width/height and data semantics remain unchanged.
- JVM 47/47; lint 0 errors/5 warnings; debug/test APKs compile. Vega/persistence
  instrumentation and device/export clipping/dimension checks were not run.

## 2026-10-19 — CUST03 categorical X-label thinning

- Added optional persisted X label count (2–50). `AxisLabelSelection` emits a
  nearest-index, evenly spaced band-axis `values` subset that retains first and
  last labels; data, domain, exact table and selection are unchanged. Vega
  `axis.values` is used because band `tickCount` is a no-op.
- JVM 50/50; lint 0 errors/5 warnings; debug/test APKs compile. Vega/persistence
  instrumentation and device collision/rotation/accessibility checks were not
  run.

## 2026-10-19 — CUST04 major-gridline width

- Added optional persisted 0.5–3.0 dp gridline width. Chart specs validate it;
  Vega emits `gridWidth` on the value axis only when gridlines are visible.
  Settings apply without requerying; missing/invalid stored values use the
  renderer default.
- JVM 51/51; VegaSpecBuilderTest 5/5 and ChartSettingsPersistenceTest 5/5 on the
  physical HP Googlebook; lint 0 errors/5 warnings; debug/test APKs compile.
  Visual, export and high-contrast checks were not run.

## 2026-10-19 — CUST06 fixed-decimal value formatting

- Added optional persisted 0–6 decimal formatting for Y-axis ticks and
  Bar/Line/Area value labels using Vega/d3 `,.Nf`. Independent review caught
  that Vega ignores `format` on text field references; labels now use a guarded
  `format(datum.y, ...)` signal. Exact tooltip values, data, aggregation and
  scales are unchanged.
- Added SVG text capture to the renderer probe completion for render-level
  assertions. JVM 52/52; VegaSpecBuilderTest 5/5, ChartSettingsPersistenceTest
  5/5 and a focused packaged-Vega format render 1/1 on the physical HP
  Googlebook; lint 0 errors/5 warnings; debug/test APKs compile. Visual
  collision, locale, extreme-magnitude and export rendering were not run.

## 2026-10-19 — CUST14 chart border

- Added optional persisted chart border. Native chart cards (including fallback
  content) draw a single 1 dp theme-divider outline; the offline renderer carries
  the intent in spec user metadata, and SVG/PNG/PDF export frames add a 1 px
  outer outline around header and chart.
- JVM 53/53; VegaSpecBuilderTest 5/5, ChartSettingsPersistenceTest 5/5 and a
  focused exported-SVG border render 1/1 on the physical HP Googlebook; lint
  0 errors/5 warnings. Native visual contrast, density, PNG/PDF pixel
  comparison and export-header geometry were not visually reviewed.

## 2026-10-19 — CUST04 gridline contrast

- Added persisted DEFAULT/STRONG gridline contrast using the theme divider and
  outline tokens. Only the value-axis grid color changes; domain/tick chrome and
  chart data/dimensions remain unchanged. Invalid/missing JSON falls back to
  DEFAULT.
- Colors now derive from `CloudyTokens`; invalid/missing JSON fallback and the
  DEFAULT renderer branch are asserted. JVM 54/54, VegaSpecBuilderTest 5/5 and
  ChartSettingsPersistenceTest 6/6 on the physical HP Googlebook; lint 0
  errors/5 warnings; debug/test APKs compile. Dark-theme, export, contrast and
  device visual checks were not run.

## 2026-10-19 — CUST06 crowded-label policy

- Added persisted SHOW_ALL/HIDE_CROWDED value-label policy. Crowding is decided
  in the text signal (Vega ignores text-mark `defined`): band width below font
  size + 36 emits an empty string for Bar, Line and Area, including multi-series
  lines. Values, data, scale, selection, tooltip, table and accessible summary
  are unchanged.
- Added a 60-category bar probe and a 40-category three-series line probe proving
  labels disappear, retained the wide formatted-label probe proving labels
  remain, and added invalid-JSON fallback coverage. JVM 55/55;
  VegaSpecBuilderTest 6/6, ChartSettingsPersistenceTest 7/7 and three focused
  render probes 1/1 on the physical HP Googlebook; lint 0 errors/5 warnings.
  Text measurement, axis-label collision and device visual review remain absent.

## 2026-10-19 — CUST14 transparent export background

- Added persisted transparent-export background. Export scene assembly uses
  `fill="none"` instead of the opaque surface while header, chart, border and
  dimensions remain; the native card stays on the app surface. Default exports
  remain opaque and no SQL/data path changes.
- Independent review found dark-theme PDF/print lost contrast on white. Fixed by
  keeping PDF opaque and painting the chart theme background before the
  transparent PNG; the control is scoped to "Transparent SVG/PNG". Added default
  opaque SVG/PNG assertions and a dark PDF compositing pixel test.
- JVM 56/56; VegaSpecBuilderTest 6/6, ChartSettingsPersistenceTest 7/7,
  transparent SVG/PNG render 1/1, default bundle 1/1 and dark PDF compositing
  1/1 on the physical HP Googlebook; lint 0 errors/5 warnings. Viewer/light-
  destination contrast and device visual review remain absent.

## 2026-10-19 — CUST04 minor gridlines

- Added optional persisted minor gridlines as a secondary value axis before the
  major axis, with grid only, no domain/ticks/labels/title/aria, 0.5 width and
  0.55 opacity. Minor positions are explicit domain-derived axis values (20
  linear intervals) rather than a second nice tick count; major chrome is drawn
  afterwards. Log axes omit minors and hide the control.
- Validation moved to emulator-first per owner instruction. JVM 57/57; manual
  emulator-5580 (API36 x86_64) instrumentation: VegaSpecBuilderTest 7/7,
  ChartSettingsPersistenceTest 7/7, minor-grid export 1/1, plus five earlier
  renderer probes 1/1. The physical Googlebook was not used.

## 2026-10-19 — CUST06 percent display

- Added persisted NUMBER/PERCENT display. Percent uses d3 `,.N%` for axis ticks
  and value labels (blank decimals default to 1); stored values, tooltips,
  scales, selection and exact exports are unchanged. Invalid/missing JSON falls
  back to NUMBER.
- Emulator-only validation: JVM 58/58; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 8/8, ChartSettingsPersistenceTest 8/8 and
  a percent render 1/1 showing 12.5%/25.0% plus an axis-only percent tick; the
  blank-decimals copy states the 1-decimal percent default. Lint 0 errors/5
  warnings. Googlebook
  untouched; visual width/collision/dark checks absent.

## 2026-10-19 — CUST05 interactive legend

- Added presentation-only hidden-series filtering. A `visible` dataset filters
  marks/labels/hit targets while the full color/legend domain and all query,
  table, exact-value and selection semantics remain. `host.js` adds per-label hit
  targets and posts revision-gated LegendToggle messages; interactive DOM is
  stripped from exports. Compose provides legend handling plus an accessible
  Series menu and show-all reset. State is ephemeral.
- Emulator-only validation: manual emulator-5580 API36 x86_64 instrumentation
  VegaSpecBuilderTest 10/10, ChartSettingsPersistenceTest 8/8, hidden-series render
  1/1, and real UIAutomator pointer taps on both legend labels 1/1 with no row
  selection; export cleanliness asserted. JVM 58/58; lint 0 errors/5 warnings.
  Googlebook untouched; visual/TalkBack checks absent.

## 2026-10-19 — CUST05 persisted hidden legend series

- Persisted hidden series in ChartOptions (bounded/trimmed JSON), sanitized them
  against the current series in ChartSpecBuilder, and routed legend/menu toggles
  through WorksheetView/WorkbookScreen to updateChartSettings. Stale keys and
  non-LINE mark changes clear hidden state without rerunning SQL. Query/table/
  exact/selection semantics are unchanged.
- Emulator-only validation: JVM 59/59; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 10/10, ChartSettingsPersistenceTest 9/9
  (save/reopen), hidden render 1/1, real legend taps 1/1; lint 0 errors/5
  warnings. Googlebook untouched; Compose product wiring and visual/TalkBack
  checks remain absent.

## 2026-10-19 — CUST03 marker visibility/size

- Added persisted Line/Area marker show/hide and 2–12 px diameter. Defaults
  remain 30/24 area units; custom size uses diameter². The 200-point cap,
  lines, hit targets, selection overlays, labels and query semantics remain
  unchanged. Invalid/missing sizes fall back to automatic; UI validation is
  gated by the marker toggle.
- Emulator-first validation: JVM 60/60; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 12/12, ChartSettingsPersistenceTest 10/10,
  hidden-marker render 1/1; lint 0 errors/5 warnings. Googlebook untouched;
  visual/shape/export checks absent.

## 2026-10-19 — CUST03 marker shape

- Added persisted marker shape (circle/square/diamond/cross) for Line/Area point
  symbols. Circle remains the default and emits no Vega shape override, so
  default output is unchanged. Lines, hit targets, selected overlays, data and
  selection are untouched; invalid JSON falls back to circle.
- Emulator-first validation: JVM 60/60; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 12/12, ChartSettingsPersistenceTest 10/10,
  circle/diamond render 1/1; lint 0 errors/5 warnings. Googlebook untouched;
  exported circle/diamond SVGs are asserted different; visual/TalkBack checks
  remain absent.

## 2026-10-19 — CUST03 line interpolation

- Added persisted linear/step/monotone interpolation for Line and Area/outline.
  Vega receives it in `encode.enter.interpolate`; linear emits no override, so
  default output is unchanged. Gaps, markers, labels, axes, selection and data
  are untouched; invalid JSON falls back to linear.
- A first implementation incorrectly placed `interpolate` at mark level; the
  render/export probe detected identical linear/step SVGs, and the property was
  moved into the encode block. Emulator-first validation: JVM 61/61;
  emulator-5580 API36 x86_64 VegaSpecBuilderTest 14/14 (line + area/outline),
  ChartSettingsPersistenceTest 10/10, linear/step render 1/1; lint 0 errors/5
  warnings. Googlebook untouched; visual checks absent.

## 2026-10-19 — CUST03 line dash style

- Added persisted solid/dashed/dotted strokes for Line marks and Area outlines.
  Vega receives `strokeDash` value refs in `encode.enter`; dotted adds a round
  cap. A first raw-array encoding was rejected by Vega as an unrecognized signal,
  caught by the render probe and fixed by wrapping values. Dotted gaps are
  width-aware so they remain separated at the 8 px maximum. Default solid emits
  no override. Area fill, markers, interpolation, selection and data remain.
- Emulator-first validation: JVM 62/62; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 15/15, ChartSettingsPersistenceTest 10/10,
  solid/dashed/dotted render 1/1; lint 0 errors/5 warnings. Googlebook untouched;
  visual dash/cap/export checks absent.

## 2026-10-19 — CUST03 line opacity

- Added optional persisted 0.1–1.0 paint opacity for Line marks and Area
  fill/outline. Default output remains opaque; Area fill multiplies its fixed
  0.32 alpha. Markers, labels, hit targets, selected overlays and data remain
  unchanged. Invalid/missing values fall back to opaque; Bar settings reset it.
- Emulator-first validation: JVM 63/63; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 16/16, ChartSettingsPersistenceTest 10/10,
  opacity render 1/1; lint 0 errors/5 warnings. Googlebook untouched; visual
  contrast/export checks absent.

## 2026-10-19 — CUST05 legend mark sample

- Added persisted MARKER/LINE legend samples for Line charts. Marker preserves
  prior behavior and marker shapes; line swatches use Vega stroke symbols with a
  200-unit size, current width and dash pattern. Plot marks, data, selection and
  legend hit targets are unchanged; Bar applies reset to MARKER.
- Emulator-first validation: JVM 64/64; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 17/17, ChartSettingsPersistenceTest 10/10,
  legend-sample render 1/1; lint 0 errors/5 warnings. Googlebook untouched;
  visual swatch/hit-target/export checks absent.

## 2026-10-19 — CUST03 marker opacity

- Added optional persisted 0.1–1.0 opacity for Line/Area point symbols,
  independent of line/area paint opacity. Default markers remain opaque; lines,
  labels, hit targets, selected overlays, data and selection are unchanged.
  Invalid/missing values fall back to opaque; hiding markers clears the value.
- Emulator-first validation: JVM 64/64; manual emulator-5580 API36 x86_64
  instrumentation VegaSpecBuilderTest 17/17 (line + area markers),
  ChartSettingsPersistenceTest 10/10, marker-opacity render 1/1; lint 0 errors/5
  warnings. Googlebook untouched; visual contrast/export checks absent.

## 2026-10-19 — CUST04 table row/column dividers

- Added persisted result-table row (default on) and column (default off)
  divider toggles in ChartOptions. ResultTable conditionally draws body row
  rules and 1 dp column rules; query/data/selection are unchanged. Settings apply
  persists without requery. Review found CloudyDivider horizontal instances were
  zero-width; the shared component now fills width/height, making row and existing
  app-wide horizontal dividers visible. Pre-fix screenshots are stale.
- Emulator-first validation: JVM 64/64; manual emulator-5580 API36 x86_64
  instrumentation ChartSettingsPersistenceTest 11/11, TableSelectionTest 5/5,
  VegaSpecBuilderTest 17/17; lint 0 errors/5 warnings. Googlebook untouched;
  no Compose/table visual or screenshot check.

## 2026-10-19 — CUST05 legend swatch opacity parity

- Legend marker/line sample swatches now mirror markerOpacity/lineOpacity via
  Vega symbolOpacity; defaults omit the property and plot/data/selection remain
  unchanged.
- Emulator-first validation: JVM 64/64; emulator-5580 API36 x86_64
  VegaSpecBuilderTest 17/17, ChartSettingsPersistenceTest 11/11, legend render
  1/1; lint 0 errors/5 warnings. Googlebook untouched; visual contrast/export
  checks absent.

## 2026-10-19 — CUST04 table divider width

- Added optional persisted 0.5–3.0 dp table-rule thickness, defaulting to 1 dp.
  Header/body/column rules share it; query/data/selection unchanged. Invalid
  values fall back to default.
- Emulator-first validation: JVM 64/64; emulator-5580 API36 x86_64
  ChartSettingsPersistenceTest 11/11, TableSelectionTest 5/5,
  VegaSpecBuilderTest 17/17; lint 0 errors/5 warnings. Googlebook untouched;
  no Compose/table visual check.

## 2026-10-19 — CUST04 table divider contrast

- Added persisted DEFAULT/STRONG table-rule contrast using divider/outline
  tokens. All header/body/column rule sites use the resolved color; toggles,
  thickness, query/data/selection are unchanged. Invalid/missing values fall
  back to DEFAULT.
- Emulator-first validation: JVM 64/64; emulator-5580 API36 x86_64
  ChartSettingsPersistenceTest 11/11, TableSelectionTest 5/5,
  VegaSpecBuilderTest 17/17; lint 0 errors/5 warnings. Googlebook untouched;
  visual contrast/table checks absent.

## 2026-10-19 — CUST06 currency display

- Added persisted CURRENCY value format with USD/EUR/GBP/JPY codes and fixed
  two-decimal formatting. USD uses d3 `$,.2f`; other currencies use axis
  label-encode and value-label signals for symbol/matching U+2212 negative
  placement. Exact tooltips/data/selection remain unchanged; decimal field is
  hidden for currency but its stored value is preserved. Crowded-label spacing scales with font size
  for currency (fontSize×7+6). Negative USD/EUR render coverage added.
- Emulator-first validation: JVM 65/65; emulator-5580 API36 x86_64
  VegaSpecBuilderTest 18/18, ChartSettingsPersistenceTest 12/12, USD/EUR render
  1/1; lint 0 errors/5 warnings. Googlebook untouched; visual/locale/export
  checks absent.

## 2026-10-19 — CUST08 marker border

- Added optional persisted theme-text-colored marker border with 0.5–3 dp width
  for Line/Area point symbols. Default off; fill/shape/size/opacity/lines/
  selection unchanged. Hiding markers clears width; invalid values fall back.
- Emulator-first validation: JVM 65/65; emulator-5580 API36 x86_64
  VegaSpecBuilderTest 18/18 (line + area), ChartSettingsPersistenceTest 12/12,
  border render 1/1; lint 0 errors/5 warnings. Googlebook untouched; visual/
  legend/export checks absent.

## 2026-10-19 — CUST04 table header divider

- Added persisted header-rule visibility, default on, sharing the existing table
  divider width/contrast. Header content/data/selection unchanged; apply remains
  presentation-only.
- Emulator-first validation: JVM 65/65; emulator-5580 API36 x86_64
  ChartSettingsPersistenceTest 12/12, TableSelectionTest 5/5,
  VegaSpecBuilderTest 18/18; lint 0 errors/5 warnings. Googlebook untouched;
  no Compose/table visual check.

## 2026-10-19 — CUST07 categorical palette

- Added computed color-blind-tested categorical palettes (light/dark) to the
  provisional token source and generator. CategoryPalette CLOUDY/COLORBLIND is
  persisted, selects the Vega/native palette, and falls back to CLOUDY on invalid
  data. Review-driven rework replaced colliding hand-picked hues with candidates
  checked pairwise under Machado protan/deutan/tritan simulation, removed the
  native label-hash mapping in favor of first-appearance cycling, and made the
  render probe assert all eight light/dark hexes.
- Emulator-first validation: JVM 67/67 (mapping, contrast and simulated CVD
  distance), token generator --check current, declared contrast 40/40;
  emulator-5580 API36 x86_64 VegaSpecBuilderTest 18/18,
  ChartSettingsPersistenceTest 12/12, eight-series palette render 1/1 in both
  themes; lint 0 errors/5 warnings. Googlebook untouched; visual/user CVD/export
  checks absent.

## 2026-10-19 — CUST08 color-series grouped/stacked bars

- Bar marks accept the Color shelf. DuckDB groups by category+series, ranks
  categories by aggregate total and orders series deterministically. The builder
  assigns category order and stack bases; grouped is the default and stacked is
  persisted. Vega lowers side-by-side/stacked rects with a series legend and no
  render-time aggregation. Bar legends are explicitly non-interactive.
- Filter/selection identity now carries the series for bars. The native fallback
  refuses color-series bars rather than drawing a single wrong bar; exact values
  remain in the table and PDF.
- The debug probe gained grouped/stacked fixtures and exposed an older probe bug
  that reset `valueFormat` to NUMBER; it now preserves the probe spec unless an
  explicit extra is supplied, restoring percent-label coverage.
- Review-driven fixes: the Y scale now covers stack endpoints (`displayY`) so
  the tallest stack is not clipped; bar legend controls are shown and Apply no
  longer clears the legend; stacking is gated to Sum/Count; the grouped-bar query
  uses the 2001-row engine limit; stacked labels honor inside-end; tooltips
  anchor at the stack endpoint.
- Validation: JVM 70/70; BarChartQueryTest 1/1 (ordering, totals, filter,
  underlying row); VegaSpecBuilderTest 19/19; VegaRendererProbeTest 39/39;
  ChartSettingsPersistenceTest 12/12. Googlebook untouched; visual, 100%,
  horizontal and native color-series fallback remain open.

## 2026-10-19 — CUST08 100% stacked bars

- Added persisted PERCENT_STACKED. The builder rejects negative or non-positive
  category totals, normalizes each positive Sum/Count category to 1.0, assigns
  cumulative bases and keeps raw aggregates in exactY/table/PDF. The chart axis
  is forced to 0–1 with percent formatting without mutating the saved display
  format. Non-additive aggregations reset the mode to grouped.
- Review-driven fix: 100% mode was still using grouped x/width because the
  grouped predicate excluded only STACKED. It now qualifies only NONE, so 100%
  uses stacked x/width. The probe now asserts equal x/width and contiguous
  exported rect geometry, and the spec test asserts stacked x/width signals.
- Validation: JVM 72/72; VegaSpecBuilderTest 19/19; VegaRendererProbeTest 40/40
  (60.0%/40.0% labels and full-stack geometry); BarChartQueryTest 1/1;
  ChartSettingsPersistenceTest 12/12; lint 0 errors/5 warnings. Googlebook
  untouched; visual/negative and native fallback remain open.

## 2026-10-19 — CUST08 horizontal bars

- Added persisted BarOrientation. Horizontal bars use an x linear value scale and
  a y category band, swap axes/gridlines/titles, and mirror grouped/stacked/100%
  layout into vertical bands with horizontal stack endpoints. Labels, selection,
  minor gridlines and tooltips follow the new geometry; default vertical output
  and query semantics are unchanged. The native fallback remains vertical-only.
- Review-driven fixes: horizontal label alignment/offset now follows screen
  geometry (so reversed axes and negatives stay outside/inside correctly),
  label clamping reserves text width, and the reverse toggle copy is
  orientation-aware.
- Validation: JVM 72/72; VegaSpecBuilderTest 19/19; VegaRendererProbeTest 41/41
  with an exported horizontal stack sharing y/height and contiguous x;
  BarChartQueryTest 1/1; ChartSettingsPersistenceTest 12/12; lint 0 errors/5
  warnings. One full renderer run had a transient process crash; immediate
  reruns passed. Googlebook untouched; visual/long-label and native fallback
  remain open.

## 2026-10-19 — CUST08 stacked/100% area and shared stack mode

- Renamed the internal stack enum to `StackMode`, added persisted
  `areaStackMode`, allowed Color on Area, and computed cumulative/percent bases
  before rendering. Area bands use explicit y/y2, outlines/symbols/labels use
  the stack endpoint, the automatic domain uses displayY, and the legend uses
  fill swatches. Hidden-series toggles now cover Area. Negative/zero-total
  stacked areas and non-additive stacking are diagnosed.
- Validation: JVM 72/72; VegaSpecBuilderTest 20/20; VegaRendererProbeTest
  42/42; ChartSettingsPersistenceTest 12/12; BarChartQueryTest 1/1; lint 0
  errors/5 warnings.

## 2026-10-19 — CUST09.1 secondary bar+line axis

- Added persisted secondary measure/aggregation and secondary-axis title/sync
  options. The grouped query returns primary and secondary values on the same
  row, so source-row identity and selection are shared. A typed `ChartLayer`
  carries one LINE series and right-axis ownership; Vega renders a separate
  `table2`, `y2` scale/axis, line and hit target. Primary bar/stack/percent and
  area paths are unchanged; horizontal primary bars reject the secondary axis.
- UI adds a Secondary Values shelf and settings; result/PDF tables label the
  second column with its measure/aggregation.
- Review-driven fixes: secondary points carry an explicit flag so tooltips use
  `y2` and skip primary-table fallback; secondary line/hit marks participate in
  tap/lasso hit-testing; secondary tooltips/axis labels avoid primary units;
  inspector headers label the secondary measure; the test fixture uses
  non-monotonic secondary values and AVG vs primary SUM, and spec tests assert
  bar→y / secondary→y2 bindings.
- Validation: JVM 74/74; DualAxisQueryTest 1/1; VegaSpecBuilderTest 21/21;
  VegaRendererProbeTest 43/43; ChartSettingsPersistenceTest 12/12;
  BarChartQueryTest 1/1; lint 0 errors/5 warnings. Googlebook untouched;
  arbitrary layers, secondary legend/aggregation UI, labels and visual checks
  remain open.

## 2026-10-19 — CUST09.2 secondary aggregation/legend/labels

- Secondary Values now has its own aggregation control and no longer follows
  primary aggregation changes. A stroke legend, persisted marker/label toggles
  and exact secondary point labels were added; the secondary legend uses the
  existing position/orientation settings while bar legends stay non-interactive.
- Review-driven fixes: secondary labels clamp inside the plot, markers are not
  clipped at the domain edge, the legend mirrors line dash/opacity, and tests
  assert the right-axis title, legend title, secondary label/marker presence,
  `y2` bindings and legend/marker/label toggle-off behavior.
- Validation: JVM 74/74; DualAxisQueryTest 1/1 (SUM primary vs AVG secondary on
  non-monotonic values); VegaSpecBuilderTest 21/21; VegaRendererProbeTest
  43/43; ChartSettingsPersistenceTest 12/12; lint 0 errors/5 warnings.
  Googlebook untouched; secondary formatting/units, interactive legend and
  visual checks remain open.

## 2026-10-19 — CUST09.3 secondary formatting and interactive legend

- Secondary measures now persist independent number/percent/currency formatting,
  decimals, currency code and units. The y2 axis, secondary labels, tooltip
  suffixes, table/inspector/PDF headers use that configuration. The secondary
  legend is interactive for dual-axis bars and toggles only the secondary marks
  through hiddenSeries; primary data/axes remain unchanged.
- Review-driven fixes: blank-decimal NUMBER labels preserve exact values;
  stored secondary decimals are sanitized and builder validation is tested;
  legend usermeta carries primary+secondary keys and BAR legend interaction is
  restricted to the secondary-only case; hidden tests cover marker/label removal.
- Validation: JVM 75/75; VegaSpecBuilderTest 21/21; DualAxisQueryTest 1/1;
  ChartSettingsPersistenceTest 12/12; VegaRendererProbeTest 43/43; lint 0
  errors/5 warnings. Googlebook untouched; manual secondary bounds, pointer
  legend testing, arbitrary layers and visual checks remain open.

## 2026-10-20 — S00.2 original asset and evidence reconciliation

- Inspected the existing clean `main` at `50dd16f`; the intended origin matched
  and remote `main` resolved to the same SHA. Default SSH failed on permissions
  of `/etc/ssh/ssh_config.d/20-systemd-ssh-proxy.conf`; read-only remote inspection
  succeeded with `GIT_SSH_COMMAND='ssh -F /dev/null'` without changing SSH config.
- Located original Cloudy tokens, both generators, design specimen and platform
  Rule 0 in the existing sibling Cloudy Workspace at `bccb328`. Recorded original
  paths/commits/hashes and a 74/74 common-token comparison. Original tokens say
  `proposed`; Cloudy Data retains its single provisional source and scope.
- Fresh token-generation check passed; contrast 40/40 declared pairs passed.
  Corrected the CH04 note to acknowledge the already implemented CUST09.3
  secondary formatting/legend subset, retaining its partial status and missing
  pointer/visual/bounds gates. The behavioral source still matches `6494746`;
  intervening `67ff8a3`/`50dd16f` only changed documentation.
- Read all three plans and the master runbook. Independent audit selected earlier
  S03.2 process-local workbook authority ahead of CUST09.4; no S11.C0, S13.C0 or
  S15.C0 completion was inferred. Physical Googlebook remains on owner hold.

## 2026-10-20 — S03.2 workbook coordinator and native multi-Activity qualification

- Replaced per-ViewModel editable document/history/save ownership with one shared
  per-workbook coordinator in the app Store registry. Save is revision-checked
  against disk and completes before observable publication. Undo/redo persist
  increasing revisions; imports and snapshot readers serialize with commands.
  Local tabs stay independent, same-view commands queue, and stale foreign edits
  report explicit conflicts. Favorites use the same authority.
- Independent review exposed and fixed new-sheet activation, ordinary same-view
  rapid-command rejection, stale save/error UI, save/publication cancellation,
  import errors after metadata publication, and ImportRecovery's nonthrowing
  retained-operation failure. Reads/edits now stay blocked until recovery repairs
  the actual accepted snapshot. Full proposal/reapply UI and journal remain gaps.
- Tests worker authored 14 meaningful native tests with fixed states and real
  DuckDB imports: count3/sum35, replacement count1/sum7, actual EACCES write,
  cancellation/exception after real save, failed rollback and later repair,
  observer isolation, local tabs, undo/redo and two real Activity tasks.
- First universal APK install failed with insufficient emulator storage. Probed
  /data (594 MiB free) and own app data/cache (<1 MiB); hypothesized package install
  overhead rather than stale Cloudy Data files. Discriminating opt-in x86_64-only
  build installed successfully; no apps/data/caches were deleted. Default build
  retains arm64-v8a+x86_64 and passed again. New build property rejects unknown ABIs.
- First native capture was taken before Compose displayed the updated title.
  Preserved it as a diagnostic; strengthened the test to assert actual UI text
  before capture and after undo. Final native suite 14/14, regressions 26/26;
  JVM 75/75, lint 0 errors/5 warnings, token check current, contrast 40/40.
- Actual emulator tasks 2384/2385 both observed 2880×1800 fullscreen. Root and
  independent reviewer inspected the final Shared Activity title capture and
  accepted only that bounded empty-workbook render. Native source/artifact hashes,
  raw logs, earlier diagnostic and limitations are recorded in
  `qualification/evidence/2026-10-20-s03-workbook-coordinator.md`.
- Physical Googlebook remained on owner hold. S03 stays partial: command IDs/journal,
  process-kill history, full conflict workflow, cross-process service/Arrow,
  physical desktop/input/accessibility and side-by-side gates remain separate.
  Next exact task is S03.3's durable rename/chart command-journal slice.

## 2026-10-20 — S03.3 typed metadata commands and atomic durable checkpoints

- Reinspected clean main/remote at c2c6b65; plans unchanged and all107 prior
  source/fixture hashes matched. Selected the exact next S03.3/F01,F64/Q17,Q34
  rename/chart journal slice over the verified S03.2 coordinator path.
- ADR0009 extends the interim JSON authority without introducing a second store:
  v3 metadata atomically includes accepted command receipts, head/checksum and
  bounded undo/redo checkpoints. v1/v2 originals are preserved by atomic backup
  before migration; older builds reject v3 instead of dropping history. Room and
  full typed command coverage remain open. No stored command runs on file open.
- Typed request IDs/window/expected revision are frozen across retries; duplicate
  receipts return the original acceptance plus current document with no replay.
  Generic coordinator history also persists. Raw/import saves clear obsolete
  snapshot history while retaining receipt tombstones. Limits50/4096/32MiB are
  explicit; reaching receipt capacity refuses a new typed command, not eviction.
- Test and probe workers owned disjoint new native test/service files. Independent
  review found no material blocker. Root resolved legacy/revision validation,
  atomic original-backup creation and safe expected→accepted acknowledgement
  edges; two real-write acknowledgement-error tests cover the queue case.
- Initial native12/12 passed. Final combined native54/54 passed in16.759s after
  review changes: journal12, writer-death2, coordinator14, affected regressions26.
  Native writer PIDs23503/23520 genuinely died at REPLACED/TEMP_SYNCED, observed
  via Binder by PID23448. Fixed states and real import totals35→7 were exact.
- Default dual-ABI debug/test build and JVM75/75 pass; lint0 errors/6 warnings.
  ChromeOsAbiSupport warning remains visible for dynamic ABI selection, while
  default package inspection confirms both ABIs. Tokens current, contrast40/40.
  Runtime used the existing x86 qualification package; no emulator data was deleted.
- Root/independent reviewer accepted the current shared-title native capture at
 2880×1800; tasks2386/2387 were distinct fullscreen Activities. No physical/input,
  accessibility, side-by-side, power-loss or concurrent process-writer gate passed.
- Metadata temp files can survive a pre-replacement SIGKILL; accepted state recovers
  but cleanup remains the next exact task. Source/artifact hashes and raw native
  proof are in qualification/evidence/2026-10-20-s03-durable-command-journal.md.
  Physical Googlebook hold and S11.C0/S13.C0/S15.C0 boundaries remain unchanged.

## 2026-10-20 — S03.3 validated metadata orphan cleanup

- Continued after the durable journal implementation 92f8aaa and source binding
  99e770d; both were normally pushed and remote main was verified. The next exact
  ready task was abandoned metadata-temp recovery, F01/F64/Q17,Q34.
- WorkbookStore now validates canonical content/journal/directory identity under
  its shared root lock before removing only direct regular numeric metadata temps.
  It never promotes a candidate, follows a candidate symlink, recurses or cleans
  missing/corrupt/future metadata. Per-root active registration protects reentrant
  loads; same-process readers wait for the writer. Failed cleanup is logged and
  valid accepted work remains available.
- Disjoint test workers added six filesystem/concurrency tests and extended three
  actual writer-death tests. Native 61/61 passed in 19.017s with no skips, including
  journal 12, coordinator 14 and native engine/import/query/persistence regressions 26.
  Binder confirmed writer PIDs23800/23817/23833 from observer PID23769. Exact content,
  history, receipts and bytes survived cleanup; real corrupt-canonical and chmod
  denial cases retained candidates. Real symlinks/targets and other files survived.
- Both x86 qualification and default dual-ABI debug/test builds passed. JVM 75/75;
  lint 0 errors/6 warnings retained. Default ZIP contains arm64-v8a+x86_64. Token
  generation current; contrast 40/40 declared pairs. No disabled checks or deleted
  emulator data. Independent source/test review found no material finding.
- Evidence, raw logs, 113 source/fixture hashes and exact runtime/package hashes:
  qualification/evidence/2026-10-20-s03-metadata-temp-recovery.md. No new visible
  surface or physical-input acceptance was claimed. Physical Googlebook remains
  on hold; power loss and concurrent cross-process writers remain unqualified.
- Full S03, Room, typed command catalog and S11.C0/S13.C0/S15.C0 stay partial/open.
  Next exact action: typed AddWorksheet with frozen request/sheet identity,
  idempotent retry/reopen, durable undo/redo and view-local activation.

## 2026-10-20 — S03.3 typed AddWorksheet and retry admission

- Reinspected clean main 6599320 and live matching origin; all 113 prior source/
  fixture hashes matched, plans unchanged/already read. The next ready task was
  AddWorksheet, F01/F64/Q17,Q19,Q34, over the verified interim checkpoint authority.
- Typed additions append exact unmapped Table defaults with stable new identity,
  one acceptance and durable history. Duplicate after undo cannot recreate a sheet.
  IDs retained by current/history/receipts cannot be recycled, including after a
  raw-save history barrier. UI freezes first-unused default name/request at first
  dispatch and activates only its own view after proven acceptance; stored reopen
  default and other local tabs stay unchanged. Unknown saved kinds/fields fail
  closed; v3/journal1 shape is retained with older readers refusing new kinds.
- Independent review found a pre-existing hidden-retry/Saved hazard. Root added a
  per-view failure barrier: later Add/no-op/import attempts are explicitly refused,
  original frozen retry stays visible, duplicate Save tokens cannot redispatch.
  Known invalid requests are terminal Change not applied so corrected actions can
  proceed. Import shares admission and checks pending identity after waiting so
  queued Apply cannot resurrect discarded staging. Full proposal reapply is open.
- Disjoint workers authored 14 command/admission tests and 2 real-death tests, plus
  only the bounded debug service extension. First combined native 77/77 passed
  in 20.406s with no skips; old 61 regressions all passed. Actual new writer
  PIDs24232/24249 died at TEMP_SYNCED/REPLACED, Binder-proven by PID24199.
- Real EACCES captured exact retry identity; actual staged CSV, rapid adds/lost ack,
  queued failure/no-op/import, terminal validation and Apply→Discard have fixed
  oracles. Default/x86 build and JVM 75/75 pass; lint 0 errors/6 warnings retained;
  default ZIP both ABIs, tokens current, contrast 40/40. No disabled assertions.
- Evidence and 115 source/fixture hashes: qualification/evidence/2026-10-20-s03-add-worksheet.md.
  Native ViewModel/state evidence does not close new banner visual/input acceptance.
  Physical hold,16KiB/Tableau blockers and S11.C0/S13.C0/S15.C0 bounds are unchanged.
  Next exact task: typed desired-value SetFavorite and stable native Home retry.

## 2026-10-20 — S03.3 typed SetFavorite and native Home intent

- Reconciled clean main77185fd/live matching origin and all115 prior source hashes;
  plans unchanged/already read, original-asset/design authority preserved.
  Earliest ready task:F01/F64/Q17,Q19,Q34 with bounded Q21 native Home checks.
- SetFavorite freezes explicit Boolean/request/window/workbook/revision; identical
  retries cannot toggle/replay after rename/undo/reopen. No-op retains redo.
  Home carries real summary revision, one unresolved intent per workbook and
  serialized list publication. Failure survives reload/filter/search; sibling
  workbooks proceed. Known acceptance remains saved even if later retry fails
  before a new outcome. Dismissal only clears a local proposal, not accepted data.
- Disjoint workers authored10 native command/controller cases and1 real Home UI
  case; independent review found and verified the acceptance-retention correction.
  Real directory EACCES and postcommit-corruption/recovery guard use exact oracles.
- Initial search and later Tab assertions raced native semantics publication;
  bounded exact waits preserve original checks. A repeated disabled-star failure
  was discriminated by XML/pixels: label on enabled decorative Icon under disabled
  Box/row. Root moved shared label to actual Role.Button; strict disabled/pointer
  behavior passes. Focus trace reaches exact star afterTab8; Enter persists false.
- Final combined native89/89,no skips,50.208s, including old77 and palette1.
  Default/x86 debug/test build and JVM75/75 pass; lint0/6 retained; both default
  ABIs present; tokens current,contrast40/40. Independent source/trace/pixel review
  accepts final wide light Home failure/saved captures with no material findings.
- Evidence, failures, diagnostic hypotheses/probes and118 source/fixture hashes:
  qualification/evidence/2026-10-20-s03-set-favorite.md. Physical owner hold,
  16KiB/Tableau, full design matrix, Room/full commands and C0 bounds unchanged.
  Next exact action:S03.4 typed persisted-reference refusal before SQL/View Data,
  with real source replacement retaining IDs until explicit repair.

## 2026-10-20 — S03.4 decoded-reference admission

- Continued clean c37eccf after normally pushing favorite implementation/binding;
  live upstream matched and118 substrate hashes were fresh. Selected first
  persisted-reference slice F01/F05/F14/F64; Q01/Q16/Q17/Q19/Q34.
- Typed read-only resolver rejects missing mappings, ambiguous/blank field IDs
  and incompatible active filter anchors before choosing SQL grain. Every
  nonnull channel resolves even when inactive; no auto-clear/name guessing.
  View Data requires runtime snapshot/sheet/query ownership and matching field
  definitions; chart/name changes remain compatible. Native selection admission
  refuses stale ownership; no persisted format/renderer/import rewrite.
- Disjoint workers authored7 reference and2 real source-replacement cases.
  First focused9/9 in0.866s; final combined101/101, no skips,50.672s includes old89
  and Bar1/Line+Area2 regressions. Exact15/20,series10/5/20,AVG3/8,7/11 after explicit
  rebind and source rows passed. Missing-path refusal precedes SQL and preserves
  exact metadata/checkpoint/history/receipts/source/Parquet bytes.
- Real undo/reopen restores broken mapping against the same replacement snapshot;
  redo restores repaired7/11. No earlier source version is retained by that undo.
  Final x86/default build, JVM75/75, lint0/6 retained, both default ABIs, current
  tokens and contrast40/40 pass. Independent source/oracle review has no material
  finding; no new error/rebind UI/native pixel claim.
- Evidence and121 source/fixture hashes:
  qualification/evidence/2026-10-20-s03-reference-admission.md. C0, physical hold,
  16KiB/Tableau, full semantic/package/Room/source-history boundaries unchanged.
  Next exact action: fail-closed malformed/future legacy filter decoding, retaining
  valid v1/v2 defaults/migration originals and v3's existing strict raw guard.

## 2026-10-20 — S03.4 strict legacy filter decoding

- Reconciled clean main/upstream/live origin e69bb7c and all121 previous hashes;
  plans already read/unchanged, scoped original assets still bccb328. Selected
  filter decode gate F01/F05/F14/F64; primary Q33/Q34, bounded Q16/Q17 regression
  prerequisites. No document/renderer/token authority replaced.
- Before edits, native future-filter test on unchanged production reproduced both
  v1/v2 losing KEEP East and returning unfiltered15/20; canonical cleanup deleted
  recovery candidates. The expected failure and initial APK/test hashes remain.
- MarkFilter now requires complete supported typed records, bounded full marks,
  explicit text/NULL values and consistent series; WorksheetDef propagates failure.
  Optional absent/null mapping defaults stay valid; nonnull bad/blank IDs refuse.
  Duplicate OR terms and empty/whitespace values preserve exact entries. v3 raw
  checks remain; unrelated legacy extensions stay in exact migration originals.
- Disjoint workers added refusal7/migration5; first focused12/12 in2.565s and
  combined113/113,no skips,56.062s pass. Exact15/10/NULL3 and duplicate15 plus actual
  source rows, backup/reopen/receipt/history/no-replay and byte-preserving refused
  load/open/save have independent native oracles. Default/x86 build, JVM75/75,
  lint0/6 retained, both default ABIs, current tokens and contrast40/40 pass.
- Independent source/oracle review has no material finding. Evidence and123 hashes:
  qualification/evidence/2026-10-20-s03-legacy-filter-decoding.md. No full parser
  capacity, error/repair pixels, hardware or new death claim; C0/Room/source-version
  and physicalhold/16KiB/Tableau gates unchanged. Next exact action: strict present
  secondaryAggregation decoding with native AVG100 versus SUM200 migration oracle.

## 2026-10-20 — S03.4 explicit secondary aggregation decoding

- Continued clean/pushed/live-matching b752e46 after strict-filter source binding;
  all123 baseline source hashes fresh, plans/authority unchanged. Selected
  F01/F17/F36/F64,Q33/Q34 numerical migration slice.
- Pre-edit native probe proved both v1/v2 malformed AVGG becameSUM and executed
  January150/200 versus validAVG100; canonical cleanup removed candidates.
  Expected failure and original APK/test-source hashes remain inspectable.
- WorksheetDef now defaults SUM only when secondaryAggregation is absent;
  present exact enumstrings remain supported, null/wrongtype/unknown reject,
  including inactive channels. No renderer/Store rule or applicability expansion.
- Disjoint workers added refusal3/migration3. Focused6/6 in1.274s, then combined
  119/119,no skips,58.029s pass. Actual AVG100 versus SUM200, exactJan2 source rows,
  original backups/history/receipts/reopen/duplicate no-replay, active/inactive
  byte-preserving refusal and rehashedv3head/history have independent oracles.
  All7enumtags are syntax-only, with separate4row/sales350/cost280 source control.
- Final x86/default build, JVM75/75, lint0/6 retained, both default ABIs, tokens
  current and contrast40/40 pass. Independent source/oracle review found no
  material issue. Evidence+125 hashes:
  qualification/evidence/2026-10-20-s03-secondary-aggregation-decoding.md.
- Hardware/reference/C0/Room/source-version and broad applicability/UI gates stay
  unchanged. Next exact action: strict shared workbook-version admission; installed
  Android JSON source coerces fractions/strings/wrappedLongs into legacy2, still
  requiring a discriminating native version probe and byte-preservation controls.

## 2026-10-20 — S03.4 shared decoded workbook-version admission

- Continued clean/upstream/live-matchingd2f3d0b; all125 prior source hashes fresh,
  already-read plans/master/scoped original assets and authority unchanged.
  SelectedF01/F64,Q33/Q34; root source/docs, disjoint native workers and review.
- Pre-edit native probe accepted2.9,string"2",Long4294967298 into legacy parsing,
  normalized3, executedEast15/West20 and removed candidates. Failure/hashes retained.
- One shared Number/finite/whole/range-beforeInt reader now governs document,
  checkpoint branch and backup naming. Existing raw/hash/journal guards unchanged;
  equivalent integral decimal/exponent numeric1..3 remains compatible.
- Native new refusal4/migration3 pass7/7 in1.729s; combined126/126,no skips,59.938s.
  Twenty-six malformed decoded/stored cases, actual NaN/±Infinity and rehashed
  v3head/history preserve rejected bytes. Literal tokens/original backups,
  history/receipts/reopen/duplicate and nativeEast15/West20/sourceEast10/5 pass.
- Default/x86 builds,JVM75/75,lint0/6 retained,both default ABIs,tokenscurrent and
  contrast40/40 pass; independent source/oracle review has no material finding.
  Evidence+127 hashes:qualification/evidence/2026-10-20-s03-workbook-version-admission.md.
  Raw lexical precision/grammar, hardware/reference,C0,Room/fullpackage remain open.
- Next exact action: discriminating native duplicate-sheet-ID multiwrite probe,
  then unique-per-document head/history admission with byte-preserving refusal;
  retain intentional missing/null active/default and empty-sheet compatibility.

## 2026-10-20 — S03.4 unique worksheet identities per document

- Continued clean/pushed/live-matching271bee8 after shared-version binding; all127
  previous hashes committed/fresh, plans/master/design/token authority unchanged.
  SelectedF01/F64,Q33/Q34 with root sharedsource/docs and disjoint test/review work.
- Native unchanged-production control with uniqueIDs passed; duplicate legacy2
  accepted one SetChart that changed2titles withreceipt1/revision5, queried15/20
  and removed candidates. Expected baseline failure/artifact hashes retained.
- One exact-equality helper checks each decoded document and constructed checkpoint
  head/undo/redo separately. No Storeordering, trim/casefold, active/default/type
  or cross-revision policy changed. Existing valid-read/tempcleanup limit retained.
- Focused refusal4/compatibility5 passes9/9 in2.081s; actualcombined135/135,no skips,
  61.609s. Legacy adjacent/nonadjacent and rehashedv3head/undo/redo preserve rejected
  files/candidates/backups/source; constructedbadcheckpoints reachzero atomicphases.
  Targeted one-sheet edits/history/reopen/duplicate, exactoriginals, case/padding,
  cross-revision IDs,null/missingactive andemptylists retain realnative15/20/10/5.
- Default/x86 build,JVM75/75,lint0/6 retained,bothdefaultABIs,tokenscurrent and
  contrast40/40 pass. Independent source/test/oracle review has no material issue.
  Evidence+129hashes:qualification/evidence/2026-10-20-s03-worksheet-identity-admission.md.
- Next exact action: native fractional/string legacy revision CAS-admission probe;
  fix only afterproof and preserve exactLong>2^53 controls. Fullpackage/type/Room/
  sourcehistory/hardware/reference/C0 gates unchanged; families remainpartial.

## 2026-10-20 — S03.4 strict decoded workbook revision admission

- Continued clean main/upstream/live origin 2242d29; all 129 preceding hashes were
  fresh, plans/master/scoped original assets and ADR authority unchanged. Selected
  F01/F64, Q33/Q34 with root source/docs, disjoint test workers and independent review.
- Unchanged-production native compatibility four passed. Four v1/v2 fraction/string
  cases coerced to revision 4 and accepted Rename to 5 with receipt/undo, native
  15/20 and deleted recovery candidates. Expected failure/artifact hashes retained.
- One private reader now preserves exact integer classes and checks Float/Double
  finite/whole/range before conversion. No Store/renderer/receipt/timestamp change.
- Focused 8/8 in 3.688 s; combined 143/143, no skips, 69.87 s. Malformed direct/stored
  revisions and rehashed v3 head/undo/redo preserve refused evidence. Exact Long
  above 2^53, largest Double below 2^63, literal integral forms, terminal revision,
  exact originals/history/receipts/reopen/duplicate and real 15/20/10/5 oracles pass.
- Default/x86 builds, JVM 75/75, lint zero errors/six retained warnings, both default
  ABIs, current tokens and contrast 40/40 pass. Independent combined source/oracle
  review found no material issue. Evidence and 131 hashes:
  qualification/evidence/2026-10-20-s03-workbook-revision-admission.md.
- Next exact action: native malformed-present favorite decoding probe; preserve
  intentional absent=false and actual Boolean migration/history/receipt controls.
  Raw lexical/full package/Room/source history/hardware/reference/C0 gates unchanged.

## 2026-10-20 — S03.4 strict present workbook favorite admission

- Continued clean main/upstream/live origin 14b6a6a after revision implementation
  eb930b7 and binding; all 131 preceding committed hashes fresh. Plans/master/
  scoped design and accepted ADR authority unchanged. Selected F01/F64, Q33/Q34
  with root source/docs, disjoint native workers and independent combined review.
- Native baseline compatibility three passed. Ten v1/v2 malformed present favorites
  became library Booleans and an unrelated Rename persisted them at revision 5
  with receipt/undo and removed candidates. Expected failure/hashes retained.
- Narrow reader preserves absent=false and requires actual Boolean for present
  values. Current v3 raw guards and Store/command/Home/renderer behavior unchanged.
- Focused 7/7 in 4.183 s; combined 150/150, no skips, 74.717 s. Twenty-three faults,
  direct nonfinite objects, rehashed v3 head/undo/redo and missing v3 members refuse;
  malformed stored load/open/list/save retains exact bytes and zero atomic phases.
  Valid defaults/projections/originals, explicit desired states/no-op/duplicate/
  undo/reopen/redo and native 15/20/source 10/5 remain independently correct.
- Default/x86 builds, JVM 75/75, lint zero errors/six retained warnings, both default
  ABIs, current tokens and contrast 40/40 pass. Independent combined source/oracle
  review found no material issue. Evidence and 133 hashes:
  qualification/evidence/2026-10-20-s03-workbook-favorite-admission.md.
- Next exact action: native dangling active-worksheet-ID selection/query/UI probe
  with distinct East 15 / West 20 sheets; preserve canonical bytes and intentional
  missing/null/empty defaults. This remains a source finding. Full package/Room/
  source history, physical hold/16 KiB/Tableau and C0 gates stay unchanged.

## 2026-10-20 — S03.2/S03.4 view-local worksheet selection consistency

- Continued clean main/upstream/live origin 3dc4c05; all 133 previous hashes fresh.
  Already-read three plans/master, original Cloudy assets and accepted authority
  remain unchanged. Selected F01/F64, Q16/Q19/Q33/Q34; disjoint native test workers,
  root-only ViewModel/docs ownership and independent source/oracle/visual review.
- Unchanged-production baseline four had three expected failures in 9.435 s:
  missing visible ID queried East 15 with View Data Idle and actual missing-sheet
  pixels; queued Undo then AVG changed unrelated East to 7.5 at revision 7.
  Valid West 20 control passed. Raw failures, APK/test hashes and captures retained.
- ViewModel projects the actual resolved local ID without changing the saved
  reopen hint. Generic sheet edits check their captured target inside shared.edit;
  a removed target reports not applied instead of using the first-sheet fallback.
  No Screen/Store/decoder/coordinator/renderer/token changes.
- Native focused six pass in 6.815 s; combined 156/156, no skips, 77.362 s. Real
  East 15 / West 20 / source 10/5/20, legacy/null/empty defaults, independent views,
  pending View Data invalidation and shared targeted edits remain correct. Queued
  missing target leaves only Undo revision 6, receipts and other sheets intact.
- Real Activity pointer East/West/East/Data source/West path and exact bytes pass.
  Root/reviewer inspect five native PNG/XML/JSON captures at 2880×1800, 240 dpi,
  light/Comfortable/fontScale1. Source mode keeps the hidden previous query while
  presenting schema/provenance. No material source/oracle/visual issue remains.
- Default/x86 builds, JVM75/75, lint0 errors/6 retained warnings, both default ABIs,
  tokens current and contrast40/40 pass. Evidence and 135 source/fixture hashes:
  qualification/evidence/2026-10-20-s03-selection-consistency.md.
- Next exact action: parent-aware native Tab/Shift+Tab/Enter worksheet traversal
  probe, then qualify/fix selected tab semantics and Cloudy focus-ring behavior.
  Nonfocusable text children alone are not a traversal failure. Physical hold,
  16 KiB/Tableau, Room/source history/full lifecycle and C0 gates remain open.

## 2026-10-20 — S02.2/S02.4 native worksheet-tab selected semantics and focus

- Continued clean main/upstream/live b68abb1; all135 prior source hashes fresh.
  Three already-read plans/master/scoped original assets and metadata/token
  authority remain intact. F01/F64,Q21/Q19; root local Screen/docs, disjoint native
  test worker and independent review, existing emulator only.
- Untouched baseline hit stale accessibility focus after24Tab; pixels/XML differed.
  Fresh-cache discriminator on byte-identical production reachedWest onTab8 and
  passed full keyboard/query/checkpoint/bytes path, failing selected=[] at9phases.
  Competing hypotheses, exact source/logs/artifacts and captures retained.
- Local strip now selectableGroup, each tab selectable(Role.Tab,selected), stable
  worksheet keys and remembered focus with existing2dp token border. No extra
  focus target/manualEnter, query/model/storage/authority/token/layout changes.
- Fixed native selected owner omits redundant click while remaining focusable;
  initial harness overconstraint was corrected from actual native counterexample.
  A duplicate BySelector selected criterion was then corrected to property read.
  Exact key bounds/selection/value/byte assertions retained; all failed probes saved.
- Focused keyboard1/1 in13.931s; finalcombined157/157,no skips,78.775s. Native
  East15/West20/Data source focus-versus-activation and exact checkpoint/source bytes
  pass. Nine final phases have one selected tab; independent focus/selected pixel
  and role-metadata review passes at2880×1800/light/Comfortable/fontScale1.
- Default/x86 builds,JVM75/75,lint0/6 retained,bothdefaultABIs,tokenscurrent,
  contrast40/40 pass. Evidence plus136 source/fixture hashes:
  qualification/evidence/2026-10-20-s02-workbook-tab-keyboard.md.
- Next exact action: native Right/Left/manualEnter and single-Tab group-exit/
  ShiftTab selected reentry probe. Current per-tabTab routes require an explicit
  qualified contract migration; preserve all existing value/Data/byte oracles.
  Roving/panel/overflow/TalkBack/physical/fullmatrix and C0/Room/reference stay open.

## 2026-10-22 — S02.2/S02.4 roving worksheet-tab focus

- Replaced the per-tab Tab-stop strip with a manual-activation roving group: arrows
  move focus without selection, Enter activates, the roving owner snaps to
  selection, Tab leaves the selected tab for New worksheet, and Shift+Tab reenters
  the selected tab. Pointer activation from a focused strip keeps focus; worksheet
  removal clamps the roving owner; deferred focus requests are cleared on blur.
- The old WorkbookTabKeyboardTest was deliberately superseded by a fresh
  WorkbookTabRovingFocusTest using the East15/West20 fixture, fresh cache-clear
  focus observations, selected/query oracles, native captures and complete
  checkpoint/revision/receipt/history/CSV/Parquet byte checks. It also covers a
  mixed pointer/keyboard focus-retention regression and Shift+Tab recovery from a
  New-worksheet overshoot.
- Independent review found pointer focus loss and stale roving index after sheet
  removal; both were fixed. A full-suite accessibility race was diagnosed from the
  retained fresh XML and handled through the group's native Shift+Tab route rather
  than weakening the fresh-focus oracle.
- Validation: JVM 75/75, lint 0 errors/7 warnings, roving native test 1/1
  (15–17 s), WorkbookSelectionUiTest 1/1, WorkbookSelectionConsistencyTest 5/5,
  full emulator native suite 236/237. The only remaining failure is the
  pre-existing ShortcutIntentTest idle-Ctrl+S expectation, which also fails on
  untouched dfea76f and is unrelated to this slice.
