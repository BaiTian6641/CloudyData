package io.github.baitian6641.cloudydata

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.EngineException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CancellationTest {
    @Test
    fun interruptStopsExpensiveQueryAndConnectionRemainsUsable() {
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val outcome = AtomicReference<Result<io.github.baitian6641.cloudydata.engine.EngineResult>?>(null)
            val worker = thread(name = "cloudy-cancel-probe") {
                outcome.set(
                    runCatching {
                        engine.execute(
                            "SELECT sum(a.i::DOUBLE * b.i::DOUBLE) " +
                                "FROM range(200000) a(i), range(200000) b(i)",
                            maxRows = 1,
                        )
                    },
                )
            }
            Thread.sleep(150)
            val started = System.nanoTime()
            engine.interrupt()
            worker.join(15_000)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("worker did not stop after interrupt", !worker.isAlive)
            val result = outcome.get()
            assertTrue("worker did not publish an outcome", result != null)
            val failure = result!!.exceptionOrNull()
            assertTrue("interrupt produced a result instead of an interrupted failure", failure != null)
            assertTrue(
                "unexpected failure type/message: $failure",
                failure is EngineException && failure.message.orEmpty().lowercase().contains("interrupt"),
            )
            assertTrue("interrupt took too long: ${elapsedMs}ms", elapsedMs < 10_000)

            Log.i(
                "CloudyCancellation",
                "interrupted=${failure!!.javaClass.simpleName} message=${failure!!.message} elapsedMs=$elapsedMs",
            )
            val followUp = engine.execute("SELECT 42::BIGINT AS answer", maxRows = 1)
            assertEquals(42L, followUp.rows.single().single())
        }
    }
}
