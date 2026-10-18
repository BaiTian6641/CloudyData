# Cloudy Data Implementation Roadmap and Agent Runbook

**Baseline:** 18 October 2026. **Status:** planned work. No stage is completed merely because it appears here. Read [file 01](Cloudy_Data_01_Product_and_Design_Contract.md) for scope/design and [file 02](Cloudy_Data_02_Architecture_and_Technical_Specification.md) for architecture before implementation.

This runbook converts the Tableau parity destination into bounded engineering stages. Begin with native feasibility and one working analytical path. Extend the same document, semantic and execution contracts through the larger capability catalog. Every completed stage needs inspectable source, passing relevant tests, actual behavior evidence, and an accurate remaining-gap record.

## 1. Release structure and execution policy

### 1.1 Milestones

| Release | Stages | Demonstrable outcome | Claim boundary |
| --- | --- | --- | --- |
| R0 Feasibility | S00–S01 | Reproducible native platform/engine/renderer/storage probes | A technical decision, not a finished analysis application. |
| R1 Working authoring slice | S02–S06 | Local import → exact summary → editable worksheet → save/reopen/export preview, with native desktop controls | Core subset; relationships/advanced calculations/dashboard parity remain incomplete. |
| R2 Analytical authoring beta | S07–S13 | Multi-table modeling, LOD/table calculations, dashboards, fuller charts, geography and analytics as verified | A capability-qualified beta, not blanket Desktop parity. |
| R3 Desktop candidate | S14–S19 | Preparation, reference connector inventory, stories/outputs, declared migration support, optional worker and release hardening | Desktop parity is claimed only for the fully passing reference inventory; blocked connectors/functions remain visible. |
| R4 Shared analysis platform | S20–S21 | Publishing, governed services, scheduling/collaboration and configured intelligence/extensions | Separate server/platform acceptance; does not change offline APK requirements. |
| R5 Parity review and maintenance baseline | S22 | Independent requirement-by-requirement review, signed release evidence and supported-capability report | Distinct desktop, service and interoperability scores with no silent omissions. |

This is a substantial multi-release software program. Use gate completion and measured velocity for scheduling. Do not promise a calendar completion date or infer that one agent session can implement all Tableau behavior. Work can ship incrementally while the parity destination remains intact.

### 1.2 Stage map and dependency order

| Stage | Name | Required predecessors | Primary feature ownership |
| --- | --- | --- | --- |
| S00 | Repository, scope and evidence baseline | None | All catalogs and version decisions. |
| S01 | Native feasibility and failure probes | S00 | Engine/platform/renderer/storage decision gates. |
| S02 | Cloudy design system and desktop shell | S01 platform decision | DATA screens' common shell and interaction. |
| S03 | Workbook authority, history and persistence | S00, S01 storage decision | F01, F61, F64 foundation. |
| S04 | Query service, import and snapshots | S01, S03 | F02–F07 local foundation. |
| S05 | Fields, types and basic semantic compiler | S03, S04 | F04/F05, F15–F17, F25 foundation. |
| S06 | Real worksheet authoring slice | S02, S05 | F31–F41 core subset. |
| S07 | Relationships, physical models and blending | S05, S06 | F09–F13. |
| S08 | Formula language, LOD and filter stages | S05, S07 | F17–F25. |
| S09 | Table calculations and analytical controls | S08 | F15, F26–F30, F35/F37. |
| S10 | Dashboard composition and actions | S06, S09 | F42–F46, F48. |
| S11 | Complete chart grammar and formatting | S06, S09 | F31–F41, F52 visual controls. |
| S12 | Geography and spatial analysis | S07, S11.C0; S18 support only for delegated operators | F49–F51, spatial F19. |
| S13 | Statistical and predictive analysis | S08, S09, S11.C0; S18 support only for delegated operators | F52–F56, analytical F19. |
| S14 | Preparation, schema evolution and refresh | S04, S07, S08 | F03/F05/F07/F08/F10/F14. |
| S15 | Connector catalog and secure gateway | S04, S07, S14 | F02/F06/F07/F09/F13 and connector inventory. |
| S16 | Stories, portable output and printing | S03, S10, S11 | F01/F47/F58–F61. |
| S17 | Tableau migration and Hyper adapters | S08–S11, S16 | F62/F63; separate compatibility track. |
| S18 | Optional compute and analytical services | S13.C0, S15.C0 | F57, extended F19/F63. |
| S19 | Desktop reliability, performance and release | Applicable S02–S18 capabilities | Full native and numerical acceptance of R3. |
| S20 | Governed collaboration platform | Stable core contracts, S15/S16/S19 | F65–F71. |
| S21 | Isolated extensions and configured intelligence | S10, S18, S20 | F46/F70/F72. |
| S22 | Independent parity closure and maintenance | All applicable stages | Final capability claims and support policy. |

Independent work can overlap after contracts stabilize: S02 UI and S03 persistence; S10 dashboards and S11 grammar; S12 spatial, S13 statistics and S14 preparation; connector adapters within S15; local output and compatible service work. A stage may prototype against a typed test double, but cannot pass its end-to-end gate on that double.

Use these explicit prerequisite sub-gates to avoid circular dependencies:

| Sub-gate | What can be independently verified | What it unlocks without claiming full stage completion |
| --- | --- | --- |
| S11.C0 | Typed MarkTable/ChartSpec geometry, layers, axes, native renderer protocol and reference-analysis controls | S12/S13 computation work before box/density/statistical marks finish their numerical acceptance. |
| S13.C0 | AnalysisSpec/input-output schema, versioning, operator catalog and independent conformance fixtures | S18 transport/isolation implementation before worker-backed statistical operators can execute. |
| S15.C0 | Connector SPI, authenticated gateway transport, capability negotiation, policies and a real basic connector/job fixture | S18 worker support before every driver, Hyper/published-source adapter or service capability closes. |

If native spatial/statistical work needs a worker, verify S11.C0 and S13.C0, then build S14 → S15.C0 → S18's required execution support before closing those S12/S13 operators. Finish S11's dependent statistical marks afterward. S15's remaining interoperability adapters can close after S17/S18; they do not block S15.C0. Stage numbers organize ownership and default sequence, not an instruction to wait for a consumer before implementing its provider.

### 1.3 Universal stage contract

Every stage entry has inputs, bounded implementation units, acceptance and a stop rule. Before coding, expand its units into tickets with exact writable paths, source revision, relevant F/Q IDs, expected behavior and evidence. New code starts with the smallest working vertical slice; add complexity only after that behavior is correct.

Stage status is `not_started`, `in_progress`, `implemented_unverified`, `verified`, `partial`, or `blocked`. A dependency requires verified evidence for the named stage or explicit sub-gate. A verified sub-gate may unlock its dependent work while its owning stage remains partial; it never promotes unfinished feature rows or the whole stage to verified. If missing hardware or reference access blocks a specific gate, keep it blocked and continue genuinely independent work; do not relabel host tests or screenshots as hardware evidence.

## 2. Foundation stages

### S00 Repository, scope and evidence baseline

**Inputs:** the three planning files, all supplied Cloudy documents, the actual repository if one exists, and available target-device information.

**Implementation units:**

1. Inspect repository state, instructions, current branch, uncommitted changes and existing modules/tests. Preserve user work. If this is a new repository, establish its minimal structure and documented build entrypoints; do not import an unrelated Cloudy Console tree wholesale.
2. Locate canonical `tokens.json`, generators, font/icon assets and selected toolchain. Record their real paths and commits. If absent, establish the explicitly provisional baseline described in file 01 and list missing roles.
3. Freeze the reference Tableau release/edition/OS and enabled-service profile. Expand F01–F72 into atomic feature, function and connector catalogs with applicability, official source and owner stage. Record uncertain or conflicting reference behavior.
4. Record actual Googlebook device/build/API/ABI/page-size/WebView/input profiles. If hardware is not available, record it as an unqualified target and use the documented emulator for limited development evidence.
5. Select and pin Kotlin, Gradle/AGP, Compose/AndroidX, SDK/NDK/CMake, DuckDB commit, JavaScript/renderer and native dependency versions. Use current stable releases where suitable; any preview/experimental dependency requires a recorded necessity and qualification.
6. Create execution state, ADR log, risk log, parity catalogs and evidence schema. Record commands actually available in this repository; never copy imaginary task names into a pass report.
7. Add a minimal compile/lint/domain-test CI path and dependency/secret hygiene. Package/release permissions remain separate from ordinary build permissions.

**Deliverables:** `docs/execution-state.md`, `docs/decisions/`, `parity/{feature,function,connector}-catalog.yaml`, `qualification/platform-profiles/`, toolchain lockfiles and a buildable minimum target.

**Acceptance:** a fresh checkout can run the documented initial build; every feature family has an owner; each dependency has a pinned source/license; missing token/device/reference inputs are visible. Review the baseline with another agent before implementation broadens.

**Stop rule:** unresolved product naming or optional font preference does not block engine work. Unknown repository history does require inspection before modifying it. No full parity claim can be based on an unexpanded catalog.

### S01 Native feasibility and failure probes

**Inputs:** S00 lockfiles, platform profile, tiny numerical fixtures and a minimal Android harness. **Decision owner:** architecture/engine leader, with independent Android review.

Implement each probe as a small runnable target with captured commands and outputs. Use synthetic data. These are real experiments, not simulated UI states.

