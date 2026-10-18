package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.ImportPhase
import io.github.baitian6641.cloudydata.data.ImportReceipt
import io.github.baitian6641.cloudydata.data.ImportRecovery
import io.github.baitian6641.cloudydata.data.ReceiptFiles
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "recovery-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun interruptedCommitRestoresPreviousSnapshotAndRemovesOperation() {
        val workbook = importWorkbook("Original")
        val originalSnapshot = workbook.snapshot!!
        val originalFinal = store.resolveInsideWorkbook(workbook.id, originalSnapshot.snapshotFile)
        val originalBytes = originalFinal.readBytes()

        val replacement = importReplacementBytes()
        val operationDir = stageOperation(workbook)
        File(operationDir, "previous").mkdirs()
        File(operationDir, "previous/snapshot.parquet").writeBytes(originalBytes)
        store.resolveInsideWorkbook(workbook.id, originalSnapshot.originalFile!!).copyTo(
            File(operationDir, "previous/original.csv"),
            overwrite = true,
        )
        originalFinal.writeBytes(replacement)
        ReceiptFiles.write(
            operationDir,
            receipt(
                workbook = workbook,
                phase = ImportPhase.DataCommitted,
                proposedSnapshotId = "unpublished-replacement",
                hadPrevious = true,
            ),
        )

        val result = ImportRecovery(store).reconcile(workbook.id)
        assertTrue(result.messages.any { it.contains("Recovered") || it.contains("already published") })
        assertEquals(originalBytes.toList(), originalFinal.readBytes().toList())
        assertFalse(operationDir.exists())
        assertEquals(originalSnapshot.id, store.load(workbook.id).snapshot?.id)
    }

    @Test
    fun publishedMetadataKeepsCommittedDataAndCleansOperation() {
        val workbook = importWorkbook("Published")
        val originalSnapshot = workbook.snapshot!!
        val replacement = importReplacementBytes()
        val finalSnapshot = store.resolveInsideWorkbook(workbook.id, originalSnapshot.snapshotFile)
        finalSnapshot.writeBytes(replacement)
        val publishedId = "published-snapshot"
        store.save(
            workbook.copy(
                snapshot = originalSnapshot.copy(id = publishedId),
            ),
        )
        val operationDir = stageOperation(workbook)
        ReceiptFiles.write(
            operationDir,
            receipt(
                workbook = workbook,
                phase = ImportPhase.DataCommitted,
                proposedSnapshotId = publishedId,
                hadPrevious = true,
            ),
        )

        val result = ImportRecovery(store).reconcile(workbook.id)
        assertTrue(result.messages.any { it.contains("already published") })
        assertEquals(replacement.toList(), finalSnapshot.readBytes().toList())
        assertFalse(operationDir.exists())
    }

    @Test
    fun preparedOperationIsDiscardedWithoutTouchingAcceptedData() {
        val workbook = importWorkbook("Prepared")
        val snapshot = workbook.snapshot!!
        val finalSnapshot = store.resolveInsideWorkbook(workbook.id, snapshot.snapshotFile)
        val originalBytes = finalSnapshot.readBytes()
        val operationDir = stageOperation(workbook)
        ReceiptFiles.write(
            operationDir,
            receipt(workbook, ImportPhase.DataPrepared, "never-committed", false),
        )

        val result = ImportRecovery(store).reconcile(workbook.id)
        assertTrue(result.messages.isEmpty())
        assertEquals(originalBytes.toList(), finalSnapshot.readBytes().toList())
        assertFalse(operationDir.exists())
    }

    private fun importWorkbook(label: String): WorkbookDocument {
        val workbook = store.create(label)
        val pending = runBlocking { importer.stageAndInspect(workbook, fixtureUri("csv/q06_lod.csv")) }
        return runBlocking { importer.apply(workbook, pending) }
    }

    private fun importReplacementBytes(): ByteArray {
        val other = store.create("Replacement")
        val pending = runBlocking { importer.stageAndInspect(other, fixtureUri("csv/q01_adversarial.csv")) }
        val applied = runBlocking { importer.apply(other, pending) }
        return store.resolveInsideWorkbook(applied.id, applied.snapshot!!.snapshotFile).readBytes()
    }

    private fun stageOperation(workbook: WorkbookDocument): File {
        val op = File(store.workbookDir(workbook.id), "staging/${UUID.randomUUID()}").apply { mkdirs() }
        return op
    }

    private fun receipt(
        workbook: WorkbookDocument,
        phase: ImportPhase,
        proposedSnapshotId: String,
        hadPrevious: Boolean,
    ): ImportReceipt = ImportReceipt(
        operationId = "op-${UUID.randomUUID()}",
        workbookId = workbook.id,
        phase = phase,
        proposedSnapshotId = proposedSnapshotId,
        displayName = "synthetic replacement",
        rowCount = 3,
        stagedBytes = 123,
        stagedSha256 = "0".repeat(64),
        hadPreviousSnapshot = hadPrevious,
        updatedAtEpochMs = System.currentTimeMillis(),
    )

    private fun fixtureUri(assetPath: String): Uri {
        val target = File(context.cacheDir, "recovery-fixture-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(target)
    }
}
