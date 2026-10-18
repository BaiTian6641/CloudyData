# Cloudy Data Product and Design Contract

**Planning baseline:** 18 October 2026. **Status:** implementation plan; no Cloudy Data application, benchmark, device qualification, or Tableau compatibility result is claimed. **Audience:** the project owner, implementation leader, coding agents, and reviewers.

Cloudy Data is a desktop data analysis and visual authoring application packaged for Googlebook's Android environment. Its target is the practical analytical capability of Tableau, with a native desktop workbench that follows the Cloudy Design Language. An analyst must be able to import data, model it, construct calculations, build interactive worksheets and dashboards, explain results, and save or export their work. Local authoring must remain useful offline.

This contract defines the destination. Intermediate releases are explicitly smaller milestones; they do not redefine the requested functional parity. The architecture and numerical contracts are in [file 02](Cloudy_Data_02_Architecture_and_Technical_Specification.md). The implementation stages, agent instructions, test fixtures, and external source register are in [file 03](Cloudy_Data_03_Implementation_Roadmap_and_Agent_Runbook.md).

## 1. Product decisions and authority

### 1.1 Decisions established by the request

1. Build a data analysis application with Tableau-like visual authoring and a functional parity target.
2. Target Googlebook desktop/laptop use: physical keyboard, trackpad/mouse, native resizable windows, multiple documents, and substantial information density.
3. Apply Cloudy's original synthesis of Carbon's productive structure and Material Design 3's tonal and adaptive behavior. The application must look like Cloudy, while preserving familiar analytical workflows.
4. Deliver the work in bounded stages that a coding agent can implement and verify.

### 1.2 Implementation defaults selected by this plan

These choices make the work concrete. They are new Cloudy Data decisions, not claims that the supplied Console documents already specify this product.

| Decision | Selected default | Qualification or consequence |
| --- | --- | --- |
| Product name | Cloudy Data | Working name; package identifiers and distribution identity are recorded in S00. |
| Primary application | Native Kotlin/Jetpack Compose Android workbench | Use real Android window, file, keyboard, accessibility, and lifecycle behavior. |
| Local analytical engine | DuckDB through a narrow native adapter | Preferred candidate only until the S01 Android feasibility gate passes; official Android support is experimental. [P04] |
| Chart surface | Packaged Vega renderer in a restricted WebView | Native shell and controls remain outside it; rendering must pass input, accessibility, memory, and offline tests. [O07, O08, P16, P25] |
| File model | Cloudy workbook definitions plus portable data snapshots | Numerical meaning belongs to Cloudy's semantic model, not to a renderer specification or opaque engine cache. |
| Default execution | On-device local processing | Import, model, core calculation, charts, dashboards, save/reopen, and export do not require a server or Linux VM. |
| Extended execution | Explicit optional connector/compute service | Provides capabilities such as desktop-only drivers or Python/R; the UI names execution location and data movement. |
| Reference baseline | A pinned Tableau Desktop release, edition, OS profile, and feature catalog | S00 records the exact baseline. Current web documentation is a discovery source, not a permanently fixed contract. |
| Service functionality | A separately deployable companion lane | Publishing, managed refresh, permissions, collaboration, and configured AI remain in the plan, with separate completion measures. |
| Compatibility | Separate tests for Tableau files and APIs | Equivalent analysis does not imply `.twb`, `.twbx`, or `.hyper` compatibility. [T36, T37] |

Google now provides a Googlebook developer guide. The name need not be treated as speculative, but it does not identify an Android API level, a driver ABI, or a public VM integration API. Record those on each actual target. [P01]

### 1.3 Document precedence

Use this precedence when the implementation repository is assembled:

1. The owner's explicit current instructions and approved product changes.
2. This Cloudy Data product contract and accepted decision records that explicitly amend it.
3. The canonical Cloudy token source and the supplied design documents within their applicable scope.
4. The architecture and stage contracts in files 02 and 03.
5. External documentation for platform/API behavior and the pinned Tableau reference.
6. Reference applications, screenshots, examples, proposals, and agent suggestions.

A newer date alone does not make a Console-specific interaction apply to data analysis. An old screenshot does not override a token or a verified native constraint. Do not copy source commit IDs, installed version numbers, stage completion claims, or model-routing instructions from the Console project into this new project's execution state.

Official platform contracts govern actual API availability, permissions and lifecycle. A product requirement states an intended outcome; it cannot override those contracts. Resolve a conflict through a tested alternative/ADR or a blocked capability, never through an invented API or a bypass of platform controls.

### 1.4 What the supplied design files establish

The original files remain unchanged. The following identifiers are used throughout this packet.