| Unit | Experiment | Pass evidence |
| --- | --- | --- |
| S01.1 | Native Compose desktop window: resize/tile, caption/insets, new instance, external display, physical input and IME | Actual observed bounds, event behavior and device/build; identify APIs unavailable on the target. |
| S01.2 | NDK DuckDB build for arm64-v8a and x86_64, pinned baseline extensions | Reproducible binaries; actual Android open/import/query/reopen, with ABI-specific results. |
| S01.3 | D1/D2-like grouping/sort/join/window workloads under constrained memory | Correct oracle checks, wall time, peak total memory, spill/disk, thermal/cache conditions and graceful refusal. |
| S01.4 | Cancel a running native query; kill worker mid-read and mid-import | UI remains usable; cancelled/interrupted state, cleaned resources, accepted old data retained. |
| S01.5 | Metadata/data receipt recovery at each commit boundary | Last valid workbook/snapshot restored; no duplicate import or false completed save. |
| S01.6 | Arrow IPC through descriptors with wide strings, Int64, Decimal and timestamps | Exact round-trip values, bounded buffers, cancellation/backpressure, no oversized Binder transactions. |
| S01.7 | Offline Vega/interpreter in the actual WebView | No CDN/network requests, native keyboard/focus/selection, accurate types, resize/export, strict origin/CSP behavior. |
| S01.8 | Native grid with 1 million logical rows and wide columns | Viewport virtualization, range selection, keyboard traversal, exact copy and bounded memory. |
| S01.9 | SAF nonseekable input, revoked grant, interrupted external save | Owned staging, explicit permission/save state, recoverable artifact and no guessed file paths. |
| S01.10 | Two windows of one workbook plus an independent workbook | One writable data owner; no cross-window source/result or lost-edit confusion. |
| S01.11 | 4 KiB and 16 KiB package/runtime qualification, both ABIs | All `.so` dependencies and final package alignment checked; actual runtime support recorded separately from cross-compilation. |
| S01.12 | Wasm or isolated-process fallback only if it resolves a concrete failed gate | Measured OPFS/worker/isolation or brokered-filesystem capability; decision explicitly states its limitations. |

**Deliverables:** probe code/scripts, data hashes, native artifacts, logs, inspected screenshots, measurement tables and ADRs selecting the engine/renderer/storage boundary.

**Acceptance:** the preferred architecture has an evidenced path for local import → exact query → native result surface → durable reopen, including interruption. A compile alone passes no execution gate.

**Stop rule:** after two attempts yielding the same failure without new evidence, document competing hypotheses and run one discriminating probe. If no viable path remains, record the exact blocked requirement and evaluate the stated alternative. Do not spend repeated cycles restyling a shell while the native engine/storage gate is unresolved.

### S02 Cloudy design system and desktop shell

**Inputs:** qualified platform route, CD02–CD15 and the canonical/provisional token source.

1. Build token generation to Compose and renderer theme outputs from one source. Bind actual Material consumers, including selected/disabled/focus/error states, typography/weights, shape and the three density tiers.
2. Implement DATA-01 plus reusable menu bar/task toolbar, panes, inspector, tabs, status strip, palette, dialogs and split dividers. Start with one real document/open command when S03 is available; label development fixtures explicitly.
3. Apply current-window constraints, caption/system/IME insets, short-window scrolling and explicit System/Comfortable defaults. Persist owning-app presentation preferences with honest save outcomes.
4. Establish one input router, focus traversal/return, ordinary copy/paste/context menus and keyboard alternatives to pane resizing. Conflict-test shortcut candidates on hardware.
5. Add new-window and document-instance routes with stable IDs. Keep shell chrome distinct from saved dashboard geometry.

**Acceptance:** DATA-01/common controls compile and render natively in light/dark, all densities, wide/narrow/short windows and large text; actual keyboard, mouse/trackpad, IME and TalkBack checks cover changed controls. Confirm that selection does not copy to clipboard and right-click opens a menu. Independent reviewer inspects final captures.

**Stop rule:** missing advanced renderer features do not justify replacing native menus/files/settings with a generic web wrapper. Missing tokens stay proposed until reviewed; do not use a second hand-edited palette.

### S03 Workbook authority, history and persistence

**Inputs:** typed model proposal, S01 persistence decision, DATA-01/open/save interactions.

1. Implement core entities with stable IDs, versioned schema and immutable revisions; define view-local versus persistent state.
2. Create the single WorkbookCoordinator, app document registry, expected-revision command validation and observable state for multiple Activities.
3. Implement command history, checkpoints, undo/redo and native formula-draft isolation. Keep accepted commands durable before reporting them saved.
4. Implement workbook package validation and local autosave; record external save as a separate provider operation. Preserve originals on migrations and future-version errors.
5. Implement operation receipts/recovery for the chosen data-authority arrangement. External snapshot files need their own durable finalization; DuckDB COMMIT is not a filesystem transaction.
6. Implement duplicate/open-in-new-window/close-view behavior. Closing a sheet view does not delete the sheet. Conflicting persistent edits return an explicit conflict.

**Acceptance:** Q16–Q19/Q34 scenarios as relevant: kill/reopen at journal boundaries; two windows edit one workbook without lost updates; undo/save/reopen preserves definition and references; malformed/too-new packages fail safely; provider failure retains local work.

**Stop rule:** do not move workbook content into SavedStateHandle or a WebView local-storage blob to avoid implementing durability. There must be one declared document authority.

### S04 Query service, local import and snapshots

**Inputs:** S01 native adapter/IPC results, S03 operation state machine and file broker.

1. Build non-exported query service, one-writer registry, request/revision envelopes, query states, deadlines and idempotent cancellation/release.
2. Implement bounded Arrow streams, progress facts, buffer leases and Binder-death/truncated-stream handling. Add native allocation and spill accounting from the first real queries.
3. Implement CSV/TSV and Parquet first, then JSON/NDJSON and the qualified Excel/SQLite/Arrow paths. Use import option/preview UI and explicit parser errors.
4. Stage sources through SAF, validate schema/types, write a new snapshot and publish it through the recorded receipt protocol. Preserve original input provenance and rejected-row counts.
5. Implement a basic full refresh to a new snapshot, with current/previous versions and cancellation. Keep incremental policies for S14.
6. Surface real DATA-02/DATA-03/DATA-11 state: preview sample, source size unknown, import phases, permission loss, failed/unknown completion and recovery.

**Acceptance:** Q01–Q03/Q17/Q18/Q23; exact values survive import/query/Arrow/grid/reopen, no giant JSON/Binder payload, cancel and worker death preserve accepted data, no unsupported parser reports success.

**Stop rule:** a missing Excel/spatial/native extension becomes a named capability gap or qualified alternate parser. It is not a reason to load Linux binaries or silently upload files.

### S05 Fields, types and basic semantic compiler

**Inputs:** stable snapshots, engine capabilities, single-table fixtures and field-role reference. [T07, T08, T13]

1. Implement stable field catalog with independent physical type, logical type, dimension/measure role, aggregation and discrete/continuous placement mode.
2. Implement typed query/MarkTable IR for a single logical table, grouping, core aggregates, row/disaggregate mode, NULLs, sorting and limited scalar expressions.
3. Add exact Decimal/Int64 transfer/display, locale/timezone/date-part versus date-value behavior, aliases and source-qualified duplicate names.
4. Define the function manifest and strict unsupported-function errors. Bind parameters and quote internal identifiers; no widget-specific handwritten SQL.
5. Add result stamps/caching so resizing/theme changes do not rerun data and old query completions cannot repaint current state.

**Acceptance:** Q02/Q09/Q10/Q14–Q16; numeric dimensions and discrete measures both work; raw/exact values differ from display rounding only as specified; aggregate results match independent fixtures.

**Stop rule:** no chart-specific calculation shortcuts may bypass the compiler. Invalid/unimplemented expressions fail visibly instead of evaluating approximately.

### S06 Real worksheet authoring slice

**Inputs:** S02 native shell, S05 semantics and bounded chart host.

1. Implement DATA-05: data pane, Rows/Columns/Filters and initial marks controls, distinct placement IDs and ordinary field/context menus.
2. Support bar, line, scatter, text/crosstab and highlight/heat views, with axis titles, legends, labels and exact native data inspection.
3. Add drag and equivalent keyboard commands, swap/reorder/duplicate placements, aggregate/disaggregate, Measure Names/Measure Values foundation, sort and one-step gesture undo.
4. Bind typed MarkTables to the offline renderer; route selection/context-menu intents to the coordinator with source revision checks.
5. Implement chart recommendation as rules over types/roles/cardinality, returning an editable worksheet specification.
6. Demonstrate the R1 journey with an actual local dataset and saved workbook: import, sum by category, filter, inspect, modify, undo, reopen and produce a verified visual export preview.

**Acceptance:** Q01/Q02/Q10/Q16/Q21/Q24/Q34; full native journey by keyboard and pointer, correct totals after reopen, stable focus, no fake data/query success and no network dependency.

**Stop rule:** R1 is a working subset. Do not call it Tableau parity or fill remaining chart/gallery entries with nonfunctional controls.

## 3. Analytical authoring stages

### S07 Relationships, physical models and blending

**Inputs:** typed semantic core, independent relationship fixtures and the pinned Tableau data-model behavior. [T01, T02, T09]

1. Implement physical join/union ASTs and DATA-04 physical-model editor, with typed conditions and intentional row multiplication.
2. Implement distinct logical tables/relationships, field provenance, model validation and conservative cardinality/integrity assumptions.
3. Implement query-grain analysis and aggregation/combination that preserves independent measures. Test unmatched keys and many-to-many cases before adding optimizer shortcuts.
4. Add multiple base facts/shared dimensions, explicit ambiguous/unrelated-field diagnostics and safe query planning for each cataloged model pattern.
5. Add worksheet blending as separately aggregated primary/secondary sources. When a remote adapter is missing, use a local fixture implementation but retain remote qualification for S15.
6. Provide model impact preview, undo and source-qualified error explanations; update dependent sheets coherently.

**Acceptance:** Q04/Q05, cross-table filter/LOD preparation cases, intentional physical-join fan-out versus preserved relationship totals, primary-domain blending and exact model save/reopen. Review generated plans, not only query text or screenshots.

