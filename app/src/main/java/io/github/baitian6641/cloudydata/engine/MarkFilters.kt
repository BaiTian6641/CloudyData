package io.github.baitian6641.cloudydata.engine

import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.WorksheetDef

/** Builds exact mark identities from the accepted result, never from source offsets. */
object MarkFilters {
    fun fromResult(
        sheet: WorksheetDef,
        result: WorksheetResult,
        selectedRows: Set<Int>,
        mode: FilterMode,
    ): MarkFilter? {
        val rowFieldId = sheet.rowFieldId ?: return null
        if (selectedRows.isEmpty()) return null
        val rows = result.result.rows
        val seriesFieldId = if (result.hasSeriesColumn) sheet.seriesFieldId else null
        val marks = selectedRows
            .asSequence()
            .sorted()
            .mapNotNull { index -> rows.getOrNull(index) }
            .map { row ->
                FilterMark(
                    row = filterValue(row.getOrNull(0)),
                    series = if (seriesFieldId != null) filterValue(row.getOrNull(1)) else null,
                )
            }
            .distinct()
            .take(MarkFilter.MAX_MARKS)
            .toList()
        if (marks.isEmpty()) return null
        return MarkFilter(
            mode = mode,
            rowFieldId = rowFieldId,
            seriesFieldId = seriesFieldId,
            marks = marks,
        )
    }

    private fun filterValue(value: Any?): String? = value?.toString()
}

/** SQL predicate for a validated mark filter; values are result-visible identities. */
object MarkFilterSql {
    fun predicate(filter: MarkFilter, rowField: String, seriesField: String?): String {
        val row = Sql.identifier(rowField)
        val series = seriesField?.let { Sql.identifier(it) }
        val clauses = filter.marks.take(MarkFilter.MAX_MARKS).map { mark ->
            if (series != null) {
                "(${match(row, mark.row)} AND ${match(series, mark.series)})"
            } else {
                "(${match(row, mark.row)})"
            }
        }
        val combined = clauses.joinToString(" OR ")
        return if (filter.mode == FilterMode.KEEP) "($combined)" else "NOT ($combined)"
    }

    private fun match(identifier: String, value: String?): String =
        if (value == null) {
            "CAST($identifier AS VARCHAR) IS NULL"
        } else {
            "CAST($identifier AS VARCHAR) = ${Sql.string(value)}"
        }
}
