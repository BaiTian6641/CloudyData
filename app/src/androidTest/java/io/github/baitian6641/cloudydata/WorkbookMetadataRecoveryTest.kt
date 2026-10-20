package io.github.baitian6641.cloudydata

import android.content.Context
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.WorkbookCheckpoint
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.3 / F01,F64 / Q17,Q34: real app-private files and concurrent store handles. */
@RunWith(AndroidJUnit4::class)
class WorkbookMetadataRecoveryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "metadata-recovery-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun validCheckpointRemovesOnlyDirectRegularNumericTempsAndKeepsExactAuthority() = runBlocking {
        val before = historyCheckpoint()
        val book = before.document
        val directory = store.workbookDir(book.id)
        val canonicalBytes = metadata(book.id).readBytes()
        val unpublished = before.copy(document = book.copy(name = "Unpublished candidate", revision = book.revision + 1))
        val abandoned = listOf(
            File(directory, "workbook-0.tmp").apply { writeText("incomplete temporary bytes") },
            File(directory, "workbook-0002.tmp").apply { writeText(unpublished.toJson().toString()) },
            File(directory, "workbook-9223372036854775807.tmp").apply { writeText("another abandoned candidate") },
        )
        val retained = listOf(
            "workbook-.tmp",
            "workbook-text.tmp",
            "workbook--1.tmp",
            "workbook-12.tmp.bak",
            "Workbook-12.tmp",
            "other-12.tmp",
            "workbook-١٢.tmp",
            "data/workbook-101.tmp",
            "source/workbook-102.tmp",
            "migration/workbook-103.tmp",
            "workbook-104.tmp/workbook-105.tmp",
        ).associate { relative ->
            val file = File(directory, relative).apply {
                parentFile!!.mkdirs()
                writeText("Preserved bytes for $relative")
            }
            file to file.readBytes()
        }
        val externalTarget = File(root, "outside-workbook-canary").apply { writeText("Unrelated target bytes") }
        val externalBytes = externalTarget.readBytes()
        val existingLink = File(directory, "workbook-106.tmp")
        val danglingLink = File(directory, "workbook-107.tmp")
        Os.symlink(externalTarget.absolutePath, existingLink.absolutePath)
        Os.symlink(File(root, "missing-symlink-target").absolutePath, danglingLink.absolutePath)
        assertTrue(Files.isSymbolicLink(existingLink.toPath()))
        assertTrue(Files.isSymbolicLink(danglingLink.toPath()))

        val recovered = WorkbookStore(root).loadCheckpoint(book.id)

        assertEquals("the canonical checkpoint is the sole authority", before, recovered)
        assertEquals("Renamed", recovered.document.name)
        assertEquals(book.revision, recovered.document.revision)
        assertEquals(listOf("Book"), recovered.undo.map { it.name })
        assertEquals(listOf("Custom"), recovered.redo.map { it.worksheets.single().chart.title })
        assertEquals(listOf("rename-history", "chart-history"), recovered.receipts.map { it.command.commandId })
        assertArrayEquals(canonicalBytes, metadata(book.id).readBytes())
        abandoned.forEach { assertFalse("remove only this unpublished regular file: ${it.name}", it.exists()) }
        retained.forEach { (file, bytes) -> assertArrayEquals("preserve ${file.relativeTo(directory)}", bytes, file.readBytes()) }
        assertTrue(File(directory, "workbook-104.tmp").isDirectory)
        assertTrue(Files.isSymbolicLink(existingLink.toPath()))
        assertTrue(Files.isSymbolicLink(danglingLink.toPath()))
        assertEquals(externalTarget.absolutePath, Os.readlink(existingLink.absolutePath))
        assertEquals(File(root, "missing-symlink-target").absolutePath, Os.readlink(danglingLink.absolutePath))
        assertArrayEquals(externalBytes, externalTarget.readBytes())

        val coordinator = WorkbookStore(root).coordinators.open(book.id)
        assertTrue(coordinator.state.value.canUndo)
        assertTrue(coordinator.state.value.canRedo)
        val duplicate = coordinator.execute(before.receipts.first().command)
        assertTrue(duplicate.duplicate)
        assertEquals(before.receipts.first().acceptedRevision, duplicate.acceptedRevision)
        assertEquals(book, duplicate.document)
        assertArrayEquals("cleanup and receipt retry never rewrite the canonical file", canonicalBytes, metadata(book.id).readBytes())
    }

    @Test
    fun missingCanonicalPreservesAllCandidatesAndNeverPromotesAValidNewerTemp() = runBlocking {
        val before = historyCheckpoint()
        val id = before.document.id
        val candidates = listOf(
            File(store.workbookDir(id), "workbook-201.tmp").apply { writeBytes(metadata(id).readBytes()) },
            File(store.workbookDir(id), "workbook-202.tmp").apply {
                writeText(before.copy(document = before.document.copy(name = "Unaccepted newer candidate", revision = before.document.revision + 1)).toJson().toString())
            },
            File(store.workbookDir(id), "workbook-203.tmp").apply { writeText("partially written candidate") },
        ).associateWith { it.readBytes() }
        assertTrue(metadata(id).delete())

        assertTrue(runCatching { WorkbookStore(root).loadCheckpoint(id) }.isFailure)
        assertTrue(runCatching { WorkbookStore(root).coordinators.open(id) }.isFailure)
        assertFalse("no candidate may be promoted to canonical metadata", metadata(id).exists())
        candidates.forEach { (file, bytes) -> assertArrayEquals("missing canonical preserves ${file.name}", bytes, file.readBytes()) }
    }

    @Test
    fun malformedOrChecksumInvalidCanonicalPreservesItsBytesAndEveryTempCandidate() = runBlocking {
        listOf("malformed JSON", "invalid checkpoint checksum", "future workbook", "future journal").forEach { fault ->
            val before = historyCheckpoint()
            val id = before.document.id
            val candidate = File(store.workbookDir(id), "workbook-301.tmp").apply { writeBytes(metadata(id).readBytes()) }
            val candidateBytes = candidate.readBytes()
            val corrupt = when (fault) {
                "malformed JSON" -> "{\"formatVersion\":3,\"truncated\":"
                "invalid checkpoint checksum" -> JSONObject(metadata(id).readText()).put("name", "Changed without acceptance").toString()
                "future workbook" -> JSONObject(metadata(id).readText()).put("formatVersion", 4).toString()
                else -> JSONObject(metadata(id).readText()).apply {
                    getJSONObject("commandJournal").put("version", 2)
                }.toString()
            }.toByteArray(Charsets.UTF_8)
            metadata(id).writeBytes(corrupt)

            assertTrue("$fault must refuse validation", runCatching { WorkbookStore(root).loadCheckpoint(id) }.isFailure)
            assertTrue("$fault must refuse coordinator open", runCatching { WorkbookStore(root).coordinators.open(id) }.isFailure)
            assertArrayEquals("$fault preserves canonical evidence", corrupt, metadata(id).readBytes())
            assertArrayEquals("$fault preserves the candidate for diagnosis", candidateBytes, candidate.readBytes())
        }
    }

    @Test
    fun deniedTempDeletionKeepsValidWorkbookAvailableAndRetainsCandidateBytes() = runBlocking {
        val before = historyCheckpoint()
        val id = before.document.id
        val directory = store.workbookDir(id)
        val canonicalBytes = metadata(id).readBytes()
        val candidate = File(directory, "workbook-401.tmp").apply { writeText("Retain if real deletion is denied") }
        val candidateBytes = candidate.readBytes()
        val originalMode = Os.stat(directory.absolutePath).st_mode and 0x1ff
        try {
            Os.chmod(directory.absolutePath, 0x16d) // 0555: readable authority, denied unlink.
            assertEquals(0x16d, Os.stat(directory.absolutePath).st_mode and 0x1ff)
            val recovered = WorkbookStore(root).loadCheckpoint(id)
            assertEquals("best-effort cleanup must not make valid accepted work unavailable", before, recovered)
            assertArrayEquals(canonicalBytes, metadata(id).readBytes())
            assertArrayEquals("denied removal retains the candidate", candidateBytes, candidate.readBytes())
        } finally {
            Os.chmod(directory.absolutePath, originalMode)
        }
        assertEquals(before, WorkbookStore(root).loadCheckpoint(id))
        assertFalse("cleanup succeeds on the next load after permissions are restored", candidate.exists())
        assertArrayEquals(canonicalBytes, metadata(id).readBytes())
    }

    @Test
    fun reentrantSecondHandleLoadAtTempSyncKeepsTheLiveWriterTempAndCommitsSuccessfully() {
        val armed = AtomicBoolean(false)
        val observations = AtomicInteger()
        var original: WorkbookDocument? = null
        var canonicalBytes = byteArrayOf()
        var observedTemp: File? = null
        val hookedStore = WorkbookStore(root) { phase ->
            if (phase == WorkbookCommitPhase.TEMP_SYNCED && armed.compareAndSet(true, false)) {
                val book = checkNotNull(original)
                val liveTemp = directRegularTemps(store.workbookDir(book.id)).single()
                val liveBytes = liveTemp.readBytes()
                observedTemp = liveTemp
                val duringCommit = WorkbookStore(root).loadCheckpoint(book.id)
                observations.incrementAndGet()
                assertEquals(book, duringCommit.document)
                assertArrayEquals(canonicalBytes, metadata(book.id).readBytes())
                assertTrue("a reentrant load must exclude the active writer's temporary file", liveTemp.exists())
                assertArrayEquals(liveBytes, liveTemp.readBytes())
            }
        }
        val book = hookedStore.create("Before reentrant load")
        original = book
        canonicalBytes = metadata(book.id).readBytes()
        armed.set(true)

        val saved = hookedStore.save(book.copy(name = "After reentrant load"))

        assertEquals(1, observations.get())
        assertEquals(book.revision + 1, saved.revision)
        assertEquals("After reentrant load", saved.name)
        assertEquals(saved, WorkbookStore(root).load(book.id))
        assertFalse("the live temporary path is consumed by the real atomic move", checkNotNull(observedTemp).exists())
        assertTrue(directRegularTemps(store.workbookDir(book.id)).isEmpty())
    }

    @Test
    fun secondHandleReaderWaitsForTheRealWriterAndReturnsItsCommittedRevisionWithoutDeadlock() {
        val armed = AtomicBoolean(false)
        val writerSynced = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val readerStarted = CountDownLatch(1)
        val readerFinished = CountDownLatch(1)
        val hookedStore = WorkbookStore(root) { phase ->
            if (phase == WorkbookCommitPhase.TEMP_SYNCED && armed.compareAndSet(true, false)) {
                writerSynced.countDown()
                check(releaseWriter.await(10, TimeUnit.SECONDS)) { "The test did not release the paused real writer." }
            }
        }
        val book = hookedStore.create("Before concurrent read")
        val canonicalBytes = metadata(book.id).readBytes()
        val otherHandle = WorkbookStore(root)
        val pool = Executors.newFixedThreadPool(2)
        try {
            armed.set(true)
            val writer = pool.submit<WorkbookDocument> {
                hookedStore.save(book.copy(name = "Committed before reader"))
            }
            assertTrue("the writer reaches its real fsynced temporary file", writerSynced.await(5, TimeUnit.SECONDS))
            val liveTemp = directRegularTemps(store.workbookDir(book.id)).single()
            assertTrue(liveTemp.length() > 0)
            assertArrayEquals("the canonical metadata is still the previous revision", canonicalBytes, metadata(book.id).readBytes())
            val reader = pool.submit<WorkbookCheckpoint> {
                readerStarted.countDown()
                try {
                    otherHandle.loadCheckpoint(book.id)
                } finally {
                    readerFinished.countDown()
                }
            }
            assertTrue(readerStarted.await(5, TimeUnit.SECONDS))
            assertFalse("the other handle must wait for the same root's in-flight writer", readerFinished.await(250, TimeUnit.MILLISECONDS))
            assertTrue("a waiting reader cannot remove the live temporary file", liveTemp.exists())
            releaseWriter.countDown()
            val saved = writer.get(5, TimeUnit.SECONDS)
            val loaded = reader.get(5, TimeUnit.SECONDS)

            assertEquals("Committed before reader", saved.name)
            assertEquals(book.revision + 1, saved.revision)
            assertEquals(saved, loaded.document)
            assertEquals(saved, WorkbookStore(root).load(book.id))
            assertFalse(liveTemp.exists())
            assertTrue(directRegularTemps(store.workbookDir(book.id)).isEmpty())
        } finally {
            releaseWriter.countDown()
            pool.shutdownNow()
            assertTrue("worker threads finish within the bounded test", pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private suspend fun historyCheckpoint(): WorkbookCheckpoint {
        val book = store.create("Book")
        val coordinator = store.coordinators.open(book.id)
        val renamed = coordinator.execute(
            WorkbookCommand.Rename("rename-history", book.id, "history-window", book.revision, "Renamed"),
        ).document
        val chart = coordinator.execute(
            WorkbookCommand.SetChart(
                "chart-history", book.id, "history-window", renamed.revision,
                book.worksheets.single().id, ChartOptions(title = "Custom"),
            ),
        ).document
        coordinator.undo(chart.revision)
        return store.loadCheckpoint(book.id)
    }

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")

    private fun directRegularTemps(directory: File): List<File> = directory.listFiles().orEmpty().filter {
        Regex("workbook-[0-9]+\\.tmp").matches(it.name) && Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS)
    }
}
