# S02 display settings evidence

**Date:** 2026-10-18
**Stage:** S02 (partial)
**Status:** per-app System/Light/Dark and three density tiers implemented and
persisted on the physical device; accessibility/input/multiwindow gates remain
**Platform:** HP Googlebook 14, Android 17/API 37, arm64, freeform desktop window
**Artifacts:** `2026-10-18-settings-light.png`,
`2026-10-18-settings-dark-compact.png`, `2026-10-18-settings-persisted.png`,
`2026-10-18-settings-persisted-ui.xml`

## Procedure and observations

1. Fresh install; Home showed Settings as a real navigation row.
2. Opened Settings. Defaults were System appearance and Comfortable density;
   the selected radio row exposed `checkable=true, checked=true` in the Android
   semantics dump.
3. Selected Dark. The entire app surface changed to the supplied dark roles
   without an Activity recreation or query rerun; status remained `Saved`.
4. Selected Compact pointer. Row heights/spacing changed while the body label
   remained the Cloudy 14 sp role.
5. Force-stopped the process and relaunched. The app reopened in Dark/Compact;
   Settings showed Dark and Compact pointer selected (`checked=true` on the
   selectable row), and the preference status was `Saved`.
6. The setting screen explicitly states that these preferences belong to Cloudy
   Data, apply to open windows/future launches, and do not change workbook data
   or recalculate a query.

## Automated checks

- `DisplayPreferencesTest` (2 tests): explicit choices persist across store
  instances; invalid stored values fall back to System/Comfortable without
  deleting the stored evidence.
- Full suite: 14 instrumentation tests per device, 0 failures; 5 JVM tests.
- `./gradlew :app:lintDebug`: 0 errors. Remaining warnings: WebMessageListener
  feature-suppression visibility (the code checks the feature before add and
  before remove), a false-positive `MissingOnRenderProcessGone` report for the
  handler already implemented, intentionally pinned Kotlin/Compose versions, and
  the adaptive-icon folder qualifier.

## Limits

- No TalkBack speech, physical keyboard traversal, IME composition, 200% text
  scale, or hit-target overlap measurement was recorded for Settings.
- Light/Dark was visually inspected, not passed through the full rendered
  contrast matrix.
- Cross-window observation is implemented through one app-level StateFlow, but
  two simultaneously open windows changing preferences were not exercised.
- The system caption bar remains OS-managed and was not recolored; this is
  expected and is not counted as a Cloudy surface.
