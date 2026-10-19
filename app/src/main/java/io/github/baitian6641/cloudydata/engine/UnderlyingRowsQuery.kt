package io.github.baitian6641.cloudydata.engine

import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef

data class UnderlyingRowsResult(
    val result: EngineResult,
    val sql: String,
    val truncated: Boolean,
    val selectedMarkCount: Int,
    val worksheetFilterApplied: Boolean,
    val sourceName: String,
)

/**
 * Resolves selected aggregate marks back to bounded source rows.
 * The selection and worksheet filters constrain a read-only Parquet query; the
 * snapshot and accepted worksheet result are never modified.
 */
class UnderlyingRowsQuery(private val engine: DuckDbEngine) {
    fun query(
        snapshot: DataSnapshot,
        sheet: WorksheetDef,
        worksheetResult: WorksheetResult,
        selectedRows: Set<Int>,
        snapshotPath: String,
    ): UnderlyingRowsResult? {
        val rowField = worksheetResult.rowField ?: return null
        val selection = MarkFilters.fromResult(
            sheet = sheet,
            result = worksheetResult,
            selectedRows = selectedRows,
            mode = FilterMode.KEEP,
        ) ?: return null
        val source = "read_parquet(${Sql.string(snapshotPath)})"
        val clauses = mutableListOf(
            MarkFilterSql.predicate(
                selection,
                rowField.name,
                worksheetResult.seriesField?.name.takeIf { selection.seriesFieldId != null },
            ),
        )
        val seriesMarks = sheet.markType == MarkType.LINE ||
            sheet.markType == MarkType.AREA ||
            sheet.markType == MarkType.BAR
        val usedSeriesId = worksheetResult.seriesField?.id.takeIf {
            seriesMarks && worksheetResult.hasSeriesColumn
        }
        val worksheetFilter = sheet.filter?.takeIf { filter ->
            filter.marks.isNotEmpty() &&
                filter.rowFieldId == rowField.id &&
                filter.seriesFieldId == usedSeriesId
        }
        if (worksheetFilter != null) {
            clauses += MarkFilterSql.predicate(
                worksheetFilter,
                rowField.name,
                worksheetResult.seriesField?.name.takeIf { usedSeriesId != null },
            )
        }
        val sql = "SELECT * FROM $source WHERE ${clauses.joinToString(" AND ")} LIMIT ${MAX_ROWS + 1}"
        val raw = engine.execute(sql, maxRows = MAX_ROWS + 1)
        val truncated = raw.rows.size > MAX_ROWS
        val visible = if (truncated) raw.copy(rows = raw.rows.take(MAX_ROWS)) else raw
        return UnderlyingRowsResult(
            result = visible,
            sql = sql,
            truncated = truncated,
            selectedMarkCount = selection.marks.size,
            worksheetFilterApplied = worksheetFilter != null,
            sourceName = snapshot.displayName,
        )
    }

    companion object {
        const val MAX_ROWS = 200
    }
}
