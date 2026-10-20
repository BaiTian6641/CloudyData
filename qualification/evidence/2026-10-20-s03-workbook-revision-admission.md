# S03.4 strict workbook revision admission

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.4; F01/F64; Q33/Q34.
**Status:** verified for the bounded decoded revision cases below; S03/full package validation remains partial.
**Source base:** `2242d29648cce50b8cbf21e657dd81e1ba3e5acf`.

## Readiness and ownership

The actual root is /home/polar/CloudyData. Main, upstream and live intended origin
match 2242d29, with a clean entry tree. All 129 preceding source/fixture hashes
match the qualified identity revision. Native 135/135 and JVM 75/75 establish the
unchanged checkpoint, migration, CAS and real-query substrate. The three plans
were already read in this session and remain unchanged, including the master
runbook. Original Cloudy assets remain at bccb328; scoped design/token authority
and accepted ADR0003/0008/0009 are preserved.

Root owns the narrow WorkbookDocument revision reader and execution documentation.
Separate workers own WorkbookRevisionRefusalTest and WorkbookRevisionMigrationTest;
a third reviews the actual combined source, independent oracles and artifacts.
Only root builds and runs native checks. There is no shared-file overlap.

## Expected behavior and verification

Android org.json getLong accepts numeric strings and truncates fractional Numbers.
Bare legacy v1/v2 packages can therefore turn malformed persisted revision identity
into a valid expected-revision command. The unchanged-production native baseline
passed all four compatibility tests and failed the refusal probe as expected.
Four independent imported CSV/Parquet workbooks (v1/v2, fraction/numeric string)
accepted revision 4.75 or "4" as revision 4. Each then accepted one Rename expecting
4, published revision 5, created one receipt and undo entry, and removed both
recovery candidates. Native East 15 / West 20 still executed. The raw baseline,
initial frozen test hashes and runtime/test APK hashes are retained.

A revision must decode as a finite whole number within 1..Long.MAX_VALUE-1.
Integral Byte/Short/Int/Long values retain an exact integer path. Floating values
must pass finite/whole/range checks before conversion; their upper bound excludes
2^63. Valid Long values above 2^53 must never pass through Double. Supported
integral decimal/exponent forms retain their parsed meaning and exact original
migration backup. Existing checkpoint bounds, history/canonical guards, command
receipt semantics, CAS and Math.addExact remain authoritative.

WorkbookDocument now uses one private decoded revision reader. Supported runtime
number classes are Byte, Short, Int, Long, Float and Double; other Number classes
are refused. Only the former getLong call changed. Checkpoint, Store, renderer,
receipt expected-revision and timestamp readers are unchanged.

Malformed stored packages must refuse load/open/save before cleanup or publication,
preserving exact metadata, candidates, existing backups, original CSV and Parquet.
Valid cases must retain exact revisions, history and receipts through save/reopen/
duplicate acknowledgement, and independent native East 15 / West 20 totals with
exact East 10 / East 5 underlying rows. Current v3 raw-content checks remain.

The focused native run passes 8/8 in 3.688 s. The four refusal tests cover 23
malformed values through direct v1/v2/v3 decoders and stored v1/v2 load/open/save,
actual JSONObject(Map) NaN and infinities, existing backup preservation, and eight
faults in each rehashed v3 head/undo/redo location beyond checksum validation.
Controlled raw floating literals assert their decoded Double type/value, avoiding
Android numberToString converting a 2^63 Double into a saturated Long token.

The four compatibility tests preserve literal 7 / 7.0 / 7e0 forms, all six supported
runtime Number classes, exact Long 9007199254740993 above Double precision and the
largest representable Double below 2^63 (independently expected 9223372036854774784).
Revisions 1 and MAX-1 load; MAX-2 to MAX-1 is the final accepted transition. A later
advance refuses with no additional atomic-write phases, while an identical request
still acknowledges its retained acceptance without replay. Exact legacy originals,
history, receipts, reopen and independent native totals/source rows pass.

Final combined native selection passes **143/143**, no skips, in **69.87 s** on
existing API36 x86_64 emulator-5580. These eight tests join all 135 preceding
regressions, including current journal/history/hash guards, migration, real writer
process deaths, Home UI, coordinator, engine, filters, View Data and palette.
There is no new revision-specific death or input/visual acceptance claim.

Final x86 and default dual-ABI debug/test builds pass. Default APK retains arm64-v8a
and x86_64; JVM 75/75 with no failures/skips; lint zero errors and six retained
warnings; generated tokens current and contrast 40/40. No assertion was weakened.
Raw before/after/build/test results are under
`android-test-results/2026-10-20-s034-workbook-revision/`. Only redundant trailing
blank lines were trimmed. Source/APK manifests are under
`artifacts/2026-10-20-s034-workbook-revision/`, with 131 source/fixture hashes and
source-manifest SHA-256
`9d1cb70fcdb3c269a7b8ed6ab27f53f0672391d404aa438e924be1699b604222`.

Independent review of the actual combined reader and both frozen test files found
no material issue. The independent final audit verified all 131 current source
hashes, the manifest digest, actual runtime/test/default APK hashes/sizes/ABIs and
raw native/JVM/lint/build/token results. No material discrepancy remains.

## Limits

This is decoded workbook-revision admission. Android JSON token rounding and
strict raw lexical validation, other schema/type/identity fields and broader
package validation remain separate. Constructed typed Long revisions keep the
existing validation rules; a valid canonical read may clean abandoned temps
before a later constructed candidate fails.

Existing API36 x86_64 emulator-5580 is reused without boot, resize, density or
preference changes. The physical Googlebook remains on owner hold. The 16 KiB
runtime and licensed Tableau gates are unavailable. Room, source-version history,
cross-process ownership, power-loss and full lifecycle acceptance remain open.
S11.C0 remains partial; S13.C0 and S15.C0 remain absent and mandatory when worker-
backed dependencies apply. Development workers do not qualify those capabilities.

## Exact next gate

S03.4 strict present workbook-favorite decoding; F01/F64, Q33/Q34. A bounded
read-only source audit finds optBoolean("favorite", false) coercing legacy strings
and defaulting malformed present data to false; library projection and an unrelated
Rename can persist that guessed Boolean. Missing favorite is intentionally false,
qualified by the existing legacy migration test. Actual present Booleans must stay
supported. Current v3 raw guards already refuse normalization. This finding is
source-only until a native legacy probe records the normalization and command
result. Root owns the narrow model reader/docs; separate native refusal/migration
workers and independent combined review are required. Preserve absent/false/true
migration originals, explicit SetFavorite desired state, history/reopen/duplicate,
source bytes and independent native 15/20 totals.
