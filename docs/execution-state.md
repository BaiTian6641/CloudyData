# Execution state

**Updated:** 2026-10-20
**Branch:** `main`
**Source:** S03.3 implementation through `eac7f6087fbbb94fadb0acf7b0f4b66a8b7c9e3c`;
all 115 committed source/fixture hashes match the artifact manifest in
`qualification/evidence/2026-10-20-s03-add-worksheet.md`.
`main` tracks `origin/main`; no release/distribution was created.
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
  worksheet query. Worksheet selection is now window-local and requeries that
  view, clearing the previous sheet's accepted result while loading. The stored
  active-sheet ID remains the reopen default; per-window process-death tab
  restoration is not yet implemented.
- S03.2 bounded process-local authority on the API36 x86_64 emulator: one
  coordinator per workbook; atomic save before publication; revision conflicts;
  shared undo/redo with monotonically increasing revisions; same-view command
  sequencing; independent local tabs; fixed-path snapshot readers serialized
  against imports. Native coordinator tests **14/14** and affected engine/
  persistence/recovery/filter/View Data/dual-axis regressions **26/26** pass.
  Real native totals 35 → 7, failed writes, cancellation after save, post-save
  import errors and unresolved-recovery read/edit blocking have explicit oracles.
  Two real Activities in tasks 2384/2385 share rename/undo and the survivor saves
  after the other closes. Actual title text assertions and an independently
  inspected native capture pass. Both observed bounds are 2880×1800; side-by-side
  placement, physical input and full Q19/S03 acceptance remain unverified.
- Rows/Values/Line- and Bar-Color placements, aggregate selection (SUM verified; others
  available), Table/Bar/Line marks, chart/table, row selection, exact-value
  inspector, generated SQL display.
- Typed `ChartSpec` and multi-series line rendering with facet-separated series,
  observed-gap handling, truncation refusal, chart/X/Y/legend titles, Y
  range/zero/log/reverse, gridlines, value labels and bottom/right legend.
- Workbook format v3 adds the atomic durable command/history envelope. v1/v2
  documents migrate on first save with exact original bytes preserved; unknown
  future v4 and corrupt/missing journals are refused without rewriting evidence.
- S03.3 typed rename/chart/worksheet-add commands and generic checkpoints restore
  bounded undo/redo after reopen. Retained identical command IDs cannot replay;
  changed request identity is refused. Real native writer-process death at temp
  fsync and post-replacement recovers exact before/after states. Final native
  run 77/77 includes worksheet commands/admission 14, metadata recovery 6, journal
  12, process-death 5, coordinator 14 and regressions 26. Valid canonical recovery removes abandoned direct metadata
  temps; missing/corrupt canonical files preserve candidates. Live same-process
  writers, symlinks and unrelated files are protected. Permission-denied cleanup
  preserves accepted access and retries on a later load.
  This qualifies single-writer process death; Room, full typed catalog and
  whole-product-process lifecycle remain separate.
- AddWorksheet accepts one exact default Table definition and durable receipt.
  Identical retries cannot add another sheet or recreate an undone addition.
  Retained current/history/receipt IDs cannot be recycled. Only the requesting
  native ViewModel activates an accepted still-present sheet; other views keep
  local tabs and the stored reopen default stays unchanged. Rapid additions pick
  the first unused Sheet N labels and retain frozen identities across retry.
- A failed view save retains its first retry and stays failed when later queued
  edits, no-ops or imports are explicitly refused before dispatch. Save retries
  that exact intent; refused later actions require a new request. Staged imports
  survive refusal; discarded/replaced/completed queued imports cannot resurrect.
  Deterministic invalid requests are Change not applied without a retry token,
  so corrected actions can proceed. Full rejected-proposal review/reapply is open.
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
- Lint: 0 errors (6 warnings). JVM: **75/75**, including set-selection reducer,
  Y tick-count, legend-layout, label-position/font-size, line-width,
  title/caption, units/anchor, padding, X-label, gridline-width/contrast,
  decimal-format, chart-border, crowded-label, palette, bar/area-stacking and
  secondary-axis cases.
  Emulator renderer probe: **43/43**; Vega spec builder: **21/21**;
  chart-settings persistence: **12/12**; color-series bar query: **1/1**;
  dual-axis query: **1/1**. CUST09.2 adds secondary legend/markers/labels and
  independent aggregation editing.
  Earlier keep/exclude, table-selection and View Data runs remain valid for the
  unchanged paths. The lasso/Y-tick/device visual assertions still lack a
  post-handoff device run; see **Implemented but unverified**. A three-device connected run still hits a
  physical-Googlebook PNG export assertion (`expected 803`, actual `804`) and
  device timeouts; the emulator suites are clean.
