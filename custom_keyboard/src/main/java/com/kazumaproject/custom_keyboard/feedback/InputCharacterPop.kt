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
import android.view.ViewTreeObserver
import android.widget.TextView

/** Shared rendering limits. Neither curve changes the glyph's XY position. */
internal object InputCharacterPopSpec {
    const val DURATION_MS = 280L
    const val MAX_ACTIVE = 12
    const val MAX_CODE_POINTS = 32

    fun scale(progress: Float): Float = InputCharacterPopSettings().scale(progress)
    fun alpha(progress: Float): Float = InputCharacterPopSettings().alpha(progress)

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
    /** Key geometry sets the origin; popup font size/position never affect this coordinate. */
    fun atKey(key: View, host: View, placement: InputCharacterPopPlacement, distanceDp: Int): InputCharacterPopGlyph {
        val keyLocation = IntArray(2).also(key::getLocationOnScreen)
        val hostLocation = IntArray(2).also(host::getLocationOnScreen)
        val keyScale = accumulatedScale(key)
        val hostScale = accumulatedScale(host)
        val (dx, dy) = placement.offset(distanceDp * key.resources.displayMetrics.density)
        return copy(
            centerX = (keyLocation[0] - hostLocation[0] + key.width / 2f * keyScale.first + dx) / hostScale.first,
            centerY = (keyLocation[1] - hostLocation[1] + key.height / 2f * keyScale.second + dy) / hostScale.second,
        )
    }

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
internal class InputCharacterPop(
    private val owner: View,
    private val clockMillis: () -> Long = SystemClock::uptimeMillis,
    private val requestFrame: (Choreographer.FrameCallback) -> Unit = { Choreographer.getInstance().postFrameCallback(it) },
    private val cancelFrame: (Choreographer.FrameCallback) -> Unit = { Choreographer.getInstance().removeFrameCallback(it) },
) {
    private data class Entry(val glyph: InputCharacterPopGlyph, val startedAt: Long, val settings: InputCharacterPopSettings)
    var settings = InputCharacterPopSettings()
    private val entries = ArrayList<Entry>(InputCharacterPopSpec.MAX_ACTIVE)
    private var host: View? = null
    private var generation = 0L
    private var framePending = false
    private val ownerLocation = IntArray(2)
    private val hostLocation = IntArray(2)
    private val locationScratch = IntArray(2)
    private var ownerWidth = 0
    private var ownerHeight = 0
    private var hostScaleX = 1f
    private var hostScaleY = 1f
    // Candidate rows can resize/move the IME root on the first composing character
    // without changing the keyboard on screen. Keep that glyph at the same screen XY.
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncGeometry()
        true
    }

    private fun syncGeometry() {
        val target = host ?: return
        owner.getLocationOnScreen(locationScratch)
        if (target.scaleX != hostScaleX || target.scaleY != hostScaleY || !owner.isShown || owner.width != ownerWidth || owner.height != ownerHeight ||
            !locationScratch.contentEquals(ownerLocation)) {
            clear()
            return
        }
        target.getLocationOnScreen(locationScratch)
        val dx = hostLocation[0] - locationScratch[0]
        val dy = hostLocation[1] - locationScratch[1]
        if (dx != 0 || dy != 0) {
            for (i in entries.indices) {
                val entry = entries[i]
                entries[i] = entry.copy(glyph = entry.glyph.copy(
                    centerX = entry.glyph.centerX + dx / hostScaleX, centerY = entry.glyph.centerY + dy / hostScaleY))
            }
        }
        locationScratch.copyInto(hostLocation)
        drawable.setBounds(0, 0, target.width, target.height)
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
            val now = clockMillis()
            for (entry in entries) {
                val t = (now - entry.startedAt).toFloat() / entry.settings.durationMs
                val glyph = entry.glyph
                val savedAlpha = glyph.paint.alpha
                glyph.paint.alpha = (savedAlpha * entry.settings.alpha(t)).toInt()
                val count = canvas.save()
                canvas.translate(glyph.centerX, glyph.centerY)
                val scale = entry.settings.scale(t)
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
            val now = clockMillis()
            entries.removeAll { now - it.startedAt >= it.settings.durationMs }
            if (entries.isEmpty()) releaseLayer(invalidatePending = false) else {
                drawable.invalidateSelf()
                scheduleFrame()
            }
        }
    }

    /** Capture before the input callback can change labels/dismiss popups. Start only on acceptance. */
    fun prepare(capture: (View) -> InputCharacterPopGlyph?): (() -> Unit)? {
        if (!enabled || !owner.isAttachedToWindow || !owner.isShown) return null
        val target = owner.rootView
        val snapshot = settings.normalized()
        val glyph = capture(target) ?: return null
        val capturedHostLocation = IntArray(2).also(target::getLocationOnScreen)
        val capturedOwnerLocation = IntArray(2).also(owner::getLocationOnScreen)
        val capturedScaleX = target.scaleX
        val capturedScaleY = target.scaleY
        val capturedWidth = owner.width
        val capturedHeight = owner.height
        val capturedGeneration = generation
        var consumed = false
        return accepted@{
            if (consumed) return@accepted
            consumed = true
            if (!enabled || generation != capturedGeneration || !owner.isAttachedToWindow ||
                !owner.isShown || owner.rootView !== target) return@accepted
            owner.getLocationOnScreen(locationScratch)
            if (target.scaleX != capturedScaleX || target.scaleY != capturedScaleY || owner.width != capturedWidth || owner.height != capturedHeight ||
                !locationScratch.contentEquals(capturedOwnerLocation)) return@accepted
            syncGeometry()
            if (generation != capturedGeneration) return@accepted
            if (host == null) {
                host = target
                drawable.setBounds(0, 0, target.width, target.height)
                target.overlay.add(drawable)
                owner.getLocationOnScreen(ownerLocation)
                hostScaleX = target.scaleX
                hostScaleY = target.scaleY
                ownerWidth = owner.width
                ownerHeight = owner.height
                target.getLocationOnScreen(hostLocation)
                target.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
            if (entries.size == InputCharacterPopSpec.MAX_ACTIVE) entries.removeAt(0)
            target.getLocationOnScreen(locationScratch)
            val positionedGlyph = glyph.copy(
                centerX = glyph.centerX + (capturedHostLocation[0] - locationScratch[0]) / hostScaleX,
                centerY = glyph.centerY + (capturedHostLocation[1] - locationScratch[1]) / hostScaleY)
            entries.add(Entry(positionedGlyph, clockMillis(), snapshot))
            drawable.invalidateSelf()
            scheduleFrame()
        }
    }

    fun clear() = releaseLayer(invalidatePending = true)

    private fun releaseLayer(invalidatePending: Boolean) {
        if (invalidatePending) generation++
        if (framePending) cancelFrame(frame)
        framePending = false
        entries.clear()
        host?.let {
            it.overlay.remove(drawable)
            if (it.viewTreeObserver.isAlive) it.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        host = null
    }

    private fun scheduleFrame() {
        if (framePending) return
        framePending = true
        requestFrame(frame)
    }
}
