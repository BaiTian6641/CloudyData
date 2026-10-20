package io.github.baitian6641.cloudydata.debug

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import io.github.baitian6641.cloudydata.data.WorkbookCommitPhase
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Debug-only, single-writer process-death probe. All files stay beneath a fixed
 * synthetic-test directory. Binding grants no imported path or execution authority.
 * The test links Binder death before sending RUN; boundary messages alone are
 * deliberately insufficient evidence that the process died.
 */
class JournalCrashProbeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean()
    private lateinit var config: ProbeConfig
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what != MSG_RUN || !started.compareAndSet(false, true)) return@Handler true
        val callback = message.replyTo ?: return@Handler true
        scope.launch {
            try {
                callback.send(Message.obtain(null, MSG_STARTED, Process.myPid(), 0))
                val store = WorkbookStore(probeRoot(this@JournalCrashProbeService, config.runId)) { phase ->
                    if (phase == config.killPhase) {
                        val pid = Process.myPid()
                        Log.i(TAG, "KILL pid=$pid phase=$phase workbook=${config.workbookId} command=${config.commandId}")
                        callback.send(Message.obtain(null, MSG_BOUNDARY, pid, phase.ordinal))
                        Process.killProcess(pid)
                        error("SIGKILL unexpectedly returned at $phase")
                    }
                }
                val coordinator = store.coordinators.open(config.workbookId)
                val command = when (config.kind) {
                    KIND_RENAME -> WorkbookCommand.Rename(
                        commandId = config.commandId,
                        workbookId = config.workbookId,
                        originWindowId = ORIGIN_WINDOW,
                        expectedRevision = config.expectedRevision,
                        name = config.value,
                    )
                    KIND_CHART -> WorkbookCommand.SetChart(
                        commandId = config.commandId,
                        workbookId = config.workbookId,
                        originWindowId = ORIGIN_WINDOW,
                        expectedRevision = config.expectedRevision,
                        worksheetId = config.worksheetId,
                        options = ChartOptions(title = config.value),
                    )
                    else -> error("Unsupported synthetic probe command")
                }
                coordinator.execute(command)
                callback.send(Message.obtain(null, MSG_FINISHED, Process.myPid(), 0))
            } catch (error: Throwable) {
                Log.e(TAG, "Probe failed before expected death", error)
                runCatching {
                    callback.send(Message.obtain(null, MSG_FAILED).apply {
                        data = Bundle().apply { putString(EXTRA_ERROR, "${error.javaClass.simpleName}: ${error.message}") }
                    })
                }
            }
        }
        true
    })

    override fun onBind(intent: Intent): IBinder {
        config = ProbeConfig(
            runId = intent.getStringExtra(EXTRA_RUN_ID) ?: error("Missing probe run ID"),
            workbookId = intent.getStringExtra(EXTRA_WORKBOOK_ID) ?: error("Missing workbook ID"),
            commandId = intent.getStringExtra(EXTRA_COMMAND_ID) ?: error("Missing command ID"),
            expectedRevision = intent.getLongExtra(EXTRA_EXPECTED_REVISION, -1),
            worksheetId = intent.getStringExtra(EXTRA_WORKSHEET_ID).orEmpty(),
            killPhase = WorkbookCommitPhase.valueOf(intent.getStringExtra(EXTRA_KILL_PHASE) ?: error("Missing boundary")),
            kind = intent.getStringExtra(EXTRA_KIND) ?: error("Missing command kind"),
            value = intent.getStringExtra(EXTRA_VALUE) ?: error("Missing synthetic value"),
        ).also {
            UUID.fromString(it.runId)
            require(it.expectedRevision >= 0)
            require(it.kind in setOf(KIND_RENAME, KIND_CHART))
            require(it.value.length <= 200)
        }
        return messenger.binder
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class ProbeConfig(
        val runId: String,
        val workbookId: String,
        val commandId: String,
        val expectedRevision: Long,
        val worksheetId: String,
        val killPhase: WorkbookCommitPhase,
        val kind: String,
        val value: String,
    )

    companion object {
        const val TAG = "JournalCrashProbe"
        const val ORIGIN_WINDOW = "journal-crash-probe"
        const val KIND_RENAME = "rename"
        const val KIND_CHART = "chart"
        const val EXTRA_RUN_ID = "probeRunId"
        const val EXTRA_WORKBOOK_ID = "workbookId"
        const val EXTRA_COMMAND_ID = "commandId"
        const val EXTRA_EXPECTED_REVISION = "expectedRevision"
        const val EXTRA_WORKSHEET_ID = "worksheetId"
        const val EXTRA_KILL_PHASE = "killPhase"
        const val EXTRA_KIND = "kind"
        const val EXTRA_VALUE = "value"
        const val EXTRA_ERROR = "error"
        const val MSG_RUN = 1
        const val MSG_STARTED = 2
        const val MSG_BOUNDARY = 3
        const val MSG_FAILED = 4
        const val MSG_FINISHED = 5

        fun probeRoot(context: Context, runId: String): File {
            // Parse and normalize before resolving, so extras cannot become paths.
            val safeRunId = UUID.fromString(runId).toString()
            require(runId == safeRunId)
            return File(File(context.filesDir, "journal-crash-probes"), safeRunId)
        }
    }
}
