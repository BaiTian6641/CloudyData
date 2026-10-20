# S03.4 strict legacy filter decoding and original preservation

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F05/F14/F64; primary Q33/Q34 migration/history cases;
Q16/Q17 regression prerequisites retain their own bounded evidence.
**Status:** verified for the bounded decoder/migration cases below; full S03
and parity families remain partial.
**Source base:** `e69bb7c2adff4ae1ff04929aa75f48499dd1a0a3`.

## Authority, freshness and ownership

Actual root/main/HEAD/remotes/upstream were inspected; clean local/upstream/live
origin matched e69bb7c at entry. All121 source/fixture hashes from the preceding
reference-admission manifest matched. The three supplied plans were already read
in this session and are unchanged, including the master runbook's S03.4 gate.
Accepted ADR0003/0008/0009 remain the local document/format authority. Original
Cloudy assets are still at the documented sibling repository's bccb328 commit;
no design/token/rendering authority or product scope is imported/replaced.

Root owns only MarkFilter/WorksheetDef decoding and docs. One worker owns the
new LegacyFilterRefusalTest; another owns LegacyFilterMigrationTest. A third is a
read-only reviewer of actual combined source, expected results and evidence.
Workers use disjoint files; root alone runs native/build integration. No new
dependency, generated native binary, renderer behavior or worker capability.

## Native baseline and actual correction

Before any production edit, the new focused future-version native test ran on
unchanged e69bb7c production. It first proves real imported KEEP East gives15.
Both bare v1 and v2 metadata with filter version2 then loaded successfully with
filter=null; real native query returned East15/West20 instead of constrained
East15. Known metadata-temp recovery candidates were also removed because the
invalid legacy file was treated as validated canonical state. One expected
failing case in0.136s is retained in baseline-native.txt; APK hashes and frozen
baseline test-source hashes are recorded in baseline-artifacts.json. The future
version test method is unchanged in the final refusal file.

WorksheetDef now propagates present filter errors rather than catching and
removing the filter. Absent/null filters remain intentionally unfiltered. Present
filters require a supported object/version/mode, known fields, nonblank string
anchors and1–2001 complete object marks. Marks require explicit text or NULL row
values; paired marks require their series value (explicit NULL is valid), and a
nonnull series value without a series anchor is refused. Mixed/oversized arrays
cannot be skipped or truncated; numeric/Boolean/object values cannot be coerced
into strings. Missing filter version still means the original version1; present
version must be numeric1, including an integral1.0 representation.

Nullable worksheet mapping IDs retain missing/null defaults. A present nonnull
ID must be a nonblank string; decoding neither trims nor clears it. Nonempty
unresolved IDs remain available for the separate semantic reference-repair path.
Unrelated legacy extension fields are preserved in the exact original backup;
no blanket legacy raw-roundtrip rule is added.

Repeated OR marks are preserved exactly, as are empty/whitespace string values.
Duplicate terms cannot multiply source rows. This deliberately replaces silent
deduplication; the bounded exact representation also roundtrips in v3. The existing
v3 checksum/content/history/envelope comparisons are unchanged and still refuse
unsupported records, including when their raw hashes are recomputed correctly.

The parse failure happens before canonical-load cleanup or accepted save/backup,
so rejected metadata, recovery candidates and accepted source/Parquet remain
available. No file is promoted, normalized in place or granted execution rights.

## Final verification

The first post-fix focused run passed12/12 in2.565s: refusal7 and valid migration5.
The final actual combined revision passed113/113 in56.062s, no skips, on the existing
API36 x86_64 emulator-5580: new12 plus prior101, including actual five single-writer
deaths, reference repair, favorites native input, coordinator, engine/query/
persistence and palette regressions. The initial expected pre-fix failure remains
in evidence; it was not hidden, retried unchanged or converted into a pass.

Real native oracles verify KEEP East15, paired East/N10, explicit NULL3, intentionally
unfiltered East15/West20 and duplicate terms staying15 with two source rows. Valid
v1/v2 defaults and exact original migration backups survive first accepted Rename,
v3 reopen, later save and duplicate acceptance. Refusal across load/open/save
preserves exact metadata, both candidates, accepted source/Parquet and no migration
backup. Rehashed malformed v3 content still refuses beyond checksum validation.

Final x86 and default dual-ABI debug/test builds pass; default APK ZIP contains
arm64-v8a+x86_64. JVM75/75 with no skips; lint0 errors/6 existing warnings including
ChromeOsAbiSupport; generated tokens current and contrast40/40. No tests/assertions
were disabled. Exact final source/fixture123 hashes, APK sizes/ABIs/SHA-256 and
native summary are in artifacts/2026-10-20-s034-legacy-filter/. Raw logs, baseline
APK/test hashes, JVM/lint/token results are in android-test-results/2026-10-20-s034-legacy-filter/.
Only redundant terminal blank lines at end of text logs are removed.

Independent read-only source/oracle review found no material blocker. Final123
hashes and raw results receive a final evidence review before commit. No new
layout, repair UI, TalkBack or physical input acceptance is inferred.

## Limits and preserved gates

This closes a bounded filter/mapping-decoder gate, not full package/schema/type
or semantic validation. Other permissive legacy numerical/presentation fields
remain separately auditable, including secondaryAggregation fallback and coercing
formatVersion parsing. The exact2001-mark positive SQL capacity and every malformed
JSON-token permutation are not qualified by this set. No automatic dependency repair, schema reconciliation,
Room, source-version retention, cross-process writer exclusion, power-loss or
new process-death acceptance is introduced. Existing writer-death tests qualify
their own actual boundaries only.

There is no new layout or input surface; query and filesystem assertions do not
qualify error pixels, TalkBack, native repair dialogs or hardware input. Existing
API36 emulator-5580 is reused without boot/resize/density/preference changes. The
physical Googlebook remains on owner hold and is not interacted with.16KiB runtime
and licensed Tableau remain unavailable. S11.C0 stays partial; S13.C0/S15.C0 absent
remain mandatory when worker-backed dependencies apply. Full S03/F14/parity stay
partial.
