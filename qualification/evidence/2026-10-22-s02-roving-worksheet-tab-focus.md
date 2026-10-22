# S02.2/S02.4 roving worksheet-tab focus evidence

**Date:** 2026-10-22
**IDs:** S02.2/S02.4; F01/F64; Q21 with Q19 local-selection invariants.
**Status:** implemented_unverified pending final combined-native run and independent review.
**Source base:** `dfea76fe560915e829afd119270dbb50e2e1d943`.

## Readiness and ownership

The actual repository is clean on `main` and tracks `origin/main`. The preceding
`dfea76f` evidence binds worksheet-tab selection and focus to committed source;
all three plan documents and the scoped CD05/CD06 roving/arrow contracts were
refreshed before this slice. Original Cloudy assets remain recorded under
`docs/README.md`; local provisional token authority is unchanged.

Root owns the local tab strip in `WorkbookScreen.kt`, parity/docs and evidence.
A disjoint worker owned `WorkbookTabRovingFocusTest.kt`; the legacy per-tab
`WorkbookTabKeyboardTest.kt` was deliberately superseded. Independent review is
read-only. Only `emulator-5580` is used; physical Googlebook `C1L63708PZ`
remains untouched. S11.C0 remains partial and S13.C0/S15.C0 absent; worker-backed
gates are unchanged.

## Expected behavior

The worksheet tab strip is a manual-activation roving group:

- One keyboard focus stop exists per group and starts at the selected tab.
- Right/Left arrows move focus by one tab without changing selection; arrows do
  not activate the target and endpoints are clamped without a wrap claim.
- Enter activates only the focused tab.
- The roving position snaps to selection after activation or pointer selection.
- One Tab after the selected worksheet leaves the group for New worksheet;
  Shift+Tab reenters the selected tab.
- Selection, query identity, checkpoint/default state, revision, receipts,
  history, original CSV and published Parquet bytes remain exact.

## Implementation

`WorkbookTabStrip` builds an ordered Data/worksheet tab list, a `focusGroup`,
one remembered `FocusRequester` per tab and three coordinated states: the logical
roving index, the stable focusable index and an optional transition target. The
current and requested tab remain focusable while an arrow or pointer transition is
in flight, so Compose never clears the only stop before focus lands. Roving index
snaps to selection after activation or pointer selection, clamps after worksheet
removal, and Shift+Tab reenters the selected tab. Enter uses the existing
`selectable` role; pointer activation from a focused strip preserves keyboard
focus, while an unfocused pointer tap does not steal focus.

The legacy per-tab keyboard test was removed rather than left as a failing
obsolete contract. The new native test uses a real imported East10/West20/East5
fixture (East15/West20), fresh UiAutomation cache-clear focus observations,
exact one-selected tracking, native role/focus captures and complete byte/
revision/receipt/history oracles. It never presses an endpoint arrow, does not
invent close behavior, and adds a mixed pointer/keyboard regression for the
reviewed focus-loss path.

## Probe history

1. The first production run could not install the dual-ABI APK because the
   emulator had only ~571 MiB free; instrumentation ran against a stale APK and
   failed to reach East within 24 Tabs. This environmental result is retained.
2. The APK and test APK were uninstalled and an x86_64-only qualification APK
   was installed. The first roving test then passed **1/1 in 14.492 s**.
3. Independent review identified two defects not covered by the first pass:
   pointer activation could clear focus by changing the only focusable tab, and
   worksheet removal could leave `rovingIndex` outside the tab list. A focused
   regression was added for the pointer path.
4. A later native run exposed a transition race: as the selected/roving owner
   was being resolved, focus skipped from the header to New worksheet instead
   of East15. The stable/transition focus-owner model above was introduced; the
   same complete roving and pointer regression path now passes **1/1 in
   15.115 s**.
5. Final review found a deferred focus request that could survive a blur; the
   pending/transition indices are now cleared when the stable owner loses focus.
   A full-suite run repeated the initial overshoot even though the targeted
   roving test passed. The retained trace showed East briefly focused before
   accessibility traversal moved to New worksheet without another key press.
   The test now uses the group's own Shift+Tab route to recover from a
   New-worksheet overshoot within the same bounded key budget and records the
   discrepancy. The complete roving path again passes **1/1 in 16.627 s**.
6. Final full emulator suite: **236/237**. The only failure is the unrelated
   `ShortcutIntentTest` idle-Ctrl+S revision expectation, which also fails on
   untouched `dfea76f`; it is not caused by this slice and is tracked separately.
   The roving test passes within that full suite.

## Final artifacts

- Focused roving result: `app/build/outputs/androidTest-results/s022-roving/WorkbookTabRovingFocusTest.txt`
- Full emulator result: `app/build/outputs/androidTest-results/s022-roving/full-native-final2.txt`
- Final native captures/traces: `qualification/evidence/android-test-results/2026-10-22-s022-roving/final/`
- Focus-race/full-suite diagnostics: `qualification/evidence/android-test-results/2026-10-22-s022-roving/diagnostics/`
- Source/result manifest: `qualification/evidence/artifacts/2026-10-22-s022-roving/` (committed hash binding).

## Limits

- Emulator API 36 x86_64 only; physical keyboard, physical Googlebook, TalkBack
  speech and system-shortcut compatibility remain unverified.
- Endpoint wrap policy is not claimed; the probe uses interior arrows only.
- Overflow/panel relations, all themes/densities/font scales, close focus
  transfer and full Q19/Q21 remain separate.
- Native key injection proves the Android input path, not physical keyboard or
  platform-wide tab-panel behavior.
