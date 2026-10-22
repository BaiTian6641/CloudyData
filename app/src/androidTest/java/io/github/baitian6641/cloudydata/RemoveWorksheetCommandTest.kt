package io.github.baitian6641.cloudydata

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.WorkbookCoordinator
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Native S02.7 RemoveWorksheet coordinator semantics: neighbor fallback, undo/redo, retry, and byte oracles. */
@RunWith(AndroidJUnit4::class)
class RemoveWorksheetCommandTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "remove-worksheet-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun removeSelectedLastSheetSelectsPreviousNeighborAndSurvivesUndoRedoRetry() = runBlocking {
        val (coordinator, book) = threeSheetBook(activeName = "West")
        val west = book.worksheets.single { it.name == "West" }
        val east = book.worksheets.single { it.name == "East" }
        val command = remove(book, "remove-west", west.id)
        val before = metadata(book.id).readBytes()
        val accepted = coordinator.execute(command)
        assertFalse(accepted.duplicate)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        assertEquals(book.worksheets.filterNot { it.id == west.id }, accepted.document.worksheets)
        assertEquals(east.id, accepted.document.activeWorksheetId)
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(accepted.document, checkpoint.document)
        assertEquals(book, checkpoint.undo.last())
        assertTrue(checkpoint.redo.isEmpty())
        assertEquals(command, checkpoint.receipts.last().command)
        assertEquals(book.revision + 1, checkpoint.receipts.last().acceptedRevision)
        assertFalse(before.contentEquals(metadata(book.id).readBytes()))

        val undone = reopen(book.id).undo(accepted.document.revision)
        assertEquals(book.worksheets, undone.worksheets)
        assertEquals(west.id, undone.activeWorksheetId)
        assertEquals(book.revision + 2, undone.revision)

        val redone = reopen(book.id).redo(undone.revision)
        assertEquals(accepted.document.worksheets, redone.worksheets)
        assertEquals(east.id, redone.activeWorksheetId)
        assertEquals(book.revision + 3, redone.revision)

        val afterRedo = metadata(book.id).readBytes()
        val retry = reopen(book.id).execute(command)
        assertTrue("A retained receipt must replay as an idempotent duplicate", retry.duplicate)
        assertEquals(accepted.acceptedRevision, retry.acceptedRevision)
        assertEquals(redone, retry.document)
        assertArrayEquals("A duplicate retry must not rewrite the workbook", afterRedo,
            metadata(book.id).readBytes())
    }

    @Test
    fun removeSelectedFirstSheetSelectsTheNextNeighbor() = runBlocking {
        val (coordinator, book) = threeSheetBook(activeName = "Sheet 1")
        val first = book.worksheets.first()
        val east = book.worksheets.single { it.name == "East" }
        val accepted = coordinator.execute(remove(book, "remove-first", first.id))
        assertEquals(book.worksheets.drop(1), accepted.document.worksheets)
        assertEquals("Removing the first selected sheet must select its next neighbor",
            east.id, accepted.document.activeWorksheetId)
        assertEquals(book, WorkbookStore(root).loadCheckpoint(book.id).undo.last())
    }

    @Test
    fun removeUnselectedSheetKeepsTheActiveWorksheet() = runBlocking {
        val (coordinator, book) = threeSheetBook(activeName = "East")
        val east = book.worksheets.single { it.name == "East" }
        val west = book.worksheets.single { it.name == "West" }
        val accepted = coordinator.execute(remove(book, "remove-unselected-west", west.id))
        assertEquals(book.worksheets.filterNot { it.id == west.id }, accepted.document.worksheets)
        assertEquals(east.id, accepted.document.activeWorksheetId)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
    }

    @Test
    fun unknownSheetFailsWithoutWritingAndDoesNotConsumeTheCommandId() = runBlocking {
        val (coordinator, book) = threeSheetBook(activeName = "East")
        val before = metadata(book.id).readBytes()
        val receiptsBefore = WorkbookStore(root).loadCheckpoint(book.id).receipts.size
        val missing = remove(book, "remove-missing", "missing-sheet")
        assertTrue(runCatching { coordinator.execute(missing) }.isFailure)
        assertArrayEquals(before, metadata(book.id).readBytes())
        assertEquals(receiptsBefore, WorkbookStore(root).loadCheckpoint(book.id).receipts.size)

        val west = book.worksheets.single { it.name == "West" }
        val corrected = remove(book, "remove-missing", west.id)
        val accepted = coordinator.execute(corrected)
        assertFalse("A failed validation must not consume the command identity", accepted.duplicate)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        assertEquals(book.worksheets.filterNot { it.id == west.id }, accepted.document.worksheets)
    }

    @Test
    fun removingTheLastRemainingWorksheetIsRefusedWithoutWriting() = runBlocking {
        val book = store.create("Book")
        val coordinator = reopen(book.id)
        val only = book.worksheets.single()
        val before = metadata(book.id).readBytes()
        assertTrue("The selected-tab fallback cannot remove the final worksheet",
            runCatching { coordinator.execute(remove(book, "remove-last", only.id)) }.isFailure)
        assertArrayEquals(before, metadata(book.id).readBytes())
        assertEquals(book, WorkbookStore(root).load(book.id))
    }

    @Test
    fun staleRevisionIsRefusedAndValidationRejectsMalformedIdentities() = runBlocking {
        val (coordinator, book) = threeSheetBook(activeName = "East")
        val west = book.worksheets.single { it.name == "West" }
        val before = metadata(book.id).readBytes()
        assertTrue(runCatching {
            coordinator.execute(remove(book, "stale-revision", west.id).copy(expectedRevision = book.revision + 9))
        }.isFailure)
        assertTrue(runCatching {
            coordinator.execute(remove(book, "bad-identity", "../escape"))
        }.isFailure)
        assertTrue(runCatching {
            coordinator.execute(remove(book, "bad-workbook", west.id).copy(workbookId = "other"))
        }.isFailure)
        assertArrayEquals(before, metadata(book.id).readBytes())
        assertEquals(book, reopen(book.id).state.value.document)
    }

    private suspend fun threeSheetBook(activeName: String): Pair<WorkbookCoordinator, WorkbookDocument> {
        val created = store.create("Book")
        val coordinator = reopen(created.id)
        val withEast = coordinator.execute(
            WorkbookCommand.AddWorksheet("add-east", created.id, "test-window", created.revision, "east-sheet", "East"),
        ).document
        val withWest = coordinator.execute(
            WorkbookCommand.AddWorksheet("add-west", created.id, "test-window", withEast.revision, "west-sheet", "West"),
        ).document
        val active = withWest.worksheets.single { it.name == activeName }
        val book = coordinator.edit(withWest.revision) { it.copy(activeWorksheetId = active.id) }
        return reopen(created.id) to book
    }

    private fun remove(document: WorkbookDocument, commandId: String, worksheetId: String) =
        WorkbookCommand.RemoveWorksheet(commandId, document.id, "remove-window", document.revision, worksheetId)

    private suspend fun reopen(id: String): WorkbookCoordinator = WorkbookStore(root).coordinators.open(id)

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")
}
