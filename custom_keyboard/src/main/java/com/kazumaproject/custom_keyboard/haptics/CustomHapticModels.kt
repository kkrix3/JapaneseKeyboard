package com.kazumaproject.custom_keyboard.haptics

data class HapticStep(
    val durationMs: Long,
    val amplitude: Int
) {
    init {
        require(durationMs >= 0L) { "durationMs must be >= 0" }
        require(amplitude in 0..255) { "amplitude must be in 0..255" }
    }
}

enum class HapticPatternKind {
    NORMAL_FLICK,
    TWO_STEP_FLICK,
    SPECIAL_KEY,
    STAGE_ADVANCE,
    STAGE_RETREAT,
    STAGE_CHANGE
}

data class HapticPattern(
    val steps: List<HapticStep>
) {
    init {
        require(steps.size <= MAX_STEPS) { "A haptic pattern can contain at most $MAX_STEPS steps" }
    }

    companion object {
        const val MAX_STEPS = 10

        val NormalFlickDefault = HapticPattern(
            listOf(HapticStep(durationMs = 5L, amplitude = 32))
        )

        val TwoStepFlickDefault = HapticPattern(
            listOf(
                HapticStep(durationMs = 5L, amplitude = 32),
                HapticStep(durationMs = 20L, amplitude = 0),
                HapticStep(durationMs = 5L, amplitude = 32)
            )
        )

        val SpecialKeyDefault = HapticPattern(
            listOf(HapticStep(durationMs = 5L, amplitude = 96))
        )

        fun defaultFor(kind: HapticPatternKind): HapticPattern = when (kind) {
            HapticPatternKind.NORMAL_FLICK -> NormalFlickDefault
            HapticPatternKind.TWO_STEP_FLICK -> TwoStepFlickDefault
            HapticPatternKind.SPECIAL_KEY -> SpecialKeyDefault
            HapticPatternKind.STAGE_ADVANCE -> HapticPattern(
                listOf(HapticStep(5, 50), HapticStep(5, 100), HapticStep(5, 50))
            )
            HapticPatternKind.STAGE_RETREAT -> HapticPattern(listOf(HapticStep(5, 50)))
            // The advance starts 30ms after the retreat starts. This pattern is independent
            // of both the two other patterns and the application-side delay preference.
            HapticPatternKind.STAGE_CHANGE -> HapticPattern(
                listOf(HapticStep(5, 50), HapticStep(25, 0),
                    HapticStep(5, 50), HapticStep(5, 100), HapticStep(5, 50))
            )
        }
    }
}
