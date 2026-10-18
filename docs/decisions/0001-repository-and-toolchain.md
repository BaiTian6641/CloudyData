# ADR 0001 — Repository baseline and pinned toolchain

**Date:** 2026-10-18
**Status:** accepted for the current implementation slice

## Context

`/home/polar/CloudyData` contained only documentation and no Git repository.
`git ls-remote --symref git@github.com:BaiTian6641/CloudyData.git` authenticated
and returned zero advertised references, establishing an empty intended remote.
The local Git identity is the configured `TIANRUI BAI <tbai9@wisc.edu>`.

The target used for device evidence is an HP Googlebook 14 (`quartz`), Android 17
(API 37), arm64-v8a, 4 KiB page size, 2880×1800 at 260 dpi, 15.8 GB RAM,
`desktop` characteristics, WebView 149.0.7827.5. A `CloudyConsoleDesktopApi37`
x86_64 emulator is the second ABI runner.

## Decision

- Initialize a dedicated repository on `main`; origin is the intended SSH URL.
- Pin the following toolchain for this slice:

| Component | Pin | Evidence/notes |
| --- | --- | --- |
| JDK | 21.0.9 LTS | `java -version` |
| Gradle | 9.8.1 | Wrapper generated from the locally cached distribution |
| AGP | 9.4.1 | Supports max API 37; requires Gradle ≥ 9.6; default NDK 28.2 |
| Kotlin | 2.2.10 | Bundled by AGP 9.4.1 built-in Kotlin; Compose compiler plugin pinned to the same version |
| Compose BOM | 2026.09.00 | Latest stable at baseline |
| compileSdk / targetSdk / minSdk | 37 / 37 / 35 | Target API 37; desktop windowing available since API 35 |
| SDK Build Tools | 37.0.0 | Installed and used |
| NDK | 28.2.13676358 | Installed; native engine and JNI |
| CMake | 3.22.1 (SDK) | Used by AGP externalNativeBuild |
| DuckDB | v1.5.6, commit `069cc9f9b5be802405797faecc284961b07c70ef` | Native Android build; see ADR 0004 |
| Activity | 1.13.0 | Stable |
| Lifecycle | 2.11.0 | Stable |
| Room | 2.8.5 | Pinned but not yet wired into a runtime path |
| WebKit | 1.17.1 | Pinned for the pending renderer host |
| DocumentFile | 1.1.0 | Stable |

- Use built-in Kotlin (no `kotlin-android` plugin).
- Use API 37 as `compileSdk` and target; minSdk 35 because the intended desktop
  windowing behavior is an Android 15+ capability.
- Do not upgrade dependencies until a concrete requirement or supported fix is
  recorded and affected contracts rerun.

## Consequences and limits

- AGP 9.4.1 with built-in Kotlin differs from older Kotlin-plugin examples; build
  files are written for the built-in path.
- API 37.1/37.2 minor SDK platforms are not used. The physical device reports
  API level 37; no minor-level API is required by the current slice.
- The source tree omits generated native binaries; `tools/build_duckdb_android.sh`
  recreates them deterministically from the pinned commit.
- A 16 KiB runtime device was unavailable. ELF load alignment is checked, but
  runtime 16 KiB support remains unqualified.