- Emulator-first rerun: manual instrumentation on `emulator-5580` (API36,
  x86_64) passed focused Vega spec 7/7 and chart-settings persistence 7/7 for the
  minor-gridline slice; the physical Googlebook was not used.
- Window probe: physical launch bounds were ignored (observed 2880×1800);
  emulator honored 1200×900. Caption insets reported zero/unavailable.
- Physical 1M×12 import/query probe: 44.7 MB CSV, 9.3 MB Parquet, inspect 4.7 s,
  apply 2.8 s, warm grouped query 7 ms, exact totals.

### Evidence

- `qualification/evidence/2026-10-20-s03-durable-command-journal.md`
- `docs/decisions/0009-atomic-command-checkpoints.md`
- `qualification/evidence/2026-10-20-s03-workbook-coordinator.md`
- `qualification/evidence/2026-10-20-s00-original-assets.md`
- Current source/APK/capture manifests and raw logs:
  `qualification/evidence/artifacts/2026-10-20-s03-coordinator/` and
  `qualification/evidence/android-test-results/2026-10-20/`
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
- `qualification/evidence/2026-10-19-cust03-y-ticks.md`
- `qualification/evidence/2026-10-19-cust05-legend-orientation.md`
- `qualification/evidence/2026-10-19-cust06-label-font-size.md`
- `qualification/evidence/2026-10-19-cust08-line-width.md`
- `qualification/evidence/2026-10-19-cust06-bar-label-position.md`
- `qualification/evidence/2026-10-19-cust02-subtitle-caption.md`
- `qualification/evidence/2026-10-19-cust02-units-axis-title.md`
- `qualification/evidence/2026-10-19-cust14-chart-padding.md`
- `qualification/evidence/2026-10-19-cust03-x-label-thinning.md`
- `qualification/evidence/2026-10-19-cust04-gridline-width.md`
- `qualification/evidence/2026-10-19-cust06-value-decimals.md`
- `qualification/evidence/2026-10-19-cust14-chart-border.md`
- `qualification/evidence/2026-10-19-cust04-gridline-contrast.md`
- `qualification/evidence/2026-10-19-cust06-crowded-labels.md`
- `qualification/evidence/2026-10-19-cust14-transparent-export.md`
- `qualification/evidence/2026-10-19-cust04-minor-gridlines.md`
- `qualification/evidence/2026-10-19-cust06-percent-display.md`
- `qualification/evidence/2026-10-19-cust05-interactive-legend.md`
- `qualification/evidence/2026-10-19-cust05-persist-hidden-series.md`
- `qualification/evidence/2026-10-19-cust03-marker-options.md`
- `qualification/evidence/2026-10-19-cust03-marker-shape.md`
- `qualification/evidence/2026-10-19-cust03-line-interpolation.md`
- `qualification/evidence/2026-10-19-cust03-line-dash.md`
- `qualification/evidence/2026-10-19-cust03-line-opacity.md`
- `qualification/evidence/2026-10-19-cust05-legend-sample.md`
- `qualification/evidence/2026-10-19-cust03-marker-opacity.md`
- `qualification/evidence/2026-10-19-cust04-table-dividers.md`
- `qualification/evidence/2026-10-19-cust05-legend-swatch-opacity.md`
- `qualification/evidence/2026-10-19-cust04-table-divider-width.md`
- `qualification/evidence/2026-10-19-cust04-table-divider-contrast.md`
- `qualification/evidence/2026-10-19-cust06-currency-display.md`
- `qualification/evidence/2026-10-19-cust08-marker-border.md`
- `qualification/evidence/2026-10-19-cust04-table-header-divider.md`
- `qualification/evidence/2026-10-19-cust07-categorical-palette.md`
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
- **Y tick count.** Persisted `ChartOptions.yTickCount` (2–50), spec validation,
  chart-settings UI and Vega `tickCount`/`labelOverlap: greedy` output are
  implemented. JVM model/spec tests pass; persistence and Vega-output
  instrumentation assertions compile but were not executed. No device visual or
  export evidence exists. The X control was removed because current X scales are
  band scales where `tickCount` has no effect.
