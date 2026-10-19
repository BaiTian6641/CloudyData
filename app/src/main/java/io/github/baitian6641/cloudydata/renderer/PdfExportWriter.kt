package io.github.baitian6641.cloudydata.renderer

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** Converts one bounded PNG raster into a one-page PDF with a white background. */
object PdfExportWriter {
    const val MAX_DIMENSION = 4096
    const val MAX_PDF_BYTES = 8 * 1024 * 1024

    fun fromPng(png: ByteArray): ByteArray {
        require(png.size in 1..MAX_PDF_BYTES) { "PNG payload is empty or too large." }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: throw IllegalArgumentException("PNG payload could not be decoded.")
        require(bitmap.width in 1..MAX_DIMENSION && bitmap.height in 1..MAX_DIMENSION) {
            "PNG dimensions are outside the allowed range."
        }
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            val bytes = output.toByteArray()
            require(bytes.size in 5..MAX_PDF_BYTES) { "PDF output is empty or too large." }
            require(bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) {
                "PDF output has an invalid signature."
            }
            return bytes
        } finally {
            document.close()
            bitmap.recycle()
        }
    }
}
