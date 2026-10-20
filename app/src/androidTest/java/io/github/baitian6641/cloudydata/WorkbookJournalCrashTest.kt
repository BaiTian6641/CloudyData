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
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.debug.JournalCrashProbeService
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
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

/** S03.3 / Q17,Q34: real SIGKILL of a separate Android writer process. */
@RunWith(AndroidJUnit4::class)
class WorkbookJournalCrashTest {
    private lateinit var context: Context
    private lateinit var runId: String
    private lateinit var root: File
    private lateinit var original: WorkbookDocument
    private lateinit var renamed: WorkbookDocument
    private lateinit var rename: WorkbookCommand.Rename
    private lateinit var chart: WorkbookCommand.SetChart

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        runId = UUID.randomUUID().toString()
        root = JournalCrashProbeService.probeRoot(context, runId)
        val store = WorkbookStore(root)
        original = store.create("Workbook A")
        rename = WorkbookCommand.Rename(
            commandId = UUID.randomUUID().toString(),
            workbookId = original.id,
            originWindowId = "initial-test-window",
            expectedRevision = original.revision,
            name = "Workbook B",
        )
        renamed = store.coordinators.open(original.id).execute(rename).document
        chart = WorkbookCommand.SetChart(
            commandId = UUID.randomUUID().toString(),
            workbookId = original.id,
            originWindowId = JournalCrashProbeService.ORIGIN_WINDOW,
            expectedRevision = renamed.revision,
            worksheetId = original.worksheets.single().id,
            options = ChartOptions(title = "Chart C"),
        )
        assertEquals(original.revision + 1, renamed.revision)
        assertEquals("Workbook B", WorkbookStore(root).load(original.id).name)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun killAfterTemporaryFileSyncKeepsAcceptedRenameAndItsHistory() = runBlocking {
        val acceptedMetadata = metadataFile().readBytes()
        val acceptedCheckpoint = WorkbookStore(root).loadCheckpoint(original.id)
        assertEquals(renamed, acceptedCheckpoint.document)
        killWriterAt(WorkbookCommitPhase.TEMP_SYNCED)
        val abandoned = abandonedMetadataTemps()
        assertEquals("SIGKILL after fsync must leave one known unpublished metadata temp", 1, abandoned.size)
        assertTrue("The fully written abandoned candidate must contain bytes", abandoned.single().length() > 0)
        assertArrayEquals("The unpublished chart cannot replace accepted canonical metadata", acceptedMetadata, metadataFile().readBytes())

        val reopenedStore = WorkbookStore(root)
        val reopened = reopenedStore.coordinators.open(original.id)
        val durable = reopened.state.value.document
        assertTrue("A validated accepted canonical checkpoint makes its abandoned temp disposable", abandonedMetadataTemps().isEmpty())
        assertFalse(abandoned.single().exists())
        assertArrayEquals("Cleanup must not rewrite accepted metadata", acceptedMetadata, metadataFile().readBytes())
        assertEquals("Cleanup must preserve content, history and receipts", acceptedCheckpoint, reopenedStore.loadCheckpoint(original.id))
        assertEquals(renamed, durable)
        assertEquals("Workbook B", durable.name)
        assertEquals(ChartOptions(), durable.worksheets.single().chart)
        assertTrue(reopened.state.value.canUndo)
        assertFalse(reopened.state.value.canRedo)

        val duplicateRename = reopened.execute(rename)
        assertTrue(duplicateRename.duplicate)
        assertEquals(renamed.revision, duplicateRename.acceptedRevision)
        assertEquals(renamed.revision, duplicateRename.document.revision)
        assertArrayEquals("Duplicate recognition must not rewrite recovered metadata", acceptedMetadata, metadataFile().readBytes())
        val undone = reopened.undo(renamed.revision)
        assertEquals("Workbook A", undone.name)
        assertEquals(ChartOptions(), undone.worksheets.single().chart)
        assertEquals(renamed.revision + 1, undone.revision)
        assertFalse(reopened.state.value.canUndo)
        assertTrue(reopened.state.value.canRedo)
        assertEquals(undone, WorkbookStore(root).load(original.id))
    }

    @Test
    fun killBeforeReplacementThenCorruptCanonicalRetainsBothRecoveryCandidates() = runBlocking {
        val acceptedMetadata = metadataFile().readBytes()
        killWriterAt(WorkbookCommitPhase.TEMP_SYNCED)
        val abandoned = abandonedMetadataTemps()
        assertEquals("Real SIGKILL must produce the recovery candidate", 1, abandoned.size)
        val candidate = abandoned.single()
        val candidateBytes = candidate.readBytes()
        assertTrue(candidateBytes.isNotEmpty())
        assertArrayEquals(acceptedMetadata, metadataFile().readBytes())

        // Corruption is constructed after the real writer death. Neither the
        // malformed canonical nor the unpublished candidate has promotion authority.
        val corruptCanonical = "{broken canonical after real writer SIGKILL".toByteArray(Charsets.UTF_8)
        metadataFile().writeBytes(corruptCanonical)
        val failure = runCatching { WorkbookStore(root).coordinators.open(original.id) }.exceptionOrNull()
        assertTrue("A corrupt canonical checkpoint must refuse reopen instead of promoting an unpublished temp", failure != null)
        assertArrayEquals("Failed reopen must preserve corrupt canonical bytes for diagnosis", corruptCanonical, metadataFile().readBytes())
        assertEquals(listOf(candidate.name), abandonedMetadataTemps().map { it.name })
        assertArrayEquals("Failed reopen must retain the exact unpublished candidate", candidateBytes, candidate.readBytes())
    }

