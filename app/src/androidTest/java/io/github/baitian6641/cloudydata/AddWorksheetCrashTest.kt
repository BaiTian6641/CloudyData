package io.github.baitian6641.cloudydata

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CommandReceipt
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.debug.JournalCrashProbeService
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.3 / Q17,Q34: a real single writer dies while adding one stable worksheet. */
@RunWith(AndroidJUnit4::class)
class AddWorksheetCrashTest {
    private lateinit var context: Context
    private lateinit var runId: String
    private lateinit var root: File
    private lateinit var original: WorkbookDocument
    private lateinit var firstSheet: WorksheetDef
    private lateinit var addedSheet: WorksheetDef
    private lateinit var request: WorkbookCommand.AddWorksheet

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        runId = UUID.randomUUID().toString()
        root = JournalCrashProbeService.probeRoot(context, runId)
        original = WorkbookStore(root).create("Worksheet recovery workbook")
        firstSheet = WorksheetDef(
            id = original.worksheets.single().id,
            name = "Sheet 1",
            rowFieldId = null,
            measureFieldId = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = true,
        )
        addedSheet = WorksheetDef(
            id = UUID.randomUUID().toString(),
            name = "Recovery worksheet",
            rowFieldId = null,
            measureFieldId = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = true,
        )
        request = WorkbookCommand.AddWorksheet(
            commandId = UUID.randomUUID().toString(),
            workbookId = original.id,
            originWindowId = JournalCrashProbeService.ORIGIN_WINDOW,
            expectedRevision = original.revision,
            worksheetId = addedSheet.id,
            name = addedSheet.name,
        )
        assertEquals(listOf(firstSheet), original.worksheets)
        assertEquals(firstSheet.id, original.activeWorksheetId)
        assertTrue(WorkbookStore(root).loadCheckpoint(original.id).receipts.isEmpty())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun killBeforeReplacementLeavesOneSheetThenIdenticalRetryAddsExactlyOnce() = runBlocking {
        val acceptedBytes = metadataFile().readBytes()
        killWriterAt(WorkbookCommitPhase.TEMP_SYNCED)
        val candidates = abandonedMetadataTemps()
        assertEquals("The synced unpublished add must leave one actual candidate", 1, candidates.size)
        assertTrue(candidates.single().length() > 0)
        assertArrayEquals("Unpublished addition must not alter canonical bytes", acceptedBytes, metadataFile().readBytes())

        val store = WorkbookStore(root)
        val reopened = store.coordinators.open(original.id)
        assertEquals(original, reopened.state.value.document)
        assertWorksheetDefinitions(reopened.state.value.document, added = false)
        assertTrue("Valid canonical reopen must clean its abandoned candidate", abandonedMetadataTemps().isEmpty())
        assertFalse(candidates.single().exists())
        assertArrayEquals("Recovery must not rewrite the accepted one-sheet workbook", acceptedBytes, metadataFile().readBytes())
        assertTrue("Unpublished AddWorksheet must not gain a receipt", store.loadCheckpoint(original.id).receipts.isEmpty())
        assertFalse(reopened.state.value.canUndo)
        assertFalse(reopened.state.value.canRedo)

        val retried = reopened.execute(request)
        assertFalse(retried.duplicate)
        assertEquals(original.revision + 1, retried.acceptedRevision)
        assertEquals(original.revision + 1, retried.document.revision)
        assertWorksheetDefinitions(retried.document, added = true)
        assertEquals(listOf(CommandReceipt(request, original.revision + 1)), store.loadCheckpoint(original.id).receipts)
        val addedBytes = metadataFile().readBytes()
        val duplicate = reopened.execute(request)
        assertTrue(duplicate.duplicate)
        assertEquals(retried.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(retried.document, duplicate.document)
        assertArrayEquals("Duplicate retry must not write or add another worksheet", addedBytes, metadataFile().readBytes())

        val secondReopen = WorkbookStore(root).coordinators.open(original.id)
        assertEquals(retried.document, secondReopen.state.value.document)
        assertWorksheetDefinitions(secondReopen.state.value.document, added = true)
        val duplicateAfterReopen = secondReopen.execute(request)
        assertTrue(duplicateAfterReopen.duplicate)
        assertEquals(retried.document, duplicateAfterReopen.document)
        assertArrayEquals(addedBytes, metadataFile().readBytes())
    }

    @Test
    fun killAfterReplacementRecoversOneAddedSheetAndReceiptAcrossUndoRedo() = runBlocking {
        killWriterAt(WorkbookCommitPhase.REPLACED)
        assertTrue("Atomic replacement consumes the temporary candidate", abandonedMetadataTemps().isEmpty())
        val store = WorkbookStore(root)
        val reopened = store.coordinators.open(original.id)
        val durable = reopened.state.value.document
        assertEquals(original.revision + 1, durable.revision)
        assertWorksheetDefinitions(durable, added = true)
        assertEquals(listOf(CommandReceipt(request, original.revision + 1)), store.loadCheckpoint(original.id).receipts)
        assertTrue(reopened.state.value.canUndo)
        assertFalse(reopened.state.value.canRedo)
        val acceptedBytes = metadataFile().readBytes()
        val duplicate = reopened.execute(request)
        assertTrue(duplicate.duplicate)
        assertEquals(durable.revision, duplicate.acceptedRevision)
        assertEquals(durable, duplicate.document)
        assertArrayEquals(acceptedBytes, metadataFile().readBytes())

        val undone = reopened.undo(durable.revision)
        assertEquals(durable.revision + 1, undone.revision)
        assertWorksheetDefinitions(undone, added = false)
        val afterUndoStore = WorkbookStore(root)
        val afterUndo = afterUndoStore.coordinators.open(original.id)
        assertEquals(undone, afterUndo.state.value.document)
        assertWorksheetDefinitions(afterUndo.state.value.document, added = false)
        assertFalse(afterUndo.state.value.canUndo)
        assertTrue(afterUndo.state.value.canRedo)
        assertEquals(listOf(CommandReceipt(request, durable.revision)), afterUndoStore.loadCheckpoint(original.id).receipts)
        val undoneBytes = metadataFile().readBytes()
        val retryAfterUndo = afterUndo.execute(request)
        assertTrue(retryAfterUndo.duplicate)
        assertEquals(durable.revision, retryAfterUndo.acceptedRevision)
        assertEquals(undone, retryAfterUndo.document)
        assertArrayEquals("An acknowledged request must not recreate an intentionally undone sheet", undoneBytes, metadataFile().readBytes())

        val redone = afterUndo.redo(undone.revision)
        assertEquals(undone.revision + 1, redone.revision)
        assertWorksheetDefinitions(redone, added = true)
        val afterRedoStore = WorkbookStore(root)
        val afterRedo = afterRedoStore.coordinators.open(original.id)
        assertEquals(redone, afterRedo.state.value.document)
        assertWorksheetDefinitions(afterRedo.state.value.document, added = true)
        assertTrue(afterRedo.state.value.canUndo)
        assertFalse(afterRedo.state.value.canRedo)
        assertEquals(listOf(CommandReceipt(request, durable.revision)), afterRedoStore.loadCheckpoint(original.id).receipts)
        val redoneBytes = metadataFile().readBytes()
        val retryAfterRedo = afterRedo.execute(request)
        assertTrue(retryAfterRedo.duplicate)
        assertEquals(durable.revision, retryAfterRedo.acceptedRevision)
        assertEquals(redone, retryAfterRedo.document)
        assertArrayEquals(redoneBytes, metadataFile().readBytes())
    }

    private fun assertWorksheetDefinitions(document: WorkbookDocument, added: Boolean) {
        assertEquals(if (added) listOf(firstSheet, addedSheet) else listOf(firstSheet), document.worksheets)
        assertEquals("The stored reopen default must remain the original worksheet", firstSheet.id, document.activeWorksheetId)
        assertEquals(original.id, document.id)
        assertEquals(original.name, document.name)
        assertEquals(original.snapshot, document.snapshot)
    }

    private fun metadataFile(): File = File(File(File(root, "workbooks"), original.id), "workbook.json")

    private fun abandonedMetadataTemps(): List<File> = metadataFile().parentFile!!.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.matches(Regex("workbook-[0-9]+\\.tmp")) }
        .sortedBy { it.name }

