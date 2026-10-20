# S03.3 abandoned metadata-temp recovery

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.3; F01/F64; Q17/Q34; Q19 conflict and Q02 engine regression retained.
**Status:** verified for this bounded metadata-temp cleanup and single-writer
process-death recovery slice. Full S03 and the protected worker contracts remain
partial.
**Source base:** `99e770d205641f1c8a53fdb720e06e1815102919`.
**Platform:** existing `emulator-5580`, Android 16/API36, x86_64. The physical
Googlebook remains on owner hold. No licensed Tableau reference is available.

## Selection and expected behavior

The preceding bounded S03.3 command/checkpoint slice is committed at `92f8aaa`
and source-bound by `99e770d`. Its 112 source/fixture hashes were checked against
the committed Git objects before selecting this continuation. A real
TEMP_SYNCED writer death already proved atomic accepted-state recovery but left
an unpublished metadata temporary file. Cleaning that known orphan is the next
ready task; it does not depend on Room or a query-worker implementation.

The accepted `workbook.json` remains the only metadata authority. Under the
shared same-process root write lock, load parses and validates the complete
canonical checkpoint and directory identity before cleaning direct regular
`workbook-<digits>.tmp` files. It does not promote any candidate, follow candidate
symlinks, recurse or change canonical bytes/history/receipts. Other filenames and
source/data/migration paths remain untouched. Missing, malformed, foreign or
future canonical metadata retains candidates for diagnosis.

A per-root active-temp set protects reentrant loads through another Store handle
on the writer's thread; other same-process threads wait for the commit to finish.
Registration precedes the commit observers and is removed in `finally`.
Enumeration/deletion errors are logged and do not prevent access to a valid
checkpoint. Cleanup is best effort when filesystem permissions deny deletion.

Root owns WorkbookStore and evidence/state documentation. One independent worker
owns only the new WorkbookMetadataRecoveryTest file; another owns only the
existing WorkbookJournalCrashTest file. A separate read-only reviewer checks the
combined implementation, tests and actual runtime evidence. No shared-file writes
overlap. The existing debug death service is reused without broadening authority.

## Verification

The combined final native run passed **61/61**, no skips, in 19.017s. It includes:

- WorkbookMetadataRecoveryTest **6/6**: exact canonical content/history/receipts;
  selective direct-file deletion; existing/dangling symlinks and other names,
  directories and nested/source/data/migration bytes preserved; missing,
  malformed, checksum-invalid and future canonical metadata retain candidates;
  real chmod 0555 denial preserves availability and candidate bytes, then cleanup
  succeeds after permissions are restored; reentrant and concurrent second-handle
  loads preserve the live writer's temp and complete without deadlock.
- WorkbookJournalCrashTest **3/3**: three genuine separate-writer deaths, observed
  via Binder from instrumentation PID23769. TEMP_SYNCED PID23800 leaves one
  candidate; valid reopen removes it and keeps exact canonical bytes, content,
  revision, undo/redo and receipts. REPLACED PID23817 recovers Chart C exactly once
  with durable undo/reopen/redo. TEMP_SYNCED PID23833 leaves a real candidate;
  corruption constructed afterward refuses reopen and retains both exact
  canonical and candidate bytes without promotion. Callbacks alone do not pass
  the test; each PID has matching KILL/BINDER_DEATH/VERIFIED_DEATH records.
- WorkbookJournalTest **12/12**, WorkbookCoordinatorTest **14/14**, EngineSmokeTest
  **4/4**, ChartSettingsPersistenceTest **12/12**, RecoveryTest **3/3**,
  MarkFilterQueryTest **3/3**, ViewDataQueryTest **3/3**, DualAxisQueryTest **1/1**.
  Real local native CSV/Parquet and exact 35 → 7 import/authority oracles remain
  covered; no fake store or replacement engine was introduced.

The real permission-denial case produced AccessDeniedException in the cleanup
diagnostic while the validated checkpoint remained available. This is an expected
tested outcome, not a silent claim that deletion always succeeds.