| ID | Supplied file | Application to Cloudy Data |
| --- | --- | --- |
| CD01 | `README.md` | Design direction, desktop scope, native-versus-specimen evidence, canonical token ownership. |
| CD02 | `principles.md` | Productive flat panes, tonal hierarchy, contextual actions, precise input, truthful uncertainty. |
| CD03 | `foundations.md` | Declared light/dark colors, typography, spacing, shape, motion, semantic pair rules. |
| CD04 | `layout.md` | Current-window layout, pane ranges, width thresholds, density, overflow, RTL. |
| CD05 | `components.md` | Component anatomy, states, focus, labels, menus, tables, feedback precedence. |
| CD06 | `interaction.md` | One focused surface, OS/IME precedence, focus return, contextual errors, operation identity. Terminal rules remain terminal-specific. |
| CD07 | `screen-specs.md` | Screen-contract method and empty/loading/error/recovery requirements; existing screen IDs describe other products. |
| CD08 | `accessibility.md` | Contrast goals, keyboard alternatives, font scaling, native assistive technology and visual evidence. |
| CD09 | `implementation-map.md` | Generated tokens, Compose wrappers, native tables, narrow renderer boundaries, qualification limits. |
| CD10 | `desktop-workflows.md` | Clear source/destination binding, precise menus, truthful configured-versus-observed state. Remote protocols and USB are outside this product. |
| CD11 | `native-workspace-review.md` | Native geometry and interaction review procedure, evidence scoped to actual components. |
| CD12 | `worker-visual-acceptance.md` | Bounded agent design packets, inspected renders, independent review, honest capability states. |
| CD13 | `2026-10-16-external-feedback-review.md` | Approved corrections versus proposals; no fourth micro-density tier, invented palette, or automatic adoption of EDA ideas. |
| CD14 | `2026-10-16-workspace-home-revision.md` | Stable home organization and native-first evidence; host home and MobaXterm input are scoped to Console/Desktop. |
| CD15 | `native-display-settings.md` | Per-app System/Light/Dark and three density choices; preference outcomes must report persistence truthfully. Do not inherit that app's storage implementation blindly. |
| CD16 | `desktop-terminal-interaction-contract.md` | Source of input-boundary discipline; terminal copy-on-select, right-click paste, and Ctrl+C interrupt do not become data-workbench defaults. |
| CD17 | `desktop-rdp-interaction-contract.md` | Evidence and input-ownership principles only; RDP rendering/capture is not a Cloudy Data dependency. |
| CD18 | `remote-session-presentation.md` | Separation of local controls and specialized surfaces; automatic fullscreen and reconnect rules do not apply to ordinary analysis. |

**Missing upstream assets:** the uploads refer to `shared/design-system/tokens.json`, generator scripts, font assets, previews, and `platform-rule-zero.md`, but do not include them. S00 must locate the actual canonical files if the repository contains them. If unavailable, create a clearly marked provisional token source from the supplied numeric values and a missing-token register. Do not invent status-container or disabled colors and label them canonical. New chart-specific tokens are proposed additions, reviewed separately.

## 2. What functional parity means

### 2.1 Four independently measured targets

| Target | Meaning | Completion rule |
| --- | --- | --- |
| Analytical behavior | Same supported analytical task and meaning: types, grain, filtering, calculations, totals, selection, and saved state | Every atomic requirement has a reproducible fixture, expected result, and passing implementation evidence. |
| Authoring workflow | An analyst can accomplish the corresponding worksheet, dashboard, story, preparation, and output workflow | Test with native keyboard/pointer and saved/reopened workbooks, including errors and recovery. |
| Service behavior | Publishing, governed sharing, refresh, catalog, collaboration, embedding, and configured service intelligence | Verify against the Cloudy companion with real authentication, policy enforcement, and recovery. An APK alone cannot pass these rows. |
| Interoperability | Import/export compatibility with named Tableau formats or APIs | Declare format/version coverage separately and report every unsupported construct. |

The first two are the primary desktop destination. The third supplies the broader Tableau-style system requested by a comprehensive parity reading. The fourth is an additional compatibility commitment only where its individual gates pass. Cloudy's branding, visual tokens, control anatomy, and desktop adaptation are intentionally its own design.

### 2.2 Freeze a baseline without hiding gaps

At S00, build `parity/feature-catalog.yaml`, `parity/function-catalog.yaml`, and `parity/connector-catalog.yaml` in the implementation repository. Each row records a stable ID, reference release/edition, applicability, official source, behavior, dependencies, test IDs, assigned stage, current status, and evidence. The tables below are seed families, not an assertion that seventy-two rows exhaust every Tableau option.

Expand families into atomic capabilities. For example, “table calculations” must produce entries for addressing, partitioning, restart, sort, running sum, lookup, rank variants, moving windows, percent difference, and missing-member behavior. “Connectors” must expand into named drivers, authentication modes, live/extract modes, platform constraints, and required data types. Inventory currently supported behavior; do not revive retired products merely because an old article mentions them. [T40, T41]

Use these statuses: `not_started`, `in_progress`, `implemented_unverified`, `verified`, `partial`, `blocked`, and `not_applicable_to_pinned_reference`. The last status requires source evidence or an explicit owner-approved scope decision. A difficult requirement cannot be removed by calling it optional or renaming the milestone.

Report a strict count of verified atomic requirements over applicable atomic requirements, plus partial and blocked counts. Do not round partial behavior into a pass. Keep numerical, workflow, connector, accessibility, service, and compatibility results separate. Weighted progress may help scheduling but cannot justify a “1:1” release claim.

### 2.3 Reference correctness

Use three evidence types:

1. Hand-computed small fixtures for numerical invariants.
2. Independent engine/reference implementations where the operation has a public mathematical definition.
3. Differential comparison with the pinned Tableau build when licensed access is available.

An official help page establishes intended behavior; it does not establish Cloudy's result. If Tableau is unavailable, mark comparative acceptance pending and continue useful implementation against explicit fixtures. Never use Cloudy's own output to manufacture the expected result. Floating-point/statistical tolerances are operation-specific and written before comparison; ordinary integer and decimal totals need exact comparisons.

## 3. Functional capability catalog

All entries start as planned. Stage numbers refer to file 03. A stage may create a minimal supporting implementation earlier; acceptance belongs to the listed owning stage.

### 3.1 Sources, preparation, and modeling

