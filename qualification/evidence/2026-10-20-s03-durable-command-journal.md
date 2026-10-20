# S03.3 durable metadata commands and checkpoints

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.3; F01/F64; Q17/Q34 and preserved Q19 conflicts; Q02 native regression.
**Status:** verified for this bounded metadata-command/checkpoint and single-writer
process-death slice. Full S03, Room integration and the complete typed command
catalog remain partial.
**Source base:** `c2c6b65de8028a5bf9850643c747e55db7468b0d`; combined tested source is
bound by 112 source/fixture hashes in `artifacts/2026-10-20-s033/source-sha256.txt`.
**Platform:** existing `emulator-5580`, Android 16/API36, x86_64, 4 KiB pages,
2880×1800 pre-existing size override, 240 dpi, font scale1.0, WebView143.0.7499.35.
The physical Googlebook remained on owner hold. Licensed Tableau was unavailable.

## Selection, authority and implementation

Reinspected clean main/HEAD/upstream/remotes and verified remote main matched
`c2c6b65`. All three plans had already been read in this session and were unchanged.
The preceding S03.2 source manifest still matched all107 files before edits. Its
coordinator, atomic JSON save and real receipt-backed import were verified entry
conditions; full foundation/stage gates were not assumed. Original Cloudy assets
remain located/hash-bound under the established documentation map; tokens and
product/design authority were preserved without importing Console behavior.

The selected task was the handoff's earliest ready S03.3 rename/chart journal
slice. Root owned commands/checkpoints, store/coordinator/ViewModel, format
migration and docs. Independent workers owned only the new journal test file,
and the debug manifest/service plus crash test file. Another agent reviewed the
combined source and final native evidence. No shared-file ownership overlapped.

ADR0009 explicitly extends the accepted app-private JSON authority. A versioned
commandJournal envelope, current document, accepted-command receipts and bounded
undo/redo snapshots are one fsynced temporary file and atomic replacement.
There is no second authoritative log or automatic command replay. Room remains
the planned destination, not an implementation claim.

- Rename/SetChart requests carry command/workbook/origin-window identity, expected
  revision, payload and explicit target sheet for chart edits. ViewModels freeze
  the entire request at first dispatch so retries retain identity.
- A retained identical request returns its original accepted revision and the
  current document without another write. Changed payload/window/revision/target
  under the same ID is refused. UI queue rebasing uses only the exact recorded
  expected→accepted edge, never a foreign newer current revision.
- A new typed request, including a no-op payload, records one acceptance/revision;
  no-op payloads add no history step. Empty undo/redo is a no-op. Undo/redo and
  generic coordinator edit history persist atomically and reconstruct on reopen.
- Raw saves/import publication clear durable history in the same atomic write,
  while preserving accepted typed IDs. Obsolete snapshot histories cannot reopen
  or replay source operations.
- v3 checkpoints validate canonical hashes, versions, IDs, positive bounded
  revisions, history ownership/order and exact normalized shape. Corrupt/future
  metadata blocks writes without rewriting diagnosis evidence. Hashes detect
  corruption; they are not authentication or an execution grant.
- v1/v2 metadata loads through migration; first save atomically preserves exact
  original bytes under migration/workbook-v<version>-original.json. v3 prevents
  older builds from saving away the new journal. The existing future-version
  test now rejects v4, while explicit v1/v2 migration tests remain.
- Bounds: combined history50, receipts4096 (no eviction; explicitly refuse new
  typed commands at capacity), metadata32MiB. These are current limits, not full
  editing-history parity or automatic compaction.

## Independent native expected results

`WorkbookJournalTest` (separate test author): **12/12**. It verifies fixed
Book→Renamed→Custom-chart states through reconstruct/undo/redo/branch, exact
retry bytes/revisions after other edits and undo, identity-mismatch rejection,
stale/missing-sheet requests, genuine EACCES before atomic replacement, real
CSV/Parquet totals35→7 across a snapshot barrier, six corrupt/future cases,
exact v1/v2 original preservation and the fifty-entry history boundary. Two
additional tests use the real write hook to lose acknowledgement after REPLACED:
only one receipt/edit commits, and two immediate ViewModel commands still persist
in order under distinct IDs and one window identity.

`WorkbookJournalCrashTest` (separate probe author): **2/2**. A nonexported,
debug-only bound service writes only beneath a validated synthetic UUID root.
Instrumentation links Binder death before requesting the typed chart fixture.
The process must actually die; boundary callbacks alone cannot satisfy the gate.

