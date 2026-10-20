# S03.2 shared workbook coordination

**Date:** 2026-10-20 (America/Chicago)
**Requirements:** S03.2; F01/F64; Q19/Q34, Q16 result invalidation and Q17
receipt-recovery cases. Q02 precision remains covered by the native regressions.
**Status:** verified for the bounded in-process/emulator cases below; full S03,
physical desktop/input, durable journal and cross-process ownership remain partial.
**Source base:** `caf3751` plus the combined implementation identified by
`artifacts/2026-10-20-s03-coordinator/source-sha256.txt` (107 files).
**Platform:** `qualification/platform-profiles/android-16-api36-existing-emulator.md`.
**Reference:** licensed Tableau comparison unavailable; these are independent
state/numeric fixtures, not differential parity acceptance.

## Selected task and prerequisites

The continuation began from clean `main` at `50dd16f`, tracking the intended
origin with matching remote HEAD. All three plans, master runbook, accepted ADRs,
source, tests and recent evidence were read. CUST09.3's source was unchanged since
`6494746`; its five referenced raw logs existed and passed, but it did not close
foundation gates. Independent audit selected the earlier ready S03.2 foundation
before the listed CUST09.4 chart continuation.

Prerequisites reused: ADR0003/0006 app-private atomic JSON metadata and real
receipt-backed local import, pinned native DuckDB binaries matching the original
ABI hashes, and the existing worksheet/ViewModel/native Activity paths. These
support the bounded coordinator work; no full S01 or S03 completion was assumed.
S11.C0 remains partial (including missing reference-analysis controls);
S13.C0/S15.C0 remain absent. No worker-backed gate was bypassed.

Writable implementation paths were WorkbookCoordinator/WorkbookStore,
WorkbookViewModel, existing Home/Workbook wiring, focused independent native tests
and evidence/state docs. `app/build.gradle.kts` gained a validated opt-in ABI
selection only after the native install storage failure. `.kotlin/` is ignored as
generated build output. Tokens, analytical queries, chart lowering and dependencies
were not changed.

## Behavior

- The app's WorkbookStore owns a registry with one coordinator per workbook.
  Accepted commands save atomically before publishing immutable state to every
  observer. Store saves compare the supplied revision with disk; stale commands
  fail before their transforms run. `create` now returns the actual saved revision.
- Undo/redo share a bounded process-local history and always persist a new revision.
  Failed writes leave the accepted state and both history directions intact.
- Same-view commands queue in order and advance only through that view's own
  accepted revisions. Foreign edits retain explicit conflict behavior. Favorite
  edits use the same authority. Conflict UI says the change was not applied.
- Worksheet selection belongs to each ViewModel; it creates no shared revision.
  Adding a sheet selects it only in the initiating view. The stored active-sheet
  ID remains a reopen default; per-window tab restoration across process death
  is not implemented by this slice.
- Imports, snapshot readers and metadata edits use the same document lock. A fixed
  snapshot path cannot be replaced while a reader uses it. Old snapshot IDs are
  refused after replacement.
- An acquired command completes persistence/publication even if its initiating
  view is cancelled. An import throwing after saving reconciles disk state before
  releasing readers. If recovery retains an unresolved operation, opening,
  reloading or failing an import keeps reads/edits blocked until recovery succeeds.
- ViewModels observe accepted shared changes, invalidate incompatible query/View
  Data generations, and avoid requerying presentation-only or rename edits.

## Independent expected results and native checks

`WorkbookCoordinatorTest` was authored by the separate `coordinator_tests` worker.
Its 14 actual Android tests establish:

1. Create equals reload; stale saves through a second store handle cannot overwrite.
2. Two opens share one identity; another workbook remains independent.
3. Simultaneous same-revision commands accept exactly one and invoke one transform.
4. Fixed Book → First → Second undo/redo/branch states persist increasing revisions.
5. Real denied directory writes publish nothing and preserve undo and redo.
6. Real CSV rows East10/West20/East5 produce count3/sum35, original bytes and one
   accepted import revision; a concurrent stale edit cannot overwrite it.
7. An in-flight read finishes at sum35 before replacement reaches sum7; old identity
   callbacks never run afterward.
8. Cancellation after the real import save still publishes the saved state.
9. A thrown finalization error after real replacement publishes the accepted sum7.
10. A constructed DataCommitted/missing-backup receipt blocks old-ID reads even
    though metadata is readable; restoring the real previous files recovers sum35.