**Stop rule:** a flattened join cannot stand in for a relationship model. Unsupported model paths remain explicit errors and parity gaps.

### S08 Formula language, LOD and filter stages

**Inputs:** S07 grain-aware IR, function catalog and current calculation/filter references. [T03–T06, T13]

1. Implement lexer/parser, typed expression AST, stable field binding, aggregate-level validation, dependency cycles and resource limits.
2. Build DATA-06 with syntax ranges, completion, function help, dependency/impact display, labeled sample preview and explicit Apply/Cancel.
3. Implement scalar/aggregate catalog in bounded families: numeric/logical; string/conversion; dates/time; aggregates/NULL/error behavior. Every function requires an independent fixture before promotion.
4. Implement filter scope/stage nodes, context filtering, top/condition/set interactions, relative/latest-date policies and backend predicate-movement barriers.
5. Implement FIXED, INCLUDE, EXCLUDE, table-scoped and nested LOD with explicit reaggregation and related-table rules. Validate compiler plans independently.
6. Add native diagnostics for unsupported function/source semantics and safe source SQL compilation. Formula text is not SQL string interpolation.

**Acceptance:** Q06/Q07/Q10/Q14/Q15 plus per-function catalog cases; ordinary versus context filter changes the required LOD denominator; aggregate/nonaggregate misuse is rejected; no formula/IME text loss.

**Stop rule:** functions absent from the verified manifest remain unsupported. Similar-looking output or an unrelated aggregation does not count as LOD completion.

### S09 Table calculations and analytical controls

**Inputs:** S08 staged plan, ordered MarkTable and total/domain contracts. [T10–T13]

1. Implement address/partition definitions, order/tie rules, frame/restart and nested table-calculation dependency evaluation.
2. Add running totals, differences, percent difference/total, rank variants, INDEX/LOOKUP, moving/window families and every remaining applicable function in small verified groups.
3. Implement explicit domain completion, missing versus NULL semantics, bins and empty row/column rules.
4. Implement automatic and supported two-pass grand/subtotals, distinct-count and ratio cases; totals use their own plan nodes.
5. Add groups, hierarchies, static/dynamic/combined sets, typed parameters/allowed values and dynamic parameter refresh behavior.
6. Build the native computation-inspection table and controls for addressing, partitioning, nested sort/restart, parameter/filter scope and relevant values.

**Acceptance:** Q08–Q13/Q34; preserve running sums when a post-calculation filter hides earlier marks; totals differ correctly from averages/sums of displayed aggregates; parameter/set state survives reopen.

**Stop rule:** a generic SQL window implementation without mark-domain and nesting behavior cannot close this stage.

### S10 Dashboard composition and actions

**Inputs:** native worksheet engine, S09 parameters/sets, action reference and object/layout model. [T14, T27, T28]

1. Implement DATA-07 object tree with nested tiled containers, floating objects, align/distribute, padding, borders, z-order, resize constraints and undo.
2. Preserve fixed/range/automatic document size independently of Android authoring bounds. Add desktop/tablet/phone output-layout authoring and previews within the desktop workbench.
3. Add worksheet instances, legends, filter/parameter controls, text/images and navigation/export objects. External web objects stay isolated until their S21 security gate.
4. Implement parameter, set, filter, navigation, highlight and URL action types, trigger/field mapping, clear-selection behavior and pinned ordering. Prevent action cycles and consolidate one user gesture into a coherent revision.
5. Add dynamic zone visibility/show-hide and outline-based keyboard movement/resize. Hiding content does not enforce data policy.
6. Implement DATA-09 separate presentation window, explicit fullscreen/return and stable input/focus ownership.

**Acceptance:** Q16/Q20/Q21/Q34; selection updates all dependent sheets coherently, clear behavior and same-type action order match the catalog, no late old result, keyboard-only object editing, save/reopen keeps geometry and action state.

**Stop rule:** independently mutating filters inside several renderers is not a dashboard action implementation. Fixed dashboard geometry must not change when app chrome reflows.

### S11 Complete chart grammar and formatting

**Inputs:** stable MarkTable/ChartSpec, S09 computation and current chart/mark reference catalog. [T15–T18, O07, O08]

First verify S11.C0 as defined in the dependency table. Box/density/statistical visual acceptance can remain partial until its S13 numerical provider passes; this does not prevent S13 from starting.

1. Close every remaining mark/channel and chart-family row: stacking/normalization, area, treemap, pie, bubbles, histogram, Gantt, bullet, box/density when analytical operators are ready, and composed views.
2. Implement multiple layers, dual axes/synchronization, Measure Names/Measure Values, independent measure defaults, nested headers/facets and per-pane scale rules.
3. Add axis limits, log/reversal, ticks/units, number/date formatting, labels, legends, annotations and reference-line/band controls.
4. Complete Pages stepping/playback, history/trails and persisted playback settings with reduced-motion behavior. A visible Pages shelf alone is insufficient.
5. Add bounded viz-in-tooltip, explicit exact-versus-formatted values, rectangular/lasso selection where appropriate and native equivalents.
6. Extend recommendations only to valid editable grammar combinations; use one renderer unless a concrete capability needs a reviewed alternate backend.

**Acceptance:** per-chart composition fixtures, Q02/Q21/Q24, multiple placements of the same field with different aggregation/date levels, dual-axis correctness, long labels, negative/log-domain errors and stable category colors in both appearances.

**Stop rule:** screenshots of disconnected chart examples do not pass grammar, selection, export or saved-workbook behavior.

### S12 Geography and spatial analysis

**Inputs:** S11 layers, spatial-source catalog and native-extension feasibility. [T19, T35, P05]

1. Implement coordinate roles and offline symbol maps with explicit CRS/axis-order/units.
2. Add qualified geometry imports and validation, filled maps, multi-layer paths/polygons, extents and selection. Complete multi-file source packages such as Shapefile components.
3. Implement geographic role recognition, ambiguous/unknown location UI and custom geocoding; track the provenance/version of bundled reference data.
4. Qualify the chosen spatial functions, projections, joins and distance/area semantics against independent fixtures.
5. Add optional online map resources through an approved provider with attribution, cache/offline policy and explicit network behavior. Test renderer fallback when WebGL or a provider is unavailable.
6. Validate offline save/reopen/export, antimeridian/poles, multipart polygons/holes, null/invalid geometry and a missing base map.

**Acceptance:** cataloged geometry/spatial cases plus Q24/Q25; plotting lat/lon is scored separately from geocoding, spatial computation and base maps. Spatial/PROJ/GDAL build, ABI, package and license evidence is attached to the exact capability.

**Stop rule:** unqualified Android spatial support uses a named optional worker route or remains blocked. It cannot be hidden behind a map-looking canvas.

### S13 Statistical and predictive analysis

**Inputs:** typed calculations, grouping/domain state, S11.C0, statistical catalog and independent reference harness. [T18, T20, T21, T33]

First verify S13.C0's AnalysisSpec/schema/catalog/fixture contract. It unlocks worker support without implying that any numerical operator has already passed. Delegated operator acceptance waits for the corresponding S18 execution capability.

1. Implement descriptive statistics and distributions with explicit sample/population, quantile, NULL and weight conventions.
2. Add trend model families and reference analysis, with coefficients, diagnostics, domain restrictions and intervals.
3. Implement the pinned exponential-smoothing forecasting families/options, model selection, seasonality, missing-period rules, horizon and interval methods; retain model/version diagnostics.
4. Implement pinned clustering behavior, including initialization, normalization, stopping, automatic cluster-count selection and unclustered/null cases. A seeded generic k-means is only a preliminary result.
5. Implement MODEL_PERCENTILE/MODEL_QUANTILE and applicable Explain Data diagnostics as individually cataloged capabilities. Generic narrative/lineage is not a substitute.
6. Expose native analytical settings, model descriptions, assumption/error states and data lineage. Record execution location for any operator using an external service.

**Acceptance:** Q28 and independent analytic fixtures with predeclared tolerances, deterministic cases and actual reference comparison where available. Save/reopen reproduces the selected algorithm/specification, not necessarily a fresh nondeterministic fit. Approximate/equivalent behavior is labeled until exact parity criteria pass.

**Stop rule:** do not tune tolerances to hide unexplained numerical differences or present an attractive fitted curve as a validated model.

## 4. Complete desktop workflow stages

### S14 Preparation, schema evolution and refresh

**Inputs:** source/snapshot authority, physical models, formula compiler and transform graph.

1. Build DATA-10 as a transform-step table plus optional graph: select/rename/cast/split/replace/trim/filter/pivot/unpivot/aggregate/deduplicate/join/union.
2. Each step declares schema, parameters, deterministic behavior, row-impact counts and labeled sample preview; accepted full output gets a new snapshot.
3. Add source replacement and schema-change impact analysis for fields, calculations, relationships, filters, dashboards and saved outputs. Preserve stable field identity through deliberate mappings.
4. Implement append-only and keyed incremental refresh as different modes, including tied watermarks, late records, updates/deletes, deduplication and schema drift.
5. Advance watermark/receipt only with an accepted snapshot. Recover interrupted refresh without duplicate rows or lost previous versions.
6. Add suitable deferred-job integration and visible pause/resume behavior. Local schedules remain best-effort; managed service scheduling belongs to S20.

**Acceptance:** Q03/Q17/Q18/Q26/Q33; independently check row counts/schema/values after each transform, cancellation and replay recovery; failed refresh keeps the old accepted data and its timestamp.

**Stop rule:** basic cleaning functions are not complete Prep parity. Expand applicable Prep-style requirements into named remaining nodes/workflows and keep them in the service/preparation catalog.

### S15 Connector catalog and secure gateway

