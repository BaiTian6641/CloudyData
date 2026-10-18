# S01 native engine evidence

**Date:** 2026-10-18
**Stage:** S01.2/S01.6 (partial)
**Status:** local query execution verified; cancellation, streaming Arrow, memory
envelope, service process and 16 KiB runtime remain unverified
**Source:** working tree before the first repository commit
**Platforms:** HP Googlebook 14 (arm64-v8a, API 37, 4 KiB) and API 37 x86_64 emulator

## Build

DuckDB `v1.5.6` at commit `069cc9f9b5be802405797faecc284961b07c70ef`,
NDK 28.2.13676358, CMake/Ninja, `android-35`, extensions
`parquet;json;core_functions`, static build, autoinstall/autoload disabled.

| ABI | Artifact bytes | SHA-256 (stripped) | ELF LOAD alignment |
| --- | --- | --- | --- |
| arm64-v8a | 47,958,680 | `31f461972a3c89f4ede587e4040909b87b0ce24762eb85f76a929150ba49da77` | `0x4000` (16 KiB) |
| x86_64 | 51,031,280 | `63f7f0c60f42b8438288150b5e199484d77e048c4dca9951c2c696860134b0fe` | `0x4000` (16 KiB) |

JNI wrapper SHA-256 at capture:
`831dd3eb545fb325452878a9c191590d7089ee151ebaa7801dfead359ca28ad9`
(arm64), `e66125a6da03330c9f2170472ed2140ff6aa7a6dcded19dde114abd21b482964`
(x86_64). These are intermediate debug artifacts, not reproducible release
artifacts.

## Device test results

Command:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Observed result: **5 tests, 0 failures, 0 errors, 0 skipped on each target**
(10 executions total). The suite includes:

1. `typedInt64DecimalTimestampAndUnicodeRoundTrip` — Int64
   `9007199254740993`, DECIMAL `0.10 + 0.20 = 0.30` (scale preserved), timestamp
   microsecond `2026-10-18 12:34:56.123456`, Unicode `héllo 世界 🚀`.
2. `csvImportPublishesVerifiedSnapshotAndReopens` — Q01 fields, embedded
   newline, escaped quotes, leading-zero IDs, snapshot/original existence,
   reopened metadata and ordered IDs.
3. `decimalAndBigIntSurviveCsvQuery` — Q02 exact Int64/DECIMAL/Unicode/timestamp
   after CSV import.
4. `worksheetAggregationUsesExactDecimalSum` — Q02 decimal SUM through
   `WorksheetQuery`.
5. `publishCsvForSafPickerReview` — test-only MediaStore fixture publisher.

Raw JUnit XML is stored under
`qualification/evidence/android-test-results/2026-10-18/`.

## Interpretation

- This is actual Android execution, not a host build or cross-compilation claim.
- It establishes the pinned native engine can open, query, return typed values
  across JNI, and read the imported Parquet snapshot on both shipped ABIs.
- It does **not** establish cancellation, interruption cleanup, Binder death,
  Arrow IPC, 1M-row memory behavior, query-service containment, extension parity
  or 16 KiB runtime support.
- The adapter materializes bounded rows in native memory before returning them.
  The 1,000-row worksheet cap and 10,000-row default are explicit limits, not a
  streaming implementation.
