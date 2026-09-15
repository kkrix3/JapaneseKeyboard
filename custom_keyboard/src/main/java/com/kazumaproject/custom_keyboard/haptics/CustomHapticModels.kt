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
    SPECIAL_KEY
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
        }
    }
}