**Inputs:** expanded S00 connector manifest, type/pushdown contracts, credentials model and S14 refresh.

Verify S15.C0's SPI/authenticated transport/capability/policy and real basic-connector fixture early. Later driver and interoperability rows remain partial independently; this lets S18 supply capabilities they require.

1. Implement a reusable connector SPI with discovery, auth, schema, preview, live/extract, cancellation, snapshot consistency and typed errors.
2. Complete local file formats, including applicable statistical/document/legacy formats through named qualified parsers.
3. Bring up a minimal authenticated connector service for drivers lacking Android support. Reuse protocol/semantics; do not build a new hosted dashboard application.
4. Qualify PostgreSQL/MySQL first against controlled real databases, then work through enterprise SQL, warehouses, lake/query platforms, object stores, SaaS/web and cube/published-source rows in separate adapter tickets.
5. Implement OAuth/account selection where required, TLS validation, secret storage, token expiry/revocation, pagination/rate limits and schema evolution. Credentials never enter the renderer or workbook.
6. Add safe custom SQL and supported result-producing procedure policies with explicit source permissions. Test every advertised authentication/query mode and type mapping.
7. Compare native/extract versus pushed-down results, bound remote costs/data transfer, and declare source consistency. Worker fallback always shows execution location and data scope.

**Acceptance:** Q15/Q22/Q25/Q26/Q29 as applicable; each connector passes real connect/discover/query/cancel/reconnect/export/refresh fixtures and its auth modes. Unavailable service/driver is an explicit unsupported state. No stub counts as connector coverage.

**Stop rule:** do not stop the inventory after popular databases or call “generic JDBC/ODBC” complete without driver/platform proof. A required missing adapter blocks full parity, not every independent connector task.

### S16 Stories, portable output and printing

**Inputs:** saved document/snapshot model, dashboards/chart grammar and output schemas. [T22, T29, T30]

1. Implement story points, captions, saved view/filter state, update/revert, duplication, reorder and presentation navigation.
2. Complete native workbook and reusable data-source packages, dependency inclusion, metadata-only/data-inclusive modes, missing-source rebind and version migration.
3. Export underlying/summary/selected data to cataloged formats with precise scope and types. Provide a safe spreadsheet-compatible CSV mode that handles formula-leading cells explicitly without silently changing raw export semantics.
4. Export PNG/SVG/PDF and qualified PowerPoint/XLSX outputs, with consistent filters, legends, fonts, page size, print pagination and source provenance.
5. Add accessible summaries/tables and independently test any tagged/accessible document claim. Identify rasterized elements or unsupported objects.
6. Implement external provider-save/publish staging and conflict recovery with frozen output identity; no success before bytes are committed as far as the provider contract establishes.

**Acceptance:** Q18/Q24/Q33/Q34; inspect files in appropriate readers, verify tables/numbers, reopen native packages, compare filter/snapshot state and confirm secret exclusion. The export scope cannot change because the user switches tabs mid-job.

**Stop rule:** an export callback or nonempty file is insufficient. Do not conflate ordinary Story points with retired natural-language Data Stories. [T40]

### S17 Tableau migration and Hyper adapters

**Inputs:** stable native workbook format, semantic/visual catalogs and a declared compatibility corpus. [T22, T36, T37]

1. Define supported reference file/build versions and a per-element mapping for `.twb/.twbx/.tds/.tdsx`, including fields, calculations, sources, sheets, layouts, actions and assets.
2. Implement safe archive/XML parsing with no external entity/resource execution. Preserve the original and list unsupported elements before the user accepts a converted copy.
3. Translate a small declared subset first, using stable native IDs and strict unsupported-formula/connector reporting. Test native save/reopen after conversion.
4. Add Hyper read/write through a qualified supported host/worker adapter when needed. Verify the exact API package/license/ABI; native Android Hyper is not an assumed capability.
5. Expand the corpus by semantic feature. Round-trip/export-to-Tableau is a separate acceptance target from one-way import.

**Acceptance:** Q27/Q33; conversion report identifies every dropped/untranslated construct; unsupported sheets remain explicitly unconverted; original data/assets survive; comparative open/render/query in the pinned Tableau build is recorded for every claimed compatibility row.

**Stop rule:** the official Document API's limited/unsupported update mechanism cannot be presented as a full file-format specification or authoring engine. Without reference access, compatibility stays unverified.

### S18 Optional compute and analytical services

**Inputs:** verified S15.C0 service transport and S13.C0 semantic/analysis contracts. Full S13/S15 completion is not a prerequisite.

1. Extend the gateway into a configured compute worker with engine/semantic/capability handshake, job ownership, deadlines, streaming and cancellation.
2. Reuse the pure semantic compiler or a strictly conformance-tested protocol; do not create a second divergent set of formula semantics.
3. Add larger-data query/extract jobs and supported Python/R analytical extension calls with typed schema, row identity, package hashes, seed and diagnostics.
4. Isolate script execution, constrain filesystem/network/resource access, reject workbook autorun, and reconcile lost acknowledgements by operation ID.
5. Provide an explicit worker installation/connection path on supported workstation/server OSes. A Googlebook VM deployment is a qualified optional endpoint with a documented route, not an invented APK launch API.
6. Verify local offline authoring still works after removing or disconnecting every worker configuration.

**Acceptance:** Q25/Q28/Q31; common native/remote deterministic operations agree, while worker-only operators use independent reference oracles; jobs authenticate/revoke/cancel/resume correctly; no cross-workbook/principal result leakage; data sent to a worker matches the selected policy. Individual numerical acceptance remains with its S12/S13/F57 requirement owner.

**Stop rule:** an unavailable native feature cannot trigger invisible upload. A worker connection is not proof of every advertised extension or driver.

### S19 Desktop reliability, performance and release

**Inputs:** all applicable desktop features and previous evidence. This is final integration hardening; individual stages already test correctness and recovery.

1. Run the full numerical/function/connector catalogs against the frozen reference scope; investigate new failures at their owning layer.
2. Measure D1/D2/D3 envelopes, cold/warm latency, UI jank, memory across UI/query/renderer, spill peak, cancel/death cleanup, thermal behavior and several open workbooks alongside ordinary apps.
3. Complete native keyboard, mouse, trackpad, IME, TalkBack, density, theme, scaling, narrow/short window, external-display and multi-instance matrices.
4. Test cold start, clean install, upgrade, migrations, corrupt source/package, revoked grants, offline operation, background/sleep, storage exhaustion and process/renderer death.
5. Verify all shipped ABIs and 4 KiB/16 KiB compatibility, native symbols, SBOM/notices, release builds, signature/update compatibility and removal of development-only stubs/secrets.
6. Produce signed APK/AAB candidates through the authorized release workflow and publish only to the explicitly authorized destination/channel. Preserve build hashes and reproducible source provenance.

**Acceptance:** no open critical data-correctness/loss/security/accessibility defects in advertised capabilities; the exact supported-device/workload matrix and remaining partial/blocked parity rows accompany the candidate. Missing actual-device evidence blocks that support claim.

**Stop rule:** do not broaden tests repeatedly after the concrete gate is sufficiently verified. Do not remove a failing parity test, weaken assertions, or mark a required adapter nonbaseline to obtain a green release.

## 5. Shared platform and parity closure

### S20 Governed collaboration platform

**Inputs:** stable document/semantic/connector/output protocols and desktop release evidence. This is an optional deployment component with required tests for every service feature claimed. [T26, T31]

1. Implement authenticated asset/version storage and publish/revert for workbooks/data sources with explicit destination and data inclusion.
2. Enforce identity, roles, object permissions and row-level policy in query/inspection/export/cache paths. Separate private local data from governed remote execution; downloaded raw data cannot remain protected by a removable visual filter.
3. Implement durable server refresh schedules, retries, watermarks, cancellation, subscriptions/alerts, job logs and missed-run recovery. Bind scheduled jobs to a valid service/user identity and secret lifecycle.
4. Build catalog/search, ownership, descriptions/certification, dependency lineage and impact analysis from real metadata.
5. Add comments, reviewed revisions and conflict-safe publishing. Use explicit optimistic concurrency first; real-time multiwriter editing requires a separate conflict model and test gate.
6. Add reader/embedded view APIs, backup/restore, audit, quotas, tenancy isolation and upgrade/migration runbooks. Reuse chart/semantic contracts without copying the whole native authoring UI.
7. Complete applicable Prep-style service workflows, data-source management, metrics/subscriptions and remaining governance rows from the reference inventory.

**Acceptance:** Q29/Q30 and service-specific fixtures with two principals/tenants, policy changes during cached sessions, revoked access, failed refresh, restored backup and version conflict. A dashboard that merely hides rows in JavaScript fails authorization.

**Stop rule:** keep service scores separate from desktop. No always-on Android background process substitutes for this infrastructure.

### S21 Isolated extensions and configured intelligence

**Inputs:** S20 identity/policy, S18 sandboxed analytical jobs and existing typed commands. [T38, T39]

1. Specify visualization/dashboard extension capabilities, lifecycle, versioning and isolation. External content receives only explicitly authorized data and a narrow API, never the privileged chart/native bridge.
2. Implement external dashboard web objects with separate origin/cookie/network policy, clear offline/unavailable states and no silent access to workbook data.
3. Inventory current reference AI/service features on the pinned build, resolving documented availability differences before tests are frozen. Do not reintroduce retired Ask Data, legacy Metrics or Data Stories as current requirements. [T40, T41]
4. Add configured assisted analysis that proposes typed formulas/queries/chart/workbook commands, identifies fields/sources used and states unsupported/uncertain answers.
5. Run all proposed changes through the deterministic compiler and normal permission/data-transfer controls. Destructive source operations, scripts or publishing retain their normal deliberate execution flow.
6. Evaluate factual/numerical grounding on a versioned benchmark, including ambiguous fields, impossible requests, adversarial text inside data and inaccessible sources. Generated text is distinct from measured results.

