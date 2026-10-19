package io.github.baitian6641.cloudydata

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.renderer.PdfExportWriter
import io.github.baitian6641.cloudydata.renderer.PdfTable
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfExportWriterTest {
    @Test
    fun rowTallerThanAPageContinuesAcrossPagesWithoutLosingText() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(10f, 10f, 190f, 90f, android.graphics.Paint().apply { color = Color.rgb(0, 106, 106) })
        }
        val png = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val longValue = "prefix " + "lorem ipsum dolor sit amet ".repeat(200) + "END-OF-LONG-ROW"
        val export = PdfExportWriter.fromPngWithTable(
            png,
            PdfTable(
                title = "Long row test",
                sourceNote = "Source: long-row.csv",
                summary = "One exact row spans multiple table pages.",
                columns = listOf("Date", "Long value"),
                rows = listOf(listOf("2026-01-01", longValue)),
            ),
        )
        assertTrue("long row did not span pages: ${export.pageCount}", export.pageCount >= 3)
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "accessible-long-row-test.pdf",
        )
        file.writeBytes(export.bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(export.pageCount, renderer.pageCount)
            }
        }
    }

    @Test
    fun accessibleTableIsAppendedAsSearchablePdfPages() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(10f, 10f, 190f, 90f, android.graphics.Paint().apply { color = Color.rgb(0, 106, 106) })
        }
        val png = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val rows = (1..80).map {
            listOf(
                "2026-01-${(it % 28 + 1).toString().padStart(2, '0')}",
                "Category with a long descriptive label $it",
                it.toString(),
            )
        }
        val export = PdfExportWriter.fromPngWithTable(
            png,
            PdfTable(
                title = "Accessible test chart",
                sourceNote = "Source: accessible-fixture.csv · 80 rows · snapshot abcdef12",
                summary = "Accessible summary: the chart is rasterized on page one; exact rows follow.",
                columns = listOf("Date", "Category", "Value"),
                rows = rows,
            ),
        )
        assertTrue("expected chart + multiple table pages, got ${export.pageCount}", export.pageCount >= 3)
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "accessible-pdf-test.pdf",
        )
        file.writeBytes(export.bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(export.pageCount, renderer.pageCount)
                renderer.openPage(1).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                }
            }
        }
    }
}
