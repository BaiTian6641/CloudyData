package io.github.baitian6641.cloudydata.engine

import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef

data class WorksheetResult(
    val result: EngineResult,
    val rowField: FieldDef?,
    val measureField: FieldDef?,
    val seriesField: FieldDef?,
    val aggregation: Aggregation,
    val markType: MarkType,
    val sql: String,
    val sourceNote: String? = null,
) {
    /** A series column exists only in the multi-series line query shape. */
    val hasSeriesColumn: Boolean
        get() = rowField != null && measureField != null && seriesField != null

    /** Index of the numeric value column in [result]. */
    val valueColumnIndex: Int
        get() = when {
            rowField == null -> 0
            hasSeriesColumn -> 2
            else -> 1
        }
}

class WorksheetQuery(private val engine: DuckDbEngine) {

    fun query(snapshot: DataSnapshot, sheet: WorksheetDef, snapshotPath: String): WorksheetResult {
        val rowField = sheet.rowFieldId?.let { id -> snapshot.columns.firstOrNull { it.id == id } }
        val measureField = sheet.measureFieldId?.let { id -> snapshot.columns.firstOrNull { it.id == id } }
        val seriesField = sheet.seriesFieldId?.let { id -> snapshot.columns.firstOrNull { it.id == id } }
        val source = "read_parquet(${Sql.string(snapshotPath)})"
        val timeSeries = sheet.markType == MarkType.LINE || sheet.markType == MarkType.AREA
        val usedSeriesFieldId = if (timeSeries) seriesField?.id else null
        val activeFilter = sheet.filter?.takeIf { filter ->
            filter.marks.isNotEmpty() &&
                filter.rowFieldId == rowField?.id &&
                filter.seriesFieldId == usedSeriesFieldId
        }
        val filterWhere = if (activeFilter != null && rowField != null) {
            val seriesName = if (usedSeriesFieldId != null) seriesField?.name else null
            " WHERE " + MarkFilterSql.predicate(activeFilter, rowField.name, seriesName)
        } else {
            ""
        }
        val sql = when {
            timeSeries && rowField != null && measureField != null -> {
                val x = Sql.identifier(rowField.name)
                val y = Sql.identifier(measureField.name)
                val series = seriesField?.let { Sql.identifier(it.name) }
                val select = buildString {
                    append("CAST($x AS VARCHAR) AS \"row\"")
                    if (series != null) append(", CAST($series AS VARCHAR) AS \"series\"")
                    append(", ").append(aggregateSql(sheet.aggregation, y)).append(" AS \"value\"")
                }
                val group = if (series != null) "GROUP BY $x, $series" else "GROUP BY $x"
                val order = if (series != null) "ORDER BY $x ASC NULLS LAST, $series ASC NULLS LAST" else "ORDER BY $x ASC NULLS LAST"
                "SELECT $select FROM $source$filterWhere $group $order LIMIT 2001"
            }
            rowField != null && measureField != null -> {
                val dimension = Sql.identifier(rowField.name)
                val measure = Sql.identifier(measureField.name)
                val aggregate = aggregateSql(sheet.aggregation, measure)
                "SELECT CAST($dimension AS VARCHAR) AS \"row\", $aggregate AS \"value\" " +
                    "FROM $source$filterWhere GROUP BY 1 ORDER BY 2 ${if (sheet.sortDescending) "DESC" else "ASC"} NULLS LAST LIMIT 1000"
            }
            rowField != null -> {
                val dimension = Sql.identifier(rowField.name)
                "SELECT CAST($dimension AS VARCHAR) AS \"row\", COUNT(*) AS \"value\" " +
                    "FROM $source$filterWhere GROUP BY 1 ORDER BY 2 ${if (sheet.sortDescending) "DESC" else "ASC"} LIMIT 1000"
            }
            measureField != null -> {
                val measure = Sql.identifier(measureField.name)
                "SELECT ${aggregateSql(sheet.aggregation, measure)} AS \"value\" FROM $source"
            }
            else -> "SELECT * FROM $source LIMIT 100"
        }
        val result = engine.execute(sql, maxRows = if (timeSeries) 2001 else 1000)
        return WorksheetResult(
            result = result,
            rowField = rowField,
            measureField = measureField,
            seriesField = seriesField,
            aggregation = sheet.aggregation,
            markType = sheet.markType,
            sql = sql,
            sourceNote = "Source: ${snapshot.displayName} · ${snapshot.rowCount} rows · " +
                "snapshot ${snapshot.id.take(8)}",
        )
    }

    private fun aggregateSql(aggregation: Aggregation, quotedIdentifier: String): String = when (aggregation) {
        Aggregation.SUM -> "SUM($quotedIdentifier)"
        Aggregation.AVG -> "AVG($quotedIdentifier)"
        Aggregation.MIN -> "MIN($quotedIdentifier)"
        Aggregation.MAX -> "MAX($quotedIdentifier)"
        Aggregation.COUNT -> "COUNT($quotedIdentifier)"
        Aggregation.COUNT_DISTINCT -> "COUNT(DISTINCT $quotedIdentifier)"
        Aggregation.NONE -> quotedIdentifier
    }
}
