# CUST13 additive and range chart selection evidence

**Date:** 2026-10-18
**Requirements:** CUST13, F39, Q21
**Platform:** API 37 userdebug `emulator-5582`, 2880×1800, 240 dpi, preserved
Area worksheet.
**Prerequisites verified:** exact source-row selection, chart/revision gate,
keyboard focus exit, accessible exact table, and single-row inspector.
**Worker-backed S11.C0/S13.C0/S15.C0:** not involved.

## Implementation

- `SelectionMode` (`REPLACE`, `TOGGLE`, `RANGE`) and `RowSelectionState`
  (`rows`, `anchor`, `primary`) plus pure `RowSelectionReducer` logic.
- Renderer click/keyboard modifiers:
  - plain click/arrow replaces;
  - Ctrl/Cmd-click or Ctrl/Cmd+arrow toggles one exact source row;
  - Shift-click or Shift+arrow selects the inclusive source-row range from the
    last replace anchor.
- Because WebView did not expose pointer Shift reliably as `shiftKey`, the host
  accepts a native MotionEvent meta-state hint (`notePointerMetaState`) for
  pointer selection; keyboard modes use the DOM event directly.
- `SelectionIntent` still requires the current chart ID/revision; stale modes/rows
  are rejected.
- The worksheet stores a row set, highlights every selected exact table row, and
  uses the endpoint as the inspector/primary row. Native/table click remains a
  replace operation. Switching worksheet tabs clears selection.
- Accessible summary documents Ctrl/Cmd add and Shift range behavior.

## Evidence

| Surface | Observation |
| --- | --- |
| Ctrl-click additive | Probe actual pointer injection: row 0 then Ctrl-click row 1 produced `rows={0,1}`, mode `TOGGLE`. |
| Shift-click range | Probe actual pointer injection: row 0 then Shift-click row 1 produced `rows={0,1}`, mode `RANGE`. |
| Keyboard range/add | Probe hardware keys: Shift+Right extended `{0,1}`, `{0,1,2}`; Ctrl+Right added rows; Ctrl+Left removed a row. |
| Product table | On the Area worksheet, ArrowRight, Shift+Right, Ctrl+Right highlighted exact table rows 0,1,2; inspector endpoint remained row 2 with tooltip `2026-03-01 = 30`. |
| Reducer | JVM tests cover replace/toggle, bidirectional range, missing anchor fallback and clamping. |
| Regression | API 37 **47/47**, 0 failures; JVM **28/28**; lint 0 errors / 5 warnings. |

## Limits

- No lasso/rectangle, keep/exclude, drill, additive table clicks, visual mark
  highlighting, or persisted selection set. The table/inspector are the feedback
  path.
- Pointer Shift requires the native MotionEvent hint; physical mouse/trackpad
  Shift qualification and hover remain pending.
- Selection is cleared on worksheet tab change and is not part of workbook
  persistence/reopen.
- TalkBack traversal of multi-selection and a status count remain unverified.
- CUST13 remains partial.

## Evidence files

- `2026-10-18-additive-range-selection.png` / `-ui.xml`
- `android-test-results/2026-10-18/emulator-5582-additive-range-selection-suite.txt`
