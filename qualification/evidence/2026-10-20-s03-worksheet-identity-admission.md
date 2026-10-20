# S03.4 per-document worksheet identity admission

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F64; Q33/Q34.
**Status:** verified for the bounded per-document uniqueness and compatibility cases below; full S03/package/identity validation remains partial.
**Implementation commit:** `8203fc3fe1ba2f5c484a1bb73455d33642e5e2e0`; all129 hashes verified against committed blobs.
**Source base:** `271bee898a17da346d2425e782273d6d5ac419da`.

## Readiness, ownership and contract

Clean main/upstream/live origin matched271bee8 after the shared version-reader
implementation29780fa and binding push. All127 preceding source/fixture hashes
were verified against committed source; native126/126,JVM75/75 and default/x86
build/lint/token checks establish the unchanged checkpoint/migration substrate.
Already-read plans/master, scoped original Cloudy assets atbccb328 and accepted
ADR0003/0008/0009/token authority remain unchanged.

Independent source review finds that duplicate worksheet IDs are admitted even
in canonical v3 records. Targeted SetChart maps every matching ID while display
selection resolves the first, allowing one command to alter multiple sheets.
The first unchanged-production native probe will distinguish this from a unique-
ID control. Scope is uniqueness within each document head/undo/redo, not across
history revisions. Blank/type/dangling-active policy remains separate; historical
missing/null active-ID fallback and empty worksheet lists remain compatible.

Root owns WorkbookDocument identity admission and Checkpoint constructed-history
validation, docs/evidence. Separate workers own WorksheetIdentityRefusalTest and
WorksheetIdentityMigrationTest; a third independently reviews combined source,
independent expected results and final artifact binding. Files are disjoint and
only root integrates/builds/runs native checks. No query, renderer, token, worker
capability or other stage authority is introduced.

## Native before/after and verification

The unchanged-production baseline ran two tests. The real unique-ID control
passed; the duplicate-ID probe failed as expected: differently named/charted
sheets sharing one ID were admitted, a single SetChart changed both titles under
one receipt at revision5, nativeEast15/West20 still executed and both recovery
candidates were removed. Raw failure, initial frozen test/source hashes and
runtime/test APK bindings are retained in the matching android-test-results path.

Root added one exact decoded-ID uniqueness helper called by Document.fromJson
and Checkpoint.validate for head and separately each undo/redo entry. There is no
cross-revision set, ID trim/case fold, Store change or schema/version replacement.
First post-fix focused9/9 passes in2.081s. The final actual combined revision
passes135/135, no skips,61.609s on existing API36 x86_64 emulator-5580: new refusal4
and compatibility5 join all126 preceding regressions, including shared versions,
filters/aggregation/references, existing writer deaths, Home input, coordinator,
queries/recovery/persistence and palette. No new identity-specific kill or new
render/input acceptance is inferred.

Adjacent/nonadjacent legacy1/2 duplicates and validly rehashed v3head/undo/redo
refuse load/open/save beyond checksum admission. Exact canonical/candidates and
source/Parquet remain unchanged; an existing backup sentinel also remains exact
with no new backup. Direct decoded duplicates and constructed head/undo/redo
checkpoints refuse serialization/save; no atomic-write phase is reached and valid
canonical/source remain exact. The valid-read/temp-cleanup limit below is retained.

Valid distinct sheets keep independent names, full chart definitions and filters.
One SetChart changes only its target, and undo/reopen/redo/duplicate preserve
history/receipts, increasing revisions and no replay. IDs repeated across revisions
are accepted; Sheet-A, sheet-a and padded Sheet-A stay three distinct definitions
with only the padded target edited. Legacy originals/backups remain byte-exact;
missing/null active-ID fallback and empty lists stay accepted without invented
sheets. Native KEEP East15, unfilteredWest20/East15, exact underlyingEast10/East5
and source bytes remain correct; empty-sheet files use a separate real source query.

Final x86/default dual-ABI debug/test builds pass. Default ZIP contains arm64-v8a
and x86_64; JVM75/75,no skips; lint0 errors/6 retained warnings; tokens current and
contrast40/40. No assertion/check is weakened or disabled. Artifacts under
artifacts/2026-10-20-s034-worksheet-identity/ record129 source/fixture hashes and
runtime/test/default APK sizes, hashes and ABIs. Raw native/build/JVM/lint/token
results remain under android-test-results/2026-10-20-s034-worksheet-identity/;
only redundant terminal blank lines are removed from text logs. Independent
combined source/test/oracle review found no material issue. The final independent
audit verifies all129 current source hashes, source-manifest SHA-256
3817154e8f4e9604d38cdc05206830afc3c77e1c582775b8310c9b02bfad55cd, actual
runtime/test/default APK bindings and raw native/JVM/lint/build/token results.
No material evidence discrepancy remains. All129 hashes also match committed source.

Rejected malformed stored packages must preserve canonical files, candidates, existing
backups, original source and Parquet through load/open/save. Valid unique sheets
must retain exact targeted one-sheet edits, history/reopen/duplicate receipts and
independently expected native totals/source rows through numeric1/2 migration.
Constructed duplicate candidates must refuse serialization/atomic publication,
without changing canonical/source/backups. A valid canonical read preceding such
candidate validation may legitimately clean abandoned temps under the existing
rule; constructed-candidate refusal does not newly promise their retention.

## Limits

No full identity/type/schema/package admission, strict raw JSON parser, source-
version history, Room, cross-process writer/power-loss/full lifecycle acceptance
is inferred. Existing API36 x86_64 emulator-5580 is reused without boot/resize/
density/preferences changes; physical Googlebook remains owner-held.16KiB runtime
and licensed Tableau are unavailable. S11.C0 partial,S13.C0/S15.C0 absent remain
mandatory when worker-backed dependencies apply. Development agents/debug writer
probes do not establish those capabilities. Families/stages remain partial.

## Exact next gate

S03.4 strict legacy revision admission,F01/F64,Q33/Q34. Independent source review
finds getLong can turn legacy revisionr+0.75 or string"r" into r, allowing a later
Rename(expectedRevision=r) to publishr+1. Current v3 canonical guards already refuse
that normalization. This is source-only until its own native probe. First run
fraction/string v1/v2 with recovery candidates; require eventual byte-preserving
refusal and valid integral controls/migration/history. Preserve exact Long
revisions above2^53 (e.g9007199254740993); a future reader must not route all Longs
through Double. Keep existing supported revision bounds and Math.addExact.
