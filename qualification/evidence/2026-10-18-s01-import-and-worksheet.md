# S01 import and worksheet vertical-slice evidence

**Date:** 2026-10-18
**Stage:** S01.9 partial, S02 partial, S03 partial, S04 partial, S05 partial, S06 partial
**Status:** verified on the pinned physical device for the synthetic Q06-shaped
flow; S01.7 (Vega), S01.8 (1M-row grid), S01.10 (multiwindow), cancellation and
independent visual review remain pending
**Platform:** HP Googlebook 14, Android 17/API 37, arm64-v8a, 4 KiB pages,
freeform desktop window, app user 10
**Artifacts:** `qualification/evidence/artifacts/2026-10-18-*` and SHA256SUMS

## Procedure

1. Installed the debug APK built from the working tree.
2. Launched `MainActivity` in a freeform desktop window.
3. Created a workbook through the real New workbook dialog.
4. Used the real `ACTION_OPEN_DOCUMENT` picker. The fixture was published to the
   current user's `Downloads` collection by the test-only
   `MediaStoreFixtureTest`; an `adb push` to user 0 storage was invisible to the
   user-10 picker.
5. Inspected the file in the import dialog (3 fields, 4 preview rows).
6. Applied the import; the workbook switched to the worksheet with `region` on
   Rows and `sales` Sum on Values and a bar mark.
7. Selected a table row and opened the Inspector modal.
8. Changed the aggregation to Average.
9. Force-stopped the process, relaunched, reopened the workbook and confirmed the
   saved source, sheet placements, chart and values.
10. Cancelled a replacement import rather than overwriting the snapshot.

Injected example: `region,customer,sales` with `(A,u,4),(A,u,6),(A,v,30),(B,w,60)`.
This is synthetic fixture data, not a production example.

## Observed behavior

| Observation | Result |
| --- | --- |
| Home initial state | Empty state, no fabricated recents; 0 workbooks. |
| New workbook | Real dialog; name persisted; route opened the workbook. |
| Import inspection | `cloudy_ui_q06 (1).csv`, 48 B, 3 fields, 4 preview rows; field chips `region · varchar`, `customer · varchar`, `sales · bigint`. |
| Applied import | Status strip `cloudy_ui_q06 (1).csv · 4 rows · local Parquet snapshot`; query state `Query ready`; engine `DuckDB v1.5.6`. |
| Default worksheet | Rows `region`, Values `sales` Sum, Bar mark; chart and table both show `B = 60`, `A = 40`. |
| Sum semantics | Group A sums 4 + 6 + 30 = 40; group B = 60. |
| Row selection / Inspector | Selected row A; Inspector reports `region A`, `Sum of sales 40`, snapshot id, format v1, revision, source path, engine version. |
| Average change | Table and chart update to B=60, A=13.333333; query remains local and ready. |
| Save/reopen | Process force-stop then relaunch: Home shows the workbook, source, 4 rows and modified time; reopen shows the same chart/table and `Saved locally`. |
| Replacement flow | Dialog names the source and warns that the previous source/snapshot are overwritten; Cancel left the current snapshot unchanged. |

## Evidence files

- `2026-10-18-home.png` / `2026-10-18-home-ui.xml`
- `2026-10-18-import-dialog.png` / `2026-10-18-import-dialog-ui.xml`
- `2026-10-18-worksheet-current.png` / `2026-10-18-worksheet-ui.xml`
- `2026-10-18-inspector.png`
- `2026-10-18-reopen.png`

The screenshots were inspected by the implementation leader only. An independent
reviewer has **not** accepted the visual/interaction surface, so the visual gate
remains `implemented_unverified`. The screenshots include an unrelated Cloudy
Console window behind the app; that window is not part of Cloudy Data.

## Q01/Q02 status

- Q01 leading-zero `00123`, quoted comma, escaped quote, embedded newline and an
  empty note were asserted in the instrumentation test. The empty note is NULL.
- Q02 Int64, exact decimal arithmetic, timestamp precision and Unicode passed via
  JNI and CSV/Parquet paths.
- Q06-shaped data was used for the visible flow, but the full Q06 LOD/context
  fixture behavior is not implemented.

## Limits

- No formula parser, filter stage, context filter, LOD, totals, table calculation
  or relationship model exists yet; the visible query is a fixed aggregate.
- The chart is a provisional native bar/table surface with a 24-category cap,
  zero-baseline horizontal bars and no negative-value drawing. It is not the
  required Vega renderer.
- Category color identity is a label-hash into the palette, not a persisted
  workbook mapping.
- The import cannot be cancelled; the preview row count is bounded; source
  replacement has no undo.
- The app was driven with `adb shell input` coordinates, not a physical
  keyboard/mouse/trackpad acceptance run. Keyboard traversal, IME, TalkBack and
  focus return still need their own evidence.