    private fun killWriterAt(phase: WorkbookCommitPhase) {
        val completed = CountDownLatch(1)
        val death = CountDownLatch(1)
        val started = CountDownLatch(1)
        val reachedBoundary = CountDownLatch(1)
        val pid = AtomicInteger(-1)
        val boundaryPid = AtomicInteger(-1)
        val observedPhase = AtomicInteger(-1)
        val failure = AtomicReference<String?>(null)
        val didConnect = AtomicBoolean()
        val linkedBinder = AtomicReference<IBinder?>(null)
        val recipient = IBinder.DeathRecipient {
            Log.i(JournalCrashProbeService.TAG, "ADD_BINDER_DEATH pid=${pid.get()} expectedPhase=$phase testPid=${Process.myPid()}")
            death.countDown()
            completed.countDown()
        }
        val callback = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                JournalCrashProbeService.MSG_STARTED -> {
                    pid.set(message.arg1)
                    started.countDown()
                }
                JournalCrashProbeService.MSG_BOUNDARY -> {
                    boundaryPid.set(message.arg1)
                    observedPhase.set(message.arg2)
                    reachedBoundary.countDown()
                }
                JournalCrashProbeService.MSG_FAILED -> {
                    failure.set(message.data.getString(JournalCrashProbeService.EXTRA_ERROR))
                    completed.countDown()
                }
                JournalCrashProbeService.MSG_FINISHED -> {
                    failure.set("AddWorksheet writer completed instead of dying at $phase")
                    completed.countDown()
                }
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!didConnect.compareAndSet(false, true)) return
                try {
                    binder.linkToDeath(recipient, 0)
                    linkedBinder.set(binder)
                    Messenger(binder).send(Message.obtain(null, JournalCrashProbeService.MSG_RUN).apply {
                        replyTo = callback
                    })
                } catch (error: Exception) {
                    failure.set("Could not begin linked AddWorksheet writer: $error")
                    completed.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.i(JournalCrashProbeService.TAG, "ADD_DISCONNECTED pid=${pid.get()} expectedPhase=$phase")
            }

