package io.github.baitian6641.cloudydata.engine

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

data class EngineColumn(val name: String, val type: String)

data class EngineResult(
    val columns: List<EngineColumn>,
    val rows: List<List<Any?>>,
    val truncated: Boolean,
)

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Narrow owner of one native DuckDB connection. All calls are blocking and must
 * run off the main thread. Query execution is serialized by the native adapter.
 */
class DuckDbEngine private constructor(private val handle: Long) : Closeable {
    private val closed = AtomicBoolean(false)

    fun execute(sql: String, maxRows: Int = DEFAULT_MAX_ROWS): EngineResult {
        check(!closed.get()) { "engine is closed" }
        val envelope = try {
            NativeDuckDb.nativeExecute(handle, sql, maxRows)
        } catch (error: IllegalStateException) {
            throw EngineException(error.message ?: "DuckDB query failed", error)
        }
        @Suppress("UNCHECKED_CAST")
        val names = (envelope[0] as Array<String?>).map { it ?: "" }
        @Suppress("UNCHECKED_CAST")
        val types = (envelope[1] as Array<String?>).map { it ?: "UNKNOWN" }
        val rawRows = envelope[2] as Array<Any?>
        val rows = rawRows.map { row -> (row as Array<Any?>).toList() }
        val truncated = envelope[3] as Boolean
        return EngineResult(
            columns = names.indices.map { EngineColumn(names[it], types[it]) },
            rows = rows,
            truncated = truncated,
        )
    }

    fun interrupt() {
        if (!closed.get()) NativeDuckDb.nativeInterrupt(handle)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            NativeDuckDb.nativeClose(handle)
        }
    }

    companion object {
        const val DEFAULT_MAX_ROWS = 10_000

        init {
            System.loadLibrary("duckdb")
            System.loadLibrary("cloudydb_jni")
        }

        fun open(
            databasePath: String,
            memoryLimitMb: Int = 256,
            threads: Int = 2,
        ): DuckDbEngine {
            val handle = try {
                NativeDuckDb.nativeOpen(databasePath, memoryLimitMb, threads)
            } catch (error: IllegalStateException) {
                throw EngineException(error.message ?: "Could not open DuckDB", error)
            }
            return DuckDbEngine(handle)
        }

        fun version(): String = NativeDuckDb.nativeVersion()
    }
}

private object NativeDuckDb {
    external fun nativeVersion(): String
    external fun nativeOpen(path: String, memoryLimitMb: Int, threads: Int): Long
    external fun nativeClose(handle: Long)
    external fun nativeInterrupt(handle: Long)
    external fun nativeExecute(handle: Long, sql: String, maxRows: Int): Array<Any?>
}
