#!/usr/bin/env bash
# Build the pinned DuckDB C library for Cloudy Data's Android ABIs.
#
# This is a qualification build, not a claim that any extension beyond the
# statically linked set is available. The exact tag and commit are frozen here;
# do not replace them with a moving main branch in a reproducible build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DUCKDB_TAG="${DUCKDB_TAG:-v1.5.6}"
DUCKDB_COMMIT="${DUCKDB_COMMIT:-069cc9f9b5be802405797faecc284961b07c70ef}"
DUCKDB_DIR="${DUCKDB_DIR:-$ROOT/third_party/duckdb}"
ANDROID_NDK="${ANDROID_NDK:-/home/polar/Android/Sdk/ndk/28.2.13676358}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-35}"
JOBS="${JOBS:-$(nproc)}"
EXTENSIONS="${DUCKDB_EXTENSIONS:-parquet;json}"
ABIS="${ABIS:-arm64-v8a x86_64}"

if [[ ! -d "$DUCKDB_DIR/.git" ]]; then
  mkdir -p "$(dirname "$DUCKDB_DIR")"
  git clone --depth 1 --branch "$DUCKDB_TAG" https://github.com/duckdb/duckdb.git "$DUCKDB_DIR"
fi

git -C "$DUCKDB_DIR" fetch --depth 1 origin "refs/tags/$DUCKDB_TAG:refs/tags/$DUCKDB_TAG" || true
git -C "$DUCKDB_DIR" checkout --detach "$DUCKDB_COMMIT"
ACTUAL_COMMIT="$(git -C "$DUCKDB_DIR" rev-parse HEAD)"
if [[ "$ACTUAL_COMMIT" != "$DUCKDB_COMMIT" ]]; then
  echo "DuckDB commit mismatch: wanted $DUCKDB_COMMIT, got $ACTUAL_COMMIT" >&2
  exit 1
fi

TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"
STRIP="$TOOLCHAIN/bin/llvm-strip"
READELF="$TOOLCHAIN/bin/llvm-readelf"

for ABI in $ABIS; do
  case "$ABI" in
    arm64-v8a|x86_64) ;;
    *) echo "Unsupported ABI $ABI" >&2; exit 1 ;;
  esac
  BUILD_DIR="$DUCKDB_DIR/build/android_${ABI}"
  echo "=== Configuring DuckDB $DUCKDB_TAG for $ABI ==="
  mkdir -p "$BUILD_DIR"
  cmake -G Ninja -S "$DUCKDB_DIR" -B "$BUILD_DIR" \
    -DEXTENSION_STATIC_BUILD=1 \
    -DDUCKDB_EXTRA_LINK_FLAGS="-llog" \
    -DBUILD_EXTENSIONS="$EXTENSIONS" \
    -DENABLE_EXTENSION_AUTOLOADING=0 \
    -DENABLE_EXTENSION_AUTOINSTALL=0 \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DLOCAL_EXTENSION_REPO="" \
    -DOVERRIDE_GIT_DESCRIBE="" \
    -DDUCKDB_EXPLICIT_PLATFORM="android_${ABI}" \
    -DBUILD_UNITTESTS=0 \
    -DBUILD_SHELL=0 \
    -DANDROID_ABI="$ABI" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=Release
  echo "=== Building DuckDB for $ABI with $JOBS jobs ==="
  cmake --build "$BUILD_DIR" --config Release -j "$JOBS"

  SOURCE="$BUILD_DIR/src/libduckdb.so"
  if [[ ! -f "$SOURCE" ]]; then
    echo "Expected $SOURCE" >&2
    exit 1
  fi
  mkdir -p "$ROOT/app/src/main/jniLibs/$ABI"
  TARGET="$ROOT/app/src/main/jniLibs/$ABI/libduckdb.so"
  cp "$SOURCE" "$TARGET"
  "$STRIP" --strip-unneeded "$TARGET"

  # Both the 4 KiB target and the 16 KiB page-size requirement need every
  # loadable segment aligned to at least 16 KiB. This checks the artifact; it
  # does not prove runtime support on a 16 KiB device.
  if ! "$READELF" -l "$TARGET" | awk '/LOAD/ {print $NF}' | grep -q '^0x4000$'; then
    echo "16 KiB load alignment check failed for $TARGET" >&2
    "$READELF" -l "$TARGET" >&2
    exit 1
  fi
  echo "$ABI $(sha256sum "$TARGET" | cut -d' ' -f1) $(stat -c%s "$TARGET") bytes"
done

echo "DuckDB $DUCKDB_TAG ($DUCKDB_COMMIT) ready for: $ABIS"
