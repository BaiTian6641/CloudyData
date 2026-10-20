# ADR 0009 — Atomic command receipts and durable history in the local metadata file

**Date:** 2026-10-20
**Status:** accepted for the bounded S03.3 local slice; Room migration remains open
**Amends:** ADR0003's local JSON authority and ADR0008's workbook format version.

## Decision and authority

The owner-directed continuation selects the next ready S03.3 rename/chart journal
slice. The architecture destination is Room metadata plus durable typed commands.
ADR0003 already makes app-private `workbook.json` authoritative for the current
local slice. Extend that same authority before introducing a second store: the
accepted document, command receipts and undo/redo checkpoint are one fsynced
JSON temporary file, atomically renamed to `workbook.json` before publication.
This is an interim implementation, not Room integration or full S03 completion.
No journal file competes with the workbook as a second source of truth.

- Format v3 stores a version-1 `commandJournal` envelope with the exact head hash,
  bounded undo/redo document snapshots and typed acceptance receipts. Canonical
  structural hashes detect corruption; they are not signatures or trust grants.
- Typed Rename and SetChart requests contain command/workbook/window identity,
  expected revision, explicit payload and (for chart) affected worksheet identity.
  Opening a file reconstructs state; it never executes or replays saved commands.
- A retained identical request resolves before stale-revision rejection and causes
  no new revision/write. Reusing an ID with changed request data is refused. The
  original accepted revision and the current document are distinct reply fields.
  ViewModel retry routing never treats a duplicate's foreign current revision as
  a new command from its own window.
- A new typed request, including a no-op payload, records acceptance under one new
  revision. No-op payloads add no undo step or redo branch. Empty undo/redo is a
  no-op. Generic coordinator edits also persist their bounded content history;
  their conversion to fully typed, deduplicated commands remains separate work.
- The combined undo+redo bound is 50. Retain at most 4096 typed receipts without
  eviction; refuse further new typed commands explicitly at capacity. Automatic
  compaction/archival and longer retention need their own tested policy. Metadata
  is bounded to 32 MiB before parsing/writing; refusal preserves accepted work.
- A raw Store save or receipt-backed import is an atomic history barrier and keeps
  receipt tombstones. Every history snapshot must belong to the current workbook
  and exact current data snapshot. Source-side operations never replay through
  undo, redo, reopening or typed-command retries.
- Unknown, missing or malformed v3 journals, checksum failure, foreign snapshot
  history and future versions fail closed without rewriting the source file.
  Canonical round-trip checks prevent permissive model parsers from silently
  changing the meaning of stored journal records.
- v1/v2 documents without a journal load through the existing model migration.
  Before their first accepted save, preserve exact original bytes under
  `migration/workbook-v<version>-original.json` through temp+fsync+atomic move.
  The v3 version prevents old builds from saving away durable history.

## Qualification and remaining scope

Independent expected-state tests cover reconstruction, undo/redo, duplicate and
mismatched requests, stale revisions, write denial, original preservation, corrupt
journals and real snapshot replacement. A debug-only nonexported bound service
kills its separate Android writer process after temporary-file fsync and after
atomic replacement; native Binder death must be observed before recovery checks.
Those probes qualify single-writer atomic visibility after process death. They do
not prove power-loss durability, concurrent writers across processes, full product
process/lifecycle restoration or hardware behavior. Directory fsync remains a
best-effort platform operation and is not advertised as a power-loss guarantee.

Room, a complete typed command catalog, formula drafts, conflict proposal/reapply,
command-retention compaction, durable source-version history and cross-process
query ownership remain separate gates. S11.C0/S13.C0/S15.C0 are unchanged.
