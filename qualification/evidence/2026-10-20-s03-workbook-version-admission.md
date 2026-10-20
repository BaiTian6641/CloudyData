# S03.4 shared workbook format-version admission

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F64; Q33/Q34.
**Status:** verified for the bounded decoded-version admission/migration cases below; full S03/package validation remains partial.
**Source base:** `d2f3d0bd30c7a58d5fefa368a6d98e6b30d15b14`.

## Readiness and ownership

Actual root/main/HEAD/remotes/upstream/clean state and recent commits were
reconciled with execution state, accepted ADRs and evidence. Local/upstream/live
origin matched d2f3d0b. All125 preceding source/fixture hashes matched the qualified
secondary-aggregation revision. The three plans were already read in this session
and remain unchanged, including the master runbook. Original Cloudy assets remain
at the mapped sibling repository's bccb328 commit; scoped design authority and
the single provisional token source are preserved.

Root owns a shared model version decoder, Checkpoint schema branching, Store
backup-version use and docs. Separate workers own WorkbookVersionRefusalTest and
WorkbookVersionMigrationTest; a third reviews actual combined source/results.
Files are disjoint, and only root runs builds/native integration. No new renderer,
dependency, native binary or worker capability is introduced. ADR0009 remains the
interim atomic JSON authority; Room/full S03 are separate.

## Decoded-value contract

Android org.json getInt can coerce a string, truncate a fraction or wrap a Long.
The selected task validates the parsed value before schema branching or Int
conversion. The formatVersion value must be Number, finite, mathematically whole
and within1..CURRENT_FORMAT_VERSION. Equivalent integral decimal/exponent numeric
representations retain their existing decoded meaning. Missing/null/string/
Boolean/container/fractional/out-of-range values refuse without cleanup or save.

Document decode, Checkpoint branching and migration backup naming share one
reader. Accepted v1/v2 still normalize to the current model only in memory until
an accepted write, which keeps exact original bytes under the proper v1/v2 backup
name. Current v3 still requires its journal; legacy schema plus a journal remains
invalid. Existing v3 raw-content/history/checksum/envelope comparisons remain.

## Before/after native evidence

Before product edits, coercibleVersionsCannotSelectLegacySchema ran against
unchanged d2f3d0b. All three distinct malformed versions (fraction2.9, string"2"
and Long4294967298) loaded as current3, executed actual East15/West20 and deleted
both recovery candidates. The expected1-test failure and initial APK/test/source
hashes are retained under android-test-results/2026-10-20-s034-workbook-version/.
The baseline test method remains unchanged in the final test file.

Root replaced all three coercing workbook-version reads with one shared reader.
The supported range is checked before toInt; no other schema/parser, renderer or
query rule changes. The first focused run passes7/7 in1.729s. The final actual
combined revision passes126/126, no skips,59.938s on API36 x86_64 emulator-5580:
new refusal4/migration3 plus all119 prior native regressions, including actual
writer deaths, Home pointer/keyboard, palette, recovery, coordinator and queries.

Twenty-six serializable malformed values refuse direct decoding and stored
load/open/save without changing exact metadata, both candidates, CSV/Parquet or
creating a backup. Actual native JSONObject(Map) Number objects prove NaN and
both infinities reach and fail the shared reader. Seven faults at each of v3
head/history still fail after valid head/journal hashes are recomputed; version/
content admission remains independent of checksum verification.

Positive files contain literal1/1.0/1e0,2/2.0/2E+0 and3/3.0/3e0. Legacy migration
preserves byte-exact original tokens/whitespace/extensions under the correct
v1/v2 filename, accepted history/revisions/receipts and duplicate no-replay through
reopen/later save. Current equivalents retain the existing journal and original
bytes without rewrite/backup. Int/Long/Double controls preserve current-required
journal and legacy-without-journal boundaries. Real native queries retain
unfiltered West20/East15, KEEP East15 and exact underlying East10/East5 source rows.

Final x86/default dual-ABI debug/test builds pass; default ZIP contains arm64-v8a
and x86_64. JVM75/75 no skips; lint0 errors/6 retained warnings; tokens current
and contrast40/40. No check is weakened or disabled. Exact127 source/fixture hashes
and APK sizes/ABIs/SHA-256 are under artifacts/2026-10-20-s034-workbook-version/.
Raw build/native/JVM/lint/token/baseline logs remain under the matching android-
test-results path; only redundant trailing blank lines are removed from text logs.
Independent static review of combined source and independent oracles found no
material issue. Final independent audit verifies all127 current source hashes,
manifest6f78113696ff03fb707f9063b2d68335ac750cf8a7f6f0cd9810d2cc01d70b2b,
actual runtime/test/default APK bindings and raw test/build/lint/token results;
no material evidence discrepancy remains. Committed-source binding follows.

## Limits

This validates decoded numeric versions, not raw JSON lexical grammar. Installed
Android JSONTokener can round numeric lexemes and recognizes some nonstandard
spellings before the model receives a Number. Full token-level JSON validation,
other package/schema/type/identity checks, journal-schema semantics and complete
parity remain separate. A huge Number cannot enter the small supported range by
Int overflow because the range check precedes conversion.

No UI/layout/input, error-pixel/TalkBack, physical hardware or new process-death
claim follows. Existing API36 x86_64 emulator-5580 is reused without boot, resize,
density or preference changes. Physical Googlebook remains on owner hold.16KiB
runtime and licensed Tableau remain unavailable. Room, source-version history,
concurrent cross-process writers and power-loss/full lifecycle remain open.
S11.C0 partial; S13.C0/S15.C0 absent and mandatory when worker-backed dependencies
apply. No stage or feature family is promoted complete by this bounded gate.

## Exact next gate

S03.4 duplicate worksheet identities (F01/F64,Q33/Q34). Independent source review
finds that two sheets with one decoded ID are admitted while SetChart maps over
all matches, so one targeted command can edit both. First run an unchanged-source
native probe with two distinct sheets sharing an ID; contrast a unique-ID control.
Require per-document uniqueness in head/history and byte-preserving refusal;
retain missing/null active-ID defaults, empty sheets and unrelated identity policy.
This is a source finding awaiting its own discriminating native proof.
