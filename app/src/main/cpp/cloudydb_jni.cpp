// Cloudy Data native DuckDB adapter.
// The adapter is deliberately narrow: open/close, bounded query execution,
// interrupt and library version. No arbitrary file or network authority is
// exposed to renderer content.

#include <jni.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "duckdb.h"

namespace {

struct ConnectionHandle {
    duckdb_database database = nullptr;
    duckdb_connection connection = nullptr;
    std::mutex mutex;
    std::atomic<bool> closed{false};
};

std::string utf8_to_utf16_units(const char *input, std::vector<jchar> &out) {
    out.clear();
    if (input == nullptr) return {};
    const auto *bytes = reinterpret_cast<const unsigned char *>(input);
    size_t index = 0;
    while (bytes[index] != 0) {
        uint32_t code_point = 0;
        unsigned char lead = bytes[index];
        size_t additional = 0;
        if (lead < 0x80) {
            code_point = lead;
            additional = 0;
        } else if ((lead & 0xE0) == 0xC0) {
            code_point = lead & 0x1F;
            additional = 1;
        } else if ((lead & 0xF0) == 0xE0) {
            code_point = lead & 0x0F;
            additional = 2;
        } else if ((lead & 0xF8) == 0xF0) {
            code_point = lead & 0x07;
            additional = 3;
        } else {
            code_point = 0xFFFD;
            additional = 0;
        }
        index++;
        bool valid = true;
        for (size_t offset = 0; offset < additional; offset++) {
            unsigned char continuation = bytes[index + offset];
            if ((continuation & 0xC0) != 0x80) {
                valid = false;
                break;
            }
            code_point = (code_point << 6) | (continuation & 0x3F);
        }
        if (!valid) {
            code_point = 0xFFFD;
            index = index;
        } else {
            index += additional;
        }
        if (code_point > 0x10FFFF || (code_point >= 0xD800 && code_point <= 0xDFFF)) {
            code_point = 0xFFFD;
        }
        if (code_point <= 0xFFFF) {
            out.push_back(static_cast<jchar>(code_point));
        } else {
            code_point -= 0x10000;
            out.push_back(static_cast<jchar>(0xD800 + (code_point >> 10)));
            out.push_back(static_cast<jchar>(0xDC00 + (code_point & 0x3FF)));
        }
    }
    return {};
}

jstring to_jstring(JNIEnv *env, const char *value) {
    if (value == nullptr) return nullptr;
    std::vector<jchar> units;
    utf8_to_utf16_units(value, units);
    return env->NewString(reinterpret_cast<const jchar *>(units.data()), static_cast<jsize>(units.size()));
}

const char *type_name(duckdb_type type) {
    switch (type) {
        case DUCKDB_TYPE_INVALID: return "INVALID";
        case DUCKDB_TYPE_BOOLEAN: return "BOOLEAN";
        case DUCKDB_TYPE_TINYINT: return "TINYINT";
        case DUCKDB_TYPE_SMALLINT: return "SMALLINT";
        case DUCKDB_TYPE_INTEGER: return "INTEGER";
        case DUCKDB_TYPE_BIGINT: return "BIGINT";
        case DUCKDB_TYPE_UTINYINT: return "UTINYINT";
        case DUCKDB_TYPE_USMALLINT: return "USMALLINT";
        case DUCKDB_TYPE_UINTEGER: return "UINTEGER";
        case DUCKDB_TYPE_UBIGINT: return "UBIGINT";
        case DUCKDB_TYPE_FLOAT: return "FLOAT";
        case DUCKDB_TYPE_DOUBLE: return "DOUBLE";
        case DUCKDB_TYPE_TIMESTAMP: return "TIMESTAMP";
        case DUCKDB_TYPE_DATE: return "DATE";
        case DUCKDB_TYPE_TIME: return "TIME";
        case DUCKDB_TYPE_INTERVAL: return "INTERVAL";
        case DUCKDB_TYPE_HUGEINT: return "HUGEINT";
        case DUCKDB_TYPE_UHUGEINT: return "UHUGEINT";
        case DUCKDB_TYPE_VARCHAR: return "VARCHAR";
        case DUCKDB_TYPE_BLOB: return "BLOB";
        case DUCKDB_TYPE_DECIMAL: return "DECIMAL";
        case DUCKDB_TYPE_TIMESTAMP_S: return "TIMESTAMP_S";
        case DUCKDB_TYPE_TIMESTAMP_MS: return "TIMESTAMP_MS";
        case DUCKDB_TYPE_TIMESTAMP_NS: return "TIMESTAMP_NS";
        case DUCKDB_TYPE_ENUM: return "ENUM";
        case DUCKDB_TYPE_LIST: return "LIST";
        case DUCKDB_TYPE_STRUCT: return "STRUCT";
        case DUCKDB_TYPE_MAP: return "MAP";
        case DUCKDB_TYPE_ARRAY: return "ARRAY";
        case DUCKDB_TYPE_UUID: return "UUID";
        case DUCKDB_TYPE_TIMESTAMP_TZ: return "TIMESTAMP_TZ";
        case DUCKDB_TYPE_TIME_TZ: return "TIME_TZ";
        case DUCKDB_TYPE_BIT: return "BIT";
        case DUCKDB_TYPE_ANY: return "ANY";
        case DUCKDB_TYPE_VARIANT: return "VARIANT";
        case DUCKDB_TYPE_BIGNUM: return "BIGNUM";
        case DUCKDB_TYPE_UNION: return "UNION";
        case DUCKDB_TYPE_GEOMETRY: return "GEOMETRY";
        case DUCKDB_TYPE_SQLNULL: return "SQLNULL";
        default: return "UNKNOWN";
    }
}

std::string pad_decimal(const char *text, int scale) {
    std::string value = text == nullptr ? "0" : text;
    const auto dot = value.find('.');
    if (scale <= 0) {
        if (dot != std::string::npos) value = value.substr(0, dot);
        return value;
    }
    if (dot == std::string::npos) {
        value += ".";
        value.append(static_cast<size_t>(scale), '0');
        return value;
    }
    const auto fraction = value.size() - dot - 1;
    if (static_cast<int>(fraction) < scale) {
        value.append(static_cast<size_t>(scale) - fraction, '0');
    }
    return value;
}

jobject cell_to_java(JNIEnv *env, duckdb_result &result, idx_t column, idx_t row, duckdb_type type, int decimal_scale) {
    if (duckdb_value_is_null(&result, column, row)) return nullptr;
    switch (type) {
        case DUCKDB_TYPE_BOOLEAN: {
            jclass clazz = env->FindClass("java/lang/Boolean");
            jmethodID method = env->GetStaticMethodID(clazz, "valueOf", "(Z)Ljava/lang/Boolean;");
            return env->CallStaticObjectMethod(clazz, method, static_cast<jboolean>(duckdb_value_boolean(&result, column, row)));
        }
        case DUCKDB_TYPE_TINYINT:
        case DUCKDB_TYPE_SMALLINT:
        case DUCKDB_TYPE_INTEGER:
        case DUCKDB_TYPE_BIGINT:
        case DUCKDB_TYPE_UTINYINT:
        case DUCKDB_TYPE_USMALLINT:
        case DUCKDB_TYPE_UINTEGER: {
            jclass clazz = env->FindClass("java/lang/Long");
            jmethodID method = env->GetStaticMethodID(clazz, "valueOf", "(J)Ljava/lang/Long;");
            return env->CallStaticObjectMethod(clazz, method, static_cast<jlong>(duckdb_value_int64(&result, column, row)));
        }
        case DUCKDB_TYPE_DECIMAL: {
            char *text = duckdb_value_varchar(&result, column, row);
            const std::string padded = pad_decimal(text, decimal_scale);
            duckdb_free(text);
            return to_jstring(env, padded.c_str());
        }
        case DUCKDB_TYPE_UBIGINT:
        case DUCKDB_TYPE_HUGEINT:
        case DUCKDB_TYPE_UHUGEINT:
        case DUCKDB_TYPE_UUID: {
            char *text = duckdb_value_varchar(&result, column, row);
            jstring value = to_jstring(env, text);
            duckdb_free(text);
            return value;
        }
        case DUCKDB_TYPE_FLOAT:
        case DUCKDB_TYPE_DOUBLE: {
            jclass clazz = env->FindClass("java/lang/Double");
            jmethodID method = env->GetStaticMethodID(clazz, "valueOf", "(D)Ljava/lang/Double;");
            return env->CallStaticObjectMethod(clazz, method, static_cast<jdouble>(duckdb_value_double(&result, column, row)));
        }
        case DUCKDB_TYPE_BLOB: {
            duckdb_blob blob = duckdb_value_blob(&result, column, row);
            jbyteArray array = env->NewByteArray(static_cast<jsize>(blob.size));
            if (array != nullptr && blob.data != nullptr) {
                env->SetByteArrayRegion(array, 0, static_cast<jsize>(blob.size), reinterpret_cast<const jbyte *>(blob.data));
            }
            duckdb_free(blob.data);
            return array;
        }
        default: {
            char *text = duckdb_value_varchar(&result, column, row);
            jstring value = to_jstring(env, text);
            duckdb_free(text);
            return value;
        }
    }
}

void throw_illegal_state(JNIEnv *env, const char *message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(clazz, message);
}

ConnectionHandle *require_handle(JNIEnv *env, jlong handle) {
    auto *connection = reinterpret_cast<ConnectionHandle *>(handle);
    if (connection == nullptr || connection->closed.load()) {
        throw_illegal_state(env, "Native DuckDB connection is closed");
        return nullptr;
    }
    return connection;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_io_github_baitian6641_cloudydata_engine_NativeDuckDb_nativeVersion(JNIEnv *env, jobject /*thiz*/) {
    return to_jstring(env, duckdb_library_version());
}

JNIEXPORT jlong JNICALL
Java_io_github_baitian6641_cloudydata_engine_NativeDuckDb_nativeOpen(
    JNIEnv *env, jobject /*thiz*/, jstring jpath, jint memoryLimitMb, jint threads) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    auto *handle = new ConnectionHandle();
    char *error = nullptr;
    duckdb_config config = nullptr;
    if (duckdb_create_config(&config) == DuckDBError) {
        env->ReleaseStringUTFChars(jpath, path);
        delete handle;
        throw_illegal_state(env, "Could not create DuckDB configuration");
        return 0;
    }
    const std::string memory_limit = "memory_limit=" + std::to_string(memoryLimitMb) + "MB";
    duckdb_set_config(config, "memory_limit", memory_limit.c_str());
    duckdb_set_config(config, "threads", std::to_string(threads).c_str());
    duckdb_set_config(config, "enable_external_access", "true");
    duckdb_set_config(config, "autoinstall_known_extensions", "false");
    duckdb_set_config(config, "autoload_known_extensions", "false");
    if (duckdb_open_ext(path, &handle->database, config, &error) == DuckDBError) {
        const std::string message = error == nullptr ? "Could not open DuckDB database" : error;
        duckdb_free(error);
        duckdb_destroy_config(&config);
        env->ReleaseStringUTFChars(jpath, path);
        delete handle;
        throw_illegal_state(env, message.c_str());
        return 0;
    }
    duckdb_destroy_config(&config);
    if (duckdb_connect(handle->database, &handle->connection) == DuckDBError) {
        duckdb_close(&handle->database);
        env->ReleaseStringUTFChars(jpath, path);
        delete handle;
        throw_illegal_state(env, "Could not create DuckDB connection");
        return 0;
    }
    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_io_github_baitian6641_cloudydata_engine_NativeDuckDb_nativeClose(JNIEnv *env, jobject /*thiz*/, jlong handle) {
    auto *connection = reinterpret_cast<ConnectionHandle *>(handle);
    if (connection == nullptr) return;
    {
        std::lock_guard<std::mutex> guard(connection->mutex);
        if (connection->closed.exchange(true)) return;
        if (connection->connection != nullptr) {
            duckdb_disconnect(&connection->connection);
            connection->connection = nullptr;
        }
        if (connection->database != nullptr) {
            duckdb_close(&connection->database);
            connection->database = nullptr;
        }
    }
    delete connection;
}

JNIEXPORT void JNICALL
Java_io_github_baitian6641_cloudydata_engine_NativeDuckDb_nativeInterrupt(JNIEnv *env, jobject /*thiz*/, jlong handle) {
    auto *connection = reinterpret_cast<ConnectionHandle *>(handle);
    if (connection == nullptr || connection->closed.load() || connection->connection == nullptr) return;
    duckdb_interrupt(connection->connection);
}

JNIEXPORT jobjectArray JNICALL
Java_io_github_baitian6641_cloudydata_engine_NativeDuckDb_nativeExecute(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jstring jsql, jint maxRows) {
    ConnectionHandle *connection = require_handle(env, handle);
    if (connection == nullptr) return nullptr;
    const char *sql = env->GetStringUTFChars(jsql, nullptr);
    duckdb_result result;
    std::memset(&result, 0, sizeof(result));
    std::lock_guard<std::mutex> guard(connection->mutex);
    if (duckdb_query(connection->connection, sql, &result) == DuckDBError) {
        const char *error = duckdb_result_error(&result);
        std::string message = error == nullptr ? "DuckDB query failed" : error;
        duckdb_destroy_result(&result);
        env->ReleaseStringUTFChars(jsql, sql);
        throw_illegal_state(env, message.c_str());
        return nullptr;
    }
    env->ReleaseStringUTFChars(jsql, sql);
    if (duckdb_result_error(&result) != nullptr) {
        const char *error = duckdb_result_error(&result);
        std::string message = error == nullptr ? "DuckDB query failed" : error;
        duckdb_destroy_result(&result);
        throw_illegal_state(env, message.c_str());
        return nullptr;
    }

    const idx_t column_count = duckdb_column_count(&result);
    const idx_t total_rows = duckdb_row_count(&result);
    const idx_t row_limit = total_rows > static_cast<idx_t>(maxRows) ? static_cast<idx_t>(maxRows) : total_rows;
    const bool truncated = row_limit < total_rows;

    jclass string_class = env->FindClass("java/lang/String");
    jclass object_class = env->FindClass("java/lang/Object");
    jobjectArray columns = env->NewObjectArray(static_cast<jsize>(column_count), string_class, nullptr);
    jobjectArray types = env->NewObjectArray(static_cast<jsize>(column_count), string_class, nullptr);
    std::vector<int> decimal_scales(static_cast<size_t>(column_count), 0);
    for (idx_t column = 0; column < column_count; column++) {
        env->SetObjectArrayElement(columns, static_cast<jsize>(column), to_jstring(env, duckdb_column_name(&result, column)));
        const duckdb_type base_type = duckdb_column_type(&result, column);
        duckdb_logical_type logical = duckdb_column_logical_type(&result, column);
        std::string type_text = type_name(base_type);
        if (logical != nullptr && base_type == DUCKDB_TYPE_DECIMAL) {
            const int width = static_cast<int>(duckdb_decimal_width(logical));
            const int scale = static_cast<int>(duckdb_decimal_scale(logical));
            decimal_scales[static_cast<size_t>(column)] = scale;
            type_text = "DECIMAL(" + std::to_string(width) + "," + std::to_string(scale) + ")";
        }
        if (logical != nullptr) duckdb_destroy_logical_type(&logical);
        env->SetObjectArrayElement(types, static_cast<jsize>(column), to_jstring(env, type_text.c_str()));
    }

    jobjectArray rows = env->NewObjectArray(static_cast<jsize>(row_limit), object_class, nullptr);
    for (idx_t row = 0; row < row_limit; row++) {
        jobjectArray values = env->NewObjectArray(static_cast<jsize>(column_count), object_class, nullptr);
        for (idx_t column = 0; column < column_count; column++) {
            const duckdb_type type = duckdb_column_type(&result, column);
            jobject value = cell_to_java(env, result, column, row, type, decimal_scales[static_cast<size_t>(column)]);
            env->SetObjectArrayElement(values, static_cast<jsize>(column), value);
            if (value != nullptr) env->DeleteLocalRef(value);
        }
        env->SetObjectArrayElement(rows, static_cast<jsize>(row), values);
        env->DeleteLocalRef(values);
    }

    jclass boolean_class = env->FindClass("java/lang/Boolean");
    jmethodID boolean_value_of = env->GetStaticMethodID(boolean_class, "valueOf", "(Z)Ljava/lang/Boolean;");
    jobject truncated_value = env->CallStaticObjectMethod(boolean_class, boolean_value_of, static_cast<jboolean>(truncated));

    jobjectArray envelope = env->NewObjectArray(4, object_class, nullptr);
    env->SetObjectArrayElement(envelope, 0, columns);
    env->SetObjectArrayElement(envelope, 1, types);
    env->SetObjectArrayElement(envelope, 2, rows);
    env->SetObjectArrayElement(envelope, 3, truncated_value);
    duckdb_destroy_result(&result);
    return envelope;
}

}  // extern "C"
