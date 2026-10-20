package io.github.baitian6641.cloudydata

import android.app.Application
import android.content.Context
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookConflictException
import io.github.baitian6641.cloudydata.data.WorkbookCoordinator
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.ui.SaveState
import io.github.baitian6641.cloudydata.ui.WorkbookViewModel
import io.github.baitian6641.cloudydata.ui.WorkbookViewModelFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S03.3 / F01,F64 / Q17,Q19,Q34. Real Android JSON/filesystem and native DuckDB.
 * A fresh WorkbookStore reconstructs process-owned objects from persisted bytes;
 * these cases do not claim an actual operating-system process-kill experiment.
 */
@RunWith(AndroidJUnit4::class)
class WorkbookJournalTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "journal-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun renameChartUndoAndRedoReconstructExactlyAtEachAcceptedBoundary() = runBlocking {
        val book = store.create("Book")
        val sheetId = book.worksheets.single().id
        var coordinator = reopen(book.id)
        val rename = coordinator.execute(rename(book, "rename-1", "Renamed"))
        assertFalse(rename.duplicate)
        assertEquals(book.revision + 1, rename.acceptedRevision)
        assertBook(rename.document, "Renamed", ChartOptions(), book.revision + 1)

        coordinator = reopen(book.id)
        assertTrue(coordinator.state.value.canUndo)
        assertFalse(coordinator.state.value.canRedo)
        val custom = ChartOptions(title = "Custom", showGridlines = false, valueDecimals = 2)
        val chart = coordinator.execute(
            WorkbookCommand.SetChart("chart-1", book.id, "window-chart", rename.acceptedRevision, sheetId, custom),
        )
        assertFalse(chart.duplicate)
        assertEquals(book.revision + 2, chart.acceptedRevision)
        assertBook(chart.document, "Renamed", custom, book.revision + 2)

        coordinator = reopen(book.id)
        val chartUndone = coordinator.undo(book.revision + 2)
        assertBook(chartUndone, "Renamed", ChartOptions(), book.revision + 3)
        assertTrue(coordinator.state.value.canUndo)
        assertTrue(coordinator.state.value.canRedo)

        coordinator = reopen(book.id)
        val renameUndone = coordinator.undo(book.revision + 3)
        assertBook(renameUndone, "Book", ChartOptions(), book.revision + 4)
        assertFalse(coordinator.state.value.canUndo)
        assertTrue(coordinator.state.value.canRedo)

        coordinator = reopen(book.id)
        val renameRedone = coordinator.redo(book.revision + 4)
        assertBook(renameRedone, "Renamed", ChartOptions(), book.revision + 5)
        coordinator = reopen(book.id)
        val chartRedone = coordinator.redo(book.revision + 5)
        assertBook(chartRedone, "Renamed", custom, book.revision + 6)
        coordinator = reopen(book.id)
        assertTrue(coordinator.state.value.canUndo)
        assertFalse(coordinator.state.value.canRedo)
        assertEquals(chartRedone, coordinator.state.value.document)
        assertEquals(chartRedone, store.load(book.id))
    }

    @Test
    fun sameReceiptReturnsCurrentDocumentWithoutReapplyingAfterForeignEditOrUndo() = runBlocking {
        val book = store.create("Book")
        val command = rename(book, "stable-rename-id", "Renamed")
        var coordinator = reopen(book.id)
        val first = coordinator.execute(command)
        val foreign = coordinator.execute(
            WorkbookCommand.SetChart(
                "foreign-chart", book.id, "other-window", first.acceptedRevision,
                book.worksheets.single().id, ChartOptions(title = "Foreign chart"),
            ),
        )
        coordinator = reopen(book.id)
        val bytesBefore = metadata(book.id).readBytes()
        val duplicate = coordinator.execute(command)
        assertTrue(duplicate.duplicate)
        assertEquals(first.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(foreign.document, duplicate.document)
        assertEquals(foreign.document, coordinator.state.value.document)
        assertArrayEquals(bytesBefore, metadata(book.id).readBytes())

        coordinator.undo(foreign.document.revision)
        coordinator.undo(coordinator.state.value.document.revision)
        coordinator = reopen(book.id)
        val undone = coordinator.state.value.document
        assertBook(undone, "Book", ChartOptions(), book.revision + 4)
        val bytesAfterUndo = metadata(book.id).readBytes()
        val retryAfterUndo = coordinator.execute(command)
        assertTrue(retryAfterUndo.duplicate)
        assertEquals(first.acceptedRevision, retryAfterUndo.acceptedRevision)
        assertEquals(undone, retryAfterUndo.document)
        assertArrayEquals(bytesAfterUndo, metadata(book.id).readBytes())
        assertFalse(coordinator.state.value.canUndo)
        assertTrue(coordinator.state.value.canRedo)
    }

    @Test
    fun reusedCommandIdentityWithChangedPayloadOriginRevisionOrTargetIsRefused() = runBlocking {
        val book = store.create("Book")
        val command = rename(book, "immutable-receipt", "Renamed")
        var coordinator = reopen(book.id)
        val accepted = coordinator.execute(command).document
        coordinator = reopen(book.id)
        val before = metadata(book.id).readBytes()
        val mismatches = listOf(
            command.copy(name = "Changed payload"),
            command.copy(originWindowId = "different-window"),
            command.copy(expectedRevision = accepted.revision),
            command.copy(workbookId = "different-workbook"),
        )
        mismatches.forEach { mismatched ->
            val error = runCatching { coordinator.execute(mismatched) }.exceptionOrNull()
            assertTrue("same ID with changed request must be refused: $mismatched / $error", error != null)
            assertEquals(accepted, coordinator.state.value.document)
            assertArrayEquals(before, metadata(book.id).readBytes())
        }
        val wrongKind = WorkbookCommand.SetChart(
            command.commandId, book.id, command.originWindowId, command.expectedRevision,
            book.worksheets.single().id, ChartOptions(title = "Wrong operation"),
        )
        assertTrue(runCatching { coordinator.execute(wrongKind) }.isFailure)
        assertArrayEquals(before, metadata(book.id).readBytes())
        assertEquals(accepted, reopen(book.id).state.value.document)
    }

    @Test
    fun staleUnknownSheetAndWrongWorkbookCommandsCannotConsumeAnIdOrChangeBytes() = runBlocking {
        val book = store.create("Book")
        val coordinator = reopen(book.id)
        val accepted = coordinator.execute(rename(book, "first", "Accepted")).document
        val before = metadata(book.id).readBytes()
        val stale = runCatching {
            coordinator.execute(rename(book, "retryable-stale", "Stale overwrite"))
        }.exceptionOrNull()
        assertTrue(stale is WorkbookConflictException)
        stale as WorkbookConflictException
        assertEquals(book.revision, stale.expectedRevision)
        assertEquals(accepted.revision, stale.actualRevision)
        assertArrayEquals(before, metadata(book.id).readBytes())

        val corrected = coordinator.execute(
            rename(accepted, "retryable-stale", "Reviewed current state"),
        )
        assertFalse(corrected.duplicate)
        assertEquals(accepted.revision + 1, corrected.acceptedRevision)
        val badSheet = WorkbookCommand.SetChart(
            "retryable-sheet", book.id, "window-chart", corrected.acceptedRevision,
            "missing-worksheet", ChartOptions(title = "No sheet"),
        )
        val beforeBadSheet = metadata(book.id).readBytes()
        assertTrue(runCatching { coordinator.execute(badSheet) }.isFailure)
        assertArrayEquals(beforeBadSheet, metadata(book.id).readBytes())
        val validSheet = coordinator.execute(badSheet.copy(worksheetId = book.worksheets.single().id))
        assertFalse(validSheet.duplicate)
        assertEquals(corrected.acceptedRevision + 1, validSheet.acceptedRevision)
        assertEquals("No sheet", validSheet.document.worksheets.single().chart.title)

        val wrongWorkbook = rename(validSheet.document, "retryable-target", "Valid target")
            .copy(workbookId = "missing-workbook")
        val beforeWrongWorkbook = metadata(book.id).readBytes()
        assertTrue(runCatching { coordinator.execute(wrongWorkbook) }.isFailure)
        assertArrayEquals(beforeWrongWorkbook, metadata(book.id).readBytes())
        assertFalse(coordinator.execute(wrongWorkbook.copy(workbookId = book.id)).duplicate)
        assertEquals("Valid target", store.load(book.id).name)
    }

    @Test
    fun branchAfterReconstructionDropsRedoAndGenericEditsShareDurableHistory() = runBlocking {
        val book = store.create("Book")
        var coordinator = reopen(book.id)
        val renamed = coordinator.execute(rename(book, "rename", "Renamed")).document
        val generic = coordinator.edit(renamed.revision) { it.copy(favorite = true, tags = listOf("reviewed")) }
        val options = ChartOptions(title = "Custom")
        val chart = coordinator.execute(
            WorkbookCommand.SetChart("chart", book.id, "window", generic.revision, book.worksheets.single().id, options),
        ).document
        coordinator = reopen(book.id)
        val firstUndo = coordinator.undo(chart.revision)
        assertBook(firstUndo, "Renamed", ChartOptions(), book.revision + 4)
        assertTrue(firstUndo.favorite)
        assertEquals(listOf("reviewed"), firstUndo.tags)
        coordinator = reopen(book.id)
        val secondUndo = coordinator.undo(firstUndo.revision)
        assertBook(secondUndo, "Renamed", ChartOptions(), book.revision + 5)
        assertFalse(secondUndo.favorite)
        assertEquals(emptyList<String>(), secondUndo.tags)
        assertTrue(coordinator.state.value.canRedo)

        val branch = coordinator.execute(rename(secondUndo, "branch", "Branch")).document
        coordinator = reopen(book.id)
        assertFalse(coordinator.state.value.canRedo)
        assertEquals(branch, coordinator.redo(branch.revision))
        assertEquals(branch, store.load(book.id))
        assertEquals("Branch", branch.name)
        assertEquals(book.revision + 6, branch.revision)
        val before = metadata(book.id).readBytes()
        val originalRetry = coordinator.execute(rename(book, "rename", "Renamed"))
        assertTrue(originalRetry.duplicate)
        assertEquals(book.revision + 1, originalRetry.acceptedRevision)
        assertEquals(branch, originalRetry.document)
        assertArrayEquals(before, metadata(book.id).readBytes())
    }

    @Test
    fun deniedAtomicWritePreservesBytesHistoryAndRetryIdentity() = runBlocking {
        val book = store.create("Book")
        var coordinator = reopen(book.id)
        val first = coordinator.execute(rename(book, "first", "First")).document
        val second = coordinator.execute(rename(first, "second", "Second")).document
        coordinator.undo(second.revision)
        coordinator = reopen(book.id)
        val before = coordinator.state.value
        assertTrue(before.canUndo)
        assertTrue(before.canRedo)
        assertEquals("First", before.document.name)
        val bytesBefore = metadata(book.id).readBytes()
        val command = rename(before.document, "failed-then-retry", "Accepted after retry")
        val directory = store.workbookDir(book.id)
        val originalMode = Os.stat(directory.path).st_mode and 0x1ff
        val error = try {
            Os.chmod(directory.path, 0x16d) // 0555: readable metadata, no temporary-file creation.
            assertEquals(0x16d, Os.stat(directory.path).st_mode and 0x1ff)
            runCatching { coordinator.execute(command) }.exceptionOrNull()
        } finally {
            Os.chmod(directory.path, originalMode)
        }
        val denial = generateSequence(error) { it.cause }.any {
            (it is ErrnoException && it.errno == OsConstants.EACCES) ||
                it.message.orEmpty().contains("Permission denied", ignoreCase = true) ||
                it.message.orEmpty().contains("EACCES")
        }
        assertTrue("a genuine filesystem EACCES must be observed, got $error", denial)
        assertEquals(before, coordinator.state.value)
        assertArrayEquals(bytesBefore, metadata(book.id).readBytes())
        coordinator = reopen(book.id)
        assertEquals(before, coordinator.state.value)
        val retry = coordinator.execute(command)
        assertFalse("failed command must not have acquired a durable receipt", retry.duplicate)
        assertEquals(before.document.revision + 1, retry.acceptedRevision)
        assertEquals("Accepted after retry", retry.document.name)
        assertFalse(coordinator.state.value.canRedo)
        coordinator = reopen(book.id)
        coordinator.undo(retry.document.revision)
        assertEquals("First", coordinator.state.value.document.name)
        coordinator.undo(coordinator.state.value.document.revision)
        assertEquals("Book", coordinator.state.value.document.name)
        assertFalse(coordinator.state.value.canUndo)
    }

    @Test
    fun realSnapshotReplacementClearsOldHistoryAndRetryDoesNotReplayOldCommandOrImport() = runBlocking {
        val book = store.create("Book")
        var coordinator = reopen(book.id)
        val importer = CsvImportService(context, store)
        val firstCsv = csv("old", "Category,Amount\nEast,10\nWest,20\nEast,5\n")
        val firstPending = importer.stageAndInspect(book, Uri.fromFile(firstCsv))
        val imported = coordinator.importSnapshot(book.revision) { importer.apply(it, firstPending) }
        assertEquals(listOf(3L, 35L), snapshotCountAndSum(imported))
        val command = rename(imported, "before-replacement", "Retained name")
        val renamed = coordinator.execute(command).document
        coordinator = reopen(book.id)
        assertTrue(coordinator.state.value.canUndo)
        val replacementCsv = csv("new", "Category,Amount\nNew,7\n")
        val replacementPending = importer.stageAndInspect(renamed, Uri.fromFile(replacementCsv))
        var sourceOperations = 0
        val replaced = coordinator.importSnapshot(renamed.revision) {
            sourceOperations += 1
            importer.apply(it, replacementPending)
        }
        assertNotEquals(imported.snapshot!!.id, replaced.snapshot!!.id)
        assertEquals(listOf(1L, 7L), snapshotCountAndSum(replaced))
        assertEquals("Retained name", replaced.name)
        assertEquals(replacementCsv.readText(), store.resolveInsideWorkbook(book.id, replaced.snapshot.originalFile!!).readText())
        coordinator = reopen(book.id)
        assertFalse(coordinator.state.value.canUndo)
        assertFalse(coordinator.state.value.canRedo)
        val snapshotBytes = store.resolveInsideWorkbook(book.id, replaced.snapshot.snapshotFile).readBytes()
        val metadataBytes = metadata(book.id).readBytes()
        val retry = coordinator.execute(command)
        assertTrue(retry.duplicate)
        assertEquals(renamed.revision, retry.acceptedRevision)
        assertEquals(replaced, retry.document)
        assertEquals(replaced, coordinator.undo(replaced.revision))
        assertEquals(replaced, coordinator.redo(replaced.revision))
        assertArrayEquals(metadataBytes, metadata(book.id).readBytes())
        assertArrayEquals(snapshotBytes, store.resolveInsideWorkbook(book.id, replaced.snapshot.snapshotFile).readBytes())
        assertEquals(listOf(1L, 7L), snapshotCountAndSum(store.load(book.id)))
        assertEquals(1, sourceOperations)
    }

    @Test
    fun corruptMissingAndFutureJournalOrWorkbookRefuseReopenAndAllWritesWithoutRepairingBytes() = runBlocking {
        val mutations: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "changed undo content" to { json ->
                json.getJSONObject("commandJournal").getJSONArray("undo").getJSONObject(0)
                    .put("name", "Corrupted prior state")
            },
            "changed current content" to { json -> json.put("name", "Corrupted current state") },
            "missing journal" to { json -> json.remove("commandJournal"); Unit },
            "future journal" to { json -> json.getJSONObject("commandJournal").put("version", 2); Unit },
            "missing journal checksum" to { json -> json.getJSONObject("commandJournal").remove("sha256"); Unit },
            "future workbook" to { json -> json.put("formatVersion", 4); Unit },
        )
        mutations.forEachIndexed { index, (label, mutate) ->
            val book = store.create("Integrity $index")
            val coordinator = reopen(book.id)
            val first = coordinator.execute(rename(book, "first-$index", "First")).document
            val second = coordinator.execute(rename(first, "second-$index", "Second")).document
            coordinator.undo(second.revision)
            val before = coordinator.state.value
            assertTrue(before.canUndo)
            assertTrue(before.canRedo)
            val json = JSONObject(metadata(book.id).readText())
            assertEquals(3, json.getInt("formatVersion"))
            assertEquals(1, json.getJSONObject("commandJournal").getInt("version"))
            mutate(json)
            metadata(book.id).writeText(json.toString())
            val evidenceBytes = metadata(book.id).readBytes()

            assertTrue("$label must refuse reconstruction", runCatching { reopen(book.id) }.isFailure)
            assertTrue("$label must refuse loading", runCatching { WorkbookStore(root).load(book.id) }.isFailure)
            assertTrue("$label must refuse direct store save", runCatching {
                WorkbookStore(root).save(before.document.copy(name = "Must not repair corruption"))
            }.isFailure)
            assertTrue("$label must refuse typed save", runCatching {
                coordinator.execute(rename(before.document, "after-corruption-$index", "Must not publish"))
            }.isFailure)
            assertTrue("$label must refuse undo", runCatching { coordinator.undo(before.document.revision) }.isFailure)
            assertTrue("$label must refuse redo", runCatching { coordinator.redo(before.document.revision) }.isFailure)
            assertTrue("$label must refuse reload", runCatching { coordinator.reload() }.isFailure)
            assertEquals("$label must not publish a replacement state", before, coordinator.state.value)
            assertArrayEquals("$label bytes are diagnosis evidence", evidenceBytes, metadata(book.id).readBytes())
        }
    }

    @Test
    fun legacyVersionsMigrateOnFirstAcceptedWriteAndPreserveExactOriginalBytesOnce() = runBlocking {
        listOf(1, 2).forEach { legacyVersion ->
            val book = store.create("Legacy $legacyVersion")
            val legacy = JSONObject(metadata(book.id).readText())
                .put("formatVersion", legacyVersion)
                .put("unknownOriginalEvidence", "Retain verbatim in backup")
            legacy.remove("commandJournal")
            val sheet = legacy.getJSONArray("worksheets").getJSONObject(0)
            if (legacyVersion == 1) {
                sheet.remove("chart")
                sheet.put("chartTitle", "Legacy title")
                sheet.put("showGridlines", false)
                sheet.put("showValueLabels", true)
            } else {
                sheet.put("chart", ChartOptions(title = "Legacy title", showGridlines = false, showValueLabels = true).toJson())
            }
            // Deliberate whitespace and unknown content detect a reserialized rather than exact backup.
            metadata(book.id).writeText(legacy.toString(2) + "\n")
            val originalBytes = metadata(book.id).readBytes()
            val backup = File(store.workbookDir(book.id), "migration/workbook-v$legacyVersion-original.json")
            var coordinator = reopen(book.id)
            assertFalse("read-only migration must not write a backup", backup.exists())
            assertArrayEquals(originalBytes, metadata(book.id).readBytes())
            val normalized = coordinator.state.value.document
            assertEquals(3, normalized.formatVersion)
            assertEquals("Legacy title", normalized.worksheets.single().chart.title)
            assertFalse(normalized.worksheets.single().chart.showGridlines)
            assertTrue(normalized.worksheets.single().chart.showValueLabels)
            val accepted = coordinator.execute(rename(normalized, "migrate-$legacyVersion", "Migrated $legacyVersion")).document
            assertEquals(book.revision + 1, accepted.revision)
            assertEquals(3, JSONObject(metadata(book.id).readText()).getInt("formatVersion"))
            assertEquals(1, JSONObject(metadata(book.id).readText()).getJSONObject("commandJournal").getInt("version"))
            assertArrayEquals("original v$legacyVersion bytes must remain exact", originalBytes, backup.readBytes())

            coordinator = reopen(book.id)
            val second = coordinator.execute(rename(accepted, "second-$legacyVersion", "Second saved title")).document
            assertArrayEquals("later saves must not overwrite the original", originalBytes, backup.readBytes())
            coordinator = reopen(book.id)
            val undone = coordinator.undo(second.revision)
            assertEquals("Migrated $legacyVersion", undone.name)
            coordinator = reopen(book.id)
            val originalRestored = coordinator.undo(undone.revision)
            assertEquals("Legacy $legacyVersion", originalRestored.name)
            assertEquals("Legacy title", originalRestored.worksheets.single().chart.title)
            assertEquals(book.revision + 4, originalRestored.revision)
            assertArrayEquals(originalBytes, backup.readBytes())
        }
    }

    @Test
    fun fiftyEntryHistoryBoundaryDoesNotEvictTheOriginalIdempotencyReceipt() = runBlocking {
        val book = store.create("Book")
        var coordinator = reopen(book.id)
        val originalCommand = rename(book, "oldest-typed-command", "First accepted name")
        val first = coordinator.execute(originalCommand).document
        repeat(50) { index ->
            coordinator.edit(coordinator.state.value.document.revision) { it.copy(name = "Step ${index + 1}") }
        }
        coordinator = reopen(book.id)
        assertEquals("Step 50", coordinator.state.value.document.name)
        assertEquals(book.revision + 51, coordinator.state.value.document.revision)
        repeat(50) {
            assertTrue(coordinator.state.value.canUndo)
            coordinator.undo(coordinator.state.value.document.revision)
        }
        coordinator = reopen(book.id)
        val boundary = coordinator.state.value.document
        assertEquals("First accepted name", boundary.name)
        assertEquals(book.revision + 101, boundary.revision)
        assertFalse(coordinator.state.value.canUndo)
        assertTrue(coordinator.state.value.canRedo)
        val before = metadata(book.id).readBytes()
        assertEquals(boundary, coordinator.undo(boundary.revision))
        val retry = coordinator.execute(originalCommand)
        assertTrue(retry.duplicate)
        assertEquals(first.revision, retry.acceptedRevision)
        assertEquals(boundary, retry.document)
        assertArrayEquals(before, metadata(book.id).readBytes())
    }

    @Test
    fun postReplacementAcknowledgementErrorReturnsProvenAcceptanceAndRetryCreatesNoRevision() = runBlocking {
        val observing = AtomicBoolean(false)
        val armed = AtomicBoolean(false)
        val replacements = AtomicInteger()
        val acknowledgementErrors = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get() && phase == WorkbookCommitPhase.REPLACED) {
                replacements.incrementAndGet()
                if (armed.compareAndSet(true, false)) {
                    acknowledgementErrors.incrementAndGet()
                    throw IOException("One-shot acknowledgement loss after the real atomic replacement")
                }
            }
        }
        val book = hookedStore.create("Book")
        val coordinator = hookedStore.coordinators.open(book.id)
        val command = rename(book, "lost-acknowledgement", "Accepted despite lost acknowledgement")
        observing.set(true)
        armed.set(true)
        val accepted = coordinator.execute(command)
        assertEquals(1, acknowledgementErrors.get())
        assertEquals(1, replacements.get())
        assertTrue("acceptance was proven from the reconciled durable receipt", accepted.duplicate)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        assertBook(accepted.document, "Accepted despite lost acknowledgement", ChartOptions(), book.revision + 1)
        assertEquals(accepted.document, coordinator.state.value.document)
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(listOf(book), checkpoint.undo)
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(command, checkpoint.receipts.single().command)
        assertEquals(book.revision + 1, checkpoint.receipts.single().acceptedRevision)
        val before = metadata(book.id).readBytes()

        val immediateRetry = coordinator.execute(command)
        val reopenedRetry = reopen(book.id).execute(command)
        listOf(immediateRetry, reopenedRetry).forEach { retry ->
            assertTrue(retry.duplicate)
            assertEquals(book.revision + 1, retry.acceptedRevision)
            assertEquals(accepted.document, retry.document)
        }
        assertEquals("retries must not call the real writer again", 1, replacements.get())
        assertEquals(1, acknowledgementErrors.get())
        assertArrayEquals(before, metadata(book.id).readBytes())
        assertEquals("Book", reopen(book.id).undo(accepted.document.revision).name)
    }

    @Test
    fun immediateSameViewRenameAndChartRemainOrderedAfterFirstReplacementLosesAcknowledgement() = runBlocking {
        val observing = AtomicBoolean(false)
        val armed = AtomicBoolean(false)
        val writerRelease = CountDownLatch(1)
        val temporarySyncs = AtomicInteger()
        val replacements = AtomicInteger()
        val acknowledgementErrors = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> {
                        temporarySyncs.incrementAndGet()
                        if (armed.get()) {
                            check(writerRelease.await(10, TimeUnit.SECONDS)) {
                                "The immediate chart command did not release the real rename write."
                            }
                        }
                    }
                    WorkbookCommitPhase.REPLACED -> {
                        replacements.incrementAndGet()
                        if (armed.compareAndSet(true, false)) {
                            acknowledgementErrors.incrementAndGet()
                            throw IOException("One-shot acknowledgement loss for the view's first accepted command")
                        }
                    }
                }
            }
        }
        val book = hookedStore.create("Book")
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        lateinit var model: WorkbookViewModel
        try {
            onMain {
                model = ViewModelProvider(
                    owner,
                    WorkbookViewModelFactory(context.applicationContext as Application, hookedStore, book.id),
                )[WorkbookViewModel::class.java]
            }
            withTimeout(20_000) {
                val loaded = model.state.first { !it.loading }
                assertEquals(null, loaded.loadError)
                assertEquals(book.revision, loaded.document!!.revision)
                val custom = ChartOptions(title = "Immediate chart", showGridlines = false, valueDecimals = 2)
                observing.set(true)
                armed.set(true)
                try {
                    onMain {
                        model.renameWorkbook("Immediate rename")
                        assertEquals("both immediate calls must observe the original revision", book.revision, model.state.value.document!!.revision)
                        model.updateChartSettings(custom)
                    }
                } finally {
                    writerRelease.countDown()
                }
                val accepted = model.state.first {
                    it.document?.name == "Immediate rename" &&
                        it.document.activeWorksheet?.chart == custom && it.save is SaveState.Saved
                }.document!!
                assertBook(accepted, "Immediate rename", custom, book.revision + 2)
                assertEquals(1, acknowledgementErrors.get())
                assertEquals(2, temporarySyncs.get())
                assertEquals(2, replacements.get())

                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(2, checkpoint.receipts.size)
                val renameReceipt = checkpoint.receipts[0]
                val chartReceipt = checkpoint.receipts[1]
                assertTrue(renameReceipt.command is WorkbookCommand.Rename)
                assertTrue(chartReceipt.command is WorkbookCommand.SetChart)
                assertNotEquals(renameReceipt.command.commandId, chartReceipt.command.commandId)
                assertEquals(renameReceipt.command.originWindowId, chartReceipt.command.originWindowId)
                assertEquals(book.revision, renameReceipt.command.expectedRevision)
                assertEquals(book.revision + 1, renameReceipt.acceptedRevision)
                assertEquals("the queued chart follows exactly this view's own accepted revision", book.revision + 1, chartReceipt.command.expectedRevision)
                assertEquals(book.revision + 2, chartReceipt.acceptedRevision)
                assertEquals(listOf("Book", "Immediate rename"), checkpoint.undo.map { it.name })
                assertEquals(listOf(ChartOptions(), ChartOptions()), checkpoint.undo.map { it.worksheets.single().chart })

                val beforeSave = metadata(book.id).readBytes()
                onMain { model.saveNow() }
                assertArrayEquals("Save must not retry an already acknowledged command", beforeSave, metadata(book.id).readBytes())
                assertEquals(2, replacements.get())
                val reopened = reopen(book.id)
                val chartUndone = reopened.undo(accepted.revision)
                assertBook(chartUndone, "Immediate rename", ChartOptions(), book.revision + 3)
                val renameUndone = reopen(book.id).undo(chartUndone.revision)
                assertBook(renameUndone, "Book", ChartOptions(), book.revision + 4)
            }
        } finally {
            writerRelease.countDown()
            onMain { owner.viewModelStore.clear() }
        }
    }

    private suspend fun reopen(workbookId: String): WorkbookCoordinator =
        WorkbookStore(root).coordinators.open(workbookId)

    private fun rename(document: WorkbookDocument, id: String, name: String) = WorkbookCommand.Rename(
        commandId = id,
        workbookId = document.id,
        originWindowId = "rename-window",
        expectedRevision = document.revision,
        name = name,
    )

    private fun assertBook(document: WorkbookDocument, name: String, chart: ChartOptions, revision: Long) {
        assertEquals(name, document.name)
        assertEquals(chart, document.worksheets.single().chart)
        assertEquals(revision, document.revision)
        assertEquals(document, WorkbookStore(root).load(document.id))
    }

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")

    private fun csv(name: String, text: String) = File(root, "$name.csv").apply { writeText(text) }

    private fun snapshotCountAndSum(document: WorkbookDocument): List<Long> {
        val path = store.resolveInsideWorkbook(document.id, document.snapshot!!.snapshotFile)
        return DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1).use { engine ->
            engine.execute(
                "SELECT COUNT(*) AS n, CAST(SUM(Amount) AS BIGINT) AS total FROM read_parquet(${Sql.string(path.absolutePath)})",
                maxRows = 1,
            ).rows.single().map { it as Long }
        }
    }
}