| ID | Capability family | Required outcome | Owning stages |
| --- | --- | --- | --- |
| F01 | Workbooks and data-source documents | Create, open, duplicate, rename, save, recover, and reuse data sources with stable identity and visible dirty state. | S03, S16 |
| F02 | Connection catalog | Browse/search named connectors; display authentication, supported modes, execution location, and actual capability. | S04, S15 |
| F03 | File data | Import CSV/TSV, Parquet, JSON/NDJSON, Excel, SQLite, and Arrow through qualified parsers; inspect options and conversion failures. | S04, S14 |
| F04 | Types and import interpretation | Override inferred types, locale, delimiters, headers, encoding and date parsing; preserve identifiers, decimals, NULL and rejected-row evidence. | S04, S05 |
| F05 | Profiling and metadata | Row counts, sample/full distinction, nulls, distributions, schema changes, aliases, descriptions, and stable field IDs. | S04, S05, S14 |
| F06 | Live and extract operation | Choose an eligible source mode; show snapshot time, cache/staleness, source identity and pushdown limits; offline cached work remains truthful. | S04, S15 |
| F07 | Refresh | Full and qualified incremental refresh, cancellation, schema reconciliation, scheduling location, and prior-version recovery. | S14, S15, S20 |
| F08 | Reproducible preparation | Rename, select, cast, split, replace, trim, filter, pivot/unpivot, aggregate, deduplicate, join and union through an ordered transform graph. | S14 |
| F09 | Physical joins and custom queries | Inner/left/right/full and supported conditions with preview, NULL behavior and intentional row multiplication; custom SQL and supported result-producing stored procedures have separate trust/connector gates. | S07, S15 |
| F10 | Unions | Manual and pattern-based append, column mapping, schema reconciliation, source provenance and repeatable refresh. | S07, S14 |
| F11 | Logical relationships | Preserve table grain, query only needed tables, distinguish relationships from fixed physical joins, and verify unmatched/many-to-many behavior. | S07 |
| F12 | Multiple facts and shared dimensions | Aggregate facts at their correct grains before combining; handle unrelated dimensions and ambiguous paths explicitly. | S07 |
| F13 | Data blending and cross-source analysis | Define primary/secondary sources, linking fields, aggregation and unsupported cases; never substitute a raw cross-source join silently. | S07, S15 |
| F14 | Source replacement and reuse | Replace a source with field mapping, dependency repair, impact preview and reversible commit; retain broken references visibly. | S14 |

Tableau distinguishes logical relationships from physical joins/unions and supports multiple base tables with shared dimensions. These need distinct model objects and test coverage; one flattened table is not a general implementation. [T01, T02]

### 3.2 Field and calculation semantics

| ID | Capability family | Required outcome | Owning stages |
| --- | --- | --- | --- |
| F15 | Data pane and organization | Search, folders, aliases, hidden fields, hierarchies, default properties and dependency discovery. | S05, S09 |
| F16 | Field roles | Dimensions/measures and discrete/continuous are independent properties; dates have explicit date-part and date-value behavior. | S05, S06 |
| F17 | Aggregations | SUM/AVG/MIN/MAX/COUNT/COUNTD/ATTR, median/percentiles and variance families as cataloged; all-null and empty input semantics are defined. | S05, S08 |
| F18 | Calculated fields | Typed formula editor with autocomplete, syntax locations, dependency cycles, error explanations, references by ID, and explicit Apply. | S08 |
| F19 | Function coverage | Inventory all applicable numeric, string, logical, date, conversion, aggregate, spatial and analytical functions, including MODEL_PERCENTILE/MODEL_QUANTILE and connector restrictions. | S08, S12, S13, S18 |
| F20 | Calculation level | Distinguish row, aggregate, LOD, table and external calculations; reject invalid aggregate/nonaggregate mixing. | S08, S09 |
| F21 | Level of detail | FIXED, INCLUDE, EXCLUDE, table-scoped and nested cases, reaggregation and multi-table scope with differential fixtures. | S08 |
| F22 | Evaluation order | Source/extract filters, context, LOD, dimension/measure filters and table-calculation filters follow the recorded semantics. | S08, S09 |
| F23 | Filter controls | Members, ranges, relative dates, wildcard/condition, top/bottom, exclusions, all/relevant values, scope across worksheets and null controls. | S08, S10 |
| F24 | Context filtering | Explicit context membership and visible performance/state consequences; context changes invalidate the appropriate caches. | S08 |
| F25 | Time and calendars | Date parts/values, fiscal calendars, week start, locale, time zone, DST and relative-date clock rules are persisted. | S05, S08 |
| F26 | Table calculations | Running/moving totals, differences, percentages, ranks, lookup, window functions, compound calculations and partition restarts. | S09 |
| F27 | Addressing and partitioning | Persist dimension order, calculation direction, sort, restart and nesting; support a inspectable computation table. | S09 |
| F28 | Bins and domain completion | Create bins; distinguish zero, explicit NULL and absent members; densify only by explicit view semantics. | S09 |
| F29 | Groups and sets | Manual/dynamic groups and sets, combined sets, membership labels, sets as filters/encodings and action-driven updates. | S09, S10 |
| F30 | Parameters | Typed scalar parameters, allowed values/ranges, controls, dynamic refresh behavior and parameter actions; no SQL text substitution. | S09, S10 |

FIXED and INCLUDE/EXCLUDE do not occupy the same position relative to dimension filters. Table-calculation filters can hide results without recomputing the data used by the calculation. The architecture must represent these distinctions explicitly. [T03, T04]

### 3.3 Worksheet authoring and visual expression