**Acceptance:** extension isolation and injection tests; no unsolicited data movement; answers cite the workbook/query state they describe; proposed calculations pass the same typed and numerical gates as manually authored ones. A generic chat box is not an analytical assistance implementation.

**Stop rule:** AI cannot repair missing deterministic functionality by inventing results or silently executing arbitrary code. Service-backed assistance is not advertised as offline if it requires a configured model endpoint.

### S22 Independent parity closure and maintenance

**Inputs:** full catalogs, reference profiles, release artifacts and evidence from all applicable stages.

1. Have a reviewer independent of the implementation author sample every feature family and inspect all required atomic pass/partial/blocked records.
2. Run representative complete workflows on actual supported Googlebook profiles: sales analysis, multi-fact actual-versus-budget, time-series/forecast, spatial dashboard, shared-policy workflow and migrated workbook.
3. Reconcile every connector/function/format/extension/device limitation. Report counts without rounding partials into passes, and distinguish documented agreement from executed Tableau comparison.
4. Publish the supported capability matrix, reference versions, known differences, workload envelopes, privacy/data-movement rules, recovery instructions and release provenance.
5. Establish maintenance ownership for Android/WebView changes, native dependencies, security fixes, connector/API drift, semantic regression and workbook migrations. Dependency upgrades must rerun the affected contract gates.
6. Tag a release only after its advertised scope passes. Full desktop parity requires all applicable desktop rows; service and interoperability claims need their separate evidence.

**Acceptance:** a new coding agent can identify what is implemented, what is verified, what remains blocked, and the next exact task without reading an unbounded chat history. Users receive truthful release notes and reproducible workbooks.

**Stop rule:** no final checklist can convert missing behavior into parity. Preserve gaps and ship an accurately described milestone if the complete target is not yet achieved.

## 6. Verification fixtures and evidence

### 6.1 Independent fixture registry

The following are **proposed tests with stated inputs and expected outcomes**, not executed Cloudy Data or Tableau results. Create actual fixture files in the implementation repository, calculate/check the small oracles independently, and add pinned Tableau recordings where available. Use deterministic synthetic data and record generator/seed/hash for larger cases.

| ID | Fixture or scenario | Expected invariant/result |
| --- | --- | --- |
| Q01 | CSV with quotes, embedded newline, duplicate header, empty cell and leading-zero ID | Parser choices and rejected rows are explicit; `00123` remains an identifier when selected as text; row counts match the oracle. |
| Q02 | Int64 `9007199254740993`; Decimal `0.10 + 0.20`; exact timestamp unit | Integer survives grid/tooltip/export unchanged; Decimal result is exactly `0.30`; no accidental JavaScript Number coercion or timestamp-unit loss. |
| Q03 | JSON absent versus explicit-null field, heterogeneous values; Excel dates/formula cached values; Parquet nested/decimal fields | Types, parse policy and missing/null distinctions survive import and source refresh. |
| Q04 | Orders `(o1,100),(o2,200),(o3,300)`; Lines `(l1,o1),(l2,o1),(l3,o2)` | Orders-only SUM=600; physical left-join row SUM=700; relationship total of Orders remains600. Additional Lines dimensions need separately recorded reference domain behavior. |
| Q05 | Sales and budgets at different grains with shared Region/Month; two independent child facts; unmatched and duplicate keys | Each fact total stays equal to its independently computed aggregate; unrelated/ambiguous dimensions are explicit, not fan-out. Blending respects the primary domain. |
| Q06 | Region/Customer/Sales: `(A,u,4),(A,u,6),(A,v,30),(B,w,60)` | A sales40/global100. `SUM(Sales)/ATTR({FIXED:SUM(Sales)})` at A is0.4 under an ordinary Region=A filter, but1.0 if that filter is context. Percent-of-visible-total is1.0 after the ordinary filter. |
| Q07 | Q06 data: average customer totals using INCLUDE Customer at Region grain; EXCLUDE Customer at Region+Customer grain | INCLUDE average A=20, B=60. EXCLUDE values A/u=40, A/v=40, B/w=60; repeated LOD output must not create an additive grand total. |
| Q08 | Group A values10,20; Group B value100 | Cell means15 and100; automatic grand mean130/3=43.333…; explicit mean of displayed means57.5. Selected total mode persists. |
| Q09 | Group A keys1,2; Group B keys2,3 | COUNTD per group=2/2; overall COUNTD=3, not4. NULL behavior has separate rows. |
| Q10 | Sales/Profit rows `(10,5),(90,9)` | Ratio of sums14/100=14%; average row ratios=(50%+10%)/2=30%. Display rounding never changes the stored result. |
| Q11 | R1 Jan/Feb/Mar=10/20/30; R2=1/2/3 | Month-addressed, Region-partitioned running totals are10/30/60 and1/3/6; reverse sort has its own independent expected sequence. |
| Q12 | Apply a table-calculation filter hiding Jan from Q11 | Remaining R1 totals stay30/60 and R2 stay3/6. An ordinary pre-calculation filter has different expected totals. |
| Q13 | Observed Jan10, FebNULL, Apr40; no March row | ZN can replace observed FebNULL with0; it cannot create March. Explicit domain completion marks March as generated. Month date-part combines years; month date-value retains year. |
| Q14 | Leap days, ISO/fiscal week-year boundaries, DST gaps/overlaps and different workbook/source timezones | Compare with the pinned timezone/calendar oracle; distinguish date parsing, instant conversion, truncation and label formatting. Persist the selected policy. |
| Q15 | Names/values containing quotes, SQL-like text, Unicode and an untrusted file-reading SQL expression | Bound values/quoted IDs remain data. Untrusted SQL/table functions cannot bypass approved source handles or run on open. |
| Q16 | Begin with accepted resultP; submit slow revisionA, then fast revisionB; deliver A last | Only a valid B can become current. Late A is always discarded/released. If B fails, retain P explicitly stale; failure does not authorize an invalidated A completion. |
| Q17 | Kill app/engine at every import/refresh data/metadata receipt boundary | Previous accepted state survives; complete new data is reconciled once; orphan files are handled; no false success or blind duplicate replay. |
| Q18 | Nonseekable content URI, revoked permission, unavailable provider, save failure and completion ambiguity | Owned staging and local recovery remain; source/target identity is frozen; no guessed path or assumed atomic provider replacement. |
| Q19 | Two views of one workbook plus another workbook; conflicting edits, closing/minimizing and reattaching | One data writer; correct revision conflict rules; no lost edits, mixed sources or cross-window result/clipboard ownership. |
| Q20 | One dashboard gesture triggers parameter, set, filter, navigation, highlight and URL actions; clear selection and cycles | Pinned action ordering, coherent resulting revision, documented clear behavior and cycle prevention; no automatic unsafe URL/network action. |
| Q21 | Native keyboard-only authoring, physical mouse/trackpad, IME, TalkBack, scaling/themes/densities, narrow/short windows | All primary tasks accessible without drag/hover; logical focus/selection survives virtualization; complete operation target and dialog actions remain reachable. |
| Q22 | Spoofed renderer origin/window/revision, malicious tooltip/image URL, package traversal/bomb and invalid Arrow lengths | Requests fail within documented limits; no unauthorized data/file/network access, code execution or privileged bridge exposure. |
| Q23 | D1/D2 queries plus several workbooks under low memory/storage; cancel, renderer death and Binder death | Bounded resource behavior, true interrupted state, no leaked leases or corrupt accepted snapshot; measured limits are reported. |
| Q24 | Export the same frozen view/story to table, image, vector, PDF and other advertised formats | Numbers, filters, units, fonts, legends, pagination and snapshot match; output scope/destination stays fixed through tab switches. |
| Q25 | Each real connector and native/remote execution pair | Correct types and pushdown results; verified auth/TLS, cancellation, snapshot consistency, rate limits, reconnect and explicit data movement. |
| Q26 | Initial IDs1→10 and2→20; tied-watermark late ID3→30, update ID2→25, then delete ID1 under a supported keyed policy | Final rows ID2=25 andID3=30, sum55; no duplicate append. Append-only mode declares that updates/deletes require a different policy. |
| Q27 | Versioned Tableau packages with both supported and unsupported formulas, actions, objects and sources | Exact per-element conversion report, original preserved, no unsupported behavior shown as translated or autorun; reference-engine verification for claimed compatibility. |
| Q28 | Versioned statistical corpus with constant/seasonal/noisy/degenerate inputs, NULLs, ties and known model outputs | Independent coefficients/quantiles/cluster/forecast checks under predefined tolerances; unsupported input yields diagnostics, not plausible guesses. |
| Q29 | Two users/tenants with different row/object/export permissions, policy changes and cache reuse | Every query, row inspection, export and cache retrieval honors current policy; hidden visual filters are irrelevant to enforcement. |
| Q30 | Server schedule restart, expired credentials, missed run, duplicate trigger and DST schedule boundary | Durable unique job identity, explicit schedule timezone, bounded retry and correct watermark/notification delivery; no double commit. |
| Q31 | Worker script accesses disallowed files/network, loops, crashes or returns wrong shape/order | Isolation/quotas/cancel work, precise typed error, no unauthorized data movement, and coherent unchanged accepted workbook. |
| Q32 | Both ABIs, 4 KiB/16 KiB, current caption/window/display APIs, Android memory/background behavior | Exact supported build/device results; unavailable newer APIs stay gated; cross-compilation is not physical-device proof. |
| Q33 | Native/converted package migrations, source schema rename/type change, missing assets and unknown future versions | IDs/dependencies map deliberately, originals survive, incompatible elements are explicit, valid versions reopen without semantic drift. |
| Q34 | Mixed command history: fields/calculations/filters/layout/refresh references, text draft, save/reopen and close view | Undo/redo restores intended document state; source side effects are not replayed; closing a view does not delete its sheet definition. |