- **Legend orientation.** Persisted Automatic/Vertical/Horizontal orientation,
  settings control, position-derived automatic direction and width/label-aware
  horizontal wrapping/label limits are implemented. JVM layout/propagation tests
  pass; persistence/Vega instrumentation and device visual checks were not run.
- **Label font size and bar position.** Persisted 8–16 font size is carried
  through Bar/Line/Area lowering. Bar marks additionally support TOP/INSIDE_END
  with sign/reversal-aware offsets, zero/short-bar clamping and on-fill contrast
  halos; Line/Area stay TOP. JVM propagation/validation and headless Vega
  geometry checks pass; instrumentation and device contrast/clipping checks were
  not run.
- **Subtitle and caption.** Bounded 160/240-character plain text is persisted,
  shown in the native chart header/footer, carried through the renderer export
  header, SVG/PNG output and PDF provenance page. JVM bounds pass; export/device
  layout, wrapping and accessibility were not run.
- **Units and axis-title alignment.** Persisted 16-character units and
  AUTO/START/END alignment are applied to native chart/table/tooltip and PDF
  labels, with units appended once to the Y title. JVM propagation passes;
  instrumentation and device placement/export checks were not run.
- **Chart padding.** Persisted 0–32 dp (default 8) is applied to the Vega root
  layout and inherited by export scenes; data and chart dimensions are
  unchanged. JVM validation passes; instrumentation and device/export clipping
  checks were not run.
- **Categorical X-label thinning.** Persisted 2–50 count emits an evenly spaced
  axis `values` subset with first/last retained; domain/data/selection are
  unchanged. JVM selection/validation and focused Vega/persistence
  instrumentation pass; device collision/accessibility checks were not run.
- **Major-gridline width/contrast.** Optional 0.5–3.0 dp `gridWidth` and a
  divider/outline contrast choice apply only to visible value-axis grids;
  data/ticks/dimensions are unchanged. JVM and focused physical
  spec/persistence checks pass; visual/export checks were not run.
- **Minor gridlines.** Optional persisted secondary linear value-axis grid with
  no labels/ticks/domain/title/aria, fixed 0.5 width/0.55 opacity, explicit
  domain-derived 20-interval positions and major grid drawn afterwards. JVM and
  focused emulator spec/persistence/render checks pass; visual/log/reverse checks
  were not run.
- **Percent display.** Optional persisted NUMBER/PERCENT formatting uses d3
  percent for value-axis ticks and labels; exact data/tooltips and scales are
  unchanged. Emulator spec/persistence and a `12.5%`/`25.0%` + axis-tick render
  pass; visual width/collision checks were not run.
- **Interactive legend.** Pointer taps on individual legend labels post a
  revision-gated toggle; hidden series are filtered from marks while the full
  legend/color domain, query, table, exact values and selection identity remain.
  A native accessible Series menu and show-all reset exist, available even when
  the legend is hidden. Emulator pointer,
  hidden/render and export-cleanliness checks pass; state is ephemeral and
  visual/TalkBack checks were not run.
- **Fixed-decimal value format.** Optional 0–6 decimals format Y-axis ticks and
  Bar/Line/Area value labels; labels use an explicit guarded `format(datum.y)`
  signal because Vega ignores text field `format`. Exact tooltip/data/export
  precision and aggregation are unchanged. JVM, focused spec/persistence and a
  packaged-Vega SVG render pass; visual collision/locale checks were not run.
- **Crowded-label policy.** Optional auto-hide uses a bounded bar-band or
  line-spacing threshold and empty text signals; default show-all preserves
  labels. A 60-category render hid labels while a wide render kept them. JVM and
  focused spec/persistence pass; text-measured collision and device visual
  checks were not run.