| ID | Capability family | Required outcome | Owning stages |
| --- | --- | --- | --- |
| F31 | Shelves and field placements | Rows, Columns, Filters, Pages, Measure Names/Measure Values, and Aggregate Measures on/off; drag/keyboard editing, undo, Pages stepping/playback and mark-history settings. | S06, S09, S11 |
| F32 | Marks and layers | Color, size, shape, label, detail, tooltip and path; multiple mark layers and per-layer overrides remain inspectable. | S06, S11 |
| F33 | Facets and drilling | Multiple row/column dimensions, panes, nested headers, hierarchy expand/collapse and small multiples preserve context. | S06, S11 |
| F34 | Chart vocabulary | Bars, stacked/normalized bars, lines, area, scatter, text tables, heat/highlight tables, treemaps, pie, bubbles, histogram, box, Gantt, bullet, combo and supported density views. | S06, S11, S12, S13 |
| F35 | Crosstabs and totals | Pivoted row/column headers, subtotals and grand totals computed at the correct grain; copy/export respects selection and exact values. | S06, S09 |
| F36 | Axes and scales | Continuous/categorical axes, dual axes, synchronization where valid, reversal, log constraints, ticks, reference domains and independent display formatting. | S11 |
| F37 | Sorting | Manual, field, aggregate, nested and tie-breaking rules; header sort and table-calculation sort remain explicit. | S06, S09 |
| F38 | Formatting and annotation | Titles, captions, units, number/date formatting, labels, legends, borders, annotations and reusable workbook themes. | S06, S11 |
| F39 | Selection and inspect data | Click, additive/range/rectangle/lasso where relevant, keep/exclude, drill and View Data; stable mark identity maps to authorized rows. | S06, S10, S11 |
| F40 | Tooltips | Typed text and fields, formatting, selection context, and bounded viz-in-tooltip; no executable dataset HTML. | S11 |
| F41 | Suggested charts | Recommend valid chart forms from field roles and cardinality; show why a choice is unavailable and preserve the resulting editable specification. | S06, S11 |

Do not equate a chart gallery with visual grammar. The same field can appear more than once with different aggregation or date-level settings. A dual-axis view needs separate scale and mark-layer ownership. A chart's displayed grain changes when a detail field is added, even if it does not appear on an axis.

### 3.4 Dashboards and presentation

| ID | Capability family | Required outcome | Owning stages |
| --- | --- | --- | --- |
| F42 | Dashboard composition | Nested horizontal/vertical containers, tiled and floating items, alignment, size, padding, z-order, undo and keyboard movement. | S10 |
| F43 | Layout variants | Fixed, range and automatic sizing, plus desktop authoring of desktop/tablet/phone dashboard output variants; preserve each variant and reveal overflow without requiring a phone app UI. | S10 |
| F44 | Filter and highlight actions | Select/hover/menu triggers, field mapping, scope and clear-selection behavior; highlight changes presentation without silently filtering totals. | S10 |
| F45 | Other actions | Parameter/set changes, sheet navigation, URL actions, and deterministic action ordering with cycle prevention. | S10 |
| F46 | Dashboard objects | Text, images, legends, filter/parameter controls, navigation/export buttons, dynamic zone visibility, isolated web objects and extensions with real capability states. | S10, S21 |
| F47 | Stories | Ordered story points with captions, view/filter state, updates, navigation and export; distinguish stories from retired natural-language Data Stories. | S16 |
| F48 | Presentation windows | Open a dashboard/story in its own native window; explicit present/fullscreen command, recoverable navigation and predictable close semantics. | S10, S16 |

An editable chart selection in a dashboard changes the canonical dashboard action state through the coordinator. A renderer may report the selected mark IDs; it must not independently decide source filters or write workbook state.

### 3.5 Geographic and statistical analysis

| ID | Capability family | Required outcome | Owning stages |
| --- | --- | --- | --- |
| F49 | Geographic roles and locations | Latitude/longitude, recognized geographic fields, disambiguation, unknown-location handling, custom geocoding and optional tile providers. | S12 |
| F50 | Map composition | Symbol/filled maps, layers, polygons/lines/paths, extents, legends, selection and offline behavior. | S12 |
| F51 | Spatial data and calculations | Qualified spatial formats, CRS handling, transformations, distance/area and spatial joins; separate geometry semantics from base-map rendering. | S12 |
| F52 | Reference analysis | Reference lines/bands/distributions and supported trend fits, scope, confidence options and model descriptions. | S11, S13 |
| F53 | Descriptive statistics | Histograms, box plots, quantiles, distribution summaries and exact sample/population conventions. | S13 |
| F54 | Forecasting | Pinned exponential-smoothing families/options, model selection, seasonality/horizon, missing-data treatment, interval rules, diagnostics and persisted model/version; generic forecasting alone does not pass. | S13 |
| F55 | Clustering | Pinned algorithm, initialization, scaling, feature selection, stopping, automatic cluster-count selection, null/unclustered handling, membership stability and interpretation. | S13 |
| F56 | Explain results | Show contributing data, calculation context, lineage and applicable Explain Data diagnostics; generic summaries do not replace those behaviors, and association does not establish causation. | S13, S20 |
| F57 | Analytical extensions | Explicit configured Python/R or other analytical service, typed inputs/outputs, row-order contract, versioning, errors and execution policy. | S18 |

An independently implemented model may provide an equivalent workflow before it reproduces a reference algorithm. Keep those states distinct. If a proprietary implementation detail prevents numerical parity, publish the exact gap; do not expand the allowed tolerance until a failing result passes.

### 3.6 Output, interoperability, and service workflows

