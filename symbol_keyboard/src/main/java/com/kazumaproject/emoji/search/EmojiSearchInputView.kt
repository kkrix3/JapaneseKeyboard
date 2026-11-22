package com.kazumaproject.emoji.search

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.symbol_keyboard.R
import kotlin.math.abs

/** Small local kana pad. TextViews intentionally cannot open a recursive IME. */
@SuppressLint("ClickableViewAccessibility")
class EmojiSearchInputView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var onText: ((String) -> Unit)? = null
    var onModify: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null
    private var chooser: PopupWindow? = null
    private var foregroundColor = 0
    private var keyColor = 0
    private val keys = mutableListOf<TextView>()
    private val rows = listOf(listOf("あいうえお", "かきくけこ", "さしすせそ"),
        listOf("たちつてと", "なにぬねの", "はひふへほ"),
        listOf("まみむめも", "やゆよ", "らりるれろ"),
        listOf("゛小", "わをんー", "閉じる"))

    init {
        orientation = VERTICAL
        isFocusable = false
        rows.forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { chars ->
                    val key = TextView(context).apply {
                        text = when (chars) {
                            "閉じる" -> context.getString(R.string.emoji_search_hide_pad)
                            "゛小" -> context.getString(R.string.emoji_search_modify)
                            else -> "${chars.first()}  ${chars.drop(1)}"
                        }
                        gravity = Gravity.CENTER
                        textSize = 16f
                        isFocusable = false
                        layoutParams = LayoutParams(0, dp(36), 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) }
                    }
                    keys.add(key)
                    if (chars == "閉じる") key.setOnClickListener { onHide?.invoke() }
                    else if (chars == "゛小") key.setOnClickListener { onModify?.invoke() }
                    else {
                        // TalkBack users can select each character from the long-press chooser.
                        key.contentDescription = context.getString(R.string.emoji_search_kana_key, chars)
                        key.setOnClickListener { onText?.invoke(chars.take(1)) }
                        key.setOnLongClickListener { showChooser(key, chars); true }
                        var startX = 0f
                        var startY = 0f
                        var longPressed = false
                        val show = Runnable { longPressed = true; showChooser(key, chars) }
                        key.setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    startX = event.x; startY = event.y; longPressed = false
                                    key.isPressed = true
                                    key.postDelayed(show, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (abs(event.x - startX) > dp(14) || abs(event.y - startY) > dp(14)) key.removeCallbacks(show)
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    key.removeCallbacks(show); key.isPressed = false
                                    if (!longPressed) {
                                        val dx = event.x - startX; val dy = event.y - startY
                                        val index = when {
                                            maxOf(abs(dx), abs(dy)) < dp(14) -> 0
                                            abs(dx) > abs(dy) -> if (dx < 0) 1 else 3
                                            else -> if (dy < 0) 2 else 4
                                        }
                                        chars.getOrNull(index)?.let { onText?.invoke(it.toString()) }
                                    }
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> { key.removeCallbacks(show); key.isPressed = false; true }
                                else -> true
                            }
                        }
                    }
                    addView(key)
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    fun setColors(foreground: Int, background: Int) {
        foregroundColor = foreground; keyColor = background
        keys.forEach { key ->
            key.setTextColor(foreground)
            key.background = GradientDrawable().apply { setColor(background); cornerRadius = dp(6).toFloat() }
        }
    }

    fun dismissChooser(): Boolean {
        val showing = chooser?.isShowing == true
        chooser?.dismiss(); chooser = null
        return showing
    }

    private fun showChooser(anchor: View, chars: String) {
        if (!anchor.isAttachedToWindow) return
        dismissChooser()
        val choices = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(keyColor)
            chars.forEach { c ->
                addView(TextView(context).apply {
                    text = c.toString(); textSize = 22f; gravity = Gravity.CENTER; setTextColor(foregroundColor)
                    layoutParams = LayoutParams(dp(44), dp(48))
                    setOnClickListener { dismissChooser(); onText?.invoke(c.toString()) }
                })
            }
        }
        chooser = PopupWindow(choices, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(keyColor))
            showAsDropDown(anchor, 0, -dp(88))
        }
    }
    override fun onDetachedFromWindow() {
        dismissChooser()
        keys.forEach { it.isPressed = false }
        super.onDetachedFromWindow()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