| Final boundary | Writer PID | Observer PID | Independent observed recovery |
| --- | --- | --- | --- |
| REPLACED |23503|23448|Workbook B / Chart C accepted once; duplicate creates no revision; undo/reconstruct/redo preserve exact state. |
| TEMP_SYNCED |23520|23448|Previously accepted Workbook B/default chart remains; rename receipt/history survive; undo restores Workbook A. |

The final log has matching KILL, BINDER_DEATH and VERIFIED_DEATH records for
both distinct PIDs. This is real separate-writer-process death with one writer,
not simulated death, power-loss durability, whole-product-process restoration or
concurrent cross-process ownership.

Final combined run: **54/54, no skips**, 16.759s:

- journal12 + process-death2;
- coordinator14, including actual Activity tasks2386/2387 and visible text assertions;
- engine4 + chart persistence12 + import recovery3 + mark filters3 + View Data3
  + dual-axis query1.

JVM **75/75**, zero failures/errors/skips. Default dual-ABI debug/test APK build
and lint pass. Lint reports **0 errors /6 warnings**: the additional
ChromeOsAbiSupport warning flags the dynamically selected ABI list, while ZIP
inspection confirms the default APK contains both arm64-v8a and x86_64. No lint
check was disabled. Generator consistency passes and contrast40/40 declared pairs
passes; no token or native dependency changed.

## Commands and artifact binding

```bash
export ANDROID_HOME=/home/polar/Android/Sdk
export ANDROID_NDK=/home/polar/Android/Sdk/ndk/28.2.13676358
./gradlew -PcloudyDataAbis=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e class io.github.baitian6641.cloudydata.WorkbookJournalTest,io.github.baitian6641.cloudydata.WorkbookJournalCrashTest,io.github.baitian6641.cloudydata.WorkbookCoordinatorTest,io.github.baitian6641.cloudydata.EngineSmokeTest,io.github.baitian6641.cloudydata.ChartSettingsPersistenceTest,io.github.baitian6641.cloudydata.RecoveryTest,io.github.baitian6641.cloudydata.MarkFilterQueryTest,io.github.baitian6641.cloudydata.ViewDataQueryTest,io.github.baitian6641.cloudydata.DualAxisQueryTest io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
```

Runtime x86_64 APK SHA256:
`9426b9091a5933d4965bef393e19b613372ea76b4f1aa884f06355ed0cb47211`.
Runtime instrumentation APK SHA256:
`bc97ebbc16e032f22a932562eacd7aa048f745d2e666d14e7f2a77e1b83e7634`.
Default dual-ABI APK SHA256:
`2eb2e47161495e9026666484590b14a1241672b34a46111ce7a8d3e03838fe42`.
The runtime package uses the established ABI qualification option to fit the
existing emulator storage budget; no apps, user files or caches were deleted.
No APK/native binary is committed or distributed as a release.

Raw outputs/JVM summary: android-test-results/2026-10-20-s033/. Terminal trailing
blank lines are omitted in text logs. initial-native.txt is the earlier12-case
run before review followups; final-native.txt and its artifact hashes bind the
final54-case run. Logs retain both runs' PID records with timestamps.
Artifact/source manifest and capture: artifacts/2026-10-20-s033/.

## Independent review and qualification limits

Reviewer journal_review found no material blocker, and root addressed the
bounded legacy/revision-validation and acknowledgement-edge followups. A backup
creation weakness found by the test author was fixed before runtime qualification:
original preservation uses temp/fsync/atomic move, not a partially written final
backup file. All final code/tests were integrated before the54-case run.

Root and the independent reviewer inspected shared-title.png (SHA256
`de69197d6b2d75f4c8fd3fcc83d7c4b32f7f70bc8133eb5d2707b217ba6c2ffd`):
Shared Activity title/Saved locally and the empty workbook surface were legible
without obvious clipping/overlap. Both Activities observed2880×1800 fullscreen;
this is not side-by-side placement or a full visual/input/accessibility matrix.

Remaining: Room migration; complete typed command coverage; retention compaction;
formula drafts and conflict proposal/reapply; source-version history; cross-process
writers/query service/Arrow; whole-product-process and physical desktop lifecycle;
power-loss/directory-durability guarantee. SIGKILL before replacement leaves an
unpublished workbook-*.tmp ignored by recovery; automatic orphan-temp cleanup is
not yet implemented and must not delete candidates when canonical metadata is
corrupt. Physical Googlebook,16KiB runtime and licensed Tableau gates remain held
or unavailable. S11.C0 stays partial; S13.C0/S15.C0 remain absent.
