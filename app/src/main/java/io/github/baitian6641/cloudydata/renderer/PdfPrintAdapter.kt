package io.github.baitian6641.cloudydata.renderer

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

/** Serves one already-frozen, bounded PDF to Android's print spooler. */
class PdfPrintAdapter(
    private val pdf: ByteArray,
    private val jobName: String,
    private val pageCount: Int,
) : PrintDocumentAdapter() {
    init {
        require(pdf.isNotEmpty()) { "PDF payload is empty." }
        require(pdf.copyOfRange(0, minOf(5, pdf.size)).contentEquals("%PDF-".toByteArray())) {
            "PDF payload has an invalid signature."
        }
        require(pageCount > 0) { "PDF page count must be positive." }
    }

    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?,
        newAttributes: android.print.PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val payload = pdf
        val count = pageCount
        val signal = cancellationSignal
        Thread {
            try {
                // The print framework owns this descriptor; do not close it here.
                val stream = FileOutputStream(destination.fileDescriptor)
                var offset = 0
                while (offset < payload.size) {
                    if (signal?.isCanceled == true) {
                        handler.post { callback.onWriteCancelled() }
                        return@Thread
                    }
                    val chunk = minOf(64 * 1024, payload.size - offset)
                    stream.write(payload, offset, chunk)
                    offset += chunk
                }
                stream.flush()
                handler.post { callback.onWriteFinished(arrayOf(PageRange(0, count - 1))) }
            } catch (error: Throwable) {
                handler.post { callback.onWriteFailed(error.message ?: "Could not write the print job.") }
            }
        }.start()
    }
}
