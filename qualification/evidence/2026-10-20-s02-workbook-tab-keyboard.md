# S02.2/S02.4 native worksheet-tab keyboard and selected semantics

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S02.2/S02.4; F01/F64; Q21 with Q19 local-selection invariants.
**Status:** verified bounded native input/selection/visual slice; full S02/Q21 remain partial.
**Source base:** `b68abb1df1e0f26b79661dbd3fa336321d585e0c`.

## Readiness and ownership

Continued the actual /home/polar/CloudyData root on clean main, tracking origin/main
and matching the live intended SSH origin at b68abb1. All 135 prior source/fixture
hashes match; native 156/156 and source-bound selection/history/import evidence
remain fresh. The three plans already read this session are unchanged, and the
master continuation and scoped tab/focus/accessibility contracts were refreshed.
Original Cloudy assets remain at the recorded sibling repository bccb328. Local
tokens and accepted metadata authority remain unchanged.

Root owns only the local WorkbookTab/strip in WorkbookScreen plus docs/evidence.
A separate worker owns WorkbookTabKeyboardTest; independent review is read-only.
Root alone builds and uses the existing emulator. There is no shared-file overlap.

## Probe and expected behavior

Real imported CSV/Parquet contains East10, West20, East5; separate filtered Table
worksheets must produce East15 and West20. Begin a fresh actual Activity with no
pointer selection. Use at most 24 Tab presses and retain native focused-node XML;
target the enabled clickable focus owner, not its nonfocusable text child.
Focus West must keep East15 selected/queried until Enter. Enter selects West20;
Shift+Tab/Enter selects East15, then Data source; Tab/Enter returns East15.

Check exact query ownership, visible values, one semantic selected tab, unchanged
metadata/revision/history/receipts/CSV/Parquet, and actual focus/selection captures.
Read-only baseline XML already shows selected=false on every parent despite the
visible East selection. Runtime traversal and activation remain to be measured.

## Authority and limits

Native key injection qualifies the Android input path, not physical keyboard or
system-shortcut compatibility. Actual TalkBack speech, roving-arrow traversal,
close/overflow focus behavior, panel relations, all themes/densities/font scales
and window geometries remain distinct requirements. A bounded pass cannot close
S02/Q21. Numerical meaning stays in the existing semantic/query layer; tabs do
not authorize data operations or persist worksheet selection.

Only existing API36 x86_64 emulator-5580 may be used, with no boot, resize, density
or global preference changes. Physical Googlebook C1L63708PZ remains owner-held;
16 KiB runtime and licensed Tableau are unavailable. Room/source-version/full
lifecycle and physical acceptance stay open. S11.C0 remains partial and S13.C0/
S15.C0 absent; their worker-backed dependency gates are unchanged.

## Initial discriminating result

Untouched production b68abb1 with the first probe failed after 24 Tab presses in
19.703 s. The native trace repeats stale-looking focus nodes: Rename at steps2–5,
Data source6–9, Sales14–16 and Area21–24. The final screenshot visibly focuses Chart
settings while the XML still identifies Area. East15 remains visible and all tabs
report selected=false. This does not establish that West cannot receive focus.

Competing hypotheses are stale accessibility-cache observations versus a product
traversal/semantics defect. Before changing production, refresh the UiAutomation
accessibility cache while retaining the same 24-key/500ms focused-target bound and
all original numeric/selection/byte assertions. Record cached versus fresh focus.
The installed API37.0 android.jar and API36 UiAutomation source expose clearCache
and findFocus. The initial test source, logs, APK hashes and captures are retained.

The fresh-observation baseline used the byte-identical production APK and reached
West on Tab8: the cached owner was East; refreshed polling observed West focused
within 363 ms. The entire keyboard path, exact East15/West20 and checkpoint/source
preservation passed. The only final failure in 18.406 s was absent selected state
at all nine phases. This isolates stale cache/publication observation from product
traversal. Focused West pixels show the default gray fill without a Cloudy ring.
Both baseline attempts and all observations remain retained; no assertion or key
limit was weakened, and no direct focus/pointer shortcut was introduced.

## Implementation

WorkbookScreen's local tab strip is a selectable group; each tab is one native
selectable control with Role.Tab and its actual selected Boolean. Focus state is
separate and draws the existing Cloudy 2dp focus border over selected/unselected
states. Worksheet compositions are keyed by stable sheet IDs. Existing native
Tab/Enter behavior remains; no extra focusable target or manual Enter handler is
added. Model/coordinator/query/storage/token sources are unchanged. The separate
focus-offset proposal and full visual/roving/tabpanel contracts remain open.

The first fixed focused run passed all six existing selection/pointer tests but
stopped the new test at its initial East-owner lookup (7 tests, one harness
failure, 10.369 s). The same APK's successful pointer test retained five captures:
selected East is enabled/focusable/selected=true and clickable=false; unselected
Data/West are enabled/focusable/clickable=true. The native Role.Tab mapping omits
a redundant activation action on the already selected tab. The original harness
required clickable=true even for this selected owner and was over-constrained.