Add exact algorithm-specific cases and connector datasets as the catalogs expand. A fixture family such as Q28 is not itself one passing test; each statistical function/model option needs its own input, oracle and evidence.

### 6.2 Evidence record

Every result should include:

```yaml
case_id: Q06-fixed-filter-ordinary
requirement_ids: [F21, F22, F24]
stage: S08
source_commit: <observed commit>
artifact_sha256: <actual tested artifact>
platform_profile: <device or host profile ID>
reference_profile: <Tableau build and service profile, or unavailable>
fixture_sha256: <actual fixture>
commands: <exact commands executed or manual procedure>
expected: <independent oracle>
observed: <actual result>
status: verified | failed | blocked | implemented_unverified
evidence_paths: <logs, data, inspected captures, reference workbook>
limits: <what this observation does not establish>
reviewer: <actual reviewer identity>
```

Keep evidence records proportional. A pure compiler test needs no invented screenshot. A native layout change needs an inspected native render and relevant input checks. A screen-reader claim needs actual speech/behavior evidence; a semantics-tree dump alone is a narrower result. Performance records include complete workload and device conditions.

### 6.3 Native visual matrix

Use CD11/CD12's process for changed surfaces:

1. Compile the exact modified target and capture its actual state.
2. Record artifact/source, OS/runtime, observed window bounds, appearance, density, font scale and screenshot hash.
3. Inspect the pixels for hierarchy, alignment, overflow, state distinctions, target/confirmation visibility and focus.
4. Exercise changed keyboard, pointer, IME, accessibility and lifecycle behavior separately.
5. Fix the observed defect, recapture affected cases and obtain an independent review. Reuse unaffected existing evidence.

The coverage matrix spans Light/Dark, Comfortable/Standard/Compact, default and large text, wide/narrow/short windows, long/empty/busy/failure/permission/confirmation states. Choose pairwise combinations for routine work and targeted full combinations where a defect/risk demands them. Do not mechanically recapture every unchanged screen after a small fix.

## 7. Coding-agent operating instructions

### 7.1 Master continuation prompt

Paste this into the coding agent with the three files and actual repository access:

```text
You are the implementation lead for Cloudy Data. Build the application described
by Cloudy_Data_01_Product_and_Design_Contract.md,
Cloudy_Data_02_Architecture_and_Technical_Specification.md, and
Cloudy_Data_03_Implementation_Roadmap_and_Agent_Runbook.md.

The target is a real Googlebook desktop Android analytical application with
Tableau functional parity as the final goal and the supplied Cloudy Design
Language. Implement useful verified milestones without redefining the goal.
The native app must support ordinary local authoring offline. A VM, browser
website, server BI application or remote worker is not an invisible substitute.

First inspect the repository instructions, git status/branch, current source,
execution-state document, accepted ADRs, tests and evidence. Read the three plan
files and relevant original Cloudy documents. Locate canonical tokens and
toolchain lockfiles. Prior conversation summaries and checkboxes are pointers,
not proof of completed code. Do not restart finished verified work merely because
you are a new agent. Do not invent APIs, installed dependencies, pass results or
missing token values.

Determine the earliest incomplete stage whose prerequisites are verified. If
this is a new project, do S00 and the bounded S01 native probes before building
the full workbench. Produce a short concrete task packet, then implement it;
do not stop after writing another general plan or an empty scaffold.

For each task:
1. Name the exact F requirements, Q fixtures, entry conditions and writable paths.
2. Inspect the existing implementation and choose a small working change.
3. Keep numerical meaning in the typed semantic core, persistent edits in the
   coordinator, native resources in the query owner and rendering in its bounded
   surface. Preserve source/window/revision identity and privacy boundaries.
4. Reuse Cloudy tokens/components. Preserve native captions, keyboard/pointer,
   IME, large text and three density tiers. Use ordinary desktop clipboard/menu
   behavior; do not import terminal right-click paste or auto-fullscreen rules.
5. Implement actual operations and accurate pending/error/unavailable states.
   No fake query results, connectors, progress, saved receipts or device proof.
6. Run meaningful tests for the changed risk. Inspect native renders and exported
   files when relevant. Check exact numerical fixtures independently. A host test
   cannot certify Android hardware behavior or Tableau compatibility.
7. Obtain independent focused review for required acceptance gates, fix material
   findings, and update status/evidence accurately. If required native visual
   review is unavailable, leave that gate implemented_unverified/partial;
   commits and independent work may continue, but do not self-certify its pass.
8. Make a coherent git commit containing the change and its evidence references.
   Preserve unrelated edits, secrets, source data and existing history.
9. Continue the next eligible bounded task while the authorized milestone and
   working context permit it. End with a precise continuation point.

If the same attempt fails twice without new evidence, stop repeating it. Record
the observed failure, alternative hypotheses and one experiment that separates
them. Ask another agent for a bounded review or evaluate the documented fallback.
Do not redesign unrelated screens, switch the whole stack, weaken a test or
remove a parity requirement to hide a blocker. If hardware, credentials, source
assets or reference access is missing, mark only the dependent gate blocked and
continue independent authorized work. Escalate only a concrete decision/input
that cannot be resolved from existing authorization and evidence.

Use git actively. Check status before edits and commits, work on a descriptive
branch/worktree when appropriate, stage only intended files and never force-push,
hard-reset, delete unrelated work or rewrite shared history. Follow existing
repository rules for pushes, PRs, signing and publishing. Do not deploy, send
messages, or distribute a release unless that destination/action is authorized.

Maintain docs/execution-state.md with current source, verified capabilities,
pending/blocked gates, exact commands and next task. Maintain append-only history
for important decisions and failed experiments. Never claim a stage complete
solely because code compiles, a screenshot exists, or tests were not run.

Report: what now works; why it changed; exact test/device/reference evidence;
commit/artifact identifiers; material limitations; and the next eligible task.
Separate implemented from verified and local from service-backed functionality.
```

### 7.2 Bounded worker packet

The leader supplies one packet per implementation unit. Independent agents can own UI, semantics/engine and adapters/tests after shared interfaces are established. Avoid simultaneous edits to the same schema, generated tokens, lockfile or execution-state file; the leader integrates those.

```yaml
task: S08.5-fixed-context-filter
goal: Preserve FIXED denominator under ordinary filters and constrain it by context.
base_commit: <actual inspected commit>
requirements: [F21, F22, F24]
references: [T03, T04, T05, T06]
read_first:
  - <actual semantic AST and planner files>
  - <actual query/filter model and Q06 fixtures>
writable_paths:
  - <bounded planner, AST and test paths>
preserve:
  - Current canonical tokens and UI layout
  - Public command and result-stamp contracts
  - Unrelated joins/table-calculation behavior
expected:
  ordinary_region_A: 0.4
  context_region_A: 1.0
verification:
  - Independent Q06 oracle
  - Planner structure and actual engine result
  - Save/reopen of filter stage
deliver:
  - Coherent code change and targeted tests
  - Actual commands/results and known limits
  - Commit or reviewable diff for leader integration
```

This is an example packet; fill paths and identifiers from the repository before issuing it. A worker can propose an interface change when evidence requires one but cannot silently rewrite shared contracts or broaden its task.

### 7.3 Git and state discipline

- Before work, inspect `git status --short`, branch/HEAD, relevant history, instructions and current tests. Never discard an unfamiliar change to make the tree clean.
- Prefer coherent commits per verified behavior. Separate large mechanical changes, dependency repins and semantic changes when that makes review clearer.
- Stage intended paths explicitly. Keep credentials, signing keys, raw private datasets, generated caches and huge capture dumps out of normal commits.
- Record exact build/data/artifact hashes in evidence manifests. Store large approved artifacts through the repository's designated artifact mechanism, linked by immutable identity.
- Before integrating parallel work, review the diff, resolve conflicts semantically and run the affected contracts. A conflict-free merge does not prove runtime compatibility.
- Use PR descriptions that state the concrete problem, resulting behavior, tested evidence and remaining limitations. Preserve actual newlines; use a body file or structured tool argument for long descriptions.
- Do not amend an old verified result to look current after a dependency change. Create a new result tied to the new source/artifact.

### 7.4 Minimum execution-state content

Keep the handoff concise enough to read on every continuation:

| Field | Required content |
| --- | --- |
| Current work | Branch/commit, working-tree changes, active stage and ticket. |
| Verified capabilities | Exact F IDs, supported platform/source mode and evidence references. |
| Implemented but unverified | Code that exists and the missing test/device/reference gate. |
| Blockers | Observed fact, impact, attempted hypotheses, remaining decision or resource. |
| Last verification | Exact commands/artifacts and dates; whether run on host, emulator, device or Tableau. |
| Next task | One ready task, prerequisites, relevant files, expected outcome and fixture IDs. |
| Protected context | User constraints, accepted ADRs, uncommitted work to preserve and publication permissions. |

Keep detailed chronological experiments in history, not an ever-growing main handoff. Preserve history when condensing active state so a new agent can investigate why an earlier approach was rejected.

## 8. Build, CI and release plan

Create jobs when the corresponding code exists; do not add green placeholders that skip the actual work.

