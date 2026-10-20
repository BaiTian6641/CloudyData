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
- Typed Rename, SetChart, AddWorksheet and SetFavorite requests contain command/workbook/window
  identity, expected revision and explicit payload. Chart edits identify their
  target sheet; additions carry the frozen new sheet ID and name.
  Opening a file reconstructs state; it never executes or replays saved commands.
- SetFavorite carries an explicit desired Boolean, never a replayable toggle.
  Home freezes that value and expected revision from its displayed summary. A
  per-Home controller keeps one pending/failed proposal per workbook, permits
  independent workbook actions, and retries the exact request. Ordinary library
  reloads and filter/search changes cannot discard an unresolved proposal.
- A Home favorite remains pending through real library refresh; success requires
  the target summary at least as recent as the returned current document. Proven
  accepted revision survives later retry/refresh failures, with saved-locally copy.
  Dismiss/reload removes only the local proposal and never undoes accepted data.
  This controller is an in-memory view projection, not persistent intent recovery
  after leaving Home, configuration recreation or whole-product process death.
- AddWorksheet creates a default unmapped Table worksheet without changing the
  stored reopen default. IDs retained by current content, either history direction
  or accepted AddWorksheet receipts cannot be recycled under a new request. An
  identical retry after undo returns its original acceptance without recreating
  the sheet. Only the requesting view activates a still-present sheet after proven
  acceptance; other views keep their local selection. The first unused default
  Sheet N label is selected once from canonical state at first dispatch.
- The additive command kind keeps the v3/journal-v1 envelope. Readers that do not
  recognize it fail closed on the unknown command kind without rewriting the
  file, rather than silently dropping receipts. Existing Rename/SetChart records
  retain their exact shape and remain readable.
- A failed view edit retains its first frozen retry. Later queued edits and
  imports are explicitly refused before dispatch until that failure is retried;
  they cannot overwrite the retry or display Saved. Refused later actions are
  not automatically replayed. Repeated Save invocations cannot reuse a completed
  retry token. Deterministic request-validation failures are Change not applied
  results without a retry token, so a corrected action can be submitted. Full
  preservation/review/reapply of multiple rejected proposals remains open.
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
- Each head and undo/redo document requires unique decoded worksheet IDs by exact
  equality. A targeted chart command cannot be admitted against two definitions
  sharing one ID. IDs reused across different revisions are normal; case/padding
  are preserved, with no new blank/type/active-reference policy. Document decoding
  rejects malformed stored packages before cleanup, and checkpoint validation
  rejects constructed duplicate head/history before serialization/publication.
  A valid canonical read may still clean abandoned temps before a later invalid
  constructed candidate is rejected under the existing Store ordering.
- Unknown, missing or malformed v3 journals, checksum failure, foreign snapshot
  history and future versions fail closed without rewriting the source file.
  Canonical round-trip checks prevent permissive model parsers from silently
  changing the meaning of stored journal records.
- Workbook schema selection and legacy backup naming share the same decoded
  version reader: only finite integral Number values in1..3 are accepted, with
  range validation before Int conversion. Strings, fractions and wrapped large
  integers cannot select a legacy branch. Equivalent supported decimal/exponent
  numeric forms retain their meaning; raw JSON lexical validation is separate.
- v1/v2 documents without a journal load through the existing model migration.
  Present filter records must decode completely: supported typed version/mode,
  known fields, nonblank string anchors,1–2001 complete text/NULL marks and
  consistent series presence. Invalid records propagate failure before canonical
  cleanup or save; they cannot disappear into an unfiltered query. Missing/null
  filters and absent optional mapping defaults remain valid; nonnull wrong-type/
  blank IDs refuse, while unresolved nonempty IDs remain available for repair.
  Repeated OR terms and empty/whitespace domain strings are preserved exactly;
  duplicates do not multiply source rows. Unrelated legacy extensions remain in
  the original backup rather than forcing a blanket raw-roundtrip migration.
  Secondary aggregation defaults to SUM only for an absent key; any present
  value must be an exact supported enum string, even on an inactive channel.
  Malformed/null data cannot turn persisted intent into a different aggregation.
  Recognized enum syntax does not qualify unsupported query combinations.
  Before their first accepted save, preserve exact original bytes under
  `migration/workbook-v<version>-original.json` through temp+fsync+atomic move.
  The v3 version prevents old builds from saving away durable history.
- After a canonical checkpoint fully validates, load may remove abandoned direct
  regular `workbook-<digits>.tmp` files under the same-process root write lock.
  A shared per-root active-temp set protects reentrant observers; concurrent
  same-process readers wait for the writer. Candidate symlinks, directories and
  other names are untouched. Missing/corrupt canonical metadata retains every
  candidate and never triggers promotion. Permission-denied cleanup is a logged
  best-effort failure and does not make a valid workbook unavailable. This rule
  does not establish cross-process writer exclusion.

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