Correct the owner classification to enabled/focusable and clickable OR selected,
while explicitly requiring every unselected tab to expose activation. Retain
exactly one selected tab, all real key events/bounds, exact numeric/query identity
and checkpoint/source preservation. This changes an incorrect accessibility-shape
assumption, not the product expectation. Initial/fresh test sources, failed run,
APK hashes and native counterexample captures are retained for independent review.

The corrected owner lookup first encountered a harness construction error:
UiAutomator BySelector rejects setting selected(true) twice. The expected-owner
helper already sets it for the selected branch, so the selected-publication wait
must inspect that owner's isSelected property instead of appending a second
selector constraint. The failed log and exact test source are retained; production
and the selected-state expectation are unchanged. The six prior tests still pass.


## Final runtime and artifact qualification

Focused final keyboard test **1/1 passes in 13.931 s**. Final combined native
**157/157**, no skips/failures, **78.775 s**, includes that exact frozen test plus
all prior 156 selection/pointer/history/admission/query regressions. All four
Enter activations and adjacent Tab/Shift+Tab focus movements preserve the exact
expected surface/query state. All nine final phases have exactly one selected
owner. Metadata, revision, history, receipts, original CSV and Parquet remain exact.

Root inspected focused unselected/selected West and Data source captures: the
Cloudy 2dp border remains visible while mint selection and exact East15/West20
remain distinct. Final native metadata agrees with focus and selection. Source
binds Compose Role.Tab and selectableGroup; platform observations include selected
flags and collection-item metadata presence, but do not expose an explicit tab-role description in
the retained extras. No spoken-tab/TalkBack acceptance is claimed. The same
2880×1800, 240dpi, fontScale1, System→Light, Comfortable window is used throughout;
IBM Plex Sans/Mono configured, caption insets reported zero. No window/device
presentation mutation occurred.

Final default and x86_64 debug/test builds pass. JVM **75/75**, no failures/errors/
skips; lint **0 errors, 6 retained warnings**. Default APK retains arm64-v8a and
x86_64; installed qualification APK is x86_64. Generated tokens remain current and
declared contrast pairs pass **40/40**. No generated/native binary was committed.

Raw results: `qualification/evidence/android-test-results/2026-10-20-s022-tab-keyboard/`.
Artifacts: `qualification/evidence/artifacts/2026-10-20-s022-tab-keyboard/`.
The manifest binds **136 source/fixture files**, final runtime/test/default APKs,
**25 capture sets** and **102 native trace/hierarchy files**. Initial sources,
failed logs and per-attempt artifact hashes preserve the diagnostic history.
Every PNG agrees with its own phase JSON hash. Source manifest SHA256:
`2f18df63a627c31dad69d5c7b0a6f61096b52059521fdc38f1262f4f76e2589f`.

| Artifact | SHA256 |
| --- | --- |
| Installed x86_64 runtime APK | `f2530f3ca9fd00c000eab63764765ed14c9d79b0ed29287423e1a82654973759` |
| Installed test APK | `6f2e96c320c43b4d9bab79998c7df31fe6a98b64a931db51d398f50fd7712066` |
| Default dual-ABI APK | `c36dc7e0632d6e2084017126ac88a155faa36a6e77abbf834db2ebcf905e214f` |

Commands (SDK/NDK paths from AGENTS.md; final runtime/test copies retained before
default packaging):

```bash
./gradlew -PcloudyDataAbis=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5580 install -r -t /tmp/cloudydata-s022-tab-runtime.apk
adb -s emulator-5580 install -r -t /tmp/cloudydata-s022-tab-runtime-tests.apk
selection=$(cat qualification/evidence/android-test-results/2026-10-20-s022-tab-keyboard/native-selection.txt)
adb -s emulator-5580 shell am instrument -w -r -e class "$selection" \
  io.github.baitian6641.cloudydata.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
```

## Next eligible boundary

S02.2/S02.4 manual-activation roving tabs, F01/F64,Q21 with Q19 invariants. CD05
requires roving focus and CD06 arrow navigation; the current per-tab Tab route is
bounded acceptance only. Probe Right/Left focus without selection, Enter activation,
one Tab leaving the group for New worksheet and Shift+Tab reentering selected East.
Preserve exact query values and checkpoint/source bytes. Current per-tab Tab route
assertions must be explicitly superseded by that qualified contract while keeping
all numeric/selection/Data/byte oracles. No endpoint wrap policy is assumed. Close,
overflow, removal focus transfer, panel relations, physical/TalkBack/fullmatrix
and all C0/Room/reference gates remain open.


Independent reviewer journal_review inspected the actual production/test changes,
all nine final native captures and phase metadata, all 136 source hashes, three
APK bindings, 25 capture sets and 102 trace hashes. No material source/oracle/
visual/binding finding remains. Its selected-role observation matches the narrower
claims above. Root reviewed the final pixels and results independently.
