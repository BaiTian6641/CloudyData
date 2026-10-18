# ADR 0004 — Native DuckDB feasibility for the current slice

**Date:** 2026-10-18
**Status:** accepted for the local slice; S01 gate remains partial

## Context

DuckDB documents Android support as experimental and recommends building from
its main branch. The architecture makes native DuckDB the preferred candidate
only after S01 proves build, ABI, execution, memory and failure behavior. A host
build or Linux binary is not evidence.

## Experiment

- Build: DuckDB `v1.5.6` at commit
  `069cc9f9b5be802405797faecc284961b07c70ef`, CMake/Ninja with NDK 28.2,
  `android-35`, static extensions `parquet`, `json`, `core_functions`,
  autoload/autoinstall disabled.
- ABIs: `arm64-v8a` and `x86_64`; `libduckdb.so` stripped to 46 MiB / 49 MiB.
- ELF load segments are 16 KiB aligned (`0x4000`) on both artifacts.
- JNI adapter: narrow `open/close/interrupt/execute` surface, typed cell
  marshalling, UTF-8→UTF-16 conversion, DECIMAL scale padding, bounded result
  rows. It does not expose SQL to renderer content.
- Actual Android execution: instrumentation tests pass on the HP Googlebook 14
  (arm64) and the API 37 x86_64 emulator. Int64 `9007199254740993`, DECIMAL
  `0.10 + 0.20 = 0.30` (scale preserved), microsecond timestamp, and Unicode
  `héllo 世界 🚀` round-trip.
- CSV import, Parquet snapshot publication, reopen and exact aggregate query
  pass on the same devices.

## Decision

Use the pinned native DuckDB build and JNI adapter for the current local
analysis slice. Keep the architecture's alternative branches available: if a
later S01 probe fails on memory/cancellation or a required extension, record the
failure and evaluate the documented Wasm path. Do not silently switch to a
server.

## Not established by this ADR

- S01.3 memory/throughput envelopes.
- S01.4 native cancellation and process-death behavior.
- S01.6 Arrow IPC transfer (the current adapter materializes a bounded result).
- Extension parity beyond `parquet`, `json`, `icu` absent, `core_functions`.
- Runtime support on a 16 KiB-page device.
- DuckDB experimental-support stability or release approval.
- Query service process containment or a security sandbox.
