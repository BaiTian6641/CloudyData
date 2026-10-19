package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.MarkFilterSql
import io.github.baitian6641.cloudydata.engine.MarkFilters
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkFilterTest {
    private val rowField = FieldDef("row", "event_date", "DATE", LogicalType.DATE, FieldRole.DIMENSION)
    private val seriesField = FieldDef("series", "region", "VARCHAR", LogicalType.STRING, FieldRole.DIMENSION)
    private val measureField = FieldDef("value", "amount", "BIGINT", LogicalType.INTEGER, FieldRole.MEASURE)

    @Test
    fun sqlPredicateEscapesValuesAndCombinesSeriesPairs() {
        val filter = MarkFilter(
            mode = FilterMode.EXCLUDE,
            rowFieldId = rowField.id,
            seriesFieldId = seriesField.id,
            marks = listOf(
                FilterMark("O'Reilly", "North"),
                FilterMark(null, "South"),
            ),
        )

        assertEquals(
            "NOT ((CAST(\"event_date\" AS VARCHAR) = 'O''Reilly' AND CAST(\"region\" AS VARCHAR) = 'North') OR " +
                "(CAST(\"event_date\" AS VARCHAR) IS NULL AND CAST(\"region\" AS VARCHAR) = 'South'))",
            MarkFilterSql.predicate(filter, rowField.name, seriesField.name),
        )
    }

    @Test
    fun sqlPredicateOmitsSeriesForSingleMarkCharts() {
        val filter = MarkFilter(
            mode = FilterMode.KEEP,
            rowFieldId = rowField.id,
            marks = listOf(FilterMark("2026-01-01")),
        )

        assertEquals(
            "((CAST(\"event_date\" AS VARCHAR) = '2026-01-01'))",
            MarkFilterSql.predicate(filter, rowField.name, null),
        )
    }

    @Test
    fun builderUsesExactVisibleRowAndSeriesIdentities() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "HUGEINT"),
                ),
                rows = listOf(
                    listOf("2026-01-01", "North", 10L),
                    listOf("2026-02-01", "North", 20L),
                    listOf("2026-02-01", "South", 2L),
                ),
                truncated = false,
            ),
            rowField = rowField,
            measureField = measureField,
            seriesField = seriesField,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        val sheet = worksheet().copy(
            markType = MarkType.LINE,
            seriesFieldId = seriesField.id,
        )

        val filter = MarkFilters.fromResult(sheet, result, setOf(2, 0, 99), FilterMode.EXCLUDE)

        assertEquals(
            MarkFilter(
                mode = FilterMode.EXCLUDE,
                rowFieldId = rowField.id,
                seriesFieldId = seriesField.id,
                marks = listOf(
                    FilterMark("2026-01-01", "North"),
                    FilterMark("2026-02-01", "South"),
                ),
            ),
            filter,
        )
    }

    @Test
    fun builderRejectsEmptySelectionAndMissingRowField() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("value", "BIGINT")),
                rows = listOf(listOf(7L)),
                truncated = false,
            ),
            rowField = null,
            measureField = measureField,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sql = "test",
        )
        val sheet = worksheet().copy(rowFieldId = null)

        assertNull(MarkFilters.fromResult(sheet, result, setOf(0), FilterMode.KEEP))
        assertNull(MarkFilters.fromResult(sheet.copy(rowFieldId = rowField.id), result, emptySet(), FilterMode.KEEP))
    }

    private fun worksheet() = WorksheetDef(
        id = "sheet",
        name = "Sheet 1",
        rowFieldId = rowField.id,
        measureFieldId = measureField.id,
        aggregation = Aggregation.SUM,
        markType = MarkType.BAR,
        sortDescending = false,
    )
}
