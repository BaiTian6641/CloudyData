# S03.4 first persisted-reference admission slice

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F05/F14/F64; Q01/Q16/Q17/Q19/Q34.
**Status:** verified for the bounded decoded-reference/query cases below; full
S03 package validation, schema reconciliation and semantic compiler remain open.
**Source base:** c37eccfd7d8f6bfe28e853ae79621c0c4ef53760.

## Readiness and authority

Continued from committed favorite implementation e27d204 and source binding
c37eccf; clean main, upstream and live intended origin matched before edits.
All118 preceding source/fixture hashes matched, and native89/89 plus JVM 75/75
were fresh for the checkpoint/coordinator/query substrate. The three plans were
already read in this session and unchanged; their status claims remain separate
from evidence. Scoped original design assets and ADR0009 interim checkpoint
rather than Room remain authority. No renderer, import identity reset, tokens,
dependency or native binary source is changed.

Root owns semantic resolution, query/Underlying Rows/result admission, native
ViewModel guards and docs. One worker owns only WorksheetReferenceTest; another
owns only SourceReferenceRepairTest. Independent review is read-only against the
actual combined source, results and hashes. Physical Googlebook owner hold,
16KiB/Tableau access gates and S11.C0/S13.C0/S15.C0 remain unchanged.

## Observed problem and implementation

Source audit found that firstOrNull resolved unknown mapping IDs as if mappings
were intentionally absent. A missing row could change grain to global SUM; missing
measure could change SUM to counts; series/secondary mappings or mismatched filter
anchors could disappear. Normal source replacement deliberately issues new field
IDs while retaining mappings, so this was reachable through real product import.

WorksheetReferences now rejects blank/duplicate snapshot field IDs and resolves
all nonnull row/measure/series/secondary IDs, including currently inactive channels.
Unresolved IDs return WorksheetReferenceException with typed channel/field identity
before SQL. An active persisted filter must resolve and match effective row/series
anchors. Legitimate null mappings remain intentional. Validation does not mutate
metadata, clear mappings, guess by display name or grant imported data authority.

Real WorksheetQuery results carry runtime snapshot ID and full stored query-shape
identity (worksheet ID, mappings, aggregations, mark, sort and filter). Paint/name
changes are excluded. View Data validates current references before any no-row
return, and requires matching stamped ownership/resolved field definitions before
source SQL. Pure MarkFilters fixtures have shape checks; native selection admission
requires current source ownership. Existing ViewModel failure/generation handling
is retained; stale View Data reports an explicit failure. No result stamp is added
to persisted workbook format.

## Independent expected results and final verification

The independent fixtures use real JNI DuckDB,
Parquet and native import/coordinator/storage operations, no mock engine/store.
Baseline Category/Series/Sales/Cost rows East/N/10/4, East/S/5/2, West/N/20/8 imply
East 15 / West 20, total 35, three series marks 10/5/20 and secondary AVG Cost 3/8.
Invalid references combined with an unavailable path must fail with the typed
reference diagnostic first. Real same-header replacement East 7 / West 11 preserves
old IDs as repairable state, then explicit rebind and View Data must return exact
7/11 and East's singleton 7. Undo restores broken references against the same new
snapshot; redo restores repaired references. Refusal must leave canonical bytes,
checkpoint history/receipts and current accepted source/snapshot bytes unchanged.

First focused native run passed 9/9 in 0.866s. Final combined native 101/101 passed
in 50.672s with no skips: new references 7 / replacement 2, prior 89 (including Home
native pointer/Tab+Enter and palette) and additional Bar 1 / Line+Area 2 query cases.
All independent numerical, typed-channel, before-path and exact persistence
assertions passed on the existing API36 x86_64 emulator-5580, reused without boot,
resize, density, preference or physical-device interaction.

Final x86/default dual-ABI debug/test builds pass. Default ZIP contains
arm64-v8a+x86_64; JVM 75/75 with no skips; lint 0 errors/6 retained warnings including
ChromeOsAbiSupport; tokens current and contrast 40/40. No assertions disabled.
Runtime SHA-256 `4233cbee2c148bbade431c13ea212c5c6bb956b3a9eddcdaf009432a2cb54e22`;
test SHA-256 `a3774e22113cbbf8825b2bef18228db7b46322bc3e6cf0514d30ea670d5930af`.
The 121 source/fixture hashes and exact APK sizes/ABIs are bound in
artifacts/2026-10-20-s034-reference-admission/. Raw build/native/JVM/lint/token logs
are in android-test-results/2026-10-20-s034-reference-admission/. Only redundant
terminal blank lines at end of text logs are removed.

Independent read-only review of source and both frozen test files found no
material blocker. The reviewer independently verified final 101/101 and all 121 hashes against
their manifest; no material source/test finding remains before commit. No new visual surface is qualified by these numerical tests.

## Limits

This is semantic admission for decoded references, not complete package parsing
or schema validation. WorksheetDef's decoder still normalizes some empty optional IDs and drops
malformed/unsupported filters. The v3 checkpoint's strict raw-content roundtrip
already rejects such normalization, including a recomputed envelope hash. Legacy
v1/v2's early-return decode does not have that raw-content guard. Fail-closed
legacy filter/package decoding, preserving original bytes and valid migrations,
remains a separate earliest S03.4 gate; this is a source finding, not a new native
legacy-decoder qualification.
No new type/aggregation validity, source schema reconciliation UI, name-based
rebind or unsupported chart combination is implemented.

Replacement still overwrites the fixed current Parquet and source/original.csv;
undo of mapping metadata cannot restore a previous source version. Durable
source-version history remains open. No Room, concurrent cross-process writers,
power-loss or new process-death acceptance is claimed. Direct native query/state
checks do not close new error/rebind dialog pixels, physical input, full Q19/Q21,
TalkBack or Tableau parity. S11.C0 partial and S13.C0/S15.C0 absent remain mandatory
when worker-backed features apply.