| Lane | Required checks/artifacts | Trigger and authority |
| --- | --- | --- |
| Source and schema | Formatting, static analysis, schema validation, generated-token consistency, dependency lock verification, secret scan | Pull requests/commits; no release secrets for untrusted code. |
| Semantic core | Parser/type/plan tests, independent Q fixtures, metamorphic properties and catalog coverage | Every semantic change; both native and worker backends when their behavior is affected. |
| Native engine | Reproducible NDK build for both ABIs, native tests, extension manifest, symbols and alignment inspection | Native/dependency changes; cached artifacts keyed to exact sources/config. |
| Android integration | APK build/lint, instrumentation on supported emulator profiles, IPC/storage/lifecycle and renderer tests | Relevant changes; distinguish emulator from physical-device evidence. |
| Desktop visual/input | Inspected native captures, physical keyboard/mouse/trackpad/IME/TalkBack and external-display cases | Human/device lane or qualified device farm; no invented headless substitute. |
| Performance | D1/D2 fixtures, memory/thermal/disk/cancel and multiple workbooks | Meaningful engine/renderer/resource changes and release gate; fixed conditions and baselines. |
| Export/interoperability | Reopen/render outputs and declared Tableau/Hyper corpus comparisons | Output/schema/renderer/migration changes; licensed/reference access where required. |
| Connector/service | Controlled actual services, auth/row policy, query/cancel/refresh, rate/expiry and cache isolation | Per-adapter changes and release coverage. |
| Package/release | Clean install/update, SBOM/notices, native symbols, 4 KiB/16 KiB, APK/AAB checksums and authorized signing | Trusted branch/tag/environment; publishing destination requires authorization. |

Use workload identity or narrowly scoped secrets where supported, minimal job permissions, pinned third-party actions and bounded artifact retention. Forked pull requests must not receive release credentials or privileged execution. Actual physical ARM64 qualification is distinct from building an ARM64 library on an x86 runner.

Useful metamorphic checks include invariance to input row order for order-independent aggregates; equivalent typed parameter binding; sum consistency under a disjoint grouping; rename stability through field IDs; cancellation leaving accepted state intact; and serialization/reopen preserving semantics. Avoid tests that merely repeat the implementation's SQL string or snapshot every line of a transient UI tree.

## 9. Source register and research limits

All external sources below were consulted through official documentation or owner repositories on **18 October 2026**. Moving `current`, `latest`, development documentation and main branches must be pinned again when S00 selects actual implementation versions. Source pages establish API/reference behavior; the proposed architecture, stage boundaries, budgets, fixtures and Cloudy additions are engineering decisions in this packet.

The 18 supplied design documents are mapped as CD01–CD18 in file 01. Their referenced repository assets and old qualification artifacts were not supplied; their contents and completion claims are not assumed. No Cloudy Data repository, native APK, target hardware, Tableau installation or connector account was exercised while preparing this plan. Those are explicit implementation gates, not hidden completed work.

### 9.1 Tableau reference behavior