| ID | Capability family | Required outcome | Owning stages |
| --- | --- | --- | --- |
| F58 | Tabular export | CSV/TSV and qualified XLSX/Arrow/Parquet export of underlying, summary or selected data with clear scope and exact values. | S16 |
| F59 | Visual export | PNG, SVG where the visual supports vectors, and PDF with consistent fonts, filters, legends, size and provenance. | S16 |
| F60 | Print and accessible delivery | Multi-page dashboard/story print, qualified PowerPoint output, page controls and accessible companion tables/descriptions; label rasterized or unsupported output elements. | S16 |
| F61 | Portable sharing | Portable workbook/data-source packages, assets, dependency manifest, missing-source rebind and version migration. | S03, S16 |
| F62 | Tableau workbook migration | Versioned `.twb/.twbx/.tds/.tdsx` importer with a conversion report, original preserved, unknown constructs retained as evidence and no false round-trip claim. | S17 |
| F63 | Hyper interoperability | Qualified `.hyper` read/write through supported environments and a tested adapter; Android support is not assumed. | S17, S18 |
| F64 | Editing history and windows | Transactional commands, undo/redo, autosave recovery, multiple views/documents, conflict handling and stable window ownership. | S03, all authoring stages |
| F65 | Publishing | Publish workbook/data-source revisions to the Cloudy service, with preview, destination, version, permissions and rollback. | S20 |
| F66 | Identity and access | Authentication, role/object permissions, row-level policy and export controls enforced at the service query boundary. | S20 |
| F67 | Managed refresh and delivery | Durable schedules, subscriptions/alerts as cataloged, retries, identities, logs and missed-run recovery on the service. | S20 |
| F68 | Catalog and lineage | Searchable assets, owners, certifications, source-to-field-to-view lineage and dependency/impact analysis. | S20 |
| F69 | Collaboration | Comments, permissions, revision history, review/publish workflow and conflict-safe edits; real-time editing only after a distinct concurrency gate. | S20 |
| F70 | Embedding and extension APIs | Versioned read/render/action APIs and isolated extension contracts; document unsupported Tableau-specific API compatibility. | S20, S21 |
| F71 | Administration | Audit events, job/resource management, health, backups, restore, upgrades and tenancy boundaries. | S20, S22 |
| F72 | Configured intelligence | Current applicable Tableau-style assisted authoring/explanations/metrics, with explicit service configuration, data scope and review before consequential changes. | S21 |

The Hyper API lists Windows, macOS and Linux support. Tableau's Document API is described as unsupported and does not provide a full authoring/rendering engine. These sources support a bounded migration project, not automatic workbook parity. [T36, T37]

## 4. Connector delivery contract

The final catalog is the pinned reference inventory, not just this initial list. All connectors declare `local_native`, `connector_service`, or `compute_worker`; `live`, `extract`, or both; authentication methods; types; pushdown; cancellation; refresh; and test evidence.

| Group | Initial named coverage | Planned route and critical gate |
| --- | --- | --- |
| Local delimited/columnar | CSV, TSV, Parquet, JSON/NDJSON, Arrow | Local parsers; encoding, nested types, bounded import, exact precision. |
| Local workbooks/databases | XLSX, legacy XLS where cataloged, SQLite, DuckDB files | Qualified parser/engine versions; formula cached-value policy, no macro execution, safe read-only import. |
| Statistical and document sources | Applicable SAS/SPSS/R-data and PDF-table connectors from the reference inventory | Named parser and documented supported variants; scanned PDF and arbitrary layout extraction remain explicit separate capabilities, with preview and correction. |
| Open relational | PostgreSQL, MySQL/MariaDB | Driver feasibility determines native versus connector service; TLS, types, query cancellation, read-only source policy. |
| Enterprise relational | SQL Server, Oracle, DB2, SAP HANA, Teradata and other baseline drivers | Service where Android driver support is absent; driver redistribution and authentication checked per package. |
| Warehouses | BigQuery, Snowflake, Redshift, Databricks SQL, Azure Synapse | Native OAuth where appropriate plus service adapters; source cost, pushdown, token expiry and result limits. |
| Query/lake platforms | Trino/Presto, Spark/Hive, Athena and baseline lakehouse sources | Exact protocols and dialect capabilities; no assertion that an arbitrary JDBC/ODBC binary works on Android. |
| Object storage and URLs | HTTPS, S3-compatible, Azure Blob, Google Cloud Storage | Approved download-to-local extract first; range/live access only when separately qualified. |
| SaaS and web | Google Sheets, Salesforce, OData and baseline web connectors | Explicit OAuth/account scope, pagination, rate limits, incremental keys, schema drift. |
| Spatial | GeoJSON and qualified Shapefile/GeoPackage/KML/GDB families | CRS and complete multi-file asset handling; native spatial dependency or explicit worker capability. |
| Published BI/cubes | Tableau sources/extracts, supported OLAP/cube sources | Dedicated interoperability/driver projects; relational SQL is not a substitute for MDX semantics. |

Data movement is a first-class user decision. Opening a local workbook must not silently upload its data because a connector or statistical operator lacks a native implementation. Offer the configured worker with a precise description of the data to be sent, or show the capability as unavailable. Remember a user's deliberately saved connection policy within its defined scope.

## 5. Desktop experience and information architecture

### 5.1 Vocabulary and normal journey

Use **Workspace**, **Workbook**, **Data source**, **Table**, **Field**, **Calculation**, **Worksheet**, **Dashboard**, **Story**, **Extract**, **Refresh**, and **Publish** consistently. An extract is an owned snapshot with provenance; a cache may be incomplete or disposable. A project/workspace groups workbooks and sources; it is not an operating-system directory grant by implication.

The default journey is:

1. Home: open a recent workbook or create one.
2. Connect: pick a local file or an explicitly configured source.
3. Inspect: verify types, schema, preview, and import interpretation.
4. Model: establish relationships or a physical table composition when needed.
5. Analyze: place fields, build calculations, filter, inspect exact results.
6. Compose: arrange worksheets and controls into a dashboard or story.
7. Save/export or publish to a named destination.

Use progressive disclosure for advanced model settings, execution diagnostics, permissions and statistical assumptions. Keep data source, snapshot time, current filter/selection context, and save state discoverable throughout analysis.

### 5.2 Native shell

