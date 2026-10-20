# S03.4 explicit secondary aggregation decoding

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F17/F36/F64; Q33/Q34 migration/history, exact native dual-axis controls.
**Status:** verified for the bounded secondary enum/migration cases below;
full S03 and aggregation/parity families remain partial.
**Source base:** `b752e46161b3d876edc48a77c5f5f55e09593c0f`.

## Readiness and ownership

Continued from committed strict filter implementation 7c78e8d and binding b752e46.
Local/upstream/live intended origin matched, with clean tree at task entry. All 123
preceding source/fixture hashes remain the verified query/storage/decoder substrate;
final 113 native and 75 JVM passed against that source. The already-read plans,
master runbook, accepted ADR0003/0008/0009 and scoped original design/token authority
are unchanged. S03 and family status stay partial.

Root owns only the secondaryAggregation branch in WorksheetDef decoding and docs.
Workers own separate LegacySecondaryAggregationRefusalTest and MigrationTest files;
a third independently reviews actual combined source, test oracles and evidence.
No shared-file overlap, new dependency, native binary, renderer or worker capability.

## Numerical meaning and intended result

The existing decoder catches every invalid present secondaryAggregation and
substitutes SUM. Bare legacy v1/v2 records bypass v3's raw-content comparison, so
malformed persisted AVG intent can become executable SUM. The existing real
fixtures/csv/dual_axis.csv provides an independent discriminant: January sales
SUM 150, costs 120/80, costAVG 100 versus costSUM 200. February is120/20 and March 80/60.

Absence retains the historical SUM default. Every present value, including a
currently inactive secondary channel, must be an exact supported Aggregation
string. Explicit null, unsupported/blank strings and wrong JSON types must fail;
there is no trim, case correction or fallback. Existing valid enum tags remain
recognized; this does not add unsupported mark/aggregation combinations. In
particular, decoding NONE is not new grouped-query applicability acceptance.

The existing v3 head/history/envelope guards remain independent of checksums.
Rejected input must preserve canonical metadata, candidates, source/Parquet and
existing backups; load cannot create a migration backup. Valid legacy migration
must keep exact originals, accepted history/receipt identities and underlying
January source rows with sales 100/50 and costs 120/80. Opening never replays import.

## Native baseline and final verification

Before any product edit, the independent native method ran on unchanged b752e46.
Valid AVG/SUM controls passed. Both v1/v2 records with AVGG then loaded as SUM and
executed January=[2026-01-01,150,200], with recovery candidates removed. The expected
single-test baseline failure and original runtime/test APK hashes remain in
baseline-native.txt and baseline-artifacts.json. That test method is unchanged in
the final refusal file.

Root replaced only the catch/default branch: absence defaults SUM; present data
must be a String accepted by Aggregation.valueOf. Errors propagate before accepted
canonical cleanup/save. The first post-fix focused run passed 6/6 in 1.274s; final actual combined revision
passed 119/119 in 58.029s with no skips on existing API36 x86_64 emulator-5580. The six
new cases join all 113 prior regressions, including actual old writer deaths,
strict filters/reference repair, Home input, coordinator, engine/query/persistence
and palette. No new secondary-specific death or hardware acceptance is claimed.

The valid constructed v1/v2 AVG fixtures retain exact January 150/100.0,
February 120/20.0 and March 80/60.0 before/after migration. Explicit/absent SUM gives
January 200. Exact January source rows, original backup bytes, accepted revision/
history/receipt identities, reopen/later save and duplicate no-replay checks pass.
All seven enum tags remain syntax-only coverage, including NONE; the independent
raw-source totals stay 4 rows / sales 350 / cost 280. Thirteen malformed values across both
versions and active/inactive channels refuse load/open/save while retaining
canonical/candidates/source/Parquet, with no migration backup created. Rehashed
malformed v3 head/history still fail content admission rather than checksum alone.

Final x86/default dual-ABI debug/test builds pass; default ZIP has arm64-v8a+x86_64.
JVM 75/75, no skips; lint 0 errors/6 retained warnings including ChromeOsAbiSupport.
Tokens current and contrast 40/40; no assertions/checks disabled. Exact 125 source/
fixture hashes and APK sizes/ABIs/SHA-256 are in artifacts/2026-10-20-s034-secondary-aggregation/.
Raw before/after native/build/JVM/lint/token results are in android-test-results/
2026-10-20-s034-secondary-aggregation/. Only redundant terminal blank lines at end
of text logs are removed. Independent read-only source/oracle review found no
material blocker. The reviewer independently verified all125 hashes, exact
manifest/APK bindings and final raw results; no material evidence gap remains. No new native render/input acceptance is inferred.

## Limits

This is a bounded numerical enum decoder, not full package/schema/type validation,
aggregation catalog conformance, formula semantics or complete dual-axis rendering.
Coercing formatVersion parsing remains a separate source finding. Generic Store
normalization and unrelated accepted chart compatibility remain unchanged.

No new error/repair UI, pixels, TalkBack or physical input acceptance is claimed.
Only the existing API36 x86_64 emulator is used, with no boot/resize/density or
preferences changes; physical Googlebook owner hold remains.16KiB runtime and
licensed Tableau access remain unavailable. Room, source-version history,
power-loss/cross-process ownership and full lifecycle remain open. S11.C0 partial;
S13.C0/S15.C0 absent and mandatory when worker-backed dependencies apply.
