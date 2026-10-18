package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LogicalType
import org.junit.Assert.assertEquals
import org.junit.Test

class SqlAndTypeTest {
    @Test
    fun stringLiteralsEscapeQuotes() {
        assertEquals("'O''Brien'", Sql.string("O'Brien"))
        assertEquals("'a''''b'", Sql.string("a''b"))
    }

    @Test
    fun identifiersEscapeDoubleQuotes() {
        assertEquals("\"a\"\"b\"", Sql.identifier("a\"b"))
        assertEquals("\"region\"", Sql.identifier("region"))
    }

    @Test
    fun decimalTypesPreserveParameterizedName() {
        val (logical, role) = CsvImportService.logicalType("DECIMAL(18,2)")
        assertEquals(LogicalType.DECIMAL, logical)
        assertEquals(FieldRole.MEASURE, role)
    }

    @Test
    fun timestampsAreDimensionsAndDoublesAreMeasures() {
        assertEquals(LogicalType.TIMESTAMP, CsvImportService.logicalType("TIMESTAMP").first)
        assertEquals(FieldRole.DIMENSION, CsvImportService.logicalType("TIMESTAMP").second)
        assertEquals(LogicalType.DOUBLE, CsvImportService.logicalType("DOUBLE").first)
        assertEquals(FieldRole.MEASURE, CsvImportService.logicalType("DOUBLE").second)
    }

    @Test
    fun leadingZeroFriendlyTextStaysText() {
        assertEquals(LogicalType.STRING, CsvImportService.logicalType("VARCHAR").first)
        assertEquals(FieldRole.DIMENSION, CsvImportService.logicalType("VARCHAR").second)
    }
}
