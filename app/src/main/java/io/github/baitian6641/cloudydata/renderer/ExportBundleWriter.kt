package io.github.baitian6641.cloudydata.renderer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One frozen visual export scene, including natively derived PDF bytes. */
data class FrozenExport(
    val svg: ByteArray,
    val png: ByteArray,
    val pdf: ByteArray,
    val pdfPages: Int,
    val width: Int,
    val height: Int,
)

sealed interface ExportWriteResult {
    data class Success(val names: List<String>, val directory: String?) : ExportWriteResult
    data class Failure(val message: String) : ExportWriteResult
}

/**
 * Writes all three frozen artifacts into a user-selected SAF tree. Files are
 * deleted on a partial failure so the caller never reports a complete export
 * when only some formats were written.
 */
object ExportBundleWriter {
    fun write(
        context: Context,
        treeUri: Uri,
        bundle: FrozenExport,
        timestamp: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()),
    ): ExportWriteResult {
        val created = mutableListOf<DocumentFile>()
        return try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val directory = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("The selected folder is unavailable.")
            if (!directory.canWrite()) error("The selected folder is not writable.")

            fun writeFile(name: String, mime: String, bytes: ByteArray) {
                val file = directory.createFile(mime, name) ?: error("Could not create $name.")
                created += file
                context.contentResolver.openOutputStream(file.uri)?.use { stream ->
                    stream.write(bytes)
                } ?: error("Could not open $name for writing.")
            }

            val files = listOf(
                Triple("cloudy-chart-$timestamp.svg", "image/svg+xml", bundle.svg),
                Triple("cloudy-chart-$timestamp.png", "image/png", bundle.png),
                Triple("cloudy-chart-$timestamp.pdf", "application/pdf", bundle.pdf),
            )
            files.forEach { (name, mime, bytes) -> writeFile(name, mime, bytes) }
            ExportWriteResult.Success(
                names = files.map { it.first },
                directory = directory.name,
            )
        } catch (error: Throwable) {
            created.forEach { runCatching { it.delete() } }
            ExportWriteResult.Failure(error.message ?: error::class.java.simpleName)
        }
    }
}