| ID | Primary sources | Use in this plan |
| --- | --- | --- |
| T01 | [Relate your data](https://help.tableau.com/current/pro/desktop/en-us/relate_tables.htm); [Data model](https://help.tableau.com/current/pro/desktop/en-us/datasource_datamodel.htm) | Logical relationships versus physical joins/unions and grain. |
| T02 | [When to use a multi-fact model](https://help.tableau.com/current/pro/desktop/en-us/datasource_mfr_when_to_use.htm) | Multiple base tables/shared dimensions and model applicability. |
| T03 | [Order of operations](https://help.tableau.com/current/pro/desktop/en-us/order_of_operations.htm) | Filter sequencing and context/Top-N behavior. |
| T04 | [Filters and LOD expressions](https://help.tableau.com/current/pro/desktop/en-us/calculations_calculatedfields_lod_filters.htm) | FIXED versus INCLUDE/EXCLUDE and post-calculation filters. |
| T05 | [LOD expressions](https://help.tableau.com/current/pro/desktop/en-us/calculations_calculatedfields_lod.htm) | LOD language and calculation scope. |
| T06 | [FIXED expressions](https://help.tableau.com/current/pro/desktop/en-us/calculations_calculatedfields_lod_fixed.htm) | Explicit grouping and FIXED examples. |
| T07 | [Data types and roles](https://help.tableau.com/current/pro/desktop/en-us/datafields_typesandroles.htm) | Dimension/measure, discrete/continuous and platform-specific source constraints. |
| T08 | [Continuous dates](https://help.tableau.com/current/pro/desktop/en-us/dates_continuous.htm); [Build a basic view](https://help.tableau.com/current/pro/desktop/en-us/getstarted_buildmanual_ex1basic.htm) | Date-part versus date-value behavior. |
| T09 | [Blend your data](https://help.tableau.com/current/pro/desktop/en-gb/multiple_connections.htm) | Primary/secondary source and aggregate blending semantics. |
| T10 | [Table calculations](https://help.tableau.com/current/pro/desktop/en-us/calculations_tablecalculations.htm) | Addressing, partitioning and compute-using rules. |
| T11 | [Grand totals](https://help.tableau.com/current/pro/desktop/en-us/calculations_totals_grandtotal_turnon.htm) | Automatic and two-pass total behavior. |
| T12 | [Missing values](https://help.tableau.com/current/pro/desktop/en-us/missing_values.htm) | NULL versus absent members and domain completion. |
| T13 | [All function categories](https://help.tableau.com/current/pro/desktop/en-us/functions_all_categories.htm); [Table-calculation functions](https://help.tableau.com/current/pro/desktop/en-us/functions_functions_tablecalculation.htm) | Exact function inventory and per-function behavior. |
| T14 | [Actions](https://help.tableau.com/current/pro/desktop/en-us/actions.htm) | Action types and execution ordering. |
| T15 | [Shelves and cards](https://help.tableau.com/current/pro/desktop/en-us/buildmanual_shelves.htm) | Worksheet composition, controls and placements. |
| T16 | [Build common chart types](https://help.tableau.com/current/pro/desktop/en-us/dataview_examples.htm); [Mark types](https://help.tableau.com/current/pro/desktop/en-us/viewparts_marks_marktypes.htm) | Chart and mark catalog. |
| T17 | [Multiple measures](https://help.tableau.com/current/pro/desktop/en-us/multiple_measures.htm) | Measure Names/Values, multiple measures and axes. |
| T18 | [Trend lines](https://help.tableau.com/current/pro/desktop/en-us/trendlines_add.htm); [Analytics pane](https://help.tableau.com/current/pro/desktop/en-us/environ_workspace_analytics_pane.htm) | Reference analysis and trend controls. |
| T19 | [Spatial files](https://help.tableau.com/current/pro/desktop/en-us/examples_spatial_files.htm); [Build maps](https://help.tableau.com/current/pro/desktop/en-us/maps_build.htm) | Geographic workflow and source formats. |
| T20 | [How forecasting works](https://help.tableau.com/current/pro/desktop/en-us/forecast_how_it_works.htm); [Forecasting](https://help.tableau.com/current/pro/desktop/en-us/forecasting.htm) | Forecast model/options and eligibility. |
| T21 | [Clustering](https://help.tableau.com/current/pro/desktop/en-us/clustering.htm) | Algorithm, scaling, automatic cluster-count and explanation. |
| T22 | [Files and folders](https://help.tableau.com/current/pro/desktop/en-us/environ_filesandfolders.htm) | Workbook, data-source and package format roles. |
| T23 | [Extract data](https://help.tableau.com/current/pro/desktop/en-us/extracting_data.htm) | Extract and refresh capability baseline. |
| T24 | [Connectors](https://help.tableau.com/current/pro/desktop/en-us/exampleconnections_overview.htm) | Named connector inventory; expand per-source pages in S00/S15. |
| T25 | [Prepare data](https://help.tableau.com/current/pro/desktop/en-us/datasource_prepare.htm) | Desktop preparation workflow and source transformations. |
| T26 | [Prep Conductor in Cloud](https://help.tableau.com/current/online/en-us/prep_conductor_online_intro.htm); [Publish Prep flows](https://help.tableau.com/current/server/en-us/prep_publishserver_overview.htm) | Preparation versus service execution/scheduling. |
| T27 | [Create dashboards](https://help.tableau.com/current/pro/desktop/en-us/dashboards_create.htm); [Tiled and floating items](https://help.tableau.com/current/pro/desktop/en-us/dashboards_organize_floatingandtiled.htm) | Dashboard object/layout composition. |
| T28 | [Dynamic zone visibility](https://help.tableau.com/current/pro/desktop/en-gb/dynamic_zone_visibility.htm) | Conditional dashboard layout behavior. |
| T29 | [Create a story](https://help.tableau.com/current/pro/desktop/en-us/story_create.htm) | Story points and presentation state. |
| T30 | [Export views](https://help.tableau.com/current/pro/desktop/en-us/save_export_image.htm) | Output workflow; expand exact formats/options at baseline capture. |
| T31 | [Permissions](https://help.tableau.com/current/online/en-us/permissions.htm); [Creator connections](https://help.tableau.com/current/online/en-us/creator_connect.htm) | Publishing/permission context; full service inventory requires S00 expansion. |
| T32 | [Interact with views](https://help.tableau.com/current/pro/desktop/en-us/data_explore_analyze_interact.htm); [View data](https://help.tableau.com/current/pro/desktop/en-us/inspectdata_viewdata.htm) | Selection, drill and underlying-data inspection. |
| T33 | [Predictive modeling](https://help.tableau.com/current/pro/desktop/en-us/predictions.htm); [Choose a predictive model](https://help.tableau.com/current/pro/desktop/en-us/predictions_choosing_model.htm) | Predictive function scope and model choices. |
| T34 | [Manage an analytics extension connection](https://help.tableau.com/current/pro/desktop/en-us/r_connection_manage.htm) | External analytical service workflow. |
| T35 | [Map marks layers](https://help.tableau.com/current/pro/desktop/en-us/maps_marks_layers.htm) | Multiple geographic layers. |
| T36 | [Tableau Document API](https://tableau.github.io/document-api-python/) | Explicit unsupported/limited update scope; no full authoring/round-trip assumption. |
| T37 | [Hyper API](https://tableau.github.io/hyper-db/docs/); [Installation/platform/license](https://tableau.github.io/hyper-db/docs/installation/) | Hyper interoperability; supported host platforms and package terms. |
| T38 | [Viz extensions](https://help.tableau.com/current/pro/desktop/en-us/viz_extensions.htm) | Extension workflows; API compatibility is a separate adapter. |
| T39 | [Tableau Agent FAQ](https://help.tableau.com/current/online/en-us/web_author_einstein_faq.htm); [Desktop Agent](https://help.tableau.com/current/pro/desktop/en-gb/desktop_einstein.htm) | Configured/service-dependent intelligence. Some availability descriptions differ; verify the exact build. |
| T40 | [Data Stories characteristics and retirement notice](https://help.tableau.com/current/pro/desktop/en-us/data_stories_characteristics.htm) | Retired Data Stories differs from active Story points. |
| T41 | [Ask Data retirement notice](https://help.tableau.com/current/online/en-us/ask_data.htm) | Avoid obsolete Ask Data/legacy Metrics assumptions in a current baseline. |

### 9.2 Googlebook, Android, DuckDB and Arrow

| ID | Primary sources | Use in this plan |
| --- | --- | --- |
| P01 | [Googlebook developer overview](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview) | Official product/platform, desktop workflow and tooling guidance. |
| P02 | [Googlebook introduction](https://blog.google/products-and-platforms/devices/googlebook/pre-order-googlebook/) | Product context; not an API or device-qualification substitute. |
| P03 | [Support desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing) | Native freeform windows, caption and multi-instance integration. |
| P04 | [Build DuckDB for Android](https://duckdb.org/docs/current/dev/building/android) | Experimental native support, ABI/NDK and static-extension build route. |
| P05 | [DuckDB extension distribution](https://duckdb.org/docs/current/extensions/extension_distribution) | Android distribution gap and platform/version-specific extensions. |
| P06 | [DuckDB-Wasm instantiation](https://duckdb.org/docs/current/clients/wasm/instantiation) | Worker/storage/runtime setup. |
| P07 | [DuckDB-Wasm OPFS persistence](https://duckdb.org/2026/09/18/opfs-wasm) | Current persistence capability and lifecycle considerations. |
| P08 | [DuckDB-Wasm overview](https://duckdb.org/docs/stable/clients/wasm/overview) | Wasm limits, execution and portability. |
| P09 | [Deploy DuckDB-Wasm](https://duckdb.org/docs/current/clients/wasm/deploying_duckdb_wasm); [Troubleshooting](https://duckdb.org/docs/current/clients/wasm/troubleshoot) | CORS, isolation, threading and runtime qualification. |
| P10 | [DuckDB concurrency](https://duckdb.org/docs/current/connect/concurrency) | Read/write process ownership. |
| P11 | [Android service manifest](https://developer.android.com/guide/topics/manifest/service-element) | Private service processes versus isolatedProcess. |
| P12 | [Multi-window drag and drop](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/multi-window) | Cross-window data and URI permissions. |
| P13 | [TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException) | Shared Binder transaction-buffer constraint. |
| P14 | [SharedMemory](https://developer.android.com/reference/android/os/SharedMemory) | Optional descriptor-backed shared buffers/protection. |
| P15 | [Arrow C Data Interface](https://arrow.apache.org/docs/18.1/format/CDataInterface.html); [Arrow columnar and IPC format](https://arrow.apache.org/docs/format/Columnar.html); [Arrow security](https://arrow.apache.org/docs/format/Security.html) | Same-process pointers, cross-process IPC distinction and validation. |
| P16 | [Load local WebView content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content) | Packaged asset origin and network fallback behavior. |
| P17 | [DuckDB C data chunks](https://duckdb.org/docs/current/clients/c/data_chunk) | Native chunk ownership and processing. |
| P18 | [DuckDB C connection/cancellation](https://duckdb.org/docs/current/clients/c/connect) | Connection, interruption and progress integration. |
| P19 | [DuckDB security policy](https://duckdb.org/security) | Threat and untrusted-input boundaries. |
| P20 | [Secure DuckDB](https://duckdb.org/docs/current/operations_manual/securing_duckdb/overview); [Secure extensions](https://duckdb.org/docs/current/operations_manual/securing_duckdb/securing_extensions) | Privileged SQL and defense-in-depth versus sandboxing. |
| P21 | [Android saved states](https://developer.android.com/topic/libraries/architecture/saving-states) | Small view state versus persistent documents. |
| P22 | [Handle WebView termination](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination) | Renderer death, disposal and recovery. |
| P23 | [Android Keystore](https://developer.android.com/privacy-and-security/keystore) | Credential protection boundary. |
| P24 | [Network security configuration](https://developer.android.com/privacy-and-security/security-config) | TLS/trust and network policy. |
| P25 | [Native API access from JavaScript](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge); [WebMessageCompat](https://developer.android.com/reference/androidx/webkit/WebMessageCompat); [Native-bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) | Origin restrictions, typed bridge and binary-message feature checks. |
| P26 | [DuckDB LTS C API](https://duckdb.org/docs/lts/clients/c/api); [C API reference](https://duckdb.org/docs/api/c/api) | API deprecations and selected chunk/Arrow conversion interfaces. |
| P27 | [Android documents and files](https://developer.android.com/training/data-storage/shared/documents-files) | SAF, grants and local storage integration. |
| P28 | [ContentProvider](https://developer.android.com/reference/android/content/ContentProvider) | Descriptor/stream behavior and seekability constraints. |
| P29 | [Long-running WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) | Quotas and appropriate background execution. |
| P30 | [Foreground-service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout) | Bounded background-service duration and type policy. |
| P31 | [DuckDB out-of-memory guidance](https://duckdb.org/docs/current/guides/performance/oom) | Engine memory limits versus total allocations. |
| P32 | [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-all) | Version-dependent memory enforcement/exit diagnostics. |
| P33 | [App memory budgets](https://developer.android.com/topic/performance/memory/app-memory-budgets) | Newer optional budget APIs; actual API/minor version must be checked. |
| P34 | [Manage app memory](https://developer.android.com/topic/performance/memory/manage-app-memory) | Modern callbacks and measured resource policy. |
| P35 | [16 KiB page-size support](https://developer.android.com/guide/practices/page-sizes) | All native dependencies and final-package alignment/runtime tests. |
| P36 | [Connected displays](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays) | Density/display transitions and native windows. |
| P37 | [DuckDB building overview](https://duckdb.org/docs/current/dev/building/overview) | Platform support tiers and reproducible builds. |
| P38 | [nanoarrow](https://arrow.apache.org/nanoarrow/latest/index.html); [nanoarrow release reference](https://arrow.apache.org/blog/2026/02/24/nanoarrow-0.8.0-release/) | Candidate small C Arrow adapter; Android qualification remains required. |
| P39 | [Room persistence](https://developer.android.com/training/data-storage/room) | On-device SQLite abstraction and migrations; use one metadata authority and pin actual library versions. |

### 9.3 Open-source references and renderer implementation

| ID | Primary sources | Role or license evidence |
| --- | --- | --- |
| O01 | [Apache Superset repository](https://github.com/apache/superset) | Project/code and Apache-2.0 boundary. |
| O02 | [Superset introduction](https://superset.apache.org/user-docs/intro/); [First dashboard](https://superset.apache.org/docs/using-superset/creating-your-first-dashboard/) | BI interaction/architecture reference; current introduction is marked Next. |
| O03 | [Metabase query builder](https://www.metabase.com/docs/latest/questions/query-builder/editor) | Guided analysis interaction. |
| O04 | [Metabase license](https://github.com/metabase/metabase/blob/master/LICENSE.txt) | Core/enterprise licensing distinction. |
| O05 | [Grafana repository](https://github.com/grafana/grafana) | Panel/time-series reference, AGPL-3.0 upstream. |
| O06 | [Perspective repository](https://github.com/perspective-dev/perspective); [OpenJS membership](https://openjsf.org/blog/perspective-joins-openjs) | Current project location and Apache-2.0 reference. |
| O07 | [Vega repository](https://github.com/vega/vega); [Vega-Lite license](https://github.com/vega/vega-lite/blob/main/LICENSE) | Selected grammar family and BSD-3-Clause boundary. |
| O08 | [Vega-Lite documentation](https://vega.github.io/vega-lite/docs/) | Encodings, composition and editable visual grammar. |
| O09 | [Apache ECharts](https://github.com/apache/echarts); [Features](https://echarts.apache.org/en/feature.html) | Chart comparison and Apache-2.0 reference. |
| O10 | [KNIME Analytics Platform](https://www.knime.com/knime-analytics-platform); [License](https://www.knime.com/downloads/full-license) | Workflow/preparation reference and license/additional-permission boundary. |
| O11 | [Orange repository](https://github.com/biolab/orange3); [License](https://github.com/biolab/orange3/blob/master/LICENSE) | Visual analytics reference and GPL-3.0-or-later boundary. |
| O12 | [Vega expression interpreter](https://vega.github.io/vega/usage/interpreter/) | CSP-compatible AST interpreter instead of runtime Function construction. |
| O13 | [Vega View API](https://vega.github.io/vega/docs/api/view/) | Canvas/SVG scene lifecycle, export and disposal. |

## 10. First handoff

Give the coding agent all three files together, the original Cloudy design documents, and the actual repository. If available, include the canonical token source and a Googlebook device profile. Ask it to execute **S00 → S01**, then demonstrate the real R1 analytical journey.

The first review should answer four concrete questions: which native architecture passed; which device/build was observed; whether tiny numerical and durability fixtures passed; and what exact task is ready next. That provides a reliable starting point for the longer parity program.
