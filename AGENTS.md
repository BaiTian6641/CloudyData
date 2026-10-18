# Repository instructions

Read `docs/README.md` and `docs/execution-state.md` before changing code.

- The three supplied plans live in `docs/plans/`. They define scope and stage
  gates; their status claims are not evidence.
- Use the Cloudy design corpus in `docs/design/` only within the mapped scope in
  `docs/README.md`. Do not import Cloudy Console terminal copy/paste, RDP, X11,
  USB, host-vault or remote-fullscreen behavior into Cloudy Data.
- Numerical meaning belongs to the typed semantic layer, not the chart surface.
  Never let renderer code decide filters, grain, aggregation or workbook state.
- Imported files, formulas, URLs and SQL do not receive implicit execution or
  file/network authority.
- Do not fabricate results. Keep `verified`, `implemented_unverified`, `partial`
  and `blocked` distinct. A compile or an attractive screenshot is not a runtime
  pass.
- `design-system/tokens.json` is the single provisional source. Do not hand-edit
  generated `CloudyTokens.kt`; run `python3 tools/generate_tokens.py`.
- Native DuckDB binaries are generated. Pin the commit in
  `tools/build_duckdb_android.sh`; do not add a Linux/JDBC binary to the APK.
- Preserve unrelated work. Do not force-push, hard-reset, amend published
  commits, disable tests or weaken assertions to obtain a green build.

## Commands

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK=$HOME/Android/Sdk/ndk/28.2.13676358
tools/build_duckdb_android.sh
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.baitian6641.cloudydata.EngineSmokeTest
python3 tools/generate_tokens.py --check
python3 tools/check_token_contrast.py
```

Update `docs/execution-state.md`, append significant findings to
`docs/execution-history.md`, and record new evidence under
`qualification/evidence/` for behavioral changes.