            override fun onNullBinding(name: ComponentName) {
                failure.set("AddWorksheet probe returned a null binding")
                completed.countDown()
            }
        }
        val intent = Intent(context, JournalCrashProbeService::class.java)
            .putExtra(JournalCrashProbeService.EXTRA_RUN_ID, runId)
            .putExtra(JournalCrashProbeService.EXTRA_WORKBOOK_ID, original.id)
            .putExtra(JournalCrashProbeService.EXTRA_COMMAND_ID, request.commandId)
            .putExtra(JournalCrashProbeService.EXTRA_EXPECTED_REVISION, original.revision)
            .putExtra(JournalCrashProbeService.EXTRA_WORKSHEET_ID, addedSheet.id)
            .putExtra(JournalCrashProbeService.EXTRA_KILL_PHASE, phase.name)
            .putExtra(JournalCrashProbeService.EXTRA_KIND, JournalCrashProbeService.KIND_ADD_WORKSHEET)
            .putExtra(JournalCrashProbeService.EXTRA_VALUE, addedSheet.name)
        var bound = false
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            assertTrue("Explicit AddWorksheet debug probe must bind", bound)
            assertTrue("Writer must terminate within the bounded timeout; failure=${failure.get()}", completed.await(15, TimeUnit.SECONDS))
            assertEquals("Writer must reach SIGKILL instead of an ordinary error", null, failure.get())
            assertTrue("Boundary callbacks alone do not prove writer death", death.await(2, TimeUnit.SECONDS))
            assertTrue("Writer must identify its real native PID", started.await(2, TimeUnit.SECONDS))
            assertTrue("Writer must report the exact reached boundary", reachedBoundary.await(2, TimeUnit.SECONDS))
            assertTrue(pid.get() > 0)
            assertNotEquals("Probe must kill a separate Android writer process", Process.myPid(), pid.get())
            assertEquals(pid.get(), boundaryPid.get())
            assertEquals(phase.ordinal, observedPhase.get())
            Log.i(JournalCrashProbeService.TAG, "ADD_VERIFIED_DEATH pid=${pid.get()} phase=$phase workbook=${original.id} command=${request.commandId}")
        } finally {
            if (bound) context.unbindService(connection)
            linkedBinder.get()?.let { binder -> runCatching { binder.unlinkToDeath(recipient, 0) } }
        }
    }
}
