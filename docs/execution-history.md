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
