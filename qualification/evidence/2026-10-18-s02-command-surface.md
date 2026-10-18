# Command surface and workbook history evidence

**Date:** 2026-10-18
**Stage:** S02/S03 partial
**Platform:** HP Googlebook 14, arm64-v8a, API 37; x86_64 API 37 emulator
**Artifacts:** `2026-10-18-command-palette.png`, command palette/UI test logs

## Command palette

- Ctrl+Shift+P opens the native palette on Home and Workbook.
- Ctrl+N opens the New workbook dialog on Home; Ctrl+F requests the search field.
- Palette supports search, Up/Down selection, Enter to run, Escape to close, and
  filters by command label or shortcut.
- Home commands: New workbook, Open settings, Show recents/favorites/all
  workbooks, Focus search.
- Workbook commands are context-specific: Save, Undo/Redo when available,
  Import/replace data, New worksheet, Rename workbook, Chart settings, Toggle
  inspector, Retry query when failed, Back to Home. Disabled/unsupported actions
  are not offered as no-ops.
- `CommandPaletteTest` runs the Settings command and verifies Escape closes the
  palette on both devices; the captured screenshot shows the palette over the
  real Home surface. Full suite: 18 tests per device, 0 failures.

## Shortcuts and history

- Ctrl+S is handled at the workbook root, requested focus on entry, and is covered
  by `ShortcutIntentTest`: launching with `EXTRA_WORKBOOK_ID` and sending the
  chord commits a newer workbook revision.
- A pure `WorkbookHistory` provides bounded undo/redo, clears redo on a new edit
  and resets on source snapshot changes. `WorkbookHistoryTest` covers undo/redo,
  bounded history and import reset.
- Device-walkthrough: changed Sum to Average, tapped Undo (restored Sum/40/60),
  tapped Redo (restored Average/60/13.333333). Undo/Redo icons expose real
  enabled states; the disabled Redo tint was inspected after redo.
- Ctrl+Z/Ctrl+Shift+Z are not wired in the workbook yet; Undo/Redo are available
  by toolbar and palette. This avoids stealing text-field undo before a formula
  draft contract exists.

## Limits

- No menu bar, split-divider keyboard resize, tab overflow menu or package Open.
- Palette focus restoration to the exact initiating control after command
  execution is not explicitly tested.
- Physical keyboard chords were sent through the instrumentation/UiAutomator
  input path, not a human typing on the Googlebook keyboard; OS-reserved chord
  conflicts remain to test.
- Multiwindow document routing has an intent extra but no cross-window revision
  coordinator; Ctrl+S is per ViewModel.