- **Transparent SVG/PNG exports.** Optional persisted transparency omits the
  SVG/PNG background while keeping header/chart/border content and normal screen
  surface. PDF/print stay opaque on the chart theme background for dark-mode
  contrast. SVG `fill="none"`, PNG alpha 0, default opacity and dark PDF
  compositing pass on device; viewer contrast and device visual review were not
  run.
- **Chart border.** Optional persisted toggle draws one 1 dp divider outline
  around the native card (including fallback content), plus a 1 px outer outline
  in exported SVG, PNG and rasterized PDF scenes. JVM, spec/persistence and an exported-SVG render
  pass; native visual/pixel checks were not run.
- **Line width.** Persisted 1–8 width is carried through Line/Area specs and
  renderer strokes with 2.0/1.5 defaults. JVM validation passes; instrumentation
  and device thickness/legend/export checks were not run.
- **Marker visibility/size/shape.** Persisted show/hide, 2–12 px diameter and
  circle/square/diamond/cross shape apply to Line/Area symbols under the
  existing 200-point cap; lines/hit targets/selection remain. Emulator
  spec/persistence/render checks pass; visual checks were not run.
- **Line interpolation.** Persisted linear/step/monotone joins apply to Line and
  Area fill/outline via `encode.enter.interpolate`; gaps/markers/selection/data
  remain. Emulator line spec/persistence/render and area spec checks pass;
  visual shape/gap checks were not run.
- **Line dash style.** Persisted solid/dashed/dotted strokes apply to Line marks
  and Area outlines; width/interpolation/markers/selection remain. Emulator
  line/area spec, persistence and solid/dashed/dotted render checks pass; visual
  dash/cap checks were not run.
- **Line opacity.** Optional 0.1–1.0 paint opacity applies to Line marks and
  Area fill/outline; markers/selection remain opaque. Emulator line/area spec,
  persistence and render checks pass; visual contrast checks were not run.
- **Legend mark sample.** Persisted marker/default or line swatch; line swatches
  mirror width/dash (not dotted round caps) and leave plot marks, data and
  selection unchanged. Emulator spec/persistence/render checks pass; visual
  spacing/hit-target checks were not run.
- **Marker opacity.** Optional 0.1–1.0 opacity applies to Line/Area point symbols
  independently of line/area paint; selected overlays/hit targets stay opaque.
  Emulator spec/persistence/render checks pass; visual contrast checks were not
  run.
- **Table dividers.** Persisted row (default on) and column (default off)
  divider toggles affect result-table borders. The shared divider fix also makes
  previously invisible horizontal rules visible app-wide; pre-fix screenshots
  are stale. Emulator persistence/table-selection pass; visual alignment/scroll
  checks were not run.
- **Legend swatch opacity.** Legend marker/line samples mirror their respective
  marker/line opacity without changing defaults or plot/data. Emulator spec/
  persistence/render checks pass; visual contrast checks were not run.
- **Table divider width/contrast.** Optional 0.5–3.0 dp thickness and
  divider/outline contrast apply to header/body/column rules; persistence and
  table-selection pass; visual alignment/contrast checks were not run.
- **Table header divider.** Persisted header-rule toggle defaults on and shares
  width/contrast; persistence/table-selection pass; visual checks were not run.
- **Categorical palette.** Generated Cloudy/color-blind-tested palettes with a
  shared resolver, first-appearance cycle mapping, persisted selection, token
  generator/contrast checks, simulated CVD distance tests and a two-theme
  eight-series emulator render; visual/user CVD checks were not run.
- **Color-series bars.** A persisted Color field produces vertical or horizontal
  grouped, stacked or 100%-stacked segments with builder-owned bases, a complete
  automatic value domain, a category legend and source-row/filter identity.
  Stacking is gated to Sum/Count; 100% mode normalizes positive totals, uses a
  0–1 percent axis and rejects negative/zero-total categories. Query, spec,
  renderer and persistence checks pass; the native fallback refuses non-vertical
  or color-series shapes and visual/negative checks were not run.
- **Area series and stacking.** Area accepts Color with overlay, stacked or 100%
  layout, builder-owned bases, display-Y domain, fill legend, exact values and
  hidden-series support. Negative/zero-total stacked cases are diagnosed;
  emulator spec/render/persistence checks pass; visual/gap checks were not run.
