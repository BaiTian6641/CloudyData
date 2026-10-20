package io.github.baitian6641.cloudydata

import android.content.Context
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.canonicalJson
import io.github.baitian6641.cloudydata.ui.HomeFavoritePhase
import io.github.baitian6641.cloudydata.ui.HomeLibraryController
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

/** S03.3 / F01,F64 / Q17,Q19,Q34: real Android persistence and Home intent ownership. */
@RunWith(AndroidJUnit4::class)
class SetFavoriteCommandTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "set-favorite-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun exactDesiredValueIsAcceptedOnceAndDurableUndoRetryAndRedoPreserveAllOtherContent() = runBlocking {
        val book = store.create("Book")
        val command = favorite(book, "favorite-on", true)
        val coordinator = WorkbookStore(root).coordinators.open(book.id)
        val accepted = coordinator.execute(command)
        assertFalse(accepted.duplicate)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        assertEquals(
            book.copy(favorite = true, revision = accepted.document.revision, updatedAtEpochMs = accepted.document.updatedAtEpochMs),
            accepted.document,
        )
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(accepted.document, checkpoint.document)
        assertEquals(listOf(book), checkpoint.undo)
        assertTrue(checkpoint.redo.isEmpty())
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(command, checkpoint.receipts.single().command)
        assertEquals(book.revision + 1, checkpoint.receipts.single().acceptedRevision)

        val undone = WorkbookStore(root).coordinators.open(book.id).undo(accepted.document.revision)
        assertFalse(undone.favorite)
        assertEquals(book.revision + 2, undone.revision)
        assertEquals(book.worksheets, undone.worksheets)
        assertEquals(book.activeWorksheetId, undone.activeWorksheetId)
        val beforeRetry = metadata(book.id).readBytes()
        val duplicate = WorkbookStore(root).coordinators.open(book.id).execute(command)
        assertTrue(duplicate.duplicate)
        assertEquals(book.revision + 1, duplicate.acceptedRevision)
        assertEquals(undone, duplicate.document)
        assertArrayEquals("original retry cannot toggle favorite back after undo", beforeRetry, metadata(book.id).readBytes())

        val redone = WorkbookStore(root).coordinators.open(book.id).redo(undone.revision)
        assertTrue(redone.favorite)
        assertEquals(book.revision + 3, redone.revision)
        assertEquals(book.worksheets, redone.worksheets)
        assertEquals(book.activeWorksheetId, redone.activeWorksheetId)
        assertEquals(redone, WorkbookStore(root).load(book.id))
    }

    @Test
    fun duplicateAfterForeignRenameReturnsCurrentDocumentAndChangedValueOrOwnershipIsRefused() = runBlocking {
        val book = store.create("Book")
        val coordinator = store.coordinators.open(book.id)
        val command = favorite(book, "fixed-favorite-request", true)
        val first = coordinator.execute(command)
        val foreign = coordinator.execute(
            WorkbookCommand.Rename("foreign-name", book.id, "foreign-window", first.document.revision, "Foreign accepted title"),
        ).document
        val reconstructed = WorkbookStore(root).coordinators.open(book.id)
        val before = metadata(book.id).readBytes()
        val duplicate = reconstructed.execute(command)
        assertTrue(duplicate.duplicate)
        assertEquals(first.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(foreign, duplicate.document)
        assertEquals("Foreign accepted title", duplicate.document.name)
        assertTrue(duplicate.document.favorite)
        listOf(
            command.copy(value = false),
            command.copy(originWindowId = "different-owner"),
            command.copy(expectedRevision = foreign.revision),
            command.copy(workbookId = "different-workbook"),
        ).forEach { changed ->
            assertTrue("the same ID cannot acquire a new intent", runCatching { reconstructed.execute(changed) }.isFailure)
            assertArrayEquals(before, metadata(book.id).readBytes())
            assertEquals(foreign, reconstructed.state.value.document)
        }
    }

    @Test
    fun noOpDesiredValueHasOneReceiptButAddsNoUndoStepAndPreservesTheRedoBranch() = runBlocking {
        val book = store.create("Book")
        val coordinator = store.coordinators.open(book.id)
        val on = coordinator.execute(favorite(book, "on-before-undo", true)).document
        val off = coordinator.undo(on.revision)
        val before = WorkbookStore(root).loadCheckpoint(book.id)
        assertFalse(off.favorite)
        assertTrue(before.undo.isEmpty())
        assertEquals(listOf(on), before.redo)
        val noOp = favorite(off, "already-off", false)
        val accepted = coordinator.execute(noOp)
        assertFalse(accepted.duplicate)
        assertEquals(book.revision + 3, accepted.acceptedRevision)
        assertFalse(accepted.document.favorite)
        val after = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(before.undo, after.undo)
        assertEquals(before.redo, after.redo)
        assertEquals(listOf("on-before-undo", "already-off"), after.receipts.map { it.command.commandId })
        val bytes = metadata(book.id).readBytes()
        assertTrue(WorkbookStore(root).coordinators.open(book.id).execute(noOp).duplicate)
        assertArrayEquals(bytes, metadata(book.id).readBytes())
        val redone = WorkbookStore(root).coordinators.open(book.id).redo(accepted.document.revision)
        assertTrue(redone.favorite)
        assertEquals(book.revision + 4, redone.revision)
    }

    @Test
    fun homeSuccessRefreshesActualFavoriteAndRevisionBeforeClearingTheAction() = runBlocking {
        val book = store.create("Book")
        val home = HomeLibraryController(store, "home-success")
        home.reload()
        assertTrue(home.state.value.loaded)
        assertEquals(null, home.state.value.loadError)
        assertFalse(home.state.value.workbooks.single { it.id == book.id }.favorite)
        assertEquals(book.revision, home.state.value.workbooks.single { it.id == book.id }.revision)
        val accepted = checkNotNull(home.setFavorite(book.id, book.revision, true))
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        assertTrue(accepted.document.favorite)
        assertTrue(home.state.value.favoriteActions.isEmpty())
        val summary = home.state.value.workbooks.single { it.id == book.id }
        assertTrue(summary.favorite)
        assertEquals(accepted.document.revision, summary.revision)
        assertEquals(WorkbookStore(root).list(), home.state.value.workbooks)
        val before = metadata(book.id).readBytes()
        assertEquals(null, home.retryFavorite(book.id))
        home.reload()
        assertArrayEquals(before, metadata(book.id).readBytes())
        val reopened = HomeLibraryController(WorkbookStore(root), "reopened-home")
        reopened.reload()
        assertTrue(reopened.state.value.workbooks.single().favorite)
        assertEquals(accepted.document.revision, reopened.state.value.workbooks.single().revision)
        assertTrue(reopened.state.value.favoriteActions.isEmpty())
    }

    @Test
    fun staleOtherHomeRetainsItsProposalAcrossReloadAndDismissalNeverUndoesTheWinner() = runBlocking {
        val book = store.create("Book")
        val first = HomeLibraryController(store, "home-first")
        val second = HomeLibraryController(store, "home-second")
        first.reload()
        second.reload()
        val staleRevision = second.state.value.workbooks.single().revision
        val winner = checkNotNull(first.setFavorite(book.id, book.revision, true))
        val before = metadata(book.id).readBytes()
        assertEquals(null, second.setFavorite(book.id, staleRevision, false))
        val proposal = checkNotNull(second.state.value.favoriteActions[book.id])
        assertEquals(HomeFavoritePhase.CONFLICT, proposal.phase)
        assertFalse(proposal.request.value)
        assertEquals(staleRevision, proposal.request.expectedRevision)
        assertEquals("home-second", proposal.request.originWindowId)
        assertTrue(proposal.message.orEmpty().isNotBlank())
        assertArrayEquals(before, metadata(book.id).readBytes())
        second.reload()
        assertEquals(proposal.request, second.state.value.favoriteActions[book.id]!!.request)
        assertEquals(HomeFavoritePhase.CONFLICT, second.state.value.favoriteActions[book.id]!!.phase)
        assertTrue(second.state.value.workbooks.single().favorite)
        assertEquals(winner.document.revision, second.state.value.workbooks.single().revision)
        assertEquals(null, second.retryFavorite(book.id))
        assertArrayEquals(before, metadata(book.id).readBytes())
        second.dismissFavorite(book.id)
        assertTrue(second.state.value.favoriteActions.isEmpty())
        assertArrayEquals("dismiss removes a local proposal, never the accepted favorite", before, metadata(book.id).readBytes())
        assertTrue(WorkbookStore(root).load(book.id).favorite)
        val correctedRevision = second.state.value.workbooks.single().revision
        val corrected = checkNotNull(second.setFavorite(book.id, correctedRevision, false))
        assertFalse(corrected.document.favorite)
        assertEquals(book.revision + 2, corrected.document.revision)
        assertNotEquals(proposal.request.commandId, WorkbookStore(root).loadCheckpoint(book.id).receipts.last().command.commandId)
    }

    @Test
    fun realWriteDenialRetainsFrozenIntentAcrossReloadAllowsOtherBookAndDoubleRetryAcceptsOnce() = runBlocking {
        val observing = AtomicBoolean(false)
        val attempts = AtomicInteger()
        val replacements = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) when (phase) {
                WorkbookCommitPhase.TEMP_SYNCED -> attempts.incrementAndGet()
                WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
            }
        }
        val first = hookedStore.create("Denied workbook")
        val other = hookedStore.create("Independent workbook")
        val home = HomeLibraryController(hookedStore, "home-denial")
        home.reload()
        val before = metadata(first.id).readBytes()
        val directory = store.workbookDir(first.id)
        val mode = Os.stat(directory.path).st_mode and 0x1ff
        observing.set(true)
        try {
            Os.chmod(directory.path, 0x16d)
            assertEquals(0x16d, Os.stat(directory.path).st_mode and 0x1ff)
            assertEquals(null, home.setFavorite(first.id, first.revision, true))
            val failed = checkNotNull(home.state.value.favoriteActions[first.id])
            assertEquals(HomeFavoritePhase.FAILED, failed.phase)
            assertTrue(failed.message.orEmpty().contains("Permission denied", ignoreCase = true) || failed.message.orEmpty().contains("EACCES"))
            assertTrue(failed.request.value)
            assertEquals(first.revision, failed.request.expectedRevision)
            assertEquals("home-denial", failed.request.originWindowId)
            assertArrayEquals(before, metadata(first.id).readBytes())
            assertEquals(0, attempts.get())
            assertEquals(0, replacements.get())
            home.reload()
            assertEquals(failed.request, home.state.value.favoriteActions[first.id]!!.request)
            assertEquals(HomeFavoritePhase.FAILED, home.state.value.favoriteActions[first.id]!!.phase)
            assertEquals(null, home.setFavorite(first.id, first.revision, false))
            assertEquals(failed.request, home.state.value.favoriteActions[first.id]!!.request)
            val independent = checkNotNull(home.setFavorite(other.id, other.revision, true))
            assertTrue(independent.document.favorite)
            assertEquals(other.revision + 1, independent.document.revision)
            assertEquals(failed.request, home.state.value.favoriteActions[first.id]!!.request)
            assertArrayEquals(before, metadata(first.id).readBytes())
            Os.chmod(directory.path, mode)

            val retries = listOf(
                async(Dispatchers.Default) { home.retryFavorite(first.id) },
                async(Dispatchers.Default) { home.retryFavorite(first.id) },
            ).map { it.await() }
            assertEquals("only one explicit retry owns the retained request", 1, retries.count { it != null })
            val accepted = retries.single { it != null }!!
            assertEquals(first.revision + 1, accepted.acceptedRevision)
            assertTrue(accepted.document.favorite)
            val checkpoint = WorkbookStore(root).loadCheckpoint(first.id)
            assertEquals(1, checkpoint.receipts.size)
            assertEquals(failed.request, checkpoint.receipts.single().command)
            assertEquals(first.revision + 1, checkpoint.receipts.single().acceptedRevision)
            assertEquals(listOf(first), checkpoint.undo)
            assertTrue(home.state.value.favoriteActions.isEmpty())
            assertTrue(home.state.value.workbooks.all { it.favorite })
            assertEquals(2, attempts.get())
            assertEquals(2, replacements.get())
            val after = metadata(first.id).readBytes()
            assertEquals(null, home.retryFavorite(first.id))
            assertArrayEquals(after, metadata(first.id).readBytes())
            assertEquals(2, replacements.get())
        } finally {
            Os.chmod(directory.path, mode)
        }
    }

    @Test
    fun acknowledgementLossAfterRealReplacementRefreshesAcceptedFavoriteAndNeverRepeatsWrite() = runBlocking {
        val observing = AtomicBoolean(false)
        val loseOnce = AtomicBoolean(false)
        val replacements = AtomicInteger()
        val errors = AtomicInteger()
        val frozen = AtomicReference<WorkbookCommand.SetFavorite>()
        lateinit var home: HomeLibraryController
        var workbookId: String? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get() && phase == WorkbookCommitPhase.REPLACED) {
                replacements.incrementAndGet()
                if (loseOnce.compareAndSet(true, false)) {
                    frozen.set(checkNotNull(home.state.value.favoriteActions[checkNotNull(workbookId)]).request)
                    errors.incrementAndGet()
                    throw IOException("One-shot lost favorite acknowledgement after actual replacement")
                }
            }
        }
        val book = hookedStore.create("Book")
        workbookId = book.id
        home = HomeLibraryController(hookedStore, "home-lost-ack")
        home.reload()
        observing.set(true)
        loseOnce.set(true)
        val accepted = checkNotNull(home.setFavorite(book.id, book.revision, true))
        assertTrue("the exact durable receipt proves acceptance after acknowledgement loss", accepted.duplicate)
        assertEquals(book.revision + 1, accepted.acceptedRevision)
        assertTrue(accepted.document.favorite)
        assertEquals(1, errors.get())
        assertEquals(1, replacements.get())
        assertTrue(home.state.value.favoriteActions.isEmpty())
        assertTrue(home.state.value.workbooks.single().favorite)
        assertEquals(accepted.document.revision, home.state.value.workbooks.single().revision)
        val command = checkNotNull(frozen.get())
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(command, checkpoint.receipts.single().command)
        val before = metadata(book.id).readBytes()
        assertEquals(null, home.retryFavorite(book.id))
        val duplicate = hookedStore.coordinators.open(book.id).execute(command)
        assertTrue(duplicate.duplicate)
        assertEquals(accepted.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(accepted.document, duplicate.document)
        assertArrayEquals(before, metadata(book.id).readBytes())
        assertEquals(1, replacements.get())
    }

    @Test
    fun acceptedFavoriteWithUnreadableProjectionRetainsAcceptedRevisionAndRetryRefreshesWithoutAnotherWrite() = runBlocking {
        val observing = AtomicBoolean(false)
        val corruptOnce = AtomicBoolean(false)
        val replacements = AtomicInteger()
        val acceptedBytes = AtomicReference<ByteArray>()
        var workbookId: String? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get() && phase == WorkbookCommitPhase.REPLACED) {
                replacements.incrementAndGet()
                if (corruptOnce.compareAndSet(true, false)) {
                    val canonicalFile = metadata(checkNotNull(workbookId))
                    acceptedBytes.set(canonicalFile.readBytes())
                    // A constructed post-commit read failure, using the actual
                    // canonical file. Preserve exact accepted bytes for repair.
                    canonicalFile.writeText("{\"postCommitProjection\":")
                }
            }
        }
        val book = hookedStore.create("Book")
        workbookId = book.id
        val home = HomeLibraryController(hookedStore, "home-projection-failure")
        home.reload()
        observing.set(true)
        corruptOnce.set(true)
        assertEquals(null, home.setFavorite(book.id, book.revision, true))
        val failed = checkNotNull(home.state.value.favoriteActions[book.id])
        assertEquals(HomeFavoritePhase.FAILED, failed.phase)
        assertEquals(book.revision + 1, failed.acceptedRevision)
        assertTrue("the failure must distinguish accepted content from an unconfirmed write", failed.message.orEmpty().contains("saved locally"))
        assertTrue(failed.request.value)
        assertEquals(book.revision, failed.request.expectedRevision)
        assertFalse(home.state.value.workbooks.single().favorite)
        assertTrue(runCatching { WorkbookStore(root).load(book.id) }.isFailure)
        assertEquals(1, replacements.get())
        val unreadableEvidence = metadata(book.id).readBytes()
        home.reload()
        assertEquals(failed.request, home.state.value.favoriteActions[book.id]!!.request)
        assertEquals(book.revision + 1, home.state.value.favoriteActions[book.id]!!.acceptedRevision)
        assertArrayEquals(unreadableEvidence, metadata(book.id).readBytes())

        // Drive the cached coordinator through a real failed save/reconciliation,
        // so the next favorite retry fails at recovery admission before it can
        // return a CommandOutcome or inspect its already committed receipt.
        val cachedCoordinator = hookedStore.coordinators.open(book.id)
        val failedEdit = runCatching {
            cachedCoordinator.edit(book.revision + 1) { it.copy(name = "Must not save while metadata is unreadable") }
        }.exceptionOrNull()
        assertTrue("the corrupt real canonical file must refuse the save", failedEdit != null)
        assertEquals(book.revision + 1, cachedCoordinator.state.value.document.revision)
        assertEquals("Book", cachedCoordinator.state.value.document.name)
        assertTrue(cachedCoordinator.state.value.document.favorite)
        assertArrayEquals(unreadableEvidence, metadata(book.id).readBytes())
        assertEquals(null, home.retryFavorite(book.id))
        val blockedRetry = checkNotNull(home.state.value.favoriteActions[book.id])
        assertEquals(HomeFavoritePhase.FAILED, blockedRetry.phase)
        assertEquals("a later admission failure cannot erase proven earlier acceptance", book.revision + 1, blockedRetry.acceptedRevision)
        assertEquals(failed.request, blockedRetry.request)
        assertTrue(blockedRetry.message.orEmpty().contains("saved locally"))
        assertTrue("this retry failed before outcome creation at the real recovery guard", blockedRetry.message.orEmpty().contains("recovery", ignoreCase = true))
        assertArrayEquals(unreadableEvidence, metadata(book.id).readBytes())
        assertEquals(1, replacements.get())

        metadata(book.id).writeBytes(checkNotNull(acceptedBytes.get()))
        val repairedBytes = metadata(book.id).readBytes()
        val recoveredDocument = cachedCoordinator.reload()
        assertEquals(book.revision + 1, recoveredDocument.revision)
        assertTrue(recoveredDocument.favorite)
        val retried = checkNotNull(home.retryFavorite(book.id))
        assertTrue("the same request was already committed", retried.duplicate)
        assertEquals(book.revision + 1, retried.acceptedRevision)
        assertTrue(retried.document.favorite)
        assertTrue(home.state.value.favoriteActions.isEmpty())
        assertTrue(home.state.value.workbooks.single().favorite)
        assertEquals(book.revision + 1, home.state.value.workbooks.single().revision)
        val checkpoint = WorkbookStore(root).loadCheckpoint(book.id)
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(failed.request, checkpoint.receipts.single().command)
        assertEquals(listOf(book), checkpoint.undo)
        assertArrayEquals("repairing the projection requires no second favorite commit", repairedBytes, metadata(book.id).readBytes())
        assertEquals(1, replacements.get())
    }

    @Test
    fun slowFavoriteCannotBeReplacedOrDismissedAndNeverMutatesTheOtherWorkbook() = runBlocking {
        val observing = AtomicBoolean(false)
        val pauseFirst = AtomicBoolean(false)
        val writerSynced = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val replacements = AtomicInteger()
        val hookedStore = WorkbookStore(root) { phase ->
            if (observing.get()) when (phase) {
                WorkbookCommitPhase.TEMP_SYNCED -> if (pauseFirst.compareAndSet(true, false)) {
                    writerSynced.countDown()
                    check(releaseWriter.await(10, TimeUnit.SECONDS)) { "The pending favorite writer was not released." }
                }
                WorkbookCommitPhase.REPLACED -> replacements.incrementAndGet()
            }
        }
        val book = hookedStore.create("Pending workbook")
        val other = hookedStore.create("Unrelated workbook")
        val before = metadata(book.id).readBytes()
        val otherBefore = metadata(other.id).readBytes()
        val home = HomeLibraryController(hookedStore, "home-pending")
        home.reload()
        observing.set(true)
        pauseFirst.set(true)
        val operation = async(Dispatchers.Default) { home.setFavorite(book.id, book.revision, true) }
        try {
            assertTrue(writerSynced.await(5, TimeUnit.SECONDS))
            val pending = checkNotNull(home.state.value.favoriteActions[book.id])
            assertEquals(HomeFavoritePhase.SAVING, pending.phase)
            assertTrue(pending.request.value)
            assertFalse(home.state.value.workbooks.single { it.id == book.id }.favorite)
            withTimeout(2_000) {
                assertEquals(null, home.setFavorite(book.id, book.revision, false))
                home.dismissFavorite(book.id)
            }
            assertEquals(pending.request, home.state.value.favoriteActions[book.id]!!.request)
            assertEquals(HomeFavoritePhase.SAVING, home.state.value.favoriteActions[book.id]!!.phase)
            assertTrue(home.state.value.favoriteActions.keys == setOf(book.id))
            assertArrayEquals(before, metadata(book.id).readBytes())
            assertArrayEquals(otherBefore, metadata(other.id).readBytes())
            assertEquals(0, replacements.get())
            releaseWriter.countDown()
            val accepted = checkNotNull(withTimeout(5_000) { operation.await() })
            assertTrue(accepted.document.favorite)
            assertEquals(book.revision + 1, accepted.document.revision)
            assertTrue(home.state.value.favoriteActions.isEmpty())
            assertTrue(home.state.value.workbooks.single { it.id == book.id }.favorite)
            assertFalse(home.state.value.workbooks.single { it.id == other.id }.favorite)
            assertEquals(other, WorkbookStore(root).load(other.id))
            assertArrayEquals(otherBefore, metadata(other.id).readBytes())
            assertEquals(1, replacements.get())
        } finally {
            releaseWriter.countDown()
        }
    }

    @Test
    fun malformedFavoritePayloadAndUnknownStoredKindRefuseWithoutRepairingValidChecksumEvidence() = runBlocking {
        listOf("string desired value", "future command kind", "future command version").forEachIndexed { index, fault ->
            val book = store.create("Unsupported favorite $index")
            val coordinator = store.coordinators.open(book.id)
            val accepted = coordinator.execute(favorite(book, "favorite-$index", true)).document
            val json = JSONObject(metadata(book.id).readText())
            val journal = json.getJSONObject("commandJournal")
            val request = journal.getJSONArray("receipts").getJSONObject(0).getJSONObject("command")
            when (fault) {
                "string desired value" -> request.put("value", "true")
                "future command kind" -> request.put("kind", "futureFavorite")
                else -> request.put("version", 999)
            }
            val unsigned = JSONObject(journal.toString()).apply { remove("sha256") }
            journal.put("sha256", MessageDigest.getInstance("SHA-256").digest(canonicalJson(unsigned).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) })
            metadata(book.id).writeText(json.toString())
            val evidence = metadata(book.id).readBytes()
            val error = runCatching { WorkbookStore(root).loadCheckpoint(book.id) }.exceptionOrNull()
            assertTrue("$fault must be refused", error != null)
            assertFalse("the checksum is valid; the command itself is unsupported", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
            assertTrue(runCatching { WorkbookStore(root).coordinators.open(book.id) }.isFailure)
            assertTrue(runCatching { WorkbookStore(root).save(accepted.copy(name = "Must not normalize unsupported intent")) }.isFailure)
            assertArrayEquals(evidence, metadata(book.id).readBytes())
        }
    }

    private fun favorite(document: WorkbookDocument, commandId: String, value: Boolean) =
        WorkbookCommand.SetFavorite(commandId, document.id, "favorite-window", document.revision, value)

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")
}
