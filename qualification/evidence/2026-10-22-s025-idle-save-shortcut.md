# S02.5 idle-save shortcut evidence

**Date:** 2026-10-22
**IDs:** S02.5; F64; Q19/Q21 desktop shortcut and durability invariants.
**Status:** implemented_unverified pending final full-native run and review.

## Readiness

The roving worksheet-tab slice is committed through `71f1d92`. Before this task,
the full emulator suite was **236/237**: the only failure was
`ShortcutIntentTest.workbookIntentOpensDocumentAndCtrlSCommitsSave`, which expected
an idle Ctrl+S to create a new revision. The same test was rebuilt and run against
untouched `dfea76f` with the same failure, proving it was a pre-existing stale
expectation rather than a roving regression.

Current accepted behavior is authoritative: `WorkbookViewModel.saveNow()` retries
only a retained failed edit, reloads only on a save conflict, and explicitly does
not create a revision for an already persisted command. Failed/conflict retry is
already covered at coordinator/journal level by existing tests.

## Change

`ShortcutIntentTest` now covers both halves of the accepted contract:

1. `workbookIntentOpensDocumentAndIdleCtrlSDoesNotInventARevision` opens a real
   workbook, sends a real Ctrl+S, and asserts checkpoint equality, unchanged
   revision and byte-identical `workbook.json`.
2. `ctrlSRetriesAFailedEditFromTheActualWorkbookKeyboard` asserts the launched
   workbook surface is visible, forces a real permission-denied commit from the
   actual UI model, verifies canonical bytes/revision stay unchanged, restores
   permissions, sends the actual Ctrl+S chord, and asserts one frozen Add
   Worksheet receipt advances revision by exactly one.

No production source changed. The failed-edit retry proves the keyboard route;
the idle case proves it never fabricates a revision.

## Evidence

- Prior-HEAD stale failure: `qualification/evidence/android-test-results/2026-10-22-s022-roving/diagnostics/shortcut-intent-prior-head.txt`
- Corrected targeted result: `app/build/outputs/androidTest-results/s022-roving/ShortcutIntentTest.txt`
- Full native result: `app/build/outputs/androidTest-results/s022-roving/full-native-238.txt`.

## Limits

- No physical keyboard, TalkBack or platform-shortcut acceptance is claimed.
- Failed/conflict Ctrl+S retry remains bounded by existing coordinator/journal
  tests; this slice does not add another production fault-injection path.
- This is a stale-test correction against the accepted no-revision idle-save
  contract, not a weakening of save durability checks.
