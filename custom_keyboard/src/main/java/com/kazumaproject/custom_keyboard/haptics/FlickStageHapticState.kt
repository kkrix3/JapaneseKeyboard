package com.kazumaproject.custom_keyboard.haptics

enum class StageChangeMode(val preferenceValue: String) {
    APPLICATION("application"), COMPOSITE("composite"), IMMEDIATE("off")
}

data class FlickStageHapticSettings(
    val suppressOscillation: Boolean = true,
    val suppressionMs: Long = 40,
    val changeMode: StageChangeMode = StageChangeMode.APPLICATION,
    val delayMs: Long = 30
)

data class FlickStageTransition(
    val retreats: Int,
    val advances: Int,
    val suppressed: Boolean
)

/** Uses recognizer paths only; never filters or modifies the recognizer's selection. */
class FlickStageHapticState {
    var path: List<String> = emptyList()
        private set

    private data class Boundary(val parent: List<String>, val branches: Set<String?>)
    private val lastCrossings = mutableMapOf<Boundary, Long>()

    fun reset() {
        path = emptyList()
        lastCrossings.clear()
    }

    fun update(next: List<String>, nowMs: Long, settings: FlickStageHapticSettings): FlickStageTransition? {
        if (next == path) return null
        val previous = path
        // Advance the observed state even when its haptic event will be suppressed.
        path = next.toList()
        var common = 0
        while (common < previous.size && common < next.size && previous[common] == next[common]) common++
        val boundary = Boundary(previous.take(common), setOf(previous.getOrNull(common), next.getOrNull(common)))
        val last = lastCrossings.put(boundary, nowMs)
        val suppress = settings.suppressOscillation && last != null &&
            nowMs - last in 0 until settings.suppressionMs
        return FlickStageTransition(previous.size - common, next.size - common, suppress)
    }
}

/** Both two-step recognizers retain the entry direction as their first-stage highlight. */
internal fun twoStepHapticPath(first: String, second: String): List<String> = when {
    first == "TAP" -> emptyList()
    second == "TAP" || second == first -> listOf(first)
    else -> listOf(first, second)
}

/** Mirrors the hierarchical recognizer's retained flick depth, including submenu entry echoes. */
internal fun hierarchicalHapticPath(stackFromRoot: List<String>, highlight: String): List<String> {
    val path = mutableListOf<String>()
    for (direction in stackFromRoot + highlight) {
        if (direction != "TAP" && direction != path.lastOrNull()) path += direction
    }
    return path
}
