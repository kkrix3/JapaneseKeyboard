package com.kazumaproject.custom_keyboard.feedback

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.widget.TextView

/** All tuning lives here. Neither curve changes the glyph's XY position. */
internal object InputCharacterPopSpec {
    const val DURATION_MS = 280L
    const val END_SCALE = 1.8f
    const val FADE_START = 0.15f
    const val MAX_ACTIVE = 12
    const val MAX_CODE_POINTS = 32

    fun scale(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return 1f + (END_SCALE - 1f) * (1f - (1f - t) * (1f - t))
    }

    fun alpha(progress: Float): Float {
        val t = ((progress - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    fun canDisplay(text: String): Boolean = text.isNotBlank() &&
        !text.any { it.isISOControl() } && text.codePointCount(0, text.length) <= MAX_CODE_POINTS
}

/** A copy of just the selected glyphs, never the key background or popup chrome. */
internal data class InputCharacterPopGlyph(
    val text: String,
    val paint: Paint,
    val centerX: Float,
    val centerY: Float,
    val drawX: Float,
    val baseline: Float,
    val scaleX: Float,
    val scaleY: Float,
) {
    companion object {
        fun capture(source: View, host: View, text: String, paint: Paint, x: Float, baseline: Float): InputCharacterPopGlyph? {
            if (!InputCharacterPopSpec.canDisplay(text) || !source.isAttachedToWindow ||
                source.width <= 0 || source.height <= 0 || paint.alpha == 0) return null
            val copy = Paint(paint)
            val ink = Rect()
            copy.getTextBounds(text, 0, text.length, ink)
            if (ink.isEmpty) return null
            val advanceOffset = when (copy.textAlign) {
                Paint.Align.CENTER -> -copy.measureText(text) / 2f
                Paint.Align.RIGHT -> -copy.measureText(text)
                else -> 0f
            }
            copy.textAlign = Paint.Align.LEFT
            val inkCenterX = advanceOffset + ink.exactCenterX()
            val inkCenterY = ink.exactCenterY()
            val sourceLocation = IntArray(2).also(source::getLocationOnScreen)
            val hostLocation = IntArray(2).also(host::getLocationOnScreen)
            val sourceScale = accumulatedScale(source)
            val hostScale = accumulatedScale(host)
            if (hostScale.first <= 0f || hostScale.second <= 0f) return null
            return InputCharacterPopGlyph(
                text, copy,
                (sourceLocation[0] - hostLocation[0] + (x + inkCenterX) * sourceScale.first) / hostScale.first,
                (sourceLocation[1] - hostLocation[1] + (baseline + inkCenterY) * sourceScale.second) / hostScale.second,
                -ink.exactCenterX(), -inkCenterY,
                sourceScale.first / hostScale.first, sourceScale.second / hostScale.second
            )
        }

        /** Simple single-line TextViews; custom-drawn popup types supply their own baseline. */
        fun fromTextView(source: TextView, host: View, text: String): InputCharacterPopGlyph? {
            val layout = source.layout ?: return null
            if (source.text.toString() != text || layout.lineCount != 1) return null
            val paint = Paint(source.paint).apply {
                color = source.currentTextColor
                textAlign = Paint.Align.LEFT
            }
            return capture(source, host, text, paint,
                source.compoundPaddingLeft + layout.getLineLeft(0) - source.scrollX,
                (source.baseline - source.scrollY).toFloat())
        }

        private fun accumulatedScale(view: View): Pair<Float, Float> {
            var x = 1f
            var y = 1f
            var current: View? = view
            while (current != null) {
                x *= current.scaleX
                y *= current.scaleY
                current = current.parent as? View
            }
            return x to y
        }
    }
}

/** A single non-interactive Drawable in the window overlay, shared by overlapping glyphs.
 * No View hierarchy or Animator is created per key. Frame callbacks stop when the list is empty.
 */
internal class InputCharacterPop(private val owner: View) {
    private data class Entry(val glyph: InputCharacterPopGlyph, val startedAt: Long)
    private val entries = ArrayList<Entry>(InputCharacterPopSpec.MAX_ACTIVE)
    private var host: View? = null
    private var generation = 0L
    private var framePending = false
    private val hostLayoutListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (l != ol || t != ot || r != or || b != ob) clear()
    }
    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) clear()
        }
    internal val activeCount: Int get() = entries.size
    internal val activeGlyphs: List<InputCharacterPopGlyph> get() = entries.map { it.glyph }

    private val drawable = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            for (entry in entries) {
                val t = (now - entry.startedAt).toFloat() / InputCharacterPopSpec.DURATION_MS
                val glyph = entry.glyph
                val savedAlpha = glyph.paint.alpha
                glyph.paint.alpha = (savedAlpha * InputCharacterPopSpec.alpha(t)).toInt()
                val count = canvas.save()
                canvas.translate(glyph.centerX, glyph.centerY)
                val scale = InputCharacterPopSpec.scale(t)
                canvas.scale(glyph.scaleX * scale, glyph.scaleY * scale)
                canvas.drawText(glyph.text, glyph.drawX, glyph.baseline, glyph.paint)
                canvas.restoreToCount(count)
                glyph.paint.alpha = savedAlpha
            }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
    private val frame = Choreographer.FrameCallback {
        framePending = false
        if (!owner.isAttachedToWindow || !owner.isShown) {
            clear()
        } else {
            val now = SystemClock.uptimeMillis()
            entries.removeAll { now - it.startedAt >= InputCharacterPopSpec.DURATION_MS }
            if (entries.isEmpty()) clear() else {
                drawable.invalidateSelf()
                scheduleFrame()
            }
        }
    }

    /** Capture before the input callback can change labels/dismiss popups. Start only on acceptance. */
    fun prepare(capture: (View) -> InputCharacterPopGlyph?): (() -> Unit)? {
        if (!enabled || !owner.isAttachedToWindow || !owner.isShown) return null
        val target = owner.rootView
        val glyph = capture(target) ?: return null
        val capturedGeneration = generation
        var consumed = false
        return accepted@{
            if (consumed) return@accepted
            consumed = true
            if (!enabled || generation != capturedGeneration || !owner.isAttachedToWindow ||
                !owner.isShown || owner.rootView !== target) return@accepted
            if (host == null) {
                host = target
                drawable.setBounds(0, 0, target.width, target.height)
                target.overlay.add(drawable)
                target.addOnLayoutChangeListener(hostLayoutListener)
            }
            if (entries.size == InputCharacterPopSpec.MAX_ACTIVE) entries.removeAt(0)
            entries.add(Entry(glyph, SystemClock.uptimeMillis()))
            drawable.invalidateSelf()
            scheduleFrame()
        }
    }

    fun clear() {
        generation++
        if (framePending) Choreographer.getInstance().removeFrameCallback(frame)
        framePending = false
        entries.clear()
        host?.let {
            it.overlay.remove(drawable)
            it.removeOnLayoutChangeListener(hostLayoutListener)
        }
        host = null
    }

    private fun scheduleFrame() {
        if (framePending) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(frame)
    }
}
