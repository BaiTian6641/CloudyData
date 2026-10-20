package io.github.baitian6641.cloudydata

import android.app.Application
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.system.Os
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.ImportPhase
import io.github.baitian6641.cloudydata.data.ImportReceipt
import io.github.baitian6641.cloudydata.data.ReceiptFiles
import io.github.baitian6641.cloudydata.data.WorkbookConflictException
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.ui.WorkbookViewModel
import io.github.baitian6641.cloudydata.ui.WorkbookViewModelFactory
import io.github.baitian6641.cloudydata.ui.SaveState
import io.github.baitian6641.cloudydata.ui.WorksheetQueryState
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android JSON/filesystem and native-engine coverage for S03.2 / Q16, Q19, Q34. */
@RunWith(AndroidJUnit4::class)
class WorkbookCoordinatorTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "coordinator-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun createReturnsDurableRevisionAndSeparateStoreHandleRejectsStaleWrite() {
        val original = store.create("Original")
        assertEquals(original, store.load(original.id))
        val otherHandle = WorkbookStore(root)
        val saved = store.save(original.copy(name = "Accepted"))
        assertEquals(original.revision + 1, saved.revision)

        val error = runCatching {
            otherHandle.save(original.copy(name = "Must not overwrite accepted work"))
        }.exceptionOrNull()

        assertTrue("stale store save must return an explicit conflict: $error", error is WorkbookConflictException)
        error as WorkbookConflictException
        assertEquals(original.revision, error.expectedRevision)
        assertEquals(saved.revision, error.actualRevision)
        assertEquals(saved, otherHandle.load(original.id))
        assertEquals("Accepted", otherHandle.load(original.id).name)
    }

    @Test
    fun registrySharesWorkbookIdentityAndKeepsOtherWorkbookIndependent() = runBlocking {
        val first = store.create("First book")
        val other = store.create("Other book")
        val windowOne = store.coordinators.open(first.id)
        val windowTwo = store.coordinators.open(first.id)
        val otherWindow = store.coordinators.open(other.id)
        assertSame(windowOne, windowTwo)
        assertNotSame(windowOne, otherWindow)

        val saved = windowOne.edit(first.revision) { document ->
            document.copy(
                name = "Shared accepted title",
                worksheets = listOf(document.worksheets.single().copy(chart = ChartOptions(title = "Revenue"))),
            )
        }

        assertEquals("Shared accepted title", windowTwo.state.value.document.name)
        assertEquals("Revenue", windowTwo.state.value.document.worksheets.single().chart.title)
        assertEquals(first.revision + 1, saved.revision)
        assertEquals(saved, WorkbookStore(root).load(first.id))
        assertEquals(other, otherWindow.state.value.document)
        assertFalse(otherWindow.state.value.canUndo)
        assertFalse(otherWindow.state.value.canRedo)
        assertEquals(other, store.load(other.id))
    }

    @Test
    fun simultaneousSameRevisionCommandsAcceptExactlyOneAndRejectBeforeTransform() = runBlocking {
        val original = store.create("Before race")
        val coordinator = store.coordinators.open(original.id)
        val start = CompletableDeferred<Unit>()
        val transformCalls = AtomicInteger()
        val attempts = listOf("Alpha winner", "Beta winner").map { name ->
            async(Dispatchers.Default) {
                start.await()
                runCatching {
                    coordinator.edit(original.revision) {
                        transformCalls.incrementAndGet()
                        it.copy(name = name)
                    }
                }
            }
        }
        start.complete(Unit)
        val results = attempts.map { it.await() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, transformCalls.get())
        val accepted = results.single { it.isSuccess }.getOrThrow()
        assertTrue(accepted.name in setOf("Alpha winner", "Beta winner"))
        assertEquals(original.revision + 1, accepted.revision)
        val conflict = results.single { it.isFailure }.exceptionOrNull()
        assertTrue("loser must receive the durable winner revision: $conflict", conflict is WorkbookConflictException)
        conflict as WorkbookConflictException
        assertEquals(original.revision, conflict.expectedRevision)
        assertEquals(accepted.revision, conflict.actualRevision)
        assertEquals(accepted, coordinator.state.value.document)
        assertEquals(accepted, store.load(original.id))

        coordinator.undo(accepted.revision)
        assertEquals("Before race", coordinator.state.value.document.name)
        assertFalse(coordinator.state.value.canUndo)
        assertTrue(coordinator.state.value.canRedo)
        assertEquals(original.revision + 2, store.load(original.id).revision)
    }

    @Test
    fun undoRedoPersistFixedStatesWithStrictlyIncreasingRevisionsAndBranchDropsRedo() = runBlocking {
        val original = store.create("Book")
        val coordinator = store.coordinators.open(original.id)
        val first = coordinator.edit(original.revision) { it.copy(name = "First") }
        val second = coordinator.edit(first.revision) { it.copy(name = "Second", favorite = true) }
        assertEquals(original.revision + 2, second.revision)

        val expected = listOf(
            Triple("First", false, true),
            Triple("Book", false, false),
            Triple("First", false, true),
            Triple("Second", true, true),
        )
        expected.forEachIndexed { index, (name, favorite, canUndo) ->
            val current = coordinator.state.value.document
            if (index < 2) coordinator.undo(current.revision) else coordinator.redo(current.revision)
            val state = coordinator.state.value
            assertEquals(name, state.document.name)
            assertEquals(favorite, state.document.favorite)
            assertEquals(canUndo, state.canUndo)
            assertEquals(index < 3, state.canRedo)
            assertEquals(original.revision + 3 + index, state.document.revision)
            assertEquals(state.document, store.load(original.id))
        }

        coordinator.undo(coordinator.state.value.document.revision)
        coordinator.edit(coordinator.state.value.document.revision) { it.copy(name = "Branch") }
        val branch = coordinator.state.value.document
        assertFalse(coordinator.state.value.canRedo)
        coordinator.redo(branch.revision)
        assertEquals(branch, coordinator.state.value.document)
        assertEquals("Branch", WorkbookStore(root).load(original.id).name)
    }

    @Test
    fun failedAtomicWritePublishesNothingAndPreservesBothHistoryDirections() = runBlocking {
        val original = store.create("Book")
        val coordinator = store.coordinators.open(original.id)
        val first = coordinator.edit(original.revision) { it.copy(name = "First") }
        val second = coordinator.edit(first.revision) { it.copy(name = "Second") }
        coordinator.undo(second.revision)
        val before = coordinator.state.value
        assertEquals("First", before.document.name)
        assertTrue(before.canUndo)
        assertTrue(before.canRedo)
        val observed = mutableListOf(before)
        val observer = launch(Dispatchers.Unconfined) {
            coordinator.state.collect { state ->
                if (state != observed.last()) observed += state
            }
        }
        try {
            val directory = store.workbookDir(original.id)
            val originalMode = Os.stat(directory.path).st_mode and 0x1ff
            val error = try {
                // Keep workbook.json readable; deny creation of atomicWrite's temporary file.
                Os.chmod(directory.path, 0x16d) // 0555
                assertEquals(before.document, store.load(original.id))
                runCatching {
                    coordinator.edit(before.document.revision) { it.copy(name = "Rejected write") }
                }.exceptionOrNull()
            } finally {
                Os.chmod(directory.path, originalMode)
            }
            assertTrue("a real denied filesystem write must fail", error != null)
            assertEquals(before, coordinator.state.value)
            assertEquals(listOf(before), observed)
            assertEquals(before.document, store.load(original.id))

            coordinator.redo(before.document.revision)
            assertEquals("Second", coordinator.state.value.document.name)
            assertEquals(before.document.revision + 1, coordinator.state.value.document.revision)
            assertFalse(coordinator.state.value.canRedo)
            coordinator.undo(coordinator.state.value.document.revision)
            coordinator.undo(coordinator.state.value.document.revision)
            assertEquals("Book", coordinator.state.value.document.name)
            assertFalse(coordinator.state.value.canUndo)
            assertEquals(coordinator.state.value.document, store.load(original.id))
        } finally {
            observer.cancel()
        }
    }

    @Test
    fun realImportSerializesEditsPublishesOnceAndDoesNotReplaySourceEffects() = runBlocking {
        val original = store.create("Import book")
        val coordinator = store.coordinators.open(original.id)
        val edited = coordinator.edit(original.revision) { it.copy(name = "Before import") }
        val importer = CsvImportService(context, store)
        val csv = csv("first", "Category,Amount\nEast,10\nWest,20\nEast,5\n")
        val pending = importer.stageAndInspect(edited, Uri.fromFile(csv))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sourceOperationCalls = AtomicInteger()
        val importing = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.importSnapshot(edited.revision) { current ->
                sourceOperationCalls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                importer.apply(current, pending)
            }
        }
        entered.await()
        val conflictingEdit = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { coordinator.edit(edited.revision) { it.copy(name = "Must not overwrite import") } }
        }
        assertFalse(conflictingEdit.isCompleted)
        assertEquals(edited, coordinator.state.value.document)
        release.complete(Unit)
        val imported = importing.await()
        val conflict = conflictingEdit.await().exceptionOrNull()
        assertTrue(conflict is WorkbookConflictException)
        assertEquals(edited.revision + 1, imported.revision)
        assertEquals("Before import", imported.name)
        assertEquals(3L, imported.snapshot!!.rowCount)
        assertEquals(listOf("Category", "Amount"), imported.snapshot.columns.map { it.name })
        assertEquals(imported, coordinator.state.value.document)
        assertEquals(imported, store.load(original.id))
        assertEquals(csv.readText(), store.resolveInsideWorkbook(imported.id, imported.snapshot.originalFile!!).readText())
        assertEquals(listOf(3L, 35L), snapshotCountAndSum(imported))
        assertFalse(coordinator.state.value.canUndo)
        assertFalse(coordinator.state.value.canRedo)

        coordinator.undo(imported.revision)
        coordinator.redo(imported.revision)
        assertEquals(imported, coordinator.state.value.document)
        assertEquals(1, sourceOperationCalls.get())
        assertEquals(listOf(3L, 35L), snapshotCountAndSum(imported))
    }

    @Test
    fun inFlightSnapshotReadFinishesBeforeReplacementAndStaleSnapshotIsRefused() = runBlocking {
        val original = store.create("Snapshot ownership")
        val coordinator = store.coordinators.open(original.id)
        val importer = CsvImportService(context, store)
        val firstPending = importer.stageAndInspect(original, Uri.fromFile(csv("old", "Category,Amount\nEast,10\nWest,25\n")))
        val first = coordinator.importSnapshot(original.revision) { importer.apply(it, firstPending) }
        val secondPending = importer.stageAndInspect(first, Uri.fromFile(csv("new", "Category,Amount\nNew,7\n")))
        val enteredRead = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        var replacementStarted = false
        val reading = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.readSnapshot(first.snapshot!!.id) {
                enteredRead.complete(Unit)
                releaseRead.await()
                snapshotCountAndSum(first)
            }
        }
        enteredRead.await()
        val replacing = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.importSnapshot(first.revision) {
                replacementStarted = true
                importer.apply(it, secondPending)
            }
        }
        assertFalse(replacementStarted)
        assertFalse(replacing.isCompleted)
        releaseRead.complete(Unit)
        assertEquals(listOf(2L, 35L), reading.await())
        val replaced = replacing.await()
        assertEquals(listOf(1L, 7L), coordinator.readSnapshot(replaced.snapshot!!.id) { snapshotCountAndSum(replaced) })
        var staleOperationRan = false
        val staleError = runCatching {
            coordinator.readSnapshot(first.snapshot!!.id) { staleOperationRan = true }
        }.exceptionOrNull()
        assertTrue("old snapshot identity must be refused after replacement", staleError != null)
        assertFalse(staleOperationRan)
        assertEquals(replaced, store.load(original.id))
    }

    @Test
    fun cancellationAfterRealImportSaveStillPublishesDurableSnapshotAndAcceptsNextEdit() = runBlocking {
        val original = store.create("Cancelled import caller")
        val coordinator = store.coordinators.open(original.id)
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(
            original,
            Uri.fromFile(csv("cancel", "Category,Amount\nEast,10\nWest,20\nEast,5\n")),
        )
        val metadataSaved = CompletableDeferred<WorkbookDocument>()
        val releaseOperation = CompletableDeferred<Unit>()
        val importing = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.importSnapshot(original.revision) { current ->
                val saved = importer.apply(current, pending)
                metadataSaved.complete(saved)
                // The files and metadata are durable, but the coordinator has not received the result.
                releaseOperation.await()
                saved
            }
        }
        val saved = withTimeout(15_000) { metadataSaved.await() }
        assertEquals(saved, store.load(original.id))
        importing.cancel()
        releaseOperation.complete(Unit)
        withTimeout(15_000) { importing.join() }

        assertTrue(importing.isCancelled)
        assertEquals(saved, coordinator.state.value.document)
        assertEquals(listOf(3L, 35L), snapshotCountAndSum(coordinator.state.value.document))
        assertFalse(coordinator.state.value.canUndo)
        assertFalse(coordinator.state.value.canRedo)
        val next = coordinator.edit(saved.revision) { it.copy(name = "Edit after cancelled caller") }
        assertEquals("Edit after cancelled caller", next.name)
        assertEquals(saved.revision + 1, next.revision)
        assertEquals(next, store.load(original.id))
        assertEquals(saved.snapshot, next.snapshot)
    }

    @Test
    fun exceptionAfterRealReplacementSaveReconcilesAuthorityBeforeNextCommand() = runBlocking {
        val original = store.create("Import exception boundary")
        val coordinator = store.coordinators.open(original.id)
        val importer = CsvImportService(context, store)
        val firstPending = importer.stageAndInspect(
            original,
            Uri.fromFile(csv("exception-old", "Category,Amount\nEast,10\nWest,20\nEast,5\n")),
        )
        val first = coordinator.importSnapshot(original.revision) { importer.apply(it, firstPending) }
        val edited = coordinator.edit(first.revision) { it.copy(name = "Retained before replacement") }
        val replacementPending = importer.stageAndInspect(
            edited,
            Uri.fromFile(csv("exception-new", "Category,Amount\nNew,7\n")),
        )
        var durableReplacement: WorkbookDocument? = null
        val error = runCatching {
            coordinator.importSnapshot(edited.revision) { current ->
                durableReplacement = importer.apply(current, replacementPending)
                // Inject a callback/finalization failure after the real importer has saved all artifacts.
                error("Failure after durable replacement")
            }
        }.exceptionOrNull()
        assertEquals("Failure after durable replacement", error?.message)
        val saved = durableReplacement!!
        assertEquals(edited.revision + 1, saved.revision)
        assertEquals(saved, store.load(original.id))
        assertEquals(saved, coordinator.state.value.document)
        assertEquals(listOf(1L, 7L), snapshotCountAndSum(saved))
        assertFalse(coordinator.state.value.canUndo)
        assertFalse(coordinator.state.value.canRedo)
        var oldReadRan = false
        val oldReadError = runCatching {
            coordinator.readSnapshot(first.snapshot!!.id) { oldReadRan = true }
        }.exceptionOrNull()
        assertTrue(oldReadError != null)
        assertFalse(oldReadRan)
        val next = coordinator.edit(saved.revision) { it.copy(name = "Accepted after reconciliation") }
        assertEquals("Accepted after reconciliation", next.name)
        assertEquals(saved.revision + 1, next.revision)
        assertEquals(next, store.load(original.id))
        assertEquals(saved.snapshot, next.snapshot)
        assertEquals(listOf(1L, 7L), snapshotCountAndSum(next))
    }

    @Test
    fun retainedRecoveryReceiptBlocksOpenCoordinatorUntilRealPreviousSnapshotIsRestored() = runBlocking {
        val original = store.create("Retained recovery")
        val importer = CsvImportService(context, store)
        val oldPending = importer.stageAndInspect(
            original,
            Uri.fromFile(csv("retained-old", "Category,Amount\nEast,10\nWest,20\nEast,5\n")),
        )
        // Import before the registry opens, then construct an interrupted replacement on disk.
        val accepted = importer.apply(original, oldPending)
        val acceptedParquet = store.resolveInsideWorkbook(accepted.id, accepted.snapshot!!.snapshotFile)
        val previousParquetBytes = acceptedParquet.readBytes()
        val previousOriginalBytes = store.resolveInsideWorkbook(accepted.id, accepted.snapshot.originalFile!!).readBytes()
        val other = store.create("Real replacement data")
        val replacementPending = importer.stageAndInspect(
            other,
            Uri.fromFile(csv("retained-new", "Category,Amount\nNew,7\n")),
        )
        val replacement = importer.apply(other, replacementPending)
        val replacementBytes = store.resolveInsideWorkbook(replacement.id, replacement.snapshot!!.snapshotFile).readBytes()
        acceptedParquet.writeBytes(replacementBytes)
        val operationId = "retained-missing-backup"
        val operationDir = File(store.workbookDir(accepted.id), "staging/$operationId").apply { mkdirs() }
        ReceiptFiles.write(
            operationDir,
            ImportReceipt(
                operationId = operationId,
                workbookId = accepted.id,
                phase = ImportPhase.DataCommitted,
                proposedSnapshotId = replacement.snapshot.id,
                displayName = "Replacement without previous backup",
                rowCount = 1,
                stagedBytes = replacementBytes.size.toLong(),
                stagedSha256 = ReceiptFiles.sha256(acceptedParquet),
                hadPreviousSnapshot = true,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        assertEquals(accepted, store.load(accepted.id))
        assertEquals(listOf(1L, 7L), snapshotCountAndSum(accepted))
        val coordinator = store.coordinators.open(accepted.id)
        assertEquals(accepted, coordinator.state.value.document)
        var readRan = false
        val readError = runCatching {
            coordinator.readSnapshot(accepted.snapshot.id) { readRan = true }
        }.exceptionOrNull()
        assertTrue("retained recovery must refuse the read", readError != null)
        assertFalse(readRan)
        var editRan = false
        val editError = runCatching {
            coordinator.edit(accepted.revision) {
                editRan = true
                it.copy(name = "Must not accept unresolved recovery")
            }
        }.exceptionOrNull()
        assertTrue("retained recovery must refuse the edit", editError != null)
        assertFalse(editRan)
        assertTrue(operationDir.exists())
        assertEquals(accepted, store.load(accepted.id))

        val reloadError = runCatching { coordinator.reload() }.exceptionOrNull()
        assertTrue("reload must report the still-missing backup", reloadError != null)
        assertTrue(operationDir.exists())
        assertTrue(runCatching {
            coordinator.readSnapshot(accepted.snapshot.id) { readRan = true }
        }.isFailure)
        assertFalse(readRan)
        assertTrue(runCatching {
            coordinator.edit(accepted.revision) {
                editRan = true
                it.copy(name = "Still blocked")
            }
        }.isFailure)
        assertFalse(editRan)

        val previousDir = File(operationDir, "previous").apply { mkdirs() }
        File(previousDir, "snapshot.parquet").writeBytes(previousParquetBytes)
        File(previousDir, "original.csv").writeBytes(previousOriginalBytes)
        assertEquals(accepted, coordinator.reload())
        assertFalse(operationDir.exists())
        assertEquals(accepted, coordinator.state.value.document)
        assertEquals(listOf(3L, 35L), coordinator.readSnapshot(accepted.snapshot.id) { snapshotCountAndSum(accepted) })
        val next = coordinator.edit(accepted.revision) { it.copy(name = "Recovered writer") }
        assertEquals(accepted.revision + 1, next.revision)
        assertEquals("Recovered writer", next.name)
        assertEquals(next, store.load(accepted.id))
    }

    @Test
    fun immediateSameViewCommandsQueueInOrderUntilSnapshotReaderReleasesWriter() = runBlocking {
        val original = store.create("Before rapid edits")
        val coordinator = store.coordinators.open(original.id)
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(
            original,
            Uri.fromFile(csv("rapid", "Category,Amount\nEast,10\nWest,20\nEast,5\n")),
        )
        val imported = coordinator.importSnapshot(original.revision) { importer.apply(it, pending) }
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        lateinit var model: WorkbookViewModel
        fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        val releaseReader = CompletableDeferred<Unit>()
        try {
            onMain {
                model = ViewModelProvider(
                    owner,
                    WorkbookViewModelFactory(context.applicationContext as Application, store, original.id),
                )[WorkbookViewModel::class.java]
            }
            withTimeout(15_000) {
                model.state.first { !it.loading && it.query is WorksheetQueryState.Ready }
                val readerEntered = CompletableDeferred<Unit>()
                val reading = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.readSnapshot(imported.snapshot!!.id) {
                        readerEntered.complete(Unit)
                        releaseReader.await()
                        snapshotCountAndSum(imported)
                    }
                }
                readerEntered.await()
                val options = ChartOptions(title = "Rapid chart", showGridlines = false, valueDecimals = 2)
                onMain {
                    model.renameWorkbook("Rapid title")
                    model.updateChartSettings(options)
                }
                assertEquals("Before rapid edits", coordinator.state.value.document.name)
                assertEquals(imported.revision, store.load(original.id).revision)
                releaseReader.complete(Unit)
                assertEquals(listOf(3L, 35L), reading.await())
                val accepted = model.state.first {
                    it.document?.name == "Rapid title" &&
                        it.document.activeWorksheet?.chart == options && it.save is SaveState.Saved
                }.document!!
                assertEquals(imported.revision + 2, accepted.revision)
                assertEquals("Rapid title", accepted.name)
                assertEquals(options, accepted.activeWorksheet!!.chart)
                assertEquals(accepted, store.load(original.id))
                assertEquals(listOf(3L, 35L), snapshotCountAndSum(accepted))
                onMain { model.undo() }
                val undone = model.state.first {
                    it.document?.revision == imported.revision + 3 && it.save is SaveState.Saved
                }.document!!
                assertEquals("Rapid title", undone.name)
                assertEquals(ChartOptions(), undone.activeWorksheet!!.chart)
            }
        } finally {
            releaseReader.complete(Unit)
            onMain { owner.viewModelStore.clear() }
        }
    }

    @Test
    fun addedSheetBecomesActiveOnlyInInitiatingViewAndTabSelectionCreatesNoRevision() = runBlocking {
        val original = store.create("Local tabs")
        val owners = List(2) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        val models = mutableListOf<WorkbookViewModel>()
        fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        try {
            onMain {
                owners.forEach { owner ->
                    models += ViewModelProvider(
                        owner,
                        WorkbookViewModelFactory(context.applicationContext as Application, store, original.id),
                    )[WorkbookViewModel::class.java]
                }
            }
            withTimeout(15_000) {
                models.forEach { model ->
                    val loaded = model.state.first { !it.loading }
                    assertEquals(null, loaded.loadError)
                }
                onMain { models[0].addWorksheet() }
                val initiating = models[0].state.first {
                    it.document?.worksheets?.size == 2 &&
                        it.document.activeWorksheetId != original.activeWorksheetId && it.save is SaveState.Saved
                }.document!!
                models[1].state.first { it.document?.worksheets?.size == 2 }
                val addedId = initiating.worksheets.single { it.id != original.activeWorksheetId }.id
                assertEquals(addedId, initiating.activeWorksheetId)
                assertEquals(original.activeWorksheetId, models[1].state.value.document!!.activeWorksheetId)
                assertEquals(original.revision + 1, initiating.revision)
                assertEquals(original.activeWorksheetId, store.load(original.id).activeWorksheetId)

                onMain { models[0].selectWorksheet(original.activeWorksheetId!!) }
                onMain { models[1].selectWorksheet(addedId) }
                assertEquals(original.activeWorksheetId, models[0].state.value.document!!.activeWorksheetId)
                assertEquals(addedId, models[1].state.value.document!!.activeWorksheetId)
                assertEquals(initiating.revision, models[0].state.value.document!!.revision)
                assertEquals(initiating.revision, models[1].state.value.document!!.revision)
                assertEquals(initiating.revision, store.load(original.id).revision)
                assertEquals(original.activeWorksheetId, store.load(original.id).activeWorksheetId)
                onMain { owners[0].viewModelStore.clear() }
                assertEquals(2, models[1].state.value.document!!.worksheets.size)
                assertEquals(2, store.load(original.id).worksheets.size)
            }
        } finally {
            onMain { owners.forEach { it.viewModelStore.clear() } }
        }
    }

    @Test
    fun twoRealViewModelsObserveSharedEditUndoAndClosingOneRetainsDocument() = runBlocking {
        val original = store.create("Two windows")
        val other = store.create("Independent window")
        val owners = List(3) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        val models = mutableListOf<WorkbookViewModel>()
        fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        try {
            onMain {
                owners.forEachIndexed { index, owner ->
                    models += ViewModelProvider(
                        owner,
                        WorkbookViewModelFactory(
                            context.applicationContext as Application,
                            store,
                            if (index < 2) original.id else other.id,
                        ),
                    )[WorkbookViewModel::class.java]
                }
            }
            withTimeout(15_000) {
                models.forEach { model ->
                    val loaded = model.state.first { !it.loading }
                    assertEquals(null, loaded.loadError)
                }
                onMain { models[0].renameWorkbook("Shared ViewModel title") }
                models.take(2).forEach { model ->
                    model.state.first { it.document?.name == "Shared ViewModel title" && it.canUndo }
                }
                assertEquals("Independent window", models[2].state.value.document?.name)
                assertFalse(models[2].state.value.canUndo)
                assertEquals("Shared ViewModel title", store.load(original.id).name)

                onMain { owners[0].viewModelStore.clear() }
                onMain { models[1].undo() }
                models[1].state.first { it.document?.name == "Two windows" && it.canRedo }
                assertEquals("Two windows", store.load(original.id).name)
                assertEquals(1, store.load(original.id).worksheets.size)
                assertEquals(original.revision + 2, store.load(original.id).revision)
                assertEquals(other, store.load(other.id))
            }
        } finally {
            onMain { owners.forEach { it.viewModelStore.clear() } }
        }
    }

    @Test
    fun twoRealActivitiesShareSavedCommandsAndClosingOneRetainsOtherWindow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = context.applicationContext as CloudyDataApp
        val appStore = app.workbookStore
        val original = appStore.create("Two real Activities")
        var first: MainActivity? = null
        var second: MainActivity? = null
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun launch(bounds: Rect): MainActivity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(MainActivity.EXTRA_WORKBOOK_ID, original.id)
            },
            ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle(),
        ) as MainActivity
        try {
            first = launch(Rect(80, 80, 1280, 980))
            instrumentation.waitForIdleSync()
            second = launch(Rect(340, 160, 1540, 1060))
            instrumentation.waitForIdleSync()
            val firstActivity = checkNotNull(first)
            val secondActivity = checkNotNull(second)
            assertNotSame(firstActivity, secondActivity)
            assertNotEquals("each open window must have its own Android task", firstActivity.taskId, secondActivity.taskId)
            assertFalse(firstActivity.isFinishing)
            assertFalse(secondActivity.isFinishing)
            val firstBounds = firstActivity.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val secondBounds = secondActivity.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            Log.i(
                "CloudyCoordinatorProbe",
                "workbook=${original.id}; firstTask=${firstActivity.taskId}, bounds=$firstBounds; " +
                    "secondTask=${secondActivity.taskId}, bounds=$secondBounds",
            )
            assertTrue(firstBounds.width() > 0 && firstBounds.height() > 0)
            assertTrue(secondBounds.width() > 0 && secondBounds.height() > 0)
            lateinit var firstModel: WorkbookViewModel
            lateinit var secondModel: WorkbookViewModel
            onMain {
                val factory = WorkbookViewModelFactory(app, appStore, original.id)
                // WorkbookScreen uses this exact Compose ViewModel key in each Activity's store.
                firstModel = ViewModelProvider(firstActivity, factory).get(original.id, WorkbookViewModel::class.java)
                secondModel = ViewModelProvider(secondActivity, factory).get(original.id, WorkbookViewModel::class.java)
            }
            assertNotSame(firstModel, secondModel)
            withTimeout(20_000) {
                listOf(firstModel, secondModel).forEach { model ->
                    val loaded = model.state.first { !it.loading }
                    assertEquals(null, loaded.loadError)
                    assertEquals(original.id, loaded.document?.id)
                }
                onMain { firstModel.renameWorkbook("Shared Activity title") }
                listOf(firstModel, secondModel).forEach { model ->
                    model.state.first { it.document?.name == "Shared Activity title" && it.canUndo }
                }
                assertEquals("Shared Activity title", appStore.load(original.id).name)
                instrumentation.waitForIdleSync()
                assertTrue(
                    "the visible second Activity must render the accepted shared title before capture",
                    UiDevice.getInstance(instrumentation).wait(
                        Until.hasObject(By.text("Shared Activity title")),
                        5_000,
                    ),
                )
                val capture = instrumentation.uiAutomation.takeScreenshot()
                assertTrue("the second Activity needs an inspectable native capture", capture != null)
                val captureFile = File(context.filesDir, "coordinator-two-activities.png")
                capture!!.useBitmap { bitmap ->
                    captureFile.outputStream().use { output ->
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    Log.i("CloudyCoordinatorProbe", "capture=${captureFile.path}, pixels=${bitmap.width}x${bitmap.height}")
                }
                assertTrue(captureFile.length() > 0)

                onMain { secondModel.undo() }
                listOf(firstModel, secondModel).forEach { model ->
                    model.state.first { it.document?.name == "Two real Activities" && it.canRedo }
                }
                assertEquals(original.revision + 2, appStore.load(original.id).revision)
                assertTrue(
                    "the visible second Activity must render the shared undo result",
                    UiDevice.getInstance(instrumentation).wait(
                        Until.hasObject(By.text("Two real Activities")),
                        5_000,
                    ),
                )
                onMain { firstActivity.finishAndRemoveTask() }
                instrumentation.waitForIdleSync()
                assertTrue(firstActivity.isFinishing)
                assertFalse(secondActivity.isFinishing)
                assertFalse(secondActivity.isDestroyed)
                assertEquals(1, secondModel.state.value.document!!.worksheets.size)
                onMain { secondModel.renameWorkbook("Surviving Activity saved") }
                val accepted = secondModel.state.first {
                    it.document?.name == "Surviving Activity saved" && it.save is SaveState.Saved
                }.document!!
                assertEquals(original.revision + 3, accepted.revision)
                assertEquals(accepted, WorkbookStore(app.filesDir).load(original.id))
                assertEquals(original.worksheets.single().id, accepted.worksheets.single().id)
            }
        } finally {
            onMain {
                first?.takeUnless { it.isDestroyed }?.finishAndRemoveTask()
                second?.takeUnless { it.isDestroyed }?.finishAndRemoveTask()
            }
            instrumentation.waitForIdleSync()
            appStore.workbookDir(original.id).deleteRecursively()
        }
    }

    private fun csv(name: String, content: String): File = File(root, "$name.csv").apply { writeText(content) }

    private inline fun Bitmap.useBitmap(action: (Bitmap) -> Unit) {
        try {
            action(this)
        } finally {
            recycle()
        }
    }

    private fun snapshotCountAndSum(document: WorkbookDocument): List<Long> {
        val path = store.resolveInsideWorkbook(document.id, document.snapshot!!.snapshotFile)
        return DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1).use { engine ->
            val result = engine.execute(
                "SELECT COUNT(*) AS n, CAST(SUM(Amount) AS BIGINT) AS total FROM read_parquet(${Sql.string(path.absolutePath)})",
                maxRows = 1,
            )
            result.rows.single().map { it as Long }
        }
    }
}
