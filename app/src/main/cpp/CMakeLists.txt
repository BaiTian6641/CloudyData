cmake_minimum_required(VERSION 3.22)
project(cloudydb_jni LANGUAGES CXX)

set(DUCKDB_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/duckdb/src/include")
set(DUCKDB_LIBRARY "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libduckdb.so")

if(NOT EXISTS "${DUCKDB_INCLUDE_DIR}/duckdb.h")
    message(FATAL_ERROR "DuckDB headers missing at ${DUCKDB_INCLUDE_DIR}. Run tools/build_duckdb_android.sh.")
endif()
if(NOT EXISTS "${DUCKDB_LIBRARY}")
    message(FATAL_ERROR "DuckDB ${ANDROID_ABI} library missing at ${DUCKDB_LIBRARY}. Run tools/build_duckdb_android.sh.")
endif()

add_library(duckdb SHARED IMPORTED)
set_target_properties(duckdb PROPERTIES IMPORTED_LOCATION "${DUCKDB_LIBRARY}")

add_library(cloudydb_jni SHARED cloudydb_jni.cpp)
target_include_directories(cloudydb_jni PRIVATE "${DUCKDB_INCLUDE_DIR}")
target_compile_options(cloudydb_jni PRIVATE -std=c++17 -O2 -fvisibility=hidden -Wall -Wextra)
target_link_libraries(cloudydb_jni duckdb android log)
