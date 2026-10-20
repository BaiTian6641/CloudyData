# S03.3 typed worksheet additions and truthful retry admission

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.3; F01/F64; Q17/Q19/Q34; Q02 engine regression retained.
**Status:** verified for this bounded typed-addition, native ViewModel admission
and single-writer process-death slice. Full S03, Room and the complete typed command
catalog remain open. New failure-banner pixels/input are implemented_unverified.
**Source base:** `659932093475e2d767e6c5270102202687714890`.
**Implementation commit:** `eac7f6087fbbb94fadb0acf7b0f4b66a8b7c9e3c`; all 115 source/fixture
hashes match committed Git objects. This final binding changes documentation only.
**Platform:** existing API36 x86_64 `emulator-5580`; the physical Googlebook remains
on owner hold. No licensed Tableau reference or 16 KiB runtime is available.

## Selection, ownership and expected result

Reinspected the established repository/root/main/HEAD/upstream/remotes and clean
status. Live remote main matched `6599320`. All 113 source/fixture hashes from the
preceding recovery evidence still matched. The three plans had already been read
in this session; their unchanged content and master runbook were retained. The
original Cloudy assets remain located under the documented sibling repository;
the scoped design authority and single provisional token source are unchanged.

The recorded earliest ready task is typed AddWorksheet, over the verified shared
coordinator, durable receipts/history, atomic v3 metadata and orphan-temp cleanup.
Root owns WorkbookCommand, WorkbookCoordinator, WorkbookViewModel and docs. The
independent test author owns only AddWorksheetCommandTest; another worker owns
only the debug JournalCrashProbeService and new AddWorksheetCrashTest. A separate
read-only reviewer checks the actual combined source/tests/results. No shared-file
write ownership overlaps. No new dependency, renderer or worker authority is added.

An accepted AddWorksheet appends exactly one default unmapped Table definition
under an explicit frozen worksheet ID/name, command/workbook/window identity and
expected revision. One atomic metadata replacement includes its receipt, content
and undo checkpoint. Opening a workbook never executes a stored request.

Duplicate recognition precedes stale/collision checks. An identical retry after
foreign edits returns the current document and original acceptance; retry after
undo leaves the sheet absent. New requests cannot reuse a sheet ID retained in
current content, either history direction or AddWorksheet receipts, including
after a raw-save history barrier. Invalid requests and collisions write nothing
and consume no command ID. Explicit duplicate labels are permitted under distinct
identities. Default UI labels use the first unused Sheet N at first dispatch;
the complete request then stays frozen across retries.

The stored reopen default stays unchanged. Only the originating view activates
the added sheet after proven acceptance and while it exists in latest canonical
state. Other views retain their local tabs. Existing Rename/SetChart records keep
their shape; unsupported saved command kinds/fields fail closed without rewriting.

## Independent review correction

Review identified a real pre-existing retry-state hazard: a failed edit could
retain a retry while a later queued no-op displayed Saved. The bounded fix uses a
per-view failure barrier at the command mutex. Later edits/imports are explicitly
reported not applied before their factories/transforms/importer run; the original
failed request and retry remain visible. Save retries that exact intent, and stale
duplicate retry tokens cannot dispatch again. Later refused actions require a new
explicit request; they are not queued for automatic replay.

Import application shares the same admission boundary and retains its staged
pending import when refused. Known deterministic IllegalArgumentException request
validation is terminal Change not applied without a retry token, allowing a
corrected request. Arbitrary recovery/I/O failures remain on the failed-save path.
At dequeue, the current staged operation ID must still match; queued Apply cannot
resurrect a discarded, replaced or completed pending import.
This does not implement the full multi-proposal review/reapply workflow.

## Verification

The first combined native run passed **77/77**, no skips, in 20.406s. No product
failure was retried to obtain this result. Code-review findings were resolved
before this run, and the matching independent regression oracles passed:

- AddWorksheetCommandTest **14/14**: exact default definition/one acceptance;
  reopen/undo/redo/duplicate-after-undo; foreign-edit duplicate returns current
  content with original acceptance; changed request refusal; invalid/stale requests
  consume no ID/write; current/history/receipt-only ID reservation; explicit
  duplicate labels; raw-save barrier retains receipt identity; unsupported saved
  kind/extra-version field fails beyond a recomputed valid journal checksum;
  actual move-time EACCES preserves the exact request across Save; rapid queued
  additions survive a real REPLACED acknowledgement error with names Sheet2/Sheet3
  and independent two-ViewModel local tabs; Add+Add and Add+no-op failures preserve
  the first frozen retry and explicitly refuse later actions; a real staged CSV
  import cannot bypass the save failure; corrected validation proceeds without
  reload; queued Apply followed by Discard remains Idle with no resurrected files.
- AddWorksheetCrashTest **2/2**: debug writer PID24232 actually dies at TEMP_SYNCED;
  unchanged one-sheet metadata/no receipt recover and only then its real orphan is
  removed. The identical request can subsequently accept one sheet once. PID24249
  actually dies at REPLACED; two exact definitions/one receipt recover, duplicate
  retries write nothing, undo/reopen/duplicate-after-undo leave the sheet absent,
  and redo/reopen restores it once. Stored reopen default never changes.