The x86 qualification build and the default dual-ABI build each passed debug/test
assembly, JVM **75/75** (zero failures/errors/skips) and lint **0 errors/6 warnings**.
The existing ChromeOsAbiSupport warning remains visible; ZIP inspection confirms
the default APK contains arm64-v8a+x86_64. Tokens are current and contrast 40/40
declared pairs pass. No tests/assertions/lint checks were disabled.

Independent code/test review by `journal_review` found no material finding on the
actual combined files. Final evidence and all 113 source/fixture hashes independently passed review
before committing. This storage-only change adds no visible surface;
the preceding scoped native capture remains historical evidence for its pinned
revision, not a new visual/hardware pass.

## Commands and artifact binding

```bash
export ANDROID_HOME=/home/polar/Android/Sdk
export ANDROID_NDK=/home/polar/Android/Sdk/ndk/28.2.13676358
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PcloudyDataAbis=x86_64
cp app/build/outputs/apk/debug/app-debug.apk /tmp/cloudydata-s033-temp-runtime-x86_64.apk
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /tmp/cloudydata-s033-temp-runtime-tests.apk
adb -s emulator-5580 install -r /tmp/cloudydata-s033-temp-runtime-x86_64.apk
adb -s emulator-5580 install -r /tmp/cloudydata-s033-temp-runtime-tests.apk
adb -s emulator-5580 shell date +%s
adb -s emulator-5580 shell am instrument -w -e class io.github.baitian6641.cloudydata.WorkbookMetadataRecoveryTest,io.github.baitian6641.cloudydata.WorkbookJournalTest,io.github.baitian6641.cloudydata.WorkbookJournalCrashTest,io.github.baitian6641.cloudydata.WorkbookCoordinatorTest,io.github.baitian6641.cloudydata.EngineSmokeTest,io.github.baitian6641.cloudydata.ChartSettingsPersistenceTest,io.github.baitian6641.cloudydata.RecoveryTest,io.github.baitian6641.cloudydata.MarkFilterQueryTest,io.github.baitian6641.cloudydata.ViewDataQueryTest,io.github.baitian6641.cloudydata.DualAxisQueryTest io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
adb -s emulator-5580 logcat -v epoch -d -s JournalCrashProbe:I WorkbookStore:W '*:S'
```

The first timestamp command used a spaced date format that adb shell split into
multiple arguments. Both packages had installed, but instrumentation had not
started. A single-argument epoch timestamp fixed capture preparation; no failed
native result was hidden or rerun. Logcat is filtered to epochs at/after that
recorded native-run start; no device logs were cleared.

Runtime x86_64 APK SHA256:
`f6c6b6757d04092e7cf23a46c2647d241300736737861bdaa9c201f425162741`.
Runtime test APK SHA256:
`9689e1445183d663313a88cb87324ea89394e87c51aad8f18e81054d842b933c`.
Default dual-ABI APK SHA256:
`2a1747b8875dceb479b8907af3f42b8fee45f1b8b94d99e3ccfa699177018f20`.

Raw build/native/lint/process logs and JVM summary are under
`android-test-results/2026-10-20-s033-temp-recovery/`. Terminal-only blank lines
are trimmed when storing text logs. Artifacts and 113 source/fixture hashes are
under `artifacts/2026-10-20-s033-temp-recovery/`. The source manifest SHA256 is
`92e63accfddc68c092538e4b91aa31d5942ef0287a98ccc32fab3ff13ca10aac`.
No APK was committed, released or distributed.

## Limits

This qualifies same-process exclusion and cleanup only after the separate probe
writer is confirmed dead. Concurrent live writers across Android processes remain
unsupported. Neither SIGKILL nor cleanup proves power-loss durability; directory
fsync remains best effort. This task does not delete legacy migration temps or
source/snapshot operation files. Missing/corrupt canonical metadata deliberately
blocks reopen and preserves candidates; manual recovery is not inferred.

Room, the remaining typed command families, formula drafts, source-version
history, receipt compaction and whole-product-process restoration remain open.
No physical Googlebook, 16 KiB runtime, Tableau, new input/visual qualification or
S11.C0/S13.C0/S15.C0 acceptance is claimed.
