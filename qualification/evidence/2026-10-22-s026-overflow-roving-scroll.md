# S02.6 overflow tab-strip roving scroll evidence

**Date:** 2026-10-22
**IDs:** S02.6; F64; CD04/CD05/CD06; Q19/Q21 local-selection and keyboard invariants.
**Status:** implemented_unverified pending final review/full-suite artifact binding.

## Readiness and authority

The repository was clean at `77515bd` after the roving tab and idle-save slices.
The full native suite had already passed **238/238** with the pre-existing roving
and idle-save evidence. This slice uses the existing API36 x86_64 emulator only;
the physical Googlebook remains untouched. The scoped Cloudy authorities remain
CD04 layout, CD05 tabs and CD06 keyboard navigation. S11.C0 remains partial and
S13.C0/S15.C0 absent; no worker-backed dependency is claimed.

## Expected behavior

- The worksheet tab strip remains a manual-activation roving group.
- Arrow movement changes only focus, never worksheet selection.
- Every focused roving tab is brought fully into the horizontal scroll viewport,
  including when the workbook opens with an overflowed selected tab.
- The New worksheet action is pinned outside the scrollable tab list and remains
  visible/focusable while tabs scroll.
- Tab leaves the selected tab for New worksheet; Shift+Tab reenters the selected
  tab; a Left arrow from the pinned control reenters the roving tab.
- Pointer activation from the focused strip preserves keyboard focus.
- No endpoint wrap behavior is asserted.
- Checkpoint/default/revision/receipts/history/CSV/Parquet stay exact.

## Implementation

Each tab owns a remembered `BringIntoViewRequester`; the roving index drives the
current tab's request so arrow, selection and pointer transitions scroll the
roving owner into view. The pinned New worksheet action is a sibling outside the
scrollable tab content. The arrow preview handler is on the outer strip so Left
from the pinned action reenters the roving owner; when no tab owns focus, every
non-arrow key (including Tab/Enter/Space) passes through to the pinned button.
The focused tab index tracks the actual focus owner, deferred requests clear on
blur, and worksheet-list removal clamps roving state.

## Review-driven findings and fixes

1. The first verification pass did not open on an off-screen selected tab and the
   bounds assertion compared only to the display edge. A separate open-at-selected
   phase now selects the last overflow worksheet, and focused bounds compare
   against the pinned New worksheet edge.
2. Moving the pinned action outside the scroll content initially moved the arrow
   handler off the outer strip, so arrows from the pinned action did not return
   to the tab list. The handler is now on the outer strip, tracks the actual
   focused tab, and has a direct native Left-arrow regression.
3. A later review found that same reentry branch consumed every key when no tab
   owned focus, so the pinned action would not receive Enter/Space or a forward
   Tab. The handler now consumes only Left for reentry; a native Enter regression
   proves the pinned action activates and the full suite passes.
4. An emulator install failure due to low `/data` space caused one stale-APK
   failure during development; the package hash was verified after reinstall.
   The physical Googlebook was untouched throughout.

## Verification

| Check | Result |
| --- | --- |
| Targeted roving + overflow open + pinned Enter | **3/3**, 65.987 s |
| Full native suite | **240/240** |
| JVM | **75/75** |
| Lint | 0 errors / 7 warnings |

Final native evidence directories:

- `qualification/evidence/android-test-results/2026-10-22-s026-overflow/roving/`
- `qualification/evidence/android-test-results/2026-10-22-s026-overflow/opens-selected/`
- `qualification/evidence/android-test-results/2026-10-22-s026-overflow/pinned-enter/`
- Full native result: `app/build/outputs/androidTest-results/s022-roving/full-native-overflow-final4.txt`
- Source/result manifest: `qualification/evidence/artifacts/2026-10-22-s026-overflow/artifacts.json`

The roving run includes 20 overflow worksheets, 20 Right arrows, 21 Left arrows,
a pointer-selected tab, Tab to the pinned New worksheet action, Left-arrow and
Shift+Tab reentry, complete state/byte oracles and focus/bounds captures. The
open-at-selected run proves the last overflow worksheet is visible and selected
on workbook open; the pinned-Enter run proves the pinned action adds exactly one
worksheet and one receipt without changing the reopen default.

## Limits

- Physical keyboard, TalkBack speech and platform shortcut acceptance remain
  unverified.
- Overflow menu and close-focus transfer remain separate; only the pinned New
  action is qualified.
- Full theme/density/font/window matrix remains open.
- Endpoint wrap remains unclaimed.