| Region | Contract |
| --- | --- |
| OS caption | Preserve real system controls, insets and window identity. The app can participate only through supported APIs. |
| Menu and task toolbar | File, Edit, Data, Worksheet, Dashboard, Story, Analysis, View, Window, Help; contextual actions and a command palette. |
| Left supporting pane | Home navigation or worksheet Data/Analytics pane. Search and field properties stay close to the relevant data source. |
| Main work area | Data grid/model, worksheet shelves and plot, dashboard canvas, or story points. It receives the largest useful continuous area. |
| Right inspector | Selected field/mark/axis/layout properties; optional and collapsible. |
| Workbook tabs | Data sources, worksheets, dashboards and stories with labeled types, dirty/error state and overflow handling. |
| Status strip | Exact data/snapshot context, query state, selection/count information and save/recovery state. Detailed diagnostics expand on request. |

Home uses Recents, Favorites, All workbooks, and user-defined workspace/tag groups. Its table includes name, location, modified time, source availability, and recoverable-draft state. This adapts Cloudy's organizational pattern; a host-connection home is inappropriate for a data analysis application.

### 5.3 Geometry and density

Use the supplied values as baselines, with growth for text scaling. Layout uses dp; text uses sp. [CD03, CD04, CD15]

| Token or region | Required baseline |
| --- | --- |
| Toolbar | 56 dp; grow or reflow if content requires it. |
| Navigation | 224 dp default; 192–280 dp adjustable. |
| Inspector | 288 dp default; 240–360 dp adjustable. |
| Status | 28 dp minimum; wrap content when needed. |
| Divider | 1 dp visual; discoverable nonoverlapping drag affordance and keyboard resize/reset. |
| Comfortable density | 48 dp nominal row/control minimum. |
| Standard pointer density | 40 dp nominal row/control minimum after precise-input qualification. |
| Compact pointer density | 32 dp nominal row/control minimum, explicit preference, with accessible target review. |
| Body and labels | 14/20 sp size/line height; density does not shrink text. |
| Metadata | 12/16 sp; never use metadata styling for the only explanation or critical error. |
| Titles | 24/32 sp page, 20/28 sp section/dialog. |

At usable widths below 600 dp, keep the active analytical surface and open navigation/inspector as reachable overlays. At 600–959 dp, allow one supporting pane. At 960–1279 dp, retain navigation plus main and overlay or substitute the inspector. At 1280 dp and above, allow both supporting panes when the main task remains usable. These are Cloudy content thresholds, not renamed Android size classes. Large text or short height can cause earlier reflow.

The shell never gains a horizontal scrollbar. Data grids, dense crosstabs, model canvases and plots may have purposeful local pan/scroll with clear bounds. A narrow desktop window keeps menus, commands and desktop editing semantics; it does not become a phone layout.

Dashboard size and layout variants are saved document properties. Android window resize, shell density and app text scaling must not silently rewrite them. The authoring viewport can fit, zoom or scroll a fixed dashboard while Cloudy panes reflow. Previewing a phone-sized published dashboard is an output-authoring task within the desktop application.

System appearance and Comfortable density are safe initial preferences. Density is explicit, sticky per app and observed across its open windows; incidental mouse/touch events do not switch it. Density-specific small/medium/large spacing is 8/12/16 dp for Comfortable, 8/8/12 dp for Standard pointer, and 4/8/8 dp for Compact pointer. Settings radio rows retain at least 48 dp targets. [CD15]

### 5.4 Color, typography, shape and motion

The declared core pairs are preserved below. Full semantic containers and disabled roles must come from the canonical token source. [CD03]

| Role | Light | Dark |
| --- | --- | --- |
| Canvas | `#F4F7F7` | `#101A1D` |
| Surface | `#FFFFFF` | `#17262B` |
| Surface muted | `#EDF2F3` | `#1C3036` |
| Surface raised | `#E3EAEC` | `#263E45` |
| Text primary | `#182427` | `#E1EBED` |
| Text secondary | `#4C6066` | `#AEC3C9` |
| Primary / on-primary | `#006A6A` / `#FFFFFF` | `#7DD5C7` / `#003A34` |
| Primary container / on-container | `#D2F1EA` / `#005047` | `#164C43` / `#B1F2DE` |
| Accent/link | `#3559C7` | `#B6C5FF` |
| Accent container | `#E4EAFF` | `#2E4076` |
| Control outline | `#667A80` | `#79959E` |
| Decorative divider | `#D3DFE2` | `#35515A` |
| Focus | `#3559C7` | `#BAC8FF` |
| Success foreground | `#216C46` | `#93D7AE` |
| Warning foreground | `#865000` | `#F5C36B` |
| Danger foreground | `#B3261E` | `#FFB4AC` |

Use paired colors, one filled teal primary action per task region, flat working panes and quiet dividers. Corner roles remain 0 for panes, 6 for fields, 8 for buttons, 12 for menus/bounded previews, and 20 for dialogs. Use the 4-unit spacing foundation with the supplied spacing scale; do not invent a fourth density tier. Preferred font candidates remain packaged IBM Plex Sans/Mono with appropriate Noto/system fallbacks after license/version and multilingual review.

The exact spacing scale is 2, 4, 8, 12, 16, 24, 32 and 48. Page/section/label weights are 500; body/metadata weights are 400. Use the initial 2 dp focus ring with a contrasting offset and keep it visible above selection/hover. Disabled controls use explicit tokens and an accessible reason. Unknown/stale state uses neutral styling plus age; an error badge does not erase a row's selection or focus. Success means a confirmed outcome, not merely a configured source or requested save. [CD03, CD05, CD08]

Motion baselines remain 100 ms immediate feedback, 160 ms small transitions, and 220 ms pane/dialog changes. Respect reduced motion. Query completion should update the result and its provenance without decorative chart morphing that hides changing values. Theme changes must not trigger data recomputation.

### 5.5 New visualization design rules

These are **Cloudy Data additions**. They require a token/schema review and do not overwrite operational status colors.

