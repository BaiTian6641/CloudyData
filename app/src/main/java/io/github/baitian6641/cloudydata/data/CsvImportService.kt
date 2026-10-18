package io.github.baitian6641.cloudydata.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.EngineException
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class DecimalCast(val precision: Int, val scale: Int)

data class PendingCsvImport(
    val workbookId: String,
    val operationId: String,
    val displayName: String,
    val stagingDirectory: File,
    val sourceFile: File,
    val sourceBytes: Long,
    val columns: List<FieldDef>,
    val decimalCasts: Map<String, DecimalCast>,
    val previewColumns: List<String>,
    val previewRows: List<List<Any?>>,
    val warnings: List<String>,
)

class CsvImportService(
    private val context: Context,
    private val store: WorkbookStore,
) {
    suspend fun stageAndInspect(workbook: WorkbookDocument, uri: Uri): PendingCsvImport {
        val operationId = UUID.randomUUID().toString()
        val staging = File(store.workbookDir(workbook.id), "staging").apply { mkdirs() }
        val operationDir = File(staging, operationId).apply { mkdirs() }
        val sourceFile = File(operationDir, "source.csv")
        val displayName = queryDisplayName(uri) ?: "Imported CSV"
        try {
            val bytes = copyBounded(uri, sourceFile)
            if (bytes == 0L) error("The selected file is empty.")
            val quotedPath = Sql.string(sourceFile.absolutePath)
            DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
                val describe = engine.execute(
                    "DESCRIBE SELECT * FROM read_csv_auto($quotedPath, header=true, sample_size=-1)",
                    maxRows = 1_000,
                )
                val nameIndex = describe.columns.indexOfFirst { it.name == "column_name" }
                val typeIndex = describe.columns.indexOfFirst { it.name == "column_type" }
                require(nameIndex >= 0 && typeIndex >= 0) { "DuckDB did not return a CSV schema." }
                val inferred = describe.rows.mapNotNull { row ->
                    val name = row.getOrNull(nameIndex) as? String ?: return@mapNotNull null
                    val type = row.getOrNull(typeIndex) as? String ?: "VARCHAR"
                    FieldDef(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        physicalType = type,
                        logicalType = logicalType(type).first,
                        role = logicalType(type).second,
                    )
                }
                require(inferred.isNotEmpty()) { "The CSV file has no readable columns." }

                val decimalResult = inferDecimalCasts(engine, quotedPath, inferred)
                val casts = decimalResult.casts
                val columns = inferred.map { field ->
                    val cast = casts[field.name] ?: return@map field
                    val physical = "DECIMAL(${cast.precision},${cast.scale})"
                    field.copy(
                        physicalType = physical,
                        logicalType = LogicalType.DECIMAL,
                        role = FieldRole.MEASURE,
                    )
                }
                val previewProjection = projection(columns, casts)
                val preview = engine.execute(
                    "SELECT $previewProjection FROM read_csv_auto($quotedPath, header=true, sample_size=-1) LIMIT 50",
                    maxRows = 50,
                )
                val warnings = buildList {
                    if (preview.truncated) add("Preview is limited to 50 rows.")
                    if (columns.any { it.logicalType == LogicalType.OTHER }) {
                        add("Some nested or special columns are shown as text and cannot be charted yet.")
                    }
                    if (casts.isNotEmpty()) {
                        add("${casts.size} decimal column(s) are imported as exact DECIMAL values, not binary floating point.")
                    }
                    if (decimalResult.remainingDouble.isNotEmpty()) {
                        add(
                            "These columns remain DOUBLE because their text is not plain decimal notation: " +
                                decimalResult.remainingDouble.joinToString(", "),
                        )
                    }
                }
                return PendingCsvImport(
                    workbookId = workbook.id,
                    operationId = operationId,
                    displayName = displayName,
                    stagingDirectory = operationDir,
                    sourceFile = sourceFile,
                    sourceBytes = bytes,
                    columns = columns,
                    decimalCasts = casts,
                    previewColumns = preview.columns.map { it.name },
                    previewRows = preview.rows,
                    warnings = warnings,
                )
            }
        } catch (error: Throwable) {
            operationDir.deleteRecursively()
            throw if (error is EngineException) error else IllegalStateException(error.message ?: "Could not inspect the CSV file.", error)
        }
    }

    suspend fun apply(workbook: WorkbookDocument, pending: PendingCsvImport): WorkbookDocument {
        val operationDir = pending.stagingDirectory
        val stagedParquet = File(operationDir, "snapshot.parquet")
        val quotedSource = Sql.string(pending.sourceFile.absolutePath)
        val quotedTarget = Sql.string(stagedParquet.absolutePath)
        try {
            DuckDbEngine.open(":memory:", memoryLimitMb = 512, threads = 2).use { engine ->
                val projected = projection(pending.columns, pending.decimalCasts)
                engine.execute(
                    "COPY (SELECT $projected FROM read_csv_auto($quotedSource, header=true, sample_size=-1)) " +
                        "TO $quotedTarget (FORMAT PARQUET)",
                    maxRows = 1,
                )
                val countResult = engine.execute("SELECT COUNT(*) AS n FROM read_parquet($quotedTarget)", maxRows = 1)
                val rowCount = (countResult.rows.firstOrNull()?.firstOrNull() as? Long)
                    ?: error("Could not verify the imported row count.")
                if (!stagedParquet.exists() || stagedParquet.length() == 0L) {
                    error("The analytical snapshot was not written.")
                }
                FileOutputStream(stagedParquet, true).use { it.fd.sync() }

                val finalDescribe = engine.execute("DESCRIBE SELECT * FROM read_parquet($quotedTarget)", maxRows = 1_000)
                val finalNameIndex = finalDescribe.columns.indexOfFirst { it.name == "column_name" }
                val finalTypeIndex = finalDescribe.columns.indexOfFirst { it.name == "column_type" }
                require(finalNameIndex >= 0 && finalTypeIndex >= 0) { "Could not verify the snapshot schema." }
                val byName = pending.columns.associateBy { it.name }
                val finalColumns = finalDescribe.rows.mapNotNull { row ->
                    val name = row.getOrNull(finalNameIndex) as? String ?: return@mapNotNull null
                    val type = row.getOrNull(finalTypeIndex) as? String ?: "VARCHAR"
                    val existing = byName[name]
                    val (logical, role) = logicalType(type)
                    FieldDef(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        physicalType = type,
                        logicalType = logical,
                        role = role,
                    )
                }
                require(finalColumns.size == pending.columns.size) {
                    "The snapshot schema does not match the inspected CSV schema."
                }

                val dataDir = File(store.workbookDir(workbook.id), "data").apply { mkdirs() }
                val sourceDir = File(store.workbookDir(workbook.id), "source").apply { mkdirs() }
                val finalParquet = File(dataDir, "snapshot.parquet")
                val finalOriginal = File(sourceDir, "original.csv")
                Files.move(
                    stagedParquet.toPath(),
                    finalParquet.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
                Files.move(
                    pending.sourceFile.toPath(),
                    finalOriginal.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
                val snapshot = DataSnapshot(
                    id = UUID.randomUUID().toString(),
                    displayName = pending.displayName,
                    sourceKind = "csv",
                    snapshotFile = "data/snapshot.parquet",
                    originalFile = "source/original.csv",
                    importedAtEpochMs = System.currentTimeMillis(),
                    rowCount = rowCount,
                    columns = finalColumns,
                )
                val saved = store.save(workbook.copy(snapshot = snapshot))
                operationDir.deleteRecursively()
                return saved
            }
        } catch (error: Throwable) {
            throw if (error is EngineException) error else IllegalStateException(error.message ?: "Could not complete the import.", error)
        }
    }

    fun discard(pending: PendingCsvImport) {
        pending.stagingDirectory.deleteRecursively()
    }

    private fun projection(columns: List<FieldDef>, casts: Map<String, DecimalCast>): String {
        return columns.joinToString(", ") { field ->
            val quoted = Sql.identifier(field.name)
            val cast = casts[field.name]
            if (cast == null) {
                quoted
            } else {
                "CAST($quoted AS DECIMAL(${cast.precision},${cast.scale})) AS $quoted"
            }
        }
    }

    private fun inferDecimalCasts(
        engine: DuckDbEngine,
        quotedPath: String,
        columns: List<FieldDef>,
    ): DecimalInference {
        val doubles = columns.filter { it.physicalType == "DOUBLE" }
        if (doubles.isEmpty()) return DecimalInference(emptyMap(), emptyList())
        val pattern = Sql.string("""^[+-]?(\d+(\.\d*)?|\.\d+)$""")
        val expressions = doubles.flatMap { field ->
            val quoted = Sql.identifier(field.name)
            listOf(
                "count(*) FILTER (WHERE $quoted IS NOT NULL AND trim($quoted) <> '' " +
                    "AND NOT regexp_full_match(trim($quoted), $pattern))",
                "coalesce(max(length(regexp_extract(trim($quoted), '\\.(\\d*)$', 1))), 0)",
                "coalesce(max(length(regexp_extract(trim($quoted), '^[+-]?(\\d*)', 1))), 0)",
            )
        }
        val stats = engine.execute(
            "SELECT ${expressions.joinToString(", ")} FROM read_csv_auto(" +
                "$quotedPath, header=true, sample_size=-1, all_varchar=true)",
            maxRows = 1,
        )
        val row = stats.rows.firstOrNull() ?: return DecimalInference(emptyMap(), doubles.map { it.name })
        val casts = mutableMapOf<String, DecimalCast>()
        val remaining = mutableListOf<String>()
        doubles.forEachIndexed { index, field ->
            val nonDecimal = row.getOrNull(index * 3) as? Long ?: 0L
            val maxScale = row.getOrNull(index * 3 + 1) as? Long ?: 0L
            val maxIntegerDigits = row.getOrNull(index * 3 + 2) as? Long ?: 0L
            val precision = maxOf(18L, maxIntegerDigits + maxScale)
            if (nonDecimal == 0L && maxScale in 1..38L && precision <= 38L) {
                casts[field.name] = DecimalCast(precision.toInt(), maxScale.toInt())
            } else {
                remaining += field.name
            }
        }
        return DecimalInference(casts, remaining)
    }

    private data class DecimalInference(
        val casts: Map<String, DecimalCast>,
        val remainingDouble: List<String>,
    )

    private fun copyBounded(uri: Uri, target: File): Long {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: error("The selected file could not be opened.")
        input.use { stream ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SOURCE_BYTES) {
                        error("The selected file exceeds the 512 MiB import limit.")
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
                output.fd.sync()
                return total
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val MAX_SOURCE_BYTES = 512L * 1024L * 1024L

        fun logicalType(duckType: String): Pair<LogicalType, FieldRole> {
            if (duckType.startsWith("DECIMAL")) return LogicalType.DECIMAL to FieldRole.MEASURE
            return when (duckType) {
                "BOOLEAN" -> LogicalType.BOOLEAN to FieldRole.DIMENSION
                "TINYINT", "SMALLINT", "INTEGER", "BIGINT", "UTINYINT", "USMALLINT", "UINTEGER" ->
                    LogicalType.INTEGER to FieldRole.MEASURE
                "HUGEINT", "UHUGEINT", "UBIGINT" -> LogicalType.DECIMAL to FieldRole.MEASURE
                "FLOAT", "DOUBLE" -> LogicalType.DOUBLE to FieldRole.MEASURE
                "DATE" -> LogicalType.DATE to FieldRole.DIMENSION
                "TIMESTAMP", "TIMESTAMP_S", "TIMESTAMP_MS", "TIMESTAMP_NS", "TIMESTAMP_TZ" ->
                    LogicalType.TIMESTAMP to FieldRole.DIMENSION
                "VARCHAR", "ENUM", "UUID", "JSON" -> LogicalType.STRING to FieldRole.DIMENSION
                else -> LogicalType.OTHER to FieldRole.DIMENSION
            }
        }
    }
}
