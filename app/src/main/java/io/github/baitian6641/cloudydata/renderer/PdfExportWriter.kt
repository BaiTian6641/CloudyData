package io.github.baitian6641.cloudydata.renderer

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

data class PdfTable(
    val title: String,
    val subtitle: String? = null,
    val caption: String? = null,
    val sourceNote: String?,
    val summary: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val rasterNotice: String = "Page 1 is a rasterized chart image. This page provides accessible title, provenance and data.",
)

data class PdfExport(val bytes: ByteArray, val pageCount: Int)

/**
 * Converts one bounded PNG raster into a PDF. When a table is supplied, a
 * paginated, searchable text companion is appended so the raster chart is not
 * the only representation.
 */
object PdfExportWriter {
    const val MAX_DIMENSION = 4096
    const val MAX_PDF_BYTES = 8 * 1024 * 1024

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val ROW_HEIGHT = 14f

    fun fromPng(png: ByteArray, backgroundColor: Int? = null): ByteArray =
        fromPngWithTable(png, null, backgroundColor).bytes

    fun fromPngWithTable(
        png: ByteArray,
        table: PdfTable?,
        backgroundColor: Int? = null,
    ): PdfExport {
        require(png.size in 1..MAX_PDF_BYTES) { "PNG payload is empty or too large." }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: throw IllegalArgumentException("PNG payload could not be decoded.")
        require(bitmap.width in 1..MAX_DIMENSION && bitmap.height in 1..MAX_DIMENSION) {
            "PNG dimensions are outside the allowed range."
        }
        val document = PdfDocument()
        try {
            val imagePage = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(imagePage)
            page.canvas.drawColor(backgroundColor ?: Color.WHITE)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
            var pageNumber = 2
            if (table != null) {
                pageNumber = drawTable(document, table, pageNumber)
            }
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            val bytes = output.toByteArray()
            require(bytes.size in 5..MAX_PDF_BYTES) { "PDF output is empty or too large." }
            require(bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) {
                "PDF output has an invalid signature."
            }
            return PdfExport(bytes, pageNumber - 1)
        } finally {
            document.close()
            bitmap.recycle()
        }
    }

    private fun drawTable(
        document: PdfDocument,
        table: PdfTable,
        firstPageNumber: Int,
    ): Int {
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 36, 39)
            textSize = 9f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val bold = Paint(normal).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val muted = Paint(normal).apply { color = Color.rgb(76, 96, 102) }
        val titlePaint = Paint(bold).apply { textSize = 18f }
        val headingPaint = Paint(bold).apply { textSize = 13f }
        val contentWidth = PAGE_WIDTH - (MARGIN * 2)
        val columns = table.columns.ifEmpty { listOf("Value") }
        val columnWidth = contentWidth / columns.size
        var pageNumber = firstPageNumber
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create(),
        )
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        var y = MARGIN + 18f
        canvas.drawText(ellipsize(table.title, titlePaint, contentWidth), MARGIN, y, titlePaint)
        y += 16f
        table.subtitle?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(ellipsize(it, muted, contentWidth), MARGIN, y, muted)
            y += 13f
        }
        table.caption?.takeIf { it.isNotBlank() }?.let {
            y = drawWrapped(canvas, it, muted, contentWidth, MARGIN, y, 11f, maxLines = 2)
            y += 2f
        }
        table.sourceNote?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(ellipsize(it, muted, contentWidth), MARGIN, y, muted)
            y += 14f
        }
        y += 8f
        canvas.drawText("Accessible chart data", MARGIN, y, headingPaint)
        y += 18f
        y = drawWrapped(canvas, table.summary, muted, contentWidth, MARGIN, y, 12f, maxLines = 6)
        y += 4f
        y = drawWrapped(canvas, table.rasterNotice, muted, contentWidth, MARGIN, y, 12f, maxLines = 3)
        y += 8f

        fun drawHeader() {
            var x = MARGIN
            columns.forEach { column ->
                canvas.drawText(ellipsize(column, bold, columnWidth - 4), x, y, bold)
                x += columnWidth
            }
            y += 4f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, muted)
            y += ROW_HEIGHT
        }

        fun finishPage() {
            val footer = "Page ${pageNumber - 1} · Cloudy Data"
            canvas.drawText(footer, MARGIN, PAGE_HEIGHT - 20f, muted)
            document.finishPage(page)
        }

        drawHeader()
        if (table.rows.isEmpty()) {
            canvas.drawText("No rows in this result.", MARGIN, y, normal)
        } else {
            table.rows.forEach { row ->
                val cellLines = columns.indices.map { index ->
                    wrapText(row.getOrNull(index) ?: "", normal, columnWidth - 4)
                }
                val totalLines = cellLines.maxOf { it.size }.coerceAtLeast(1)
                var lineIndex = 0
                while (lineIndex < totalLines) {
                    if (y + ROW_HEIGHT > PAGE_HEIGHT - 34f) {
                        finishPage()
                        page = document.startPage(
                            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create(),
                        )
                        canvas = page.canvas
                        canvas.drawColor(Color.WHITE)
                        y = MARGIN + 12f
                        canvas.drawText(
                            ellipsize("${table.title} · continued", headingPaint, contentWidth),
                            MARGIN,
                            y,
                            headingPaint,
                        )
                        y += 18f
                        drawHeader()
                    }
                    val fit = ((PAGE_HEIGHT - 34f - y) / ROW_HEIGHT).toInt().coerceAtLeast(1)
                    val lines = minOf(fit, totalLines - lineIndex)
                    var x = MARGIN
                    cellLines.forEach { cell ->
                        for (offset in 0 until lines) {
                            cell.getOrNull(lineIndex + offset)?.let { cellText ->
                                canvas.drawText(cellText, x, y + (offset * ROW_HEIGHT), normal)
                            }
                        }
                        x += columnWidth
                    }
                    y += lines * ROW_HEIGHT
                    lineIndex += lines
                }
            }
        }
        finishPage()
        return pageNumber
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: Paint,
        maxWidth: Float,
        x: Float,
        startY: Float,
        lineHeight: Float,
        maxLines: Int = Int.MAX_VALUE,
    ): Float {
        if (text.isEmpty() || maxLines <= 0) return startY
        val wrapped = wrapText(text, paint, maxWidth)
        val visible = if (wrapped.size <= maxLines) {
            wrapped
        } else {
            wrapped.take(maxLines - 1) +
                ellipsize(wrapped.drop(maxLines - 1).joinToString(" "), paint, maxWidth)
        }
        visible.forEachIndexed { index, line ->
            canvas.drawText(line, x, startY + (index * lineHeight), paint)
        }
        return startY + visible.size * lineHeight
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            val measured = paint.breakText(remaining, true, maxWidth, null)
            if (measured <= 0) break
            var end = measured
            if (end < remaining.length) {
                val space = remaining.lastIndexOf(' ', end)
                if (space > 0) end = space
            }
            lines += remaining.substring(0, end).trimEnd()
            remaining = remaining.substring(end).trimStart()
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