- Separate categorical, sequential, diverging, selection and missing-data palettes from shell success/warning/danger. A teal chart category is not a healthy-state signal.
- Keep category identity stable across views, filters, reopen and appearance changes. Save the mapping by category key and palette version. Appearance may adjust contrast through an explicitly paired palette without changing category meaning.
- Sequential scales encode ordered magnitude; diverging scales record a meaningful midpoint. Mark missing/unknown data with a distinct pattern, symbol or label instead of assigning it an ordinary quantitative color.
- Expose axis range, zero baseline, log scale and aggregation. Default bars to a zero baseline; permit deliberate exceptions with a visible scale indication. Never silently clip negative values or plot nonpositive values on a log scale.
- Use clear units and exact tooltip/table values. Display-format rounding must not alter calculation or export precision.
- Density reduction, sampling, binning and aggregation must be labeled. No silent sample may drive an apparently exact grand total.
- Show filters, denominator, reference period, confidence interval and uncertainty when they change interpretation. Do not describe a correlation or forecast as a proven causal explanation.
- Provide a native accessible result summary and equivalent data exploration. A canvas with an accessible name alone is insufficient.
- Permit user chart styling, but keep app chrome and system controls governed by Cloudy tokens. Embedded images and charts keep their own semantic data colors.

## 6. Screen contracts

Use `DATA-` IDs so they cannot be confused with the supplied Console screen IDs.

| Screen | Main task and primary action | Required interactions and states |
| --- | --- | --- |
| DATA-01 Home | Find/open work; New workbook when empty | Recents/favorites/tags, table selection, open in new window, missing location, recovery draft, long names, no fabricated examples in production recents. |
| DATA-02 Connect | Select a source; Import or Connect | File picker, named accounts/endpoints, preview before import, unsupported connector reason, permission denied, token expired, cancellation. |
| DATA-03 Inspect data | Validate schema and types; Apply import | Virtual grid, profile side panel, sample/full label, conversion errors, rejected rows, source size unknown, schema drift. |
| DATA-04 Model | Define logical/physical structure; Apply model change | Drag/keyboard add table, relationship editor, physical join canvas, cardinality hints, ambiguous paths, duplicate keys, disconnected tables. |
| DATA-05 Worksheet | Explore one view; contextual Add field or Apply | Data pane, Rows/Columns/Filters/Pages, mark controls, plot, legends, data inspection, field errors, empty result, stale query, cancellation. |
| DATA-06 Formula editor | Create or repair a calculation; Apply | Typed completion, syntax locations, dependency list, aggregate-level errors, preview on labeled sample, keyboard editing, CJK composition. |
| DATA-07 Dashboard | Compose an interactive analytical view; Present | Outline/layers, tiled/floating layout, selection/resize handles, keyboard move/resize, filters/actions, focus route out of canvas. |
| DATA-08 Story | Explain a sequence; Add point or Present | Ordered points, captions, saved state, update/revert, unsaved point state, keyboard reordering. |
| DATA-09 Present | Explore a dashboard/story | Native independent window, explicit fullscreen, visible return/exit, filter accessibility, no authoring chrome requirement. |
| DATA-10 Data preparation | Build reusable transforms; Run or Apply | Step graph plus step table, schema preview, rejected-row evidence, step disable/reorder, cancel, dependency failure, prior result retained. |
| DATA-11 Jobs and sources | Monitor imports/refreshes; contextual Retry/Cancel | Source/workbook/destination identity, real progress phase, bytes/rows when known, completion unknown, credentials required, resume safety. |
| DATA-12 Save/export/publish | Create a named output; Save/Export/Publish | Scope/filter preview, exact destination, overwrite/version conflict, data-inclusion choice, disclosure of remote upload, partial/failed write. |
| DATA-13 Settings | Configure appearance/input/storage/connections | Three densities, System/Light/Dark, shortcut discovery/remap, cache budget, data movement defaults, account removal consequences. |
| DATA-14 Diagnostics | Understand a failure; Copy/export redacted report | Engine/renderer versions, device profile, query ID/revision, measured memory, last error, source metadata redaction, no raw data by default. |
| DATA-15 Shared workspace | Find governed assets and publish revisions | Authentication, role-aware actions, freshness, comments, lineage, unavailable/offline state; service permissions remain authoritative. |

Each screen needs empty, loading, ready, partial, stale, failed, permission-required and recovery states where applicable. An incomplete feature can be disabled with an accurate explanation in a development build; it cannot display a success simulation when invoked.

## 7. Desktop interaction contract

### 7.1 Input ownership

One window has one focused recipient: menu/palette, native editor, grid, chart, model or dashboard canvas, or modal. OS-reserved input wins; active IME composition/modal behavior comes next; valid contextual app commands follow. Hover does not change focus. Dialog dismissal restores the initiating control or a documented surviving neighbor. [CD06]

Ordinary workbench behavior applies: Ctrl+C copies selected values/text, Ctrl+V pastes into an editable target, and right-click opens a context menu. Selecting a chart or grid does not overwrite the system clipboard. Clipboard export chooses raw values, formatted values, or image deliberately. Formula/editor undo takes precedence while its local draft is active; workbook undo applies after a committed analytical command.

### 7.2 Initial shortcut map

All chords are proposed defaults and must be conflict-tested on actual Googlebook keyboard layouts. Commands retain a menu/palette route if a chord belongs to the OS.

