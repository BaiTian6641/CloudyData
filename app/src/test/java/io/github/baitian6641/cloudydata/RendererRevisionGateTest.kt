package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.renderer.RendererRevisionGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRevisionGateTest {
    @Test
    fun lateCompletionsFromSupersededChartOrRevisionAreRejected() {
        val gate = RendererRevisionGate()
        gate.dispatch("sheet-1", 4)
        assertTrue(gate.isCurrent("sheet-1", 4))
        assertFalse(gate.isCurrent("sheet-1", 3))
        assertFalse(gate.isCurrent("sheet-2", 4))

        gate.dispatch("sheet-1", 5)
        assertFalse("old revision must be discarded after a new dispatch", gate.isCurrent("sheet-1", 4))
        assertTrue(gate.isCurrent("sheet-1", 5))
    }
}
