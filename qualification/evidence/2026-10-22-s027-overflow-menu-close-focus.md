# S02.7 overflow menu and close focus transfer evidence

**Date:** 2026-10-22
**IDs:** S02.7; F64/F01; CD05 tabs/CD06 keyboard; Q19/Q21 selection and focus invariants.
**Status:** verified pending final review handoff.

## Readiness and authority

The slice started from `26c97dc` with the full native suite at **240/240** and the
S02.6 roving/overflow scroll contract qualified. Authorities: CD05 tabs, CD06
keyboard navigation, File 01 close semantics (a closed worksheet view is removed
from the strip; deleting sheet definitions is not claimed here). S11.C0 remains
partial and S13.C0/S15.C0 absent; no worker-backed dependency is claimed.

## Expected behavior

- A pinned overflow action stays visible beside the pinned New worksheet action
  while worksheet tabs scroll.
- Keyboard (Tab to the action, Enter opens, arrows navigate, Enter selects,
  Escape dismisses) and pointer both drive the menu.
- Selecting a clipped worksheet from the menu selects it, scrolls it into view,
  and returns focus to its tab.
- Closing a worksheet removes it through a real `RemoveWorksheet` command with a
  typed receipt, an undo snapshot and exact canonical bytes; closing the selected
  worksheet moves selection and focus to the neighbor; closing an unselected
  worksheet keeps this view's selection and focus.
- Removing the last remaining worksheet and removing unknown or malformed
  identities are refused without writing or consuming the command ID.
- Checkpoint/revision/receipt/history/CSV/Parquet oracles stay exact.

## Implementation

- `WorkbookCommand.RemoveWorksheet` is a validated, journaled command kind
  (`removeWorksheet`) carrying only the stable worksheet identity; receipt replay
  is idempotent and failed validation consumes nothing.
- The coordinator removes the definition, snapshots the predecessor for undo,
  and maps the canonical `activeWorksheetId` reopen hint to the previous
  neighbor, or the first remaining worksheet when the first one is removed. The
  final worksheet is never removed.
- `WorkbookViewModel.removeWorksheet` mirrors that neighbor fallback for the
  view-local selection only when the removed sheet is the view's selected sheet;
  removing another sheet leaves the view's selection untouched.
- The tab strip gains a pinned overflow action between the scrollable tabs and
  the pinned New worksheet action. The menu lists every worksheet with a close
  control per entry (one focus stop per row); menu selection/close return focus
  through the existing roving owner, deferred until the accepted document no
  longer contains the removed sheet. On the strip itself, Delete or Backspace on
  a focused worksheet tab closes it through the same command path.

## Review-driven findings and fixes

1. First-draft close focus targeted the coordinator's canonical fallback; on a
   view whose selection differs from the persisted reopen hint, focus and
   selection diverged. The ViewModel now mirrors the fallback for the view-local
   selection, and the strip's focus target matches it.
2. Menu entry lookup under UiAutomator could not depend on `scrollable` or fixed
   scroll gestures: on the tall emulator display every entry renders at once, and
   injected 100%-span scroll gestures start outside the popup bounds. Entry
   lookup now pairs a worksheet label with the Close control on the same row and
   swipes inside the popup bounds only when the list actually scrolls.
3. A system promo overlay ("Dismiss overlay Congratulations…") and leftover
   desktop windows stole window focus mid-run, swallowing real key events. The
   fixture now dismisses such overlays and starts from the launcher; the keyboard
   menu phase waits until the popup actually holds key focus before arrows.
4. `WorkbookCoordinatorTest.twoRealActivitiesShareSavedCommandsAndClosingOneRetainsOtherWindow`
   and `VegaRendererProbeTest.exportsCurrentChartPngWithBoundedDimensionsAndEvidenceFile`
   fail in the current physical desktop environment (window visibility noise;
   803 vs 804 px export rounding). The coordinator case fails identically on the
   unmodified `26c97dc` baseline, so both are recorded as environment/renderer
   precision issues, not regressions from this slice.

## Independent review findings and resolutions

1. Strip focus indices (`focusableIndex` unclamped; index-based anchors drifting
   on external worksheet mutations) are now remapped by tab identity on every
   tab-list change, with the close transfer owning the indices while pending.
2. The close control is disabled and no focus-transfer slot is created when only
   one worksheet remains; the ViewModel guard already refused the command.
3. Menu rows now have exactly one focus stop; keyboard close moved to a strip
   Delete/Backspace shortcut on the focused tab. Evidence: the physical Delete
   key (KEYCODE_FORWARD_DEL) closes the selected tab and transfers focus.
4. Assertion tightenings: the menu-open phase now asserts the first entry holds
   keyboard focus before arrows; Escape asserts focus returns to the pinned
   overflow action; the menu phase asserts the union of rendered labels equals
   the full worksheet set; a selected-first-worksheet close is exercised; the
   roving walk asserts the pinned overflow action's bounds never change; the
   coordinator matrix adds empty/oversize identities and refusal-then-reuse.
5. Reviewer-verified: coordinator ordering, idempotent replay, AddWorksheet
   identity reservation, view-local vs canonical split, failure paths, and
   regression-freedom of the updated Tab/arrow routes.

## Verification

| Check | Result |
| --- | --- |
| Targeted tab suite, emulator API36 | WorkbookTabRovingFocusTest **4/4** + RemoveWorksheetCommandTest **6/6** (post-review build) |
| Targeted tab suite, physical Googlebook (API37, pre-review build) | 3 × **10/10** tab+removal; AddWorksheetCommandTest **14/14** |
| Full native suite, emulator, post-review build | **247/247** |
| Full native suite, physical Googlebook (pre-review build) | 245/247 with the two recorded environmental failures above |
| JVM | **75/75** |
| Lint | 0 errors / 7 warnings |

Final native evidence directories (emulator captures):

- `qualification/evidence/android-test-results/2026-10-22-s027-menu-close/menu-close/`
- `qualification/evidence/android-test-results/2026-10-22-s027-menu-close/roving/`
- `qualification/evidence/android-test-results/2026-10-22-s027-menu-close/opens-selected/`
- `qualification/evidence/android-test-results/2026-10-22-s027-menu-close/pinned-enter/`
- Full-suite summary: `qualification/evidence/android-test-results/2026-10-22-s027-menu-close/full-suite-emulator.txt`
- Source/result manifest: `qualification/evidence/artifacts/2026-10-22-s027-menu-close/artifacts.json`

The menu run covers keyboard open/first-entry focus/arrow/Enter selection and
Escape dismissal with focus returned to the pinned action, pointer selection of
the clipped Overflow 20 entry, full worksheet-set listing, pointer close of an
unselected sheet with focus retained, strip Delete close of the selected sheet
with neighbor selection/focus, a selected-first-worksheet close through the
menu, and receipt/revision/undo/byte oracles on the persisted workbook.

## Limits

- Physical keyboard, TalkBack speech and platform shortcut acceptance remain
  unverified.
- Worksheet deletion from the workbook definition set with dependency handling
  is a separate, unclaimed capability; close here removes the tab through the
  journaled RemoveWorksheet command.
- The two physical-environment failures above remain open and are tracked as
  environmental, proven against the unmodified baseline.
- Full theme/density/font/window matrix and endpoint wrap remain open.