- **Secondary bar+line axis (CUST09.1/2/3).** A vertical Bar can carry one
  persisted second measure as a typed LINE layer with a right `y2` axis, shared
  grouped-row identity, independent/synchronized domain, independent aggregation,
  secondary legend/markers/labels, independent number/percent/currency/unit
  formatting, interactive legend toggling, table/PDF column and settings. Query/
  spec/render/persistence checks pass; arbitrary layers, manual secondary bounds,
  color-series combination, horizontal dual axes and visual checks remain absent.
- **Currency display.** Persisted USD/EUR/GBP/JPY formatting applies fixed
  two-decimal grouped values/symbols (matching U+2212 negatives) to axis and
  labels; exact values/tooltips/data remain. Emulator negative-render,
  spec/persistence checks pass; visual/locale checks were not run.
- **Marker border.** Optional theme-text-colored stroke with 0.5–3 dp width
  applies to Line/Area symbols; defaults/other marker encodings unchanged.
  Emulator spec/persistence/render checks pass; visual contrast checks were not
  run.
- Earlier chart/customization captures remain leader-inspected only. The S03.2
  shared-title empty-workbook capture now has independent bounded acceptance;
  keyboard traversal, IME, TalkBack, focus return, shortcuts, density modes and
  light/dark coverage still require their own runs.
- Rename, new worksheet/local tabs and real replacement import now have bounded
  S03.2 native cases and S03.3 typed-add/ViewModel admission cases. Their complete
  dialog/pointer/keyboard workflow acceptance,
  favorites-toggle UI and full non-SUM aggregate conformance remain unverified.
- The worksheet Cancel action's UI wiring is not exercised against a slow query;
  only the native interrupt and query-generation invalidation code exist.
- The shared coordinator now rejects stale revisions and serializes same-view
  edits, imports and snapshot reads in one app process (14/14 native tests).
  Rename/chart/add IDs and atomic durable history now have 12 journal tests, 14
  worksheet/admission tests, 5 actual writer-death cases and 6 recovery tests.
  Complete typed command coverage, Room integration, rejected-proposal review/
  reapply, cross-process ownership and whole-product
  process restoration remain open. Conflict-banner visual/input review remains
  unverified. History50/receipts4096/metadata32MiB bounds are explicit; receipt
  capacity refuses new typed commands instead of silently evicting IDs.
- New retry/admission error messages are verified as native ViewModel states;
  their banner pixels, short-window wrapping, keyboard/pointer Retry and TalkBack
  remain implemented_unverified. Existing banner components/layout are reused.
- Missing/corrupt canonical metadata and permission-denied cleanup preserve
  metadata temporary candidates. Validated recovery removes only known abandoned
  direct regular files; no automatic promotion or manual recovery is implemented.
- The chart's first-appearance category colors can change when result order or filters change;
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
- **Current device state:** the physical API37 Googlebook (`C1L63708PZ`) remains
  on owner hold and was not interacted with. Existing API36 `emulator-5580` was
  reused for S03.2/S03.3 without boot/resize/density changes. A first dual-ABI APK
  install failed with insufficient storage (594 MiB free); an explicit x86_64-only
  qualification package installed and passed. Default builds still include both
  ABIs. The absent rootable `emulator-5582` was not restarted or reprobed.

## Not implemented / blocked

- S01.6 Arrow IPC, S01.8 million-row virtualized grid and S01.11 16 KiB runtime
  remain unimplemented/unqualified. S01.10 is partial: two same-workbook native
  Activity tasks share edits/undo and survive closing one on the API36 emulator;
  full three-view/workbook, placement/conflict and physical-input acceptance
  remains open. S01.4 has a native interrupt probe and UI action but no
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
- Query service process, Arrow transport, Room integration and the remaining
  fully typed command catalog,
  cross-process coordination and full conflict-resolution workflow, formula
  parser, LOD/filters, relationships, totals, table calculations, dashboards,
  connectors, complete portable/output workflows and Tableau interoperability.
  Bounded shared undo/redo history now persists in the same atomic metadata
  checkpoint. Full source-version history and all command kinds remain partial.
