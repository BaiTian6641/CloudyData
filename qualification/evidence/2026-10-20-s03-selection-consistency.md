# S03.2/S03.4 view-local worksheet selection consistency

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.2/S03.4; F01/F64; Q16/Q19/Q33/Q34.
**Status:** verified for the bounded API36 x86_64 native slice; full stages remain partial.
**Source base:** `3dc4c0528eeb6195561bbb59701783b541c1c131`.
**Implementation commit:** `e7ceffae56e4254583c1e52110a073d4dc7c8c2f`; all 135 hashes verified against committed blobs.

## Readiness and ownership

Actual root /home/polar/CloudyData, clean main/upstream/live intended origin at
3dc4c05. All 133 prior source/fixture hashes match current source, binding native
150/150 and JVM 75/75 checkpoint, migration, identity and query ownership evidence.
The three plans were already read this session and remain unchanged. Original
Cloudy assets remain bccb328; scoped design/token authority and accepted
ADR0003/0008/0009 are preserved. The roadmap master instructions were refreshed.

Root owns WorkbookViewModel selection resolution, WorkbookScreen only if needed,
and execution documentation. Separate workers own WorkbookSelectionConsistencyTest
and WorkbookSelectionUiTest. A third independently reviews actual combined source,
expected numeric results, native input/captures and artifact bindings. Only root
builds and runs devices. No shared-file overlap or storage/renderer authority move.

## Expected result and discriminating probe

Source paths disagree for a nonnull dangling stored activeWorksheetId: the model
falls back to the first sheet, the ViewModel retains the missing visible ID but
queries the fallback, Screen reports a missing sheet, and View Data refuses the
query/selection mismatch. The native baseline below confirms both paths.

Use real imported CSV/Parquet with two unique sheets: KEEP East returns 15 with
source rows 10/5; KEEP West returns 20 with source row 20. The unchanged-production baseline ran four tests in 9.435 s: the valid West
control passed and three regression assertions failed as expected. A dangling
visible ID coexisted with query identity East and real value 15; View Data remained
Idle. The actual Activity displayed "This worksheet no longer exists" while its
footer reported Query ready. Its subsequent native West 20 / East 15 pointer
controls and canonical/source byte checks passed before the final assertion.

The queue probe held a real coordinator snapshot read (COUNT 3 / SUM 35), queued
Undo then AVG for a selected added sheet, and released the reader. Expected only
Undo revision 6; actual revision 7 changed the unrelated East sheet to AVG 7.5,
left West SUM 20, retained one receipt and reported Saved. Raw failures, initial
APK/test-source hashes and three baseline native captures are retained.

The first test build had one Kotlin predicate parser error; the author added an
explicit return@first without changing its condition. The failed build log and
successful retry remain available. No runtime probe was repeated without new evidence.

The intended local resolution is valid existing local selection, then valid saved
reopen default, then first sheet, else null. The visible document must carry that
actual resolved ID so tab, query, View Data and edits agree. This local projection
must not persist a repaired default or tab selection. Preserve valid saved defaults,
missing/null legacy defaults, empty sheets, separate views, shared commands and
AddWorksheet/undo behavior. No new strict package-admission policy is introduced.

Independent review also identifies a queued-edit risk: generic sheet mutations
capture a view's active ID, but an earlier queued Undo can remove that sheet.
After revision rebasing, the model fallback can then transform the first sheet.
The discriminating native test holds a real coordinator snapshot reader, queues
Undo followed by a generic aggregation edit, then releases the writer. Only Undo
may publish; the later edit must explicitly refuse the missing captured target,
without changing other sheets, history receipts or source bytes. Local selection
fallback is presentation behavior and cannot retarget a previously captured edit.

Current Screen resolution already falls through for a missing tab once the
ViewModel publishes a coherent active ID. Preserve it unless native evidence
requires a separate fix; no frame-level or layout redesign claim is intended.

## Limits

Only the existing API36 x86_64 emulator-5580 is used; no boot, window resize,
density or global preference mutation. Physical Googlebook C1L63708PZ remains
owner-held. Missing 16 KiB runtime and licensed Tableau gates stay blocked. Room,
source-version history, cross-process coordination, full lifecycle/physical input
and complete native visual matrix remain partial or unqualified. S11.C0 remains
partial, S13.C0/S15.C0 absent and mandatory when worker-backed dependencies apply.
Development agents do not qualify those capabilities.

## Implementation and review

WorkbookViewModel is the only changed production file. acceptDocument now resolves
its fallback through canonical.activeWorksheet.id, publishing the actual local
ID without changing the stored reopen hint. Generic sheet edits capture the visible
ID; inside shared.edit, after revision admission but before transformation, the
captured target must still exist. Missing targets report that the change was not
applied. Existing typed SetChart target validation and conflict/retry behavior remain.
Screen, workbook decoder, coordinator, Store, query engine and design tokens are
unchanged. No simultaneous-frame rendering or complete typed command claim follows.

