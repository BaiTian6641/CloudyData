# S03.4 strict present workbook-favorite admission

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F64; Q33/Q34.
**Status:** verified for the bounded decoded favorite cases below; S03/full package validation remains partial.
**Source base:** `14b6a6a1b00abed86823f24ad3935d3f8fd0c2f6`.

## Readiness and ownership

Continued clean main/upstream/live origin after revision implementation eb930b7
and source binding 14b6a6a. All 131 committed source/fixture hashes match; native
143/143 and JVM 75/75 establish the current strict revision/version/worksheet-ID,
checkpoint/history/migration and typed SetFavorite substrate. Already-read plans,
master instructions, scoped original Cloudy assets bccb328 and ADR0003/0008/0009
remain authoritative. The design/token source remains unchanged.

Root owns only the narrow WorkbookDocument favorite reader and docs/evidence.
Independent workers own WorkbookFavoriteRefusalTest and WorkbookFavoriteMigrationTest
in disjoint files. A third reviews actual combined source, independent oracles and
artifact binding. Only root integrates, builds and uses the existing emulator.

## Contract and discriminating probe

The current optBoolean reader accepts legacy strings as Booleans and substitutes
false for malformed present data. Store.list projects the normalized value and an
unrelated Rename can persist it. The native baseline below confirms this path.
Missing favorite intentionally defaults to false in existing legacy migration tests;
present false and true are supported. All other present types must be refused before
stored cleanup or publication, with exact metadata, candidates, backups and source
retained. Current v3 raw head/history checks remain authoritative.
Missing-key compatibility applies to legacy documents; a stored v3 document with
the member removed still fails the existing canonical round-trip guard. Existing
Store.list omits malformed packages after failed loading; no new repair UI is claimed.

The unchanged-production baseline passed all three compatibility tests and failed
one refusal probe as expected (4 tests, 1 failure, 2.688 s). Ten independent v1/v2
files containing strings "true"/"false", an unknown string, number 1 or explicit
null were accepted. Actual HomeLibraryController reload projected the guessed
Boolean; one unrelated Rename published revision 5 from 4 with a Boolean persisted
value, one receipt and one undo entry. Both recovery candidates were removed.
Native East 15 / West 20 still executed. Raw baseline results, frozen test-source
hashes and runtime/test APK hashes are retained before the production change.

One private reader now preserves absent=false and requires a present value to be
an actual Boolean. Only the former optBoolean call changed. No Store, command,
Home controller, renderer or v3 raw-content guard changed.

Valid absent/false/true documents must retain exact legacy originals, library
projection, explicit desired-value SetFavorite, history/reopen/duplicate receipt
semantics and native East 15 / West 20 plus exact source East 10 / East 5. The three unchanged compatibility tests passed the baseline and remain frozen.
Focused native verification passes **7/7 in 4.183 s**. Four refusal tests cover 23
malformed present values in direct v1/v2/v3 decoding and stored legacy files,
actual JSONObject(Map) nonfinite Number objects, zero atomic-write phases,
library omission and exact metadata/candidate/source/existing-backup preservation.
Real SetFavorite(true), Rename and Undo establish distinct head/history Boolean
states before validly rehashed v3 head/undo/redo mutations; malformed present fields
and missing members refuse beyond checksum checks. No invalid nonfinite JSON text
acceptance is claimed.

Three positive tests preserve absent/false/true legacy library projection and
read-only metadata, exact migration originals, both desired-value directions,
accepted no-op receipts without a new undo step, duplicate acknowledgement before
and after undo, fresh reopening and redo. Current exact Booleans with existing
history/receipts also remain intact. Real native KEEP East 15, unfiltered West 20 /
East 15, underlying East 10 / East 5 and CSV/Parquet bytes are independent oracles.

Final combined native selection passes **150/150**, no skips, in **74.717 s** on
existing API36 x86_64 emulator-5580. Seven new tests join all 143 preceding affected
regressions, including the strict revision/version/identity readers, current raw
and history guards, real writer process deaths, existing Home UI, shared windows,
queries, recovery and palette. No new favorite-specific death or UI claim follows.

Final x86 and default dual-ABI debug/test builds pass. Default APK retains arm64-v8a
and x86_64; JVM 75/75 with no failures/errors/skips; lint zero errors and six retained
warnings; tokens current and contrast 40/40. No test/assertion was weakened.
Raw before/after/build results are under
`android-test-results/2026-10-20-s034-workbook-favorite/`; only redundant trailing
blank lines were trimmed. Source/APK binding is under
`artifacts/2026-10-20-s034-workbook-favorite/`, including 133 source/fixture hashes
and source-manifest SHA-256
`2b9c8db83d4e37205fd4e9897e7cff0dbe38c24bf50927b7ed562e29c85083c5`.

Independent review of the actual reader, seven frozen tests and retained baseline
found no material issue. The independent final audit verified all 133 current
source/fixture hashes, the source manifest digest, actual runtime/test/default APK
hashes/sizes/ABIs and raw native/JVM/build/lint/token results. No material discrepancy
remains.

## Limits

This qualifies the decoded document favorite field. Full schema/type/package
validation, raw JSON lexical rules, library repair/error UI, Room, source-version
history, cross-process ownership and full lifecycle/power-loss acceptance remain
open. Existing API36 x86_64 emulator-5580 is reused without boot/resize/density or
preference changes. Physical Googlebook remains owner-held; 16 KiB runtime and
licensed Tableau are unavailable. S11.C0 remains partial; S13.C0 and S15.C0 remain
absent and mandatory when worker-backed dependencies apply. Development agents do
not qualify those capabilities. Feature families and stages remain partial.

## Exact next gate

S03.2/S03.4 view-local worksheet selection consistency; F01/F64, Q16/Q19/Q33/Q34.
A read-only source audit finds that a nonnull dangling stored activeWorksheetId
survives the current decoder. WorkbookDocument.activeWorksheet falls back to the
first sheet, and the native ViewModel can query that sheet while retaining the
missing visible ID. WorkbookScreen then reports that the sheet no longer exists,
and View Data refuses because the query and visible selection IDs differ. This
is a source finding until its own native probe; it does not establish a new strict
package-admission policy.

First use two uniquely identified sheets on real imported data: KEEP East 15
(source rows 10/5) and KEEP West 20 (source row 20). Persist a missing active ID,
reopen through the native ViewModel and actual Activity, and record visible ID,
query identity/result and missing-sheet text. Explicitly select West and require
selection/query/View Data to agree on 20 without changing canonical bytes.
Preserve missing/null active-default and empty-sheet compatibility. Root owns
narrow view selection resolution and documentation; disjoint native workers and
independent combined review are required. Choose coherent local fallback or a
repairable selection error deliberately after the probe; do not tighten the
stored decoder implicitly or silently permit hidden first-sheet edits.