- 16 KiB runtime device and licensed Tableau reference are unavailable.
- Original tokens/generators/platform Rule 0 were located in the sibling Cloudy
  Workspace and hash-bound on 2026-10-20. Common numeric tokens match 74/74 checks.
  The original source itself says proposed; adoption and native design acceptance
  remain separate. Cloudy Data tokens remain the single provisional source.
- S11.C0 remains partial; S13.C0 and S15.C0 are absent. Their worker-backed
  dependency rules remain mandatory; none is promoted by this coordinator slice.

## Last verification (2026-10-20)

- Combined x86 and default dual-ABI debug/test build and JVM 75/75 pass; lint
  0 errors/6 warnings. Existing ChromeOsAbiSupport warning remains visible;
  default ZIP contains arm64-v8a+x86_64. No checks/assertions were disabled.
- Existing API36 emulator-5580 first combined native run 77/77, no skips, 20.406s:
  AddWorksheet/admission14, new add deaths2, metadata recovery6, journal12,
  prior journal deaths3, coordinator14, engine/persistence/query regressions26.
- Runtime APK `62b045ae5ec0e52e68979285a95aa0cdab44183f8dd0b1bdc4e0f679164b2e03`;
  test APK `d0d8f8353fa04a72275e83a3765001f99ee3a3d1d4a8a4d7e6ee213c0beffdbf`.
- New add writer PIDs24232(TEMP_SYNCED)/24249(REPLACED) actually died, observed
  from PID24199 via Binder. Prior chart/recovery deaths also passed at
  PIDs24274/24290/24306. Exact definitions, stored default, receipts and durable
  undo/reopen/redo passed; duplicate-after-undo did not recreate the sheet.
- Real move-time EACCES, post-replacement acknowledgement loss, staged native CSV,
  blocked queued Add/no-op/import, invalid→corrected request and queued Apply→
  Discard all have independent exact oracles. Tokens current; contrast 40/40.
- Independent source/test review resolved failure-barrier, import admission,
  terminal-validation and stale pending-operation findings before this run.
  No new native pixel/input acceptance is claimed for the failure-banner text.
- Current logs: qualification/evidence/android-test-results/2026-10-20-s033-add-worksheet/;
  source/artifact manifest: qualification/evidence/artifacts/2026-10-20-s033-add-worksheet/.
  Physical Googlebook,16KiB,Tableau,power-loss,cross-process gates remain open/blocked.

## Next exact task

**S03.3 typed SetFavorite(desiredValue) and retry-safe Home intent.**

- IDs: F01/F64, Q17/Q19/Q34; bounded Q21 Home-action checks. Prerequisites:
  verified typed receipts/checkpoint, durable history, shared coordinator and
  native failure admission. No new engine or worker capability is needed.
- Paths: model/WorkbookCommand.kt, data/WorkbookCoordinator.kt, ui/HomeScreen.kt
  or a narrow Home command owner, disjoint focused native tests, evidence/state.
- Action: replace Home's generic replayable toggle with an explicit desired
  Boolean and frozen command/workbook/window/expected-revision identity. Keep
  the same exact request across acknowledgement loss/retry, expose pending/error
  truthfully, and reload the actual accepted library state. Preserve other views,
  workbooks, worksheets, snapshot and stored default.
- Independent oracle: false→true accepts once/one receipt; duplicate after rename
  returns current document without writing; changed desired value under the same
  ID is refused; undo/reopen/duplicate leaves false and redo alone restores true.
  No-op acceptance adds no history step. Exercise the actual native Home star and
  Favorites filter, target semantics and nested-row non-opening; inspect affected
  native render/input, then independently review the combined revision/results.
- Room, full proposals, remaining typed mapping/aggregation/sort families and
  source-version history remain separate. S11.C0 stays partial; S13.C0/S15.C0
  remain mandatory when applicable and are not promoted by this local slice.

## Protected context

- Normal push of staged local commits to `origin/main` is authorized by the
  current owner instruction; force-push, tags/releases, branch-protection
  changes and external messages remain unauthorized.
- Preserve the planning documents' scope; do not import Cloudy Console terminal,
  RDP/X11/USB, host-vault or remote-session behavior.
- Do not add a Linux/JDBC DuckDB binary to the APK. Native artifacts must be built
  from the pinned commit by `tools/build_duckdb_android.sh`.
- The proposed disabled/chart tokens are provisional, not canonical.
