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
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookConflictException
import io.github.baitian6641.cloudydata.data.WorkbookCoordinator
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import io.github.baitian6641.cloudydata.model.canonicalJson
import io.github.baitian6641.cloudydata.ui.SaveState
import io.github.baitian6641.cloudydata.ui.ImportUiState
import io.github.baitian6641.cloudydata.ui.WorkbookViewModel
import io.github.baitian6641.cloudydata.ui.WorkbookViewModelFactory
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Native S03.3 / F01,F64 / Q17,Q19,Q34 AddWorksheet and real ViewModel persistence cases. */
@RunWith(AndroidJUnit4::class)
class AddWorksheetCommandTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "add-worksheet-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun appendHasExactDefaultsAndDurableReceiptWhileReopenUndoRedoPreserveStoredDefault() = runBlocking {
        val book = store.create("Book")
        val command = add(book, "append", "new-sheet", "測定 😊")
        val coordinator = reopen(book.id)
        val accepted = coordinator.execute(command)
        assertFalse(accepted.duplicate)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        val expectedSheet = defaultSheet("new-sheet", "測定 😊")
        assertEquals(book.worksheets + expectedSheet, accepted.document.worksheets)
        assertEquals(book.activeWorksheetId, accepted.document.activeWorksheetId)
        assertEquals(book.name, accepted.document.name)
        assertEquals(book.snapshot, accepted.document.snapshot)
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(accepted.document, checkpoint.document)
        assertEquals(listOf(book), checkpoint.undo)
        assertTrue(checkpoint.redo.isEmpty())
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(command, checkpoint.receipts.single().command)
        assertEquals(book.revision + 1, checkpoint.receipts.single().acceptedRevision)

        val undone = reopen(book.id).undo(accepted.document.revision)
        assertEquals(book.worksheets, undone.worksheets)
        assertEquals(book.activeWorksheetId, undone.activeWorksheetId)
        assertEquals(book.revision + 2, undone.revision)
        val afterUndo = metadata(book.id).readBytes()
        val retry = reopen(book.id).execute(command)
        assertTrue(retry.duplicate)
        assertEquals(book.revision + 1, retry.acceptedRevision)
        assertEquals(undone, retry.document)
        assertArrayEquals("retry after undo must never recreate the removed definition", afterUndo, metadata(book.id).readBytes())

        val redone = reopen(book.id).redo(undone.revision)
        assertEquals(book.worksheets + expectedSheet, redone.worksheets)
        assertEquals(book.activeWorksheetId, redone.activeWorksheetId)
        assertEquals(book.revision + 3, redone.revision)
        val reopened = reopen(book.id)
        assertEquals(redone, reopened.state.value.document)
        assertTrue(reopened.state.value.canUndo)
        assertFalse(reopened.state.value.canRedo)
    }

    @Test
    fun identicalRetryAfterForeignEditReturnsCurrentDefinitionAndChangedRequestIsRefused() = runBlocking {
        val book = store.create("Book")
        val command = add(book, "same-id", "added-sheet", "Added")
        val coordinator = reopen(book.id)
        val first = coordinator.execute(command)
        val foreign = coordinator.execute(
            WorkbookCommand.Rename("foreign-rename", book.id, "foreign-window", first.document.revision, "Foreign accepted title"),
        ).document
        val reconstructed = reopen(book.id)
        val before = metadata(book.id).readBytes()
        val duplicate = reconstructed.execute(command)
        assertTrue(duplicate.duplicate)
        assertEquals(first.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(foreign, duplicate.document)
        assertEquals(2, duplicate.document.worksheets.size)
        listOf(
            command.copy(worksheetId = "changed-sheet"),
            command.copy(name = "Changed name"),
            command.copy(originWindowId = "changed-window"),
            command.copy(expectedRevision = foreign.revision),
            command.copy(workbookId = "foreign-workbook"),
        ).forEach { changed ->
            assertTrue("same command ID cannot change request: $changed", runCatching { reconstructed.execute(changed) }.isFailure)
            assertEquals(foreign, reconstructed.state.value.document)
            assertArrayEquals(before, metadata(book.id).readBytes())
        }
        assertEquals(foreign, WorkbookStore(root).load(book.id))
    }

    @Test
    fun currentHistoryAndReceiptOnlyWorksheetIdentityCollisionsAreAllRefused() = runBlocking {
        val book = store.create("Book")
        val coordinator = reopen(book.id)
        val currentCollision = add(book, "retryable-current-collision", book.worksheets.single().id, "Collision")
        val beforeCurrent = metadata(book.id).readBytes()
        assertTrue(runCatching { coordinator.execute(currentCollision) }.isFailure)
        assertArrayEquals(beforeCurrent, metadata(book.id).readBytes())
        val added = coordinator.execute(currentCollision.copy(worksheetId = "reserved-sheet")).document
        val removed = coordinator.edit(added.revision) {
            it.copy(worksheets = it.worksheets.filterNot { sheet -> sheet.id == "reserved-sheet" })
        }
        assertTrue(removed.worksheets.none { it.id == "reserved-sheet" })
        assertTrue(WorkbookStore(root).loadCheckpoint(book.id).undo.any { state -> state.worksheets.any { it.id == "reserved-sheet" } })
        val historicalCollision = add(removed, "historical-collision", "reserved-sheet", "Cannot reuse history identity")
        val beforeHistory = metadata(book.id).readBytes()
        assertTrue(runCatching { coordinator.execute(historicalCollision) }.isFailure)
        assertArrayEquals(beforeHistory, metadata(book.id).readBytes())

        val other = store.create("Receipt-only reservation")
        var otherCoordinator = reopen(other.id)
        val original = add(other, "original-reservation", "receipt-reserved-sheet", "Original reservation")
        val accepted = otherCoordinator.execute(original).document
        val undone = otherCoordinator.undo(accepted.revision)
        val branch = otherCoordinator.edit(undone.revision) { it.copy(name = "Branched after undo") }
        otherCoordinator = reopen(other.id)
        val receiptOnly = WorkbookStore(root).loadCheckpoint(other.id)
        assertTrue((listOf(receiptOnly.document) + receiptOnly.undo + receiptOnly.redo).none {
            state -> state.worksheets.any { it.id == "receipt-reserved-sheet" }
        })
        assertTrue(receiptOnly.receipts.any { it.command == original })
        val beforeReceiptOnly = metadata(other.id).readBytes()
        assertTrue(runCatching {
            otherCoordinator.execute(add(branch, "new-id-same-sheet", "receipt-reserved-sheet", "Cannot recycle accepted identity"))
        }.isFailure)
        val retry = otherCoordinator.execute(original)
        assertTrue(retry.duplicate)
        assertEquals(branch, retry.document)
        assertArrayEquals(beforeReceiptOnly, metadata(other.id).readBytes())
    }

    @Test
    fun invalidIdentityOrNameAndStaleRevisionWriteNothingAndDoNotConsumeCommandIds() = runBlocking {
        val book = store.create("Book")
        val coordinator = reopen(book.id)
        var current = book
        val malformed: List<(WorkbookCommand.AddWorksheet) -> WorkbookCommand.AddWorksheet> = listOf(
            { it.copy(worksheetId = "") },
            { it.copy(worksheetId = "../escape") },
            { it.copy(worksheetId = "contains space") },
            { it.copy(worksheetId = "a".repeat(129)) },
            { it.copy(name = "") },
            { it.copy(name = "   ") },
            { it.copy(name = " padded ") },
            { it.copy(name = "a".repeat(513)) },
            { it.copy(originWindowId = "") },
            { it.copy(workbookId = "other-workbook") },
        )
        malformed.forEachIndexed { index, mutate ->
            val valid = add(current, "correctable-$index", "valid-sheet-$index", "Valid $index")
            val before = metadata(book.id).readBytes()
            assertTrue("invalid request $index is refused", runCatching { coordinator.execute(mutate(valid)) }.isFailure)
            assertArrayEquals(before, metadata(book.id).readBytes())
            assertEquals(current, coordinator.state.value.document)
            val corrected = coordinator.execute(valid)
            assertFalse("a rejected request never consumes its command ID", corrected.duplicate)
            assertEquals(current.revision + 1, corrected.acceptedRevision)
            current = corrected.document
        }
        val stale = add(book, "stale-correctable", "stale-sheet", "Reviewed stale command")
        val beforeStale = metadata(book.id).readBytes()
        val conflict = runCatching { coordinator.execute(stale) }.exceptionOrNull()
        assertTrue(conflict is WorkbookConflictException)
        conflict as WorkbookConflictException
        assertEquals(book.revision, conflict.expectedRevision)
        assertEquals(current.revision, conflict.actualRevision)
        assertArrayEquals(beforeStale, metadata(book.id).readBytes())
        val corrected = coordinator.execute(stale.copy(expectedRevision = current.revision))
        assertFalse(corrected.duplicate)
        assertEquals(current.revision + 1, corrected.acceptedRevision)
        assertEquals(12, corrected.document.worksheets.size)
    }

    @Test
    fun explicitDuplicateNamesRemainAllowedWithDistinctStableIdentities() = runBlocking {
        val book = store.create("Book")
        val coordinator = reopen(book.id)
        val first = coordinator.execute(add(book, "same-name-first", "first-new-sheet", "Sheet 1")).document
        val second = coordinator.execute(add(first, "same-name-second", "second-new-sheet", "Sheet 1")).document
        assertEquals(listOf("Sheet 1", "Sheet 1", "Sheet 1"), second.worksheets.map { it.name })
        assertEquals(3, second.worksheets.map { it.id }.distinct().size)
        assertEquals(book.activeWorksheetId, second.activeWorksheetId)
        assertEquals(second, WorkbookStore(root).load(book.id))
    }

    @Test
    fun rawSaveHistoryBarrierPreservesAcceptedIdentityReservationAndOriginalRetry() = runBlocking {
        val book = store.create("Book")
        val command = add(book, "retained-through-barrier", "retained-sheet-id", "Retained identity")
        var coordinator = reopen(book.id)
        val added = coordinator.execute(command).document
        val undone = coordinator.undo(added.revision)
        val barrier = WorkbookStore(root).save(undone.copy(name = "Accepted history barrier"))
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertTrue(checkpoint.undo.isEmpty())
        assertTrue(checkpoint.redo.isEmpty())
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(command, checkpoint.receipts.single().command)
        coordinator = reopen(book.id)
        val before = metadata(book.id).readBytes()
        assertTrue(runCatching {
            coordinator.execute(add(barrier, "different-command-id", "retained-sheet-id", "Must not recycle"))
        }.isFailure)
        val retry = coordinator.execute(command)
        assertTrue(retry.duplicate)
        assertEquals(book.revision + 1, retry.acceptedRevision)
        assertEquals(barrier, retry.document)
        assertEquals(book.worksheets, retry.document.worksheets)
        assertArrayEquals(before, metadata(book.id).readBytes())
    }

    @Test
    fun unknownSavedCommandKindOrCommandVersionRefusesOpenAndWritesWithValidJournalChecksum() = runBlocking {
        listOf("unknown kind", "unknown command version").forEachIndexed { index, fault ->
            val book = store.create("Unsupported receipt $index")
            val coordinator = reopen(book.id)
            val accepted = coordinator.execute(add(book, "supported-$index", "saved-sheet-$index", "Added")).document
            val json = JSONObject(metadata(book.id).readText())
            val journal = json.getJSONObject("commandJournal")
            val savedCommand = journal.getJSONArray("receipts").getJSONObject(0).getJSONObject("command")
            if (fault == "unknown kind") savedCommand.put("kind", "futureAddWorksheet") else savedCommand.put("version", 999)
            val unsigned = JSONObject(journal.toString()).apply { remove("sha256") }
            val checksum = MessageDigest.getInstance("SHA-256").digest(canonicalJson(unsigned).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            journal.put("sha256", checksum)
            metadata(book.id).writeText(json.toString())
            val before = metadata(book.id).readBytes()
            val failure = runCatching { WorkbookStore(root).loadCheckpoint(book.id) }.exceptionOrNull()
            assertTrue("$fault must fail beyond the valid journal checksum: $failure", failure != null)
            assertFalse("this case must exercise command parsing, not checksum rejection", failure!!.message.orEmpty().contains("checksum", ignoreCase = true))
            assertTrue(runCatching { reopen(book.id) }.isFailure)
            assertTrue(runCatching { WorkbookStore(root).save(accepted.copy(name = "Must not repair unsupported records")) }.isFailure)
            assertArrayEquals(before, metadata(book.id).readBytes())
        }
    }

    @Test
    fun viewRetryAfterRealMoveDenialRetainsTheExactFrozenRequestAndActivatesOneSheet() = runBlocking {
        val armed = AtomicBoolean(false)
        val attemptedRequest = AtomicReference<JSONObject>()
        val nativeDenial = AtomicInteger(-1)
        var workbookId: String? = null
        var originalMode: Int? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (phase == WorkbookCommitPhase.TEMP_SYNCED && armed.compareAndSet(true, false)) {
                val directory = store.workbookDir(checkNotNull(workbookId))
                val temporary = directory.listFiles().orEmpty().single { Regex("workbook-[0-9]+\\.tmp").matches(it.name) }
                val receipts = JSONObject(temporary.readText()).getJSONObject("commandJournal").getJSONArray("receipts")
                attemptedRequest.set(receipts.getJSONObject(receipts.length() - 1).getJSONObject("command"))
                originalMode = Os.stat(directory.path).st_mode and 0x1ff
                Os.chmod(directory.path, 0x16d) // 0555: the following real atomic move must fail.
                try {
                    Os.rename(temporary.path, File(directory, "denied-move-probe").path)
                } catch (denied: ErrnoException) {
                    nativeDenial.set(denied.errno)
                }
                assertEquals("native rename confirms the actual denial before the production move", OsConstants.EACCES, nativeDenial.get())
            }
        }
        val book = hookedStore.create("Book")
        workbookId = book.id
        val before = metadata(book.id).readBytes()
        val owner = owner()
        lateinit var model: WorkbookViewModel
        try {
            model = newModel(owner, hookedStore, book.id)
            withTimeout(20_000) {
                awaitLoaded(model)
                armed.set(true)
                onMain { model.addWorksheet() }
                val failed = model.state.first { it.save is SaveState.Failed }
                val message = (failed.save as SaveState.Failed).message
                assertEquals(OsConstants.EACCES, nativeDenial.get())
                assertTrue("the real failed atomic move must reach the view", message.isNotBlank())
                assertEquals(book.worksheets, failed.document!!.worksheets)
                assertEquals(book.activeWorksheetId, failed.document.activeWorksheetId)
                assertArrayEquals(before, metadata(book.id).readBytes())
                val frozen = checkNotNull(attemptedRequest.get())
                assertEquals("Sheet 2", frozen.getString("name"))
                assertEquals(book.revision, frozen.getLong("expectedRevision"))
                assertTrue(frozen.getString("worksheetId").isNotEmpty())
                Os.chmod(store.workbookDir(book.id).path, checkNotNull(originalMode))
                originalMode = null
                onMain { model.saveNow() }
                val saved = model.state.first { it.document?.worksheets?.size == 2 && it.save is SaveState.Saved }
                val addedId = saved.document!!.worksheets.single { it.id != book.worksheets.single().id }.id
                assertEquals(addedId, saved.document.activeWorksheetId)
                assertEquals(book.revision + 1, saved.document.revision)
                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(book.activeWorksheetId, checkpoint.document.activeWorksheetId)
                assertEquals(1, checkpoint.receipts.size)
                val receipt = checkpoint.receipts.single()
                val acceptedCommand = receipt.command as WorkbookCommand.AddWorksheet
                assertEquals(frozen.getString("commandId"), acceptedCommand.commandId)
                assertEquals(frozen.getString("originWindowId"), acceptedCommand.originWindowId)
                assertEquals(frozen.getLong("expectedRevision"), acceptedCommand.expectedRevision)
                assertEquals(frozen.getString("worksheetId"), acceptedCommand.worksheetId)
                assertEquals(frozen.getString("name"), acceptedCommand.name)
                assertEquals(defaultSheet(addedId, "Sheet 2"), checkpoint.document.worksheets.last())
                val savedBytes = metadata(book.id).readBytes()
                onMain { model.saveNow() }
                val duplicate = hookedStore.coordinators.open(book.id).execute(acceptedCommand)
                assertTrue(duplicate.duplicate)
                assertEquals(book.revision + 1, duplicate.acceptedRevision)
                assertArrayEquals(savedBytes, metadata(book.id).readBytes())
            }
        } finally {
            originalMode?.let { Os.chmod(store.workbookDir(book.id).path, it) }
            onMain { owner.viewModelStore.clear() }
        }
    }

    @Test
    fun rapidTwoAddsSurviveLostAcknowledgementAndKeepOtherViewsLocalTabAndStoredDefault() = runBlocking {
        val observing = AtomicBoolean(false)
        val armed = AtomicBoolean(false)
        val releaseWriter = CountDownLatch(1)
        val errors = AtomicInteger()
        val replacements = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> if (armed.get()) {
                        check(releaseWriter.await(10, TimeUnit.SECONDS)) { "Immediate commands did not release the writer." }
                    }
                    WorkbookCommitPhase.REPLACED -> {
                        replacements.incrementAndGet()
                        if (armed.compareAndSet(true, false)) {
                            errors.incrementAndGet()
                            throw IOException("One-shot acknowledgement loss after AddWorksheet replacement")
                        }
                    }
                }
            }
        }
        val book = hookedStore.create("Book")
        val owners = listOf(owner(), owner())
        val first = newModel(owners[0], hookedStore, book.id)
        val second = newModel(owners[1], hookedStore, book.id)
        try {
            withTimeout(20_000) {
                awaitLoaded(first)
                awaitLoaded(second)
                observing.set(true)
                armed.set(true)
                try {
                    onMain {
                        first.addWorksheet()
                        assertEquals(book.revision, first.state.value.document!!.revision)
                        first.addWorksheet()
                    }
                } finally {
                    releaseWriter.countDown()
                }
                val accepted = first.state.first { it.document?.worksheets?.size == 3 && it.save is SaveState.Saved }.document!!
                val otherView = second.state.first { it.document?.worksheets?.size == 3 }.document!!
                assertEquals(listOf("Sheet 1", "Sheet 2", "Sheet 3"), accepted.worksheets.map { it.name })
                assertEquals(3, accepted.worksheets.map { it.id }.distinct().size)
                assertEquals(accepted.worksheets.last().id, accepted.activeWorksheetId)
                assertEquals(book.activeWorksheetId, otherView.activeWorksheetId)
                assertEquals(book.revision + 2, accepted.revision)
                assertEquals(1, errors.get())
                assertEquals(2, replacements.get())
                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(book.activeWorksheetId, checkpoint.document.activeWorksheetId)
                assertEquals(accepted.worksheets, checkpoint.document.worksheets)
                val receipts = checkpoint.receipts
                assertEquals(2, receipts.size)
                val adds = receipts.map { it.command as WorkbookCommand.AddWorksheet }
                assertEquals(listOf("Sheet 2", "Sheet 3"), adds.map { it.name })
                assertEquals(listOf(book.revision, book.revision + 1), adds.map { it.expectedRevision })
                assertEquals(listOf(book.revision + 1, book.revision + 2), receipts.map { it.acceptedRevision })
                assertNotEquals(adds[0].commandId, adds[1].commandId)
                assertNotEquals(adds[0].worksheetId, adds[1].worksheetId)
                assertEquals(adds[0].originWindowId, adds[1].originWindowId)
                assertEquals(adds.map { defaultSheet(it.worksheetId, it.name) }, accepted.worksheets.drop(1))
                onMain { owners[0].viewModelStore.clear() }
                onMain { second.selectWorksheet(adds[0].worksheetId) }
                assertEquals(adds[0].worksheetId, second.state.value.document!!.activeWorksheetId)
                assertEquals(book.activeWorksheetId, WorkbookStore(root).load(book.id).activeWorksheetId)
                assertEquals(3, WorkbookStore(root).load(book.id).worksheets.size)
            }
        } finally {
            releaseWriter.countDown()
            onMain { owners.forEach { it.viewModelStore.clear() } }
        }
    }

    @Test
    fun failedFirstQueuedAddBlocksLaterAddUntilExactExplicitRetryThenAcceptsAFreshSecondAdd() = runBlocking {
        val observing = AtomicBoolean(false)
        val failFirst = AtomicBoolean(false)
        val releaseWriter = CountDownLatch(1)
        val attempts = AtomicInteger()
        val replacements = AtomicInteger()
        val frozenRequest = AtomicReference<JSONObject>()
        var workbookId: String? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> {
                        attempts.incrementAndGet()
                        if (failFirst.compareAndSet(true, false)) {
                            frozenRequest.set(requestFromLiveTemp(checkNotNull(workbookId)))
                            check(releaseWriter.await(10, TimeUnit.SECONDS)) { "Queued Add B did not release Add A's writer." }
                            throw IOException("One-shot Add A failure before atomic replacement")
                        }
                    }
                    WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
                }
            }
        }
        val book = hookedStore.create("Book")
        workbookId = book.id
        val before = metadata(book.id).readBytes()
        val owner = owner()
        val model = newModel(owner, hookedStore, book.id)
        try {
            withTimeout(20_000) {
                awaitLoaded(model)
                observing.set(true)
                failFirst.set(true)
                try {
                    onMain {
                        model.addWorksheet()
                        assertEquals(book.revision, model.state.value.document!!.revision)
                        model.addWorksheet()
                    }
                } finally {
                    releaseWriter.countDown()
                }
                val refusedLater = model.state.first {
                    (it.save as? SaveState.Failed)?.message.orEmpty().contains("later change was not applied", ignoreCase = true)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue(refusedLater.save is SaveState.Failed)
                assertTrue(model.state.value.save is SaveState.Failed)
                assertEquals(book, model.state.value.document)
                assertEquals("only Add A reached the real writer; queued Add B was refused", 1, attempts.get())
                assertEquals(0, replacements.get())
                assertArrayEquals(before, metadata(book.id).readBytes())
                assertTrue(WorkbookStore(root).loadCheckpoint(book.id).receipts.isEmpty())
                val frozen = checkNotNull(frozenRequest.get())
                assertEquals("Sheet 2", frozen.getString("name"))

                // The failing condition is now repaired. Two Save activations must
                // consume the retained retry once rather than dispatch a second edit.
                onMain {
                    model.saveNow()
                    model.saveNow()
                }
                val firstAccepted = model.state.first {
                    it.document?.worksheets?.size == 2 && it.save is SaveState.Saved
                }.document!!
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(book.revision + 1, firstAccepted.revision)
                assertEquals(listOf("Sheet 1", "Sheet 2"), firstAccepted.worksheets.map { it.name })
                val firstCheckpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(1, firstCheckpoint.receipts.size)
                assertFrozenRequest(frozen, firstCheckpoint.receipts.single().command as WorkbookCommand.AddWorksheet)
                assertEquals(2, attempts.get())
                assertEquals(1, replacements.get())

                onMain { model.addWorksheet() }
                val secondAccepted = model.state.first {
                    it.document?.worksheets?.size == 3 && it.save is SaveState.Saved
                }.document!!
                assertEquals(book.revision + 2, secondAccepted.revision)
                assertEquals(listOf("Sheet 1", "Sheet 2", "Sheet 3"), secondAccepted.worksheets.map { it.name })
                assertEquals(secondAccepted.worksheets.last().id, secondAccepted.activeWorksheetId)
                val finalCheckpoint = WorkbookStore(root).loadCheckpoint(book.id)
                val receipts = finalCheckpoint.receipts
                assertEquals("the refused B was never replayed automatically", 2, receipts.size)
                assertEquals(listOf("Sheet 2", "Sheet 3"), receipts.map { (it.command as WorkbookCommand.AddWorksheet).name })
                assertFrozenRequest(frozen, receipts[0].command as WorkbookCommand.AddWorksheet)
                assertNotEquals(receipts[0].command.commandId, receipts[1].command.commandId)
                assertEquals(book.activeWorksheetId, finalCheckpoint.document.activeWorksheetId)
                assertEquals(3, attempts.get())
                assertEquals(2, replacements.get())
            }
        } finally {
            releaseWriter.countDown()
            onMain { owner.viewModelStore.clear() }
        }
    }

    @Test
    fun queuedGenericNoOpCannotHideAFailedAddOrDiscardItsExactRetry() = runBlocking {
        val observing = AtomicBoolean(false)
        val failFirst = AtomicBoolean(false)
        val releaseWriter = CountDownLatch(1)
        val attempts = AtomicInteger()
        val replacements = AtomicInteger()
        val frozenRequest = AtomicReference<JSONObject>()
        var workbookId: String? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> {
                        attempts.incrementAndGet()
                        if (failFirst.compareAndSet(true, false)) {
                            frozenRequest.set(requestFromLiveTemp(checkNotNull(workbookId)))
                            check(releaseWriter.await(10, TimeUnit.SECONDS)) { "The generic no-op did not release the Add writer." }
                            throw IOException("One-shot Add failure before the queued generic no-op")
                        }
                    }
                    WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
                }
            }
        }
        val book = hookedStore.create("Book")
        workbookId = book.id
        val before = metadata(book.id).readBytes()
        val owner = owner()
        val model = newModel(owner, hookedStore, book.id)
        try {
            withTimeout(20_000) {
                awaitLoaded(model)
                assertTrue(model.state.value.document!!.activeWorksheet!!.sortDescending)
                observing.set(true)
                failFirst.set(true)
                try {
                    onMain {
                        model.addWorksheet()
                        model.setSortDescending(true) // An actual generic command whose transform would be a no-op.
                    }
                } finally {
                    releaseWriter.countDown()
                }
                model.state.first {
                    (it.save as? SaveState.Failed)?.message.orEmpty().contains("later change was not applied", ignoreCase = true)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue("the queued no-op must leave the failure visible", model.state.value.save is SaveState.Failed)
                assertEquals(book, model.state.value.document)
                assertEquals(1, attempts.get())
                assertEquals(0, replacements.get())
                assertArrayEquals("neither Add nor the later no-op was accepted", before, metadata(book.id).readBytes())
                val unchanged = WorkbookStore(root).loadCheckpoint(book.id)
                assertTrue(unchanged.receipts.isEmpty())
                assertTrue(unchanged.undo.isEmpty())
                assertTrue(unchanged.redo.isEmpty())

                onMain { model.saveNow() }
                val accepted = model.state.first {
                    it.document?.worksheets?.size == 2 && it.save is SaveState.Saved
                }.document!!
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(book.revision + 1, accepted.revision)
                assertEquals(listOf("Sheet 1", "Sheet 2"), accepted.worksheets.map { it.name })
                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(1, checkpoint.receipts.size)
                assertFrozenRequest(checkNotNull(frozenRequest.get()), checkpoint.receipts.single().command as WorkbookCommand.AddWorksheet)
                assertEquals(listOf(book), checkpoint.undo)
                assertEquals(book.activeWorksheetId, checkpoint.document.activeWorksheetId)
                assertTrue(checkpoint.document.worksheets.all { it.sortDescending })
                assertEquals(2, attempts.get())
                assertEquals(1, replacements.get())
            }
        } finally {
            releaseWriter.countDown()
            onMain { owner.viewModelStore.clear() }
        }
    }

    @Test
    fun pendingRealImportCannotBypassFailedAddAndSaveRetriesOnlyTheFrozenAdd() = runBlocking {
        val observing = AtomicBoolean(false)
        val failFirst = AtomicBoolean(false)
        val attempts = AtomicInteger()
        val replacements = AtomicInteger()
        val frozenRequest = AtomicReference<JSONObject>()
        var workbookId: String? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> {
                        attempts.incrementAndGet()
                        if (failFirst.compareAndSet(true, false)) {
                            frozenRequest.set(requestFromLiveTemp(checkNotNull(workbookId)))
                            throw IOException("One-shot Add failure before the staged import")
                        }
                    }
                    WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
                }
            }
        }
        val book = hookedStore.create("Book")
        workbookId = book.id
        val before = metadata(book.id).readBytes()
        val csvText = "Category,Amount\nEast,10\nWest,20\nEast,5\n"
        val csv = File(root, "pending-real-import.csv").apply { writeText(csvText) }
        val owner = owner()
        val model = newModel(owner, hookedStore, book.id)
        try {
            withTimeout(20_000) {
                awaitLoaded(model)
                onMain { model.inspectCsv(Uri.fromFile(csv)) }
                val pending = (model.state.first { it.import is ImportUiState.Ready }.import as ImportUiState.Ready).pending
                assertEquals(listOf("Category", "Amount"), pending.columns.map { it.name })
                assertEquals(3, pending.previewRows.size)
                assertEquals(csvText, pending.sourceFile.readText())
                val sourceBytes = pending.sourceFile.readBytes()
                val stagedNames = pending.stagingDirectory.listFiles().orEmpty().map { it.name }.sorted()
                assertFalse(File(pending.stagingDirectory, "receipt.json").exists())
                assertFalse(File(pending.stagingDirectory, "snapshot.parquet").exists())
                assertArrayEquals("inspection does not accept workbook metadata", before, metadata(book.id).readBytes())

                observing.set(true)
                failFirst.set(true)
                onMain { model.addWorksheet() }
                model.state.first { it.save is SaveState.Failed }
                onMain { model.applyImport() }
                val refused = model.state.first {
                    (it.import as? ImportUiState.Failed)?.message.orEmpty().contains("import was not applied", ignoreCase = true)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val failedImport = refused.import as ImportUiState.Failed
                assertSame("the user's already inspected import remains available", pending, failedImport.pending)
                assertTrue("import refusal must retain the original save failure", refused.save is SaveState.Failed)
                assertTrue((refused.save as SaveState.Failed).message.contains("One-shot Add failure"))
                assertEquals(book, refused.document)
                assertEquals(1, attempts.get())
                assertEquals(0, replacements.get())
                assertArrayEquals(before, metadata(book.id).readBytes())
                val unchanged = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(null, unchanged.document.snapshot)
                assertTrue(unchanged.receipts.isEmpty())
                assertArrayEquals(sourceBytes, pending.sourceFile.readBytes())
                assertEquals(stagedNames, pending.stagingDirectory.listFiles().orEmpty().map { it.name }.sorted())
                assertFalse("the real importer must not start its receipt state machine", File(pending.stagingDirectory, "receipt.json").exists())
                assertFalse(File(store.workbookDir(book.id), "data/snapshot.parquet").exists())
                assertFalse(File(store.workbookDir(book.id), "source/original.csv").exists())

                onMain { model.saveNow() }
                val accepted = model.state.first {
                    it.document?.worksheets?.size == 2 && it.save is SaveState.Saved
                }.document!!
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(book.revision + 1, accepted.revision)
                assertEquals(null, accepted.snapshot)
                assertEquals(listOf("Sheet 1", "Sheet 2"), accepted.worksheets.map { it.name })
                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(1, checkpoint.receipts.size)
                assertFrozenRequest(checkNotNull(frozenRequest.get()), checkpoint.receipts.single().command as WorkbookCommand.AddWorksheet)
                assertEquals(book.activeWorksheetId, checkpoint.document.activeWorksheetId)
                assertTrue(checkpoint.document.worksheets.all { it.rowFieldId == null && it.measureFieldId == null })
                assertSame(pending, (model.state.value.import as ImportUiState.Failed).pending)
                assertArrayEquals(sourceBytes, pending.sourceFile.readBytes())
                assertEquals(stagedNames, pending.stagingDirectory.listFiles().orEmpty().map { it.name }.sorted())
                assertFalse("Save retries Add only; refused import is never replayed", File(pending.stagingDirectory, "receipt.json").exists())
                assertFalse(File(store.workbookDir(book.id), "data/snapshot.parquet").exists())
                assertEquals(2, attempts.get())
                assertEquals(1, replacements.get())
            }
        } finally {
            onMain { owner.viewModelStore.clear() }
        }
    }

    @Test
    fun invalidRenameIsTerminalChangeNotAppliedAndAValidAddSucceedsWithoutReloadOrHiddenRetry() = runBlocking {
        val observing = AtomicBoolean(false)
        val attempts = AtomicInteger()
        val replacements = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> attempts.incrementAndGet()
                    WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
                }
            }
        }
        val book = hookedStore.create("Book")
        val before = metadata(book.id).readBytes()
        val owner = owner()
        val model = newModel(owner, hookedStore, book.id)
        try {
            withTimeout(20_000) {
                awaitLoaded(model)
                observing.set(true)
                onMain { model.renameWorkbook("x".repeat(513)) }
                val rejected = model.state.first { it.save is SaveState.Conflict }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue((rejected.save as SaveState.Conflict).message.contains("512"))
                assertEquals(book, rejected.document)
                assertEquals(0, attempts.get())
                assertEquals(0, replacements.get())
                assertArrayEquals(before, metadata(book.id).readBytes())
                assertTrue(WorkbookStore(root).loadCheckpoint(book.id).receipts.isEmpty())

                // Corrected intent proceeds on this same loaded view. There is no
                // reload or Save call that could clear an incorrectly retained retry.
                onMain { model.addWorksheet() }
                val accepted = model.state.first {
                    it.document?.worksheets?.size == 2 && it.save is SaveState.Saved
                }.document!!
                assertEquals("Book", accepted.name)
                assertEquals(book.revision + 1, accepted.revision)
                assertEquals(listOf("Sheet 1", "Sheet 2"), accepted.worksheets.map { it.name })
                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(1, checkpoint.receipts.size)
                assertTrue("the rejected rename has no receipt or hidden retry", checkpoint.receipts.single().command is WorkbookCommand.AddWorksheet)
                assertEquals(1, attempts.get())
                assertEquals(1, replacements.get())
                val acceptedBytes = metadata(book.id).readBytes()
                onMain { model.saveNow() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue(model.state.value.save is SaveState.Saved)
                assertEquals("Book", model.state.value.document!!.name)
                assertArrayEquals("Save cannot replay the invalid rename", acceptedBytes, metadata(book.id).readBytes())
                assertEquals(1, attempts.get())
                assertEquals(1, replacements.get())
            }
        } finally {
            onMain { owner.viewModelStore.clear() }
        }
    }

    @Test
    fun discardedPendingImportSupersedesItsQueuedApplyWhileAddOwnsTheViewWriter() = runBlocking {
        val observing = AtomicBoolean(false)
        val pauseFirst = AtomicBoolean(false)
        val releaseWriter = CountDownLatch(1)
        val attempts = AtomicInteger()
        val replacements = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) {
                when (phase) {
                    WorkbookCommitPhase.TEMP_SYNCED -> {
                        attempts.incrementAndGet()
                        if (pauseFirst.compareAndSet(true, false)) {
                            check(releaseWriter.await(10, TimeUnit.SECONDS)) { "Discard did not release the Add writer." }
                        }
                    }
                    WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
                }
            }
        }
        val book = hookedStore.create("Book")
        val csv = File(root, "discard-queued-import.csv").apply {
            writeText("Category,Amount\nEast,10\nWest,20\n")
        }
        val owner = owner()
        val model = newModel(owner, hookedStore, book.id)
        try {
            withTimeout(20_000) {
                awaitLoaded(model)
                onMain { model.inspectCsv(Uri.fromFile(csv)) }
                val pending = (model.state.first { it.import is ImportUiState.Ready }.import as ImportUiState.Ready).pending
                assertTrue(pending.stagingDirectory.isDirectory)
                assertTrue(pending.sourceFile.isFile)
                observing.set(true)
                pauseFirst.set(true)
                try {
                    onMain {
                        model.addWorksheet()
                        model.applyImport()
                        model.discardImport()
                        assertTrue("Discard immediately supersedes the queued request", model.state.value.import is ImportUiState.Idle)
                        assertFalse(pending.stagingDirectory.exists())
                    }
                } finally {
                    releaseWriter.countDown()
                }
                val accepted = model.state.first {
                    it.document?.worksheets?.size == 2 && it.save is SaveState.Saved
                }.document!!
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue("superseded Apply must not produce a phantom import failure", model.state.value.import is ImportUiState.Idle)
                assertTrue(model.state.value.save is SaveState.Saved)
                assertEquals(null, accepted.snapshot)
                assertEquals(book.revision + 1, accepted.revision)
                assertEquals(listOf("Sheet 1", "Sheet 2"), accepted.worksheets.map { it.name })
                assertFalse("queued Apply cannot recreate discarded staging", pending.stagingDirectory.exists())
                assertFalse(pending.sourceFile.exists())
                assertFalse(File(store.workbookDir(book.id), "data/snapshot.parquet").exists())
                assertFalse(File(store.workbookDir(book.id), "source/original.csv").exists())
                val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
                assertEquals(null, checkpoint.document.snapshot)
                assertEquals(book.activeWorksheetId, checkpoint.document.activeWorksheetId)
                assertEquals(1, checkpoint.receipts.size)
                assertTrue("only Add was accepted", checkpoint.receipts.single().command is WorkbookCommand.AddWorksheet)
                assertEquals(1, attempts.get())
                assertEquals(1, replacements.get())
            }
        } finally {
            releaseWriter.countDown()
            onMain { owner.viewModelStore.clear() }
        }
    }

    private fun add(document: WorkbookDocument, commandId: String, worksheetId: String, name: String) =
        WorkbookCommand.AddWorksheet(commandId, document.id, "add-window", document.revision, worksheetId, name)

    private fun defaultSheet(id: String, name: String) = WorksheetDef(
        id = id,
        name = name,
        rowFieldId = null,
        measureFieldId = null,
        aggregation = Aggregation.SUM,
        markType = MarkType.TABLE,
        sortDescending = true,
        seriesFieldId = null,
        secondaryMeasureFieldId = null,
        secondaryAggregation = Aggregation.SUM,
        chart = ChartOptions(),
        filter = null,
    )

    private suspend fun reopen(id: String): WorkbookCoordinator = WorkbookStore(root).coordinators.open(id)

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")

    private fun requestFromLiveTemp(id: String): JSONObject {
        val temporary = store.workbookDir(id).listFiles().orEmpty().single { Regex("workbook-[0-9]+\\.tmp").matches(it.name) }
        val receipts = JSONObject(temporary.readText()).getJSONObject("commandJournal").getJSONArray("receipts")
        return receipts.getJSONObject(receipts.length() - 1).getJSONObject("command")
    }

    private fun assertFrozenRequest(frozen: JSONObject, accepted: WorkbookCommand.AddWorksheet) {
        assertEquals(frozen.getString("commandId"), accepted.commandId)
        assertEquals(frozen.getString("workbookId"), accepted.workbookId)
        assertEquals(frozen.getString("originWindowId"), accepted.originWindowId)
        assertEquals(frozen.getLong("expectedRevision"), accepted.expectedRevision)
        assertEquals(frozen.getString("worksheetId"), accepted.worksheetId)
        assertEquals(frozen.getString("name"), accepted.name)
    }

    private fun owner(): ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun newModel(owner: ViewModelStoreOwner, targetStore: WorkbookStore, id: String): WorkbookViewModel {
        lateinit var result: WorkbookViewModel
        onMain {
            result = ViewModelProvider(owner, WorkbookViewModelFactory(context.applicationContext as Application, targetStore, id))[WorkbookViewModel::class.java]
        }
        return result
    }

    private suspend fun awaitLoaded(model: WorkbookViewModel) {
        val state = model.state.first { !it.loading }
        assertEquals(null, state.loadError)
    }

    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
}
