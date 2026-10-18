# Cloudy Data risk log

Status vocabulary: `open`, `mitigated`, `accepted`, `blocked`, `closed`.
This log records engineering risk, not stage completion.

| ID | Risk | Evidence / impact | Mitigation | Status |
| --- | --- | --- | --- | --- |
| R01 | Native DuckDB Android support is experimental and pinned to one commit. | v1.5.6 arm64/x86_64 builds and device query tests pass; cancellation/memory gates remain. | Freeze commit, rebuild script, ABI/page checks; keep Wasm alternative in architecture. | open |
| R02 | Current result transfer materializes a bounded row set instead of Arrow IPC. | Worksheet queries cap at 1,000 rows; adapter default 10,000. | Add streaming/paged transfer and Arrow IPC in S01.6/S04; never claim large-result readiness. | open |
| R03 | Import can leave orphaned files if metadata publication fails after data move. | State machine not implemented; no crash-injection test yet. | Add operation receipt and reconciliation in S03/S04; preserve prior state. | open |
| R04 | Source replacement overwrites previous snapshot/original. | Dialog discloses; no version retention. | Add previous-version retention and undo before R1 acceptance. | open |
| R05 | CSV import is not cancellable and stages up to 512 MiB. | Copy/scan occurs in a coroutine without interrupt plumbing. | Add cancellable streaming and explicit cancellation in S04; label current limit. | open |
| R06 | Chart is a provisional native bar/table surface, not the required bounded Vega renderer. | S01.7 and S11 not implemented. | Implement offline Vega host, CSP/interpreter, origin checks, accessible equivalent, then retire or retain provisional backend by ADR. | open |
| R07 | Category color stability is based on a label hash, not a persisted palette-version map. | Stable for unchanged labels; collisions possible. | Persist category-key→palette mapping in workbook; test across filters/reopen/appearance. | open |
| R08 | No canonical upstream token source. | Provisional tokens; missing disabled/chart roles. | Keep single generated source; reconcile when assets are supplied; independent contrast/visual review. | open |
| R09 | Multiwindow and coordinator semantics incomplete. | Single Activity route; save mutex prevents stale document overwrite; no cross-window revision protocol. | Implement WorkbookCoordinator, command revisions, conflict handling in S03. | open |
| R10 | No 16 KiB runtime device. | ELF alignment checked, runtime profile absent. | Retain unqualified claim; test on 16 KiB hardware when available. | blocked |
| R11 | No licensed Tableau reference. | No differential parity results. | Keep reference profile pending; do not claim Tableau comparison. | blocked |
| R12 | SAF MIME reporting varies by provider. | Picker filters hid a valid `text/comma-separated-values` file; broad picker now validates content. | Validate delimited text in importer; add explicit size/encoding checks and error copy. | mitigated |
| R13 | The device runs as Android user 10; adb shell pushes to user 0 are invisible to SAF. | Manual qualification required MediaStore insertion for the picker. | Document the environment; use test-only MediaStore publisher for picker review. | mitigated |
| R14 | Read-only Compose UI test coverage is thin. | Real device screenshots/UI tree inspected; no automated UI assertions. | Add Compose UI tests for create/import/query/reopen and focus/IME cases. | open |
| R15 | The UI lacks a Settings screen and command palette. | No settings actions exposed; no inactive rows kept. | Implement DATA-13/DATA-01 commands with real persistence in S02. | open |