Independent source/oracle review found no material issue after the queued-target
guard was included. Root and reviewer inspected the baseline native missing-sheet
message alongside Query ready. Fixed focused/combined runtime and visual review
also pass, as recorded below. Baseline and final captures report 2880x1800 window/decor/screenshot, 240 dpi,
fontScale 1, System appearance resolved Light, Comfortable density and configured
IBM Plex Sans/Mono. Caption insets report zero. No physical or full-window-matrix
acceptance is inferred.

## Final qualification

- Focused new cases: **6/6**, no skips, **6.815 s**. Combined actual revision:
  **156/156**, no skips or failures, **77.362 s**. The five ViewModel tests and one
  actual Activity test join all 150 prior checkpoint/admission/native regressions.
- Real East SUM 15, West SUM 20 and exact source rows East 10/5 and West 20 agree
  with worksheet/query identity. The queued removed-target edit is explicitly
  refused, leaving only Undo at revision 6, unchanged other sheets and receipts.
- v1/v2 missing/null defaults, current null defaults, empty v1/v2/v3 documents,
  valid West defaults and invalid selection no-ops preserve canonical/source bytes.
  Opening never triggers a migration save. Two real ViewModels retain independent
  tabs over shared rename/count commands; West COUNT becomes 1 while East SUM is
  15. A real snapshot reader holds a pending View Data request while West is
  selected; immediate invalidation clears old query/source state and completion
  cannot reopen it. These are real Store/Parquet/DuckDB operations.
- Actual native pointer controls traverse East 15 → West 20 → East 15 → Data source
  → West 20, with exact metadata/source/Parquet preservation. Five final PNG/XML/
  JSON sets were independently inspected by root and reviewer. Selected tab,
  visible result and query ownership agree; no missing-sheet message, clipping or
  overlap was observed in this window. Data source shows real schema/provenance
  while retaining the hidden last-sheet query. This is not a frame-atomicity claim.
- Default and explicit x86_64 debug/test builds pass. JVM **75/75**, no errors,
  failures or skips. Lint **0 errors, 6 retained warnings**. Default APK contains
  **arm64-v8a and x86_64**; the installed qualification APK is explicitly x86_64.
  Generated tokens are current; declared contrast pairs **40/40** pass.
- Independent actual combined source/oracle and native visual review found no
  material issue. The new generic target-existence guard resolves the review's
  queued-Undo finding; no assertion was weakened to obtain a passing result.

Final capture configuration: task 2458, window/decor/capture 2880×1800, 1920×1200dp,
240 dpi, fontScale 1, System appearance resolved Light, Comfortable density,
configured IBM Plex Sans / IBM Plex Mono, reported caption insets zero. Native
captures are retained without alteration. Initial fixed PNG SHA256:
`97f236601f0c1880b5576024faef0f6add692edcfd01f3deee8c364157bdf825`.

## Reproduction and bindings

Raw logs and selected instrumentation class list:
`qualification/evidence/android-test-results/2026-10-20-s032-selection/`.
Baseline logs include the expected failures and the one corrected Kotlin predicate
parser error. Final-native.txt contains 156 success completions and no failure or
skip status. jvm-summary.json and lint-results.xml preserve their result counts.

Commands (using the established Android SDK/NDK paths from AGENTS.md):

```bash
./gradlew -PcloudyDataAbis=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5580 install -r -t /tmp/cloudydata-s032-selection-runtime.apk
adb -s emulator-5580 install -r -t /tmp/cloudydata-s032-selection-runtime-tests.apk
selection=$(cat qualification/evidence/android-test-results/2026-10-20-s032-selection/native-selection.txt)
adb -s emulator-5580 shell am instrument -w -r -e class "$selection" \
  io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
```

`qualification/evidence/artifacts/2026-10-20-s032-selection/manifest.json` binds
135 source/fixture files, the installed runtime/test APKs, the default dual-ABI APK
and all eight baseline/final capture sets. All PNGs match their own phase JSON
hashes. Source manifest SHA256:
`97cc4bf76d36c69e6bb98c22e299bbd512f218b4c03636c42d60345ca1133e92`.

| Artifact | SHA256 |
| --- | --- |
| Installed x86_64 runtime APK | `388a7e275c5ea8677c63e270becf964796950bf8f5ad241770e15a4a1352e912` |
| Installed test APK | `4b88053d3b82d05e18958682d963e284a8a5ba4fd58d914821eca841c036c348` |
| Default dual-ABI APK | `995f04c0568596d89d98cdb6c7421236425c81c7c0b6bc1af6977951e84360fb` |

## Next eligible boundary

S02.2/S02.4 native worksheet-tab keyboard and semantics qualification, F01/F64,
Q21 with Q19 invariants. Read-only source/XML audit confirms clickable/focusable
parents around nonfocusable text children, so the child alone is not evidence of
keyboard failure. Explicit tab role/selected semantics and focus-ring state are
absent. Use a fresh real two-sheet Activity and bounded parent-aware Tab traversal;
focus must not change the selected result until Enter. Retain focus XML/pixels,
then qualify Shift+Tab and Data source return without metadata writes. Physical
keyboard, TalkBack, roving-arrow behavior and complete visual acceptance remain
separate. The exact packet and ownership are in docs/execution-state.md.
