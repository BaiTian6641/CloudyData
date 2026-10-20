package io.github.baitian6641.cloudydata.engine

import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef

enum class WorksheetReferenceChannel {
    SNAPSHOT, ROWS, MEASURE, SERIES, SECONDARY_MEASURE, FILTER_ROWS, FILTER_SERIES, FILTER_SHAPE, RESULT,
}

class WorksheetReferenceException(
    val channel: WorksheetReferenceChannel,
    val fieldId: String?,
    message: String,
) : IllegalArgumentException(message)

/** Runtime query ownership, independent of chart paint and worksheet display name. */
data class WorksheetQueryIdentity(
    val snapshotId: String,
    val worksheetId: String,
    val rowFieldId: String?,
    val measureFieldId: String?,
    val seriesFieldId: String?,
    val secondaryMeasureFieldId: String?,
    val aggregation: Aggregation,
    val secondaryAggregation: Aggregation,
    val markType: MarkType,
    val sortDescending: Boolean,
    val filter: MarkFilter?,
) {
    fun matchesSheet(sheet: WorksheetDef): Boolean = copy(snapshotId = "") == from("", sheet)

    companion object {
        fun from(snapshotId: String, sheet: WorksheetDef) = WorksheetQueryIdentity(
            snapshotId, sheet.id, sheet.rowFieldId, sheet.measureFieldId, sheet.seriesFieldId,
            sheet.secondaryMeasureFieldId, sheet.aggregation, sheet.secondaryAggregation,
            sheet.markType, sheet.sortDescending, sheet.filter,
        )
    }
}

data class ResolvedWorksheetReferences(
    val rowField: FieldDef?,
    val measureField: FieldDef?,
    val seriesField: FieldDef?,
    val secondaryMeasureField: FieldDef?,
    val usedSeriesField: FieldDef?,
    val usedSecondaryMeasure: FieldDef?,
    val filter: MarkFilter?,
)

/** Read-only semantic admission. Missing references remain in metadata for explicit repair. */
object WorksheetReferences {
    fun resolve(snapshot: DataSnapshot, sheet: WorksheetDef): ResolvedWorksheetReferences {
        val fields = linkedMapOf<String, FieldDef>()
        snapshot.columns.forEach { field ->
            if (field.id.isBlank() || fields.put(field.id, field) != null) {
                throw WorksheetReferenceException(WorksheetReferenceChannel.SNAPSHOT, field.id,
                    "The snapshot has an empty or duplicate field identity '${field.id}'.")
            }
        }
        fun resolveId(id: String?, channel: WorksheetReferenceChannel): FieldDef? = id?.let {
            fields[it] ?: throw WorksheetReferenceException(channel, it,
                "Broken field reference ($channel): '$it' is unavailable in this snapshot. Rebind the field to continue.")
        }
        val row = resolveId(sheet.rowFieldId, WorksheetReferenceChannel.ROWS)
        val measure = resolveId(sheet.measureFieldId, WorksheetReferenceChannel.MEASURE)
        val series = resolveId(sheet.seriesFieldId, WorksheetReferenceChannel.SERIES)
        val secondary = resolveId(sheet.secondaryMeasureFieldId, WorksheetReferenceChannel.SECONDARY_MEASURE)
        val seriesMarks = sheet.markType == MarkType.LINE || sheet.markType == MarkType.AREA || sheet.markType == MarkType.BAR
        val usedSeries = series.takeIf { seriesMarks && row != null && measure != null }
        val usedSecondary = secondary.takeIf {
            sheet.markType == MarkType.BAR && row != null && measure != null && usedSeries == null
        }
        val filter = sheet.filter?.takeIf { it.marks.isNotEmpty() }
        if (filter != null) {
            resolveId(filter.rowFieldId, WorksheetReferenceChannel.FILTER_ROWS)
            resolveId(filter.seriesFieldId, WorksheetReferenceChannel.FILTER_SERIES)
            if (filter.rowFieldId != row?.id || filter.seriesFieldId != usedSeries?.id) {
                throw WorksheetReferenceException(WorksheetReferenceChannel.FILTER_SHAPE, filter.rowFieldId,
                    "The saved filter does not match this worksheet's row and series references. Repair or clear the filter to continue.")
            }
        }
        return ResolvedWorksheetReferences(row, measure, series, secondary, usedSeries, usedSecondary, filter)
    }

    fun requireCurrentResult(snapshot: DataSnapshot, sheet: WorksheetDef, result: WorksheetResult) {
        val refs = resolve(snapshot, sheet)
        if (result.queryIdentity != WorksheetQueryIdentity.from(snapshot.id, sheet) ||
            result.rowField != refs.rowField || result.measureField != refs.measureField ||
            result.seriesField != refs.seriesField || result.secondaryMeasureField != refs.usedSecondaryMeasure ||
            result.markType != sheet.markType || result.aggregation != sheet.aggregation ||
            result.secondaryAggregation != sheet.secondaryAggregation.takeIf { refs.usedSecondaryMeasure != null }) {
            throw WorksheetReferenceException(WorksheetReferenceChannel.RESULT, null,
                "The selected result belongs to a different snapshot or worksheet query. Refresh the worksheet before using its marks.")
        }
    }
}