11. Immediate rename+chart commands both save; undoing chart retains the rename.
12. New-sheet activation and later selections are independent between ViewModels.
13. Two ViewModels observe shared edit/undo and retain work after one closes.
14. Two real MainActivities occupy distinct native tasks, receive the shared edit
    and undo, and the surviving Activity saves after the other closes. Exact visible
    title text is asserted before capture and after undo.

Final coordinator run: **14/14, 0 skipped**, 4.637 s.
Native regressions: **26/26, 0 skipped** (engine4, chart persistence12,
receipt recovery3, mark filters3, View Data3, dual-axis query1), 0.668 s.
Default dual-ABI debug/test build passes; JVM **75/75**, 0 failures/errors/skipped;
lint **0 errors / 5 warnings**. Generator check passes; token contrast **40/40**
configured pairs passes. Native binaries were not rebuilt: their hashes match the
qualified pinned v1.5.6 artifacts in `2026-10-18-s01-native-engine.md`.

Commands actually used:

```bash
export ANDROID_HOME=/home/polar/Android/Sdk
export ANDROID_NDK=/home/polar/Android/Sdk/ndk/28.2.13676358
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew -PcloudyDataAbis=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e class io.github.baitian6641.cloudydata.WorkbookCoordinatorTest io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5580 shell am instrument -w -e class io.github.baitian6641.cloudydata.EngineSmokeTest,io.github.baitian6641.cloudydata.ChartSettingsPersistenceTest,io.github.baitian6641.cloudydata.RecoveryTest,io.github.baitian6641.cloudydata.MarkFilterQueryTest,io.github.baitian6641.cloudydata.ViewDataQueryTest,io.github.baitian6641.cloudydata.DualAxisQueryTest io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
```

Runtime APK SHA-256:
`e830c93e90f5a22cd5b391bebed0f9f1ac6483bb605885962a40310fbfd5420b` (x86_64 only).
Final runtime test APK SHA-256:
`9e1ac5bbf1af20431398915eb88773bdfb7ec4c351dcb666f9ec13bb23c1075d`.
The earlier 26-test regression run used the same production APK; its earlier
instrumentation APK differed only before adding visible-text Activity assertions.
Full sizes, default package ABIs/hash and source hashes are in the artifact manifest.
No APK or native binary is committed as source or published as a release.

## Review, failures and limits

Independent reviewer `state_audit` found and root resolved: new-sheet activation,
same-view rapid-command conflicts, stale save badge/error retention, cancellation
between save/publication, failed import finalization, and nonthrowing retained
recovery. The final source re-review reported no remaining material finding.

The first Activity capture showed the old title although ViewModel values were
updated. That image was retained as `two-activities-before-frame-wait.png`; the
probe was strengthened to assert actual UI text before capture and after undo.
The final 14-test rerun passed. Root and the independent reviewer both inspected
`two-activities.png`, SHA-256
`e093cbbb69b0dc2d0323d02ff5a17de0abefdf4dfc3bb5a10d4b0f143da35ebf`,
and saw the shared title, saved badge and legible empty worksheet without obvious
clipping/overlap. This is one light/default-font surface, not a full visual matrix.

The universal APK's first emulator install failed for storage. The discriminating
x86_64-only package installed successfully, preserving emulator apps/data. No
physical Googlebook interaction occurred. Default Gradle first required its normal
writable cache outside the sandbox and then passed under approved execution.
The optional Android CLI wrapper could not initialize its cache in the restricted
filesystem; native checks used the existing explicit-serial adb/instrumentation path.

Remaining gates: durable command IDs/journal and history across process death;
full conflict proposal/reapply UI; actual process kills at commit boundaries;
cross-process writers/query service, Arrow/result leases; cancellation UI with a
slow query; background/side-by-side/external-display and physical input/IME/TalkBack;
conflict-banner native visual/input review. The two observed Activities were both
2880×1800 fullscreen, not accepted side-by-side desktop windows. Physical device
`C1L63708PZ` stays on owner hold. Licensed Tableau and 16 KiB runtime remain blocked.
No chart/stage/parity row was promoted from this foundation evidence.

Plain-text run logs omit trailing terminal blank lines. Raw logs and JVM summary: `android-test-results/2026-10-20/s032-*`.
Inspected captures, complete source/fixture hashes and artifact identities:
`artifacts/2026-10-20-s03-coordinator/`.