    @Test
    fun killAfterAtomicReplacementRecoversChartOnceAndDurableUndoRedo() = runBlocking {
        killWriterAt(WorkbookCommitPhase.REPLACED)
        val reopened = WorkbookStore(root).coordinators.open(original.id)
        val durable = reopened.state.value.document
        assertEquals("Workbook B", durable.name)
        assertEquals(ChartOptions(title = "Chart C"), durable.worksheets.single().chart)
        assertEquals(renamed.revision + 1, durable.revision)
        assertTrue(reopened.state.value.canUndo)
        assertFalse(reopened.state.value.canRedo)

        val duplicate = reopened.execute(chart)
        assertTrue(duplicate.duplicate)
        assertEquals(durable.revision, duplicate.acceptedRevision)
        assertEquals(durable, duplicate.document)
        assertEquals(durable, WorkbookStore(root).load(original.id))

        val undone = reopened.undo(durable.revision)
        assertEquals("Workbook B", undone.name)
        assertEquals(ChartOptions(), undone.worksheets.single().chart)
        assertEquals(durable.revision + 1, undone.revision)
        val afterUndoRestart = WorkbookStore(root).coordinators.open(original.id)
        assertEquals(undone, afterUndoRestart.state.value.document)
        assertTrue(afterUndoRestart.state.value.canUndo)
        assertTrue(afterUndoRestart.state.value.canRedo)
        val redone = afterUndoRestart.redo(undone.revision)
        assertEquals("Workbook B", redone.name)
        assertEquals(ChartOptions(title = "Chart C"), redone.worksheets.single().chart)
        assertEquals(undone.revision + 1, redone.revision)
        assertEquals(redone, WorkbookStore(root).load(original.id))
        val afterRedoRestart = WorkbookStore(root).coordinators.open(original.id)
        assertTrue(afterRedoRestart.state.value.canUndo)
        assertFalse(afterRedoRestart.state.value.canRedo)
        val duplicateAfterHistory = afterRedoRestart.execute(chart)
        assertTrue(duplicateAfterHistory.duplicate)
        assertEquals(durable.revision, duplicateAfterHistory.acceptedRevision)
        assertEquals(redone, duplicateAfterHistory.document)
        assertEquals(redone, WorkbookStore(root).load(original.id))
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
            Log.i(JournalCrashProbeService.TAG, "BINDER_DEATH pid=${pid.get()} expectedPhase=$phase testPid=${Process.myPid()}")
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
                    failure.set("Writer reported completion instead of dying at $phase")
                    completed.countDown()
                }
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                // Android may rebind after a death. Only the first process gets RUN.
                if (!didConnect.compareAndSet(false, true)) return
                try {
                    binder.linkToDeath(recipient, 0)
                    linkedBinder.set(binder)
                    Messenger(binder).send(Message.obtain(null, JournalCrashProbeService.MSG_RUN).apply {
                        replyTo = callback
                    })
                } catch (error: Exception) {
                    failure.set("Could not begin linked writer: $error")
                    completed.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // Disconnection is supplementary. Only DeathRecipient proves death.
                Log.i(JournalCrashProbeService.TAG, "DISCONNECTED pid=${pid.get()} expectedPhase=$phase")
            }

            override fun onNullBinding(name: ComponentName) {
                failure.set("Probe returned a null binding")
                completed.countDown()
            }
        }
        val intent = Intent(context, JournalCrashProbeService::class.java)
            .putExtra(JournalCrashProbeService.EXTRA_RUN_ID, runId)
            .putExtra(JournalCrashProbeService.EXTRA_WORKBOOK_ID, original.id)
            .putExtra(JournalCrashProbeService.EXTRA_COMMAND_ID, chart.commandId)
            .putExtra(JournalCrashProbeService.EXTRA_EXPECTED_REVISION, renamed.revision)
            .putExtra(JournalCrashProbeService.EXTRA_WORKSHEET_ID, original.worksheets.single().id)
            .putExtra(JournalCrashProbeService.EXTRA_KILL_PHASE, phase.name)
            .putExtra(JournalCrashProbeService.EXTRA_KIND, JournalCrashProbeService.KIND_CHART)
            .putExtra(JournalCrashProbeService.EXTRA_VALUE, "Chart C")
        var bound = false
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            assertTrue("Explicit debug probe must bind", bound)
            assertTrue("Writer must terminate within the bounded probe timeout; failure=${failure.get()}", completed.await(15, TimeUnit.SECONDS))
            assertEquals("Writer must reach SIGKILL instead of an ordinary error", null, failure.get())
            assertTrue("Binder death is required; a boundary callback is insufficient", death.await(2, TimeUnit.SECONDS))
            assertTrue("The dying service must identify its native PID", started.await(2, TimeUnit.SECONDS))
            assertTrue("The dying service must identify the exact commit boundary", reachedBoundary.await(2, TimeUnit.SECONDS))
            assertTrue("A real service PID must be reported", pid.get() > 0)
            assertNotEquals("Writer must be in a separate Android process", Process.myPid(), pid.get())
            assertEquals(pid.get(), boundaryPid.get())
            assertEquals(phase.ordinal, observedPhase.get())
            Log.i(JournalCrashProbeService.TAG, "VERIFIED_DEATH pid=${pid.get()} phase=$phase workbook=${original.id} command=${chart.commandId}")
        } finally {
            if (bound) context.unbindService(connection)
            linkedBinder.get()?.let { binder -> runCatching { binder.unlinkToDeath(recipient, 0) } }
        }
    }
}