- Prior regressions: metadata recovery6, journal12, journal process-death3,
  coordinator14, engine4, chart persistence12, import recovery3, mark filters3,
  View Data3, dual-axis query1. These include genuine native CSV/Parquet totals
  35 → 7, real denial and exact prior content/history/receipt oracles.

All five real writer deaths have matching KILL plus Binder-death and verified-death
records observed from instrumentation PID24199. The old chart probes used writer
PIDs24274(TEMP_SYNCED),24290(REPLACED),24306(TEMP_SYNCED). A boundary callback alone
cannot satisfy the tests. Tests exercise one writer at a time, then recover only
after Binder proves that writer dead.

The production-only x86 build and combined x86 build passed. Default dual-ABI
debug/test assembly, JVM **75/75** (zero failures/errors/skips) and lint
**0 errors/6 warnings** passed. ZIP inspection confirms arm64-v8a+x86_64 in the
default APK. Existing ChromeOsAbiSupport warning remains visible; no checks or
assertions were disabled. Token generation is current; contrast 40/40 declared
pairs pass. No dependency, native engine or generated token changed.

Independent reviewer `journal_review` inspected the actual combined source and
all 14 command tests plus death tests. Root resolved every material finding, added
the discriminating admission regressions and ran the final combined revision.
Final raw evidence and all 115 hashes passed independent review before committing.

## Commands and source/artifact binding

```bash
export ANDROID_HOME=/home/polar/Android/Sdk
export ANDROID_NDK=/home/polar/Android/Sdk/ndk/28.2.13676358
./gradlew :app:assembleDebug -PcloudyDataAbis=x86_64
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PcloudyDataAbis=x86_64
cp app/build/outputs/apk/debug/app-debug.apk /tmp/cloudydata-s033-add-runtime-x86_64.apk
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /tmp/cloudydata-s033-add-runtime-tests.apk
adb -s emulator-5580 install -r /tmp/cloudydata-s033-add-runtime-x86_64.apk
adb -s emulator-5580 install -r /tmp/cloudydata-s033-add-runtime-tests.apk
adb -s emulator-5580 shell date +%s
adb -s emulator-5580 shell am instrument -w -e class io.github.baitian6641.cloudydata.AddWorksheetCommandTest,io.github.baitian6641.cloudydata.AddWorksheetCrashTest,io.github.baitian6641.cloudydata.WorkbookMetadataRecoveryTest,io.github.baitian6641.cloudydata.WorkbookJournalTest,io.github.baitian6641.cloudydata.WorkbookJournalCrashTest,io.github.baitian6641.cloudydata.WorkbookCoordinatorTest,io.github.baitian6641.cloudydata.EngineSmokeTest,io.github.baitian6641.cloudydata.ChartSettingsPersistenceTest,io.github.baitian6641.cloudydata.RecoveryTest,io.github.baitian6641.cloudydata.MarkFilterQueryTest,io.github.baitian6641.cloudydata.ViewDataQueryTest,io.github.baitian6641.cloudydata.DualAxisQueryTest io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
adb -s emulator-5580 logcat -v epoch -d -s JournalCrashProbe:I WorkbookStore:W '*:S'
```

Runtime x86_64 APK SHA256:
`62b045ae5ec0e52e68979285a95aa0cdab44183f8dd0b1bdc4e0f679164b2e03`.
Runtime test APK SHA256:
`d0d8f8353fa04a72275e83a3765001f99ee3a3d1d4a8a4d7e6ee213c0beffdbf`.
Default dual-ABI APK SHA256:
`b673c7486f718a122fbc9ff8a14a1bf57a833e3e1a280568a2fc03955acbc7bf`.

Raw build/native/lint/process logs, actual run-start epoch and JVM summary:
`android-test-results/2026-10-20-s033-add-worksheet/`. Logcat is filtered to epochs
at/after that native-run start; no logs were cleared. Terminal-only trailing blank
lines are trimmed in stored text logs. Source/artifact manifests:
`artifacts/2026-10-20-s033-add-worksheet/`, 115 source/fixture files, manifest SHA256
`dc4178d125ab7302c3bba22d08f7f674ec4f1c4f522d7f5c3aa0395858a7bc4b`.
No APK was committed, distributed or released.

## Limits

The JSON authority is ADR0009's accepted interim implementation; Room remains the
destination. History50/receipts4096/metadata32MiB limits remain unchanged. Accepted
receipts do not authorize execution, source replacement or filesystem/network
access. The debug probe stays nonexported and confined to fixed UUID fixture roots.

Single-writer process death is distinct from power loss, whole-product-process
restoration and concurrent live cross-process writers. Native ViewModel behavior
does not itself prove physical keyboard/pointer, side-by-side placement, TalkBack,
all visual modes or complete Q19 desktop acceptance. No C0 gate is promoted:
S11.C0 remains partial; S13.C0/S15.C0 remain absent and mandatory when applicable.
The new failure-message pixels, wrap/short-window fit and Retry keyboard/pointer
path were not captured or exercised. Existing banners are reused without layout
changes; native ViewModel state assertions do not close that visual/input gate.
