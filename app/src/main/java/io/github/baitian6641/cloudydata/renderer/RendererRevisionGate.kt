package io.github.baitian6641.cloudydata.renderer

/**
 * Rejects renderer completions/intents that do not match the latest dispatched
 * chart revision. The renderer is asynchronous; a late completion must never
 * become the current answer.
 */
class RendererRevisionGate {
    @Volatile
    private var chartId: String? = null

    @Volatile
    private var revision: Long = -1

    fun dispatch(chartId: String, revision: Long) {
        this.chartId = chartId
        this.revision = revision
    }

    fun isCurrent(chartId: String, revision: Long): Boolean =
        this.chartId == chartId && this.revision == revision
}
