package io.github.baitian6641.cloudydata

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.renderer.ExportBundleWriter
import io.github.baitian6641.cloudydata.renderer.ExportWriteResult
import io.github.baitian6641.cloudydata.renderer.FrozenExport
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportBundleWriterTest {
    @Test
    fun unknownSafTreeFailsWithoutClaimingACompleteExport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missingTree = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2F" +
                "cloudy-missing-${UUID.randomUUID()}",
        )
        val result = ExportBundleWriter.write(
            context = context,
            treeUri = missingTree,
            bundle = FrozenExport(
                svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray(),
                png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
                pdf = "%PDF-".toByteArray(),
                width = 1,
                height = 1,
            ),
            timestamp = "failure-test",
        )
        assertTrue("missing SAF tree was reported as success: $result", result is ExportWriteResult.Failure)
        assertTrue("failure has no diagnostic", (result as ExportWriteResult.Failure).message.isNotBlank())
    }
}
