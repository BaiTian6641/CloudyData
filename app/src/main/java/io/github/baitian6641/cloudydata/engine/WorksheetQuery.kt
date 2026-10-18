package io.github.baitian6641.cloudydata.engine

import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.WorksheetDef

data class WorksheetResult(
    val result: EngineResult,
    val rowField: FieldDef?,
    val measureField: FieldDef?,
    val aggregation: Aggregation,
    val sql: String,
)

class WorksheetQuery(private val engine: DuckDbEngine) {

    fun query(snapshot: DataSnapshot, sheet: WorksheetDef, snapshotPath: String): WorksheetResult {
        val rowField = sheet.rowFieldId?.let { id -> snapshot.columns.firstOrNull { it.id == id } }
        val measureField = sheet.measureFieldId?.let { id -> snapshot.columns.firstOrNull { it.id == id } }
        val source = "read_parquet(${Sql.string(snapshotPath)})"
        val sql = when {
            rowField != null && measureField != null -> {
                val dimension = Sql.identifier(rowField.name)
                val measure = Sql.identifier(measureField.name)
                val aggregate = aggregateSql(sheet.aggregation, measure)
                "SELECT CAST($dimension AS VARCHAR) AS \"row\", $aggregate AS \"value\" " +
                    "FROM $source GROUP BY 1 ORDER BY 2 ${if (sheet.sortDescending) "DESC" else "ASC"} NULLS LAST LIMIT 1000"
            }
            rowField != null -> {
                val dimension = Sql.identifier(rowField.name)
                "SELECT CAST($dimension AS VARCHAR) AS \"row\", COUNT(*) AS \"value\" " +
                    "FROM $source GROUP BY 1 ORDER BY 2 ${if (sheet.sortDescending) "DESC" else "ASC"} LIMIT 1000"
            }
            measureField != null -> {
                val measure = Sql.identifier(measureField.name)
                "SELECT ${aggregateSql(sheet.aggregation, measure)} AS \"value\" FROM $source"
            }
            else -> "SELECT * FROM $source LIMIT 100"
        }
        val result = engine.execute(sql, maxRows = 1000)
        return WorksheetResult(result, rowField, measureField, sheet.aggregation, sql)
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
