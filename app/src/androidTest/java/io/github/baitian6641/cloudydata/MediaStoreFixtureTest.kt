package io.github.baitian6641.cloudydata

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Qualification helper only. Publishes a synthetic fixture into the current
 * Android user's Downloads collection so the real SAF picker can be exercised.
 * It is not part of the production import path.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreFixtureTest {
    @Test
    fun publishCsvForSafPickerReview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "cloudy_ui_q06.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val existing = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("cloudy_ui_q06.csv"),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        if (existing != null) {
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns._ID}=?", arrayOf(existing.toString()))
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create Downloads entry")
        instrumentation.context.assets.open("csv/q06_lod.csv").use { input ->
            resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                ?: error("Could not open Downloads output")
        }
        Log.i("CloudyQualification", "published fixture at $uri")
    }
}
