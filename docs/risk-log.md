# Cloudy Data risk log

Status vocabulary: `open`, `mitigated`, `accepted`, `blocked`, `closed`.
This log records engineering risk, not stage completion.

| ID | Risk | Evidence / impact | Mitigation | Status |
| --- | --- | --- | --- | --- |
| R01 | Native DuckDB Android support is experimental and pinned to one commit. | v1.5.6 arm64/x86_64 builds and device query tests pass; cancellation/memory gates remain. | Freeze commit, rebuild script, ABI/page checks; keep Wasm alternative in architecture. | open |
| R02 | Current result transfer materializes a bounded row set instead of Arrow IPC. | Worksheet queries cap at 1,000 rows; adapter default 10,000. | Add streaming/paged transfer and Arrow IPC in S01.6/S04; never claim large-result readiness. | open |
| R03 | Import can leave orphaned files if metadata publication fails after data move. | Receipts, previous-file backup and reconciliation now cover constructed interrupted/published/prepared states. | Add real process-kill/fault injection at every boundary; verify directory durability. | mitigated |
| R04 | Source replacement overwrites previous snapshot/original. | Dialog discloses; no version retention. | Add previous-version retention and undo before R1 acceptance. | open |
| R05 | CSV import is not cancellable and stages up to 512 MiB. | Copy/scan occurs in a coroutine without interrupt plumbing. | Add cancellable streaming and explicit cancellation in S04; label current limit. | open |
| R06 | Chart is a provisional native bar/table surface, not the required bounded Vega renderer. | Offline Vega probe passes on both targets; product integration, death/resize/selection/export remain. | Integrate only after S01.7 lifecycle qualification; keep native accessible equivalent. | mitigated |
| R07 | Category color stability is based on a label hash, not a persisted palette-version map. | Stable for unchanged labels; collisions possible. | Persist category-key→palette mapping in workbook; test across filters/reopen/appearance. | open |
| R08 | No canonical upstream token source. | Provisional tokens; missing disabled/chart roles. | Keep single generated source; reconcile when assets are supplied; independent contrast/visual review. | open |
| R09 | Multiwindow and coordinator semantics incomplete. | Single Activity route; save mutex prevents stale document overwrite; no cross-window revision protocol. | Implement WorkbookCoordinator, command revisions, conflict handling in S03. | open |
| R10 | No 16 KiB runtime device. | ELF alignment checked, runtime profile absent. | Retain unqualified claim; test on 16 KiB hardware when available. | blocked |
| R11 | No licensed Tableau reference. | No differential parity results. | Keep reference profile pending; do not claim Tableau comparison. | blocked |
| R12 | SAF MIME reporting varies by provider. | Picker filters hid a valid `text/comma-separated-values` file; broad picker now validates content. | Validate delimited text in importer; add explicit size/encoding checks and error copy. | mitigated |
| R13 | The device runs as Android user 10; adb shell pushes to user 0 are invisible to SAF. | Manual qualification required MediaStore insertion for the picker. | Document the environment; use test-only MediaStore publisher for picker review. | mitigated |
| R16 | Recovery tests simulate file/receipt states rather than killing the process at each boundary. | No actual crash injection; storage-full and rename-failure cases untested. | Add a fault-injection harness and storage-exhaustion run. | open |
| R17 | Vega scene/DOM counts are not stable evidence of every visual mark. | Probe observed 2 data values but one scene rect and SVG paths; screenshot is the visual evidence. | Add pixel/semantic export checks; do not infer mark correctness from counts alone. | open |
| R18 | Renderer death could leave a dead WebView or false completed chart. | `onRenderProcessGone` returns true and reports an error, but no forced-death test exists. | Add a supported renderer-termination probe and replay accepted state; bound restarts. | open |
| R14 | Read-only Compose UI test coverage is thin. | Real device screenshots/UI tree inspected; no automated UI assertions. | Add Compose UI tests for create/import/query/reopen and focus/IME cases. | open |
| R15 | The UI lacks a command palette and broad desktop command routing. | Settings is real and persisted; no inactive settings rows remain. New/save/import have direct controls, but keyboard chords and palette are absent. | Add a real command registry/palette and conflict-test shortcuts in S02. | open |