| Command | Proposed chord | Scope |
| --- | --- | --- |
| New workbook / Open / Save | Ctrl+N / Ctrl+O / Ctrl+S | Workbench, except an active editor's explicitly local handling. |
| Save as | Ctrl+Shift+S | Workbench. |
| Undo / Redo | Ctrl+Z / Ctrl+Shift+Z, with Ctrl+Y where compatible | Focused text draft first, then workbook command history. |
| Copy / Paste / Select all | Ctrl+C / Ctrl+V / Ctrl+A | Focused text/grid/chart context; never global row selection unexpectedly. |
| Find | Ctrl+F | Focused data pane/grid or current document surface. |
| Command palette | Ctrl+Shift+P | Qualified local shortcut. |
| Move focus between panes | F6 / Shift+F6 | Workbench pane cycle, always with a visible alternative. |
| Context menu | Shift+F10 or hardware Menu key | Focused item. |
| Close view | Ctrl+W | Current tab/view with dirty-state handling; does not silently cancel other windows' jobs. |
| Dismiss/cancel gesture | Escape | Top overlay or active drag; not a blanket command to abort every query. |
| Delete placement/object | Delete | Selected shelf placement/dashboard object; source data remains intact. |

### 7.3 Drag, selection and concurrent work

- A field drop displays its destination and operation before commit: add placement, replace field, filter, or encode. Use an explicit modifier or menu for duplication. Invalid drops explain type/scope incompatibility.
- Keyboard alternatives include Add to Rows/Columns/Color/Filters, move before/after, choose target container, resize via numeric properties, and reorder story points.
- External file drops capture the intended workbook/window and request URI access. A later tab switch cannot retarget an import. Revoke a source grant safely without erasing imported snapshots.
- Grid selection supports cell/row/column and additive/range modes as explicitly implemented. Chart selection reports stable keys and its current result revision.
- Multiple windows of one workbook share a single durable document authority and revision stream. Pan, zoom, focus and temporary selection can remain window-local. Persistent edits are serialized and reflected across views.
- Closing a view, closing a workbook, cancelling a query, stopping a refresh and discarding a draft are distinct actions with distinct consequences.

Closing a worksheet/dashboard/story view instance does not delete its definition from the workbook. Delete sheet/object is a separate command with dependency handling and undo. A tab that cannot be hidden independently must use its documented close-workbook behavior rather than silently treating Close as Delete.

## 8. Quality and acceptance targets

### 8.1 Proposed workload profiles

These are engineering targets to measure in S01 and refine in S19, not performance claims or guarantees based solely on row count.

| Profile | Fixture | Intended behavior |
| --- | --- | --- |
| D0 correctness | 10–1,000 rows with adversarial types, duplicates and missing values | Exact deterministic numerical and workflow fixtures. |
| D1 everyday | 1 million rows × 20 columns; separately vary strings, cardinality and skew | Interactive authoring with bounded memory and responsive cancellation. |
| D2 demanding local | 10 million rows × 30 columns, multiple relations, sort/window operations | Spill or explicit resource refusal; persistent UI responsiveness; no blanket in-memory promise. |
| D3 beyond local envelope | 100 million rows or workloads larger than measured native limits | Explicit extract/aggregation/worker choices; local capability remains honestly bounded. |

Record input bytes, schema, cardinality, compression, query, device RAM/SoC/storage, Android/API/page size, thermal state, cache state, engine build and renderer build. Compare matched conditions, report p50/p95, and retain failures. Core UI feedback targets are under 100 ms for command acknowledgement and under 200 ms for the first visible/usable menu or pane response; animation completion is measured separately and may follow the inherited 220 ms pane/dialog timing. Initial query targets are p95 under 1 s for selected warm D1 summaries and under 3 s for selected cold D1 summaries, subject to S01 feasibility. A query exceeding a target shows accurate progress and cancellation, not a fake completed chart.

### 8.2 Release-blocking qualities

The following block acceptance of the affected capability or release:

- Incorrect totals, filter scope, grain, precision, denominator or time-zone interpretation.
- Lost accepted workbook edits, silent source replacement, corrupt save, or false “saved/refreshed” state.
- A result from an old query revision appearing as the answer to the current controls.
- An essential control inaccessible by keyboard, obscured at supported scaling, or trapped inside a canvas/WebView.
- Undisclosed data sampling or network transfer.
- A claimed connector, format, ABI, native input behavior or service policy without matching evidence.
- Credentials inside workbooks, logs, diagnostic exports or renderer messages.
- A remote row-level access policy implemented only as a removable dashboard filter.

Text targets remain at least 4.5:1 for ordinary text and applicable 3:1 goals for large text and meaningful controls. Test light/dark, all three density modes, 100/125/150/200% text scaling where supported, narrow and short windows, external display changes, physical keyboard/mouse/trackpad, TalkBack, reduced motion, CJK and RTL. Passing a contrast script or browser screenshot does not certify native accessibility. [CD08, CD12]

In short/scaled dialogs, the title, explanation and complete frozen operation target must be reachable through normal scrolling while Confirm/Cancel remain reachable. Opening an editor for an offscreen selected item reveals that editor; ordinary typing or recreation must not repeatedly jump its scroll position. Grid virtualization, sort/filter and refresh preserve or explicitly reconcile focused-cell and selection identity. Chart selection needs keyboard exploration or an equivalent native data operation, not only a drag gesture. [CD11, CD12]

## 9. Completion and change control

File 03 defines staged releases. S22 produces a capability report with the exact pinned baseline, verified/partial/blocked counts, supported devices, connector modes, format versions and service requirements. It may declare desktop parity only when all applicable desktop requirements pass. It may declare broader service parity only when that separate lane passes. It must never describe the initial vertical slice as a complete Tableau replacement.

If a feasibility experiment forces a different engine, renderer or deployment route, preserve this product contract, record the evidence and change the implementation decision through an ADR. A limitation may delay a requirement or require an explicitly approved scope change; it does not erase the requirement automatically.

**Next action for the coding agent:** read all three files, inspect the actual repository and canonical design assets, then execute S00 followed by the bounded S01 feasibility packet. Do not start by generating the whole interface or by installing a server BI dashboard and calling it the product.
