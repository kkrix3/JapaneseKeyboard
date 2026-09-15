package com.kazumaproject.custom_keyboard.haptics

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.kazumaproject.custom_keyboard.R

/** Draft changes are isolated until Save, and survive activity recreation. */
class HapticPatternEditorDialog : DialogFragment() {
    private lateinit var preferences: CustomHapticPreferences
    private lateinit var player: CustomHapticPlayer
    private lateinit var kind: HapticPatternKind
    private val steps = mutableListOf<HapticStep>()
    private lateinit var rows: LinearLayout
    private val addButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = CustomHapticPreferences(requireContext())
        player = CustomHapticPlayer(requireContext())
        kind = HapticPatternKind.valueOf(requireArguments().getString(ARG_KIND)!!)
        val draft = savedInstanceState?.getString(STATE_DRAFT)
        steps.addAll((preferences.parsePattern(draft) ?: preferences.pattern(kind)).steps)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, preferences.serializePattern(HapticPattern(steps.toList())))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        addButtons.clear()
        val content = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        content.addView(TextView(requireContext()).apply { setText(R.string.haptic_editor_help) })
        rows = column()
        content.addView(rows)
        val presets = LinearLayout(requireContext())
        listOf("Tick-like" to 32, "Click-like" to 96, "Heavy-like" to 255).forEach { (label, amplitude) ->
            val button = button(label) { append(HapticStep(5, amplitude)) }
            addButtons += button
            presets.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(presets)
        val custom = button(getString(R.string.haptic_add_custom)) { editStep(null) }
        addButtons += custom
        content.addView(custom)
        content.addView(button(getString(R.string.haptic_clear)) { steps.clear(); renderRows() })
        content.addView(button(getString(R.string.haptic_reset)) {
            steps.clear(); steps.addAll(HapticPattern.defaultFor(kind).steps); renderRows()
        })
        content.addView(button(getString(R.string.haptic_preview)) {
            player.preview(HapticPattern(steps.toList()))
        })
        renderRows()
        return AlertDialog.Builder(requireContext())
            .setTitle(titleFor(kind))
            .setView(ScrollView(requireContext()).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.haptic_save) { _, _ ->
                preferences.savePattern(kind, HapticPattern(steps.toList()))
            }.create()
    }

    private fun append(step: HapticStep) {
        if (steps.size >= HapticPattern.MAX_STEPS) return
        steps += step
        renderRows()
    }

    private fun renderRows() {
        rows.removeAllViews()
        steps.forEachIndexed { index, step ->
            rows.addView(TextView(requireContext()).apply {
                text = "${index + 1}. ${step.durationMs} ms / ${step.amplitude}"
                textSize = 18f
                setPadding(0, dp(12), 0, 0)
            })
            val actions = LinearLayout(requireContext())
            fun add(label: String, enabled: Boolean = true, action: () -> Unit) {
                actions.addView(button(label, action).apply { isEnabled = enabled },
                    LinearLayout.LayoutParams(0, -2, 1f))
            }
            add(getString(R.string.haptic_edit)) { editStep(index) }
            add("↑", index > 0) {
                java.util.Collections.swap(steps, index, index - 1); renderRows()
            }
            add("↓", index < steps.lastIndex) {
                java.util.Collections.swap(steps, index, index + 1); renderRows()
            }
            add(getString(R.string.haptic_delete)) { steps.removeAt(index); renderRows() }
            rows.addView(actions)
        }
        if (steps.isEmpty()) rows.addView(TextView(requireContext()).apply { setText(R.string.haptic_empty) })
        addButtons.forEach { it.isEnabled = steps.size < HapticPattern.MAX_STEPS }
    }

    private fun editStep(index: Int?) {
        if (index == null && steps.size >= HapticPattern.MAX_STEPS) return
        val initial = index?.let { steps[it] } ?: HapticStep(5, 32)
        val fields = column().apply { setPadding(dp(20), 0, dp(20), 0) }
        fun field(label: Int, value: String): EditText {
            fields.addView(TextView(requireContext()).apply { setText(label) })
            return EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(value)
                selectAll()
                fields.addView(this)
            }
        }
        val duration = field(R.string.haptic_duration, initial.durationMs.toString())
        val amplitude = field(R.string.haptic_amplitude, initial.amplitude.toString())
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.haptic_edit)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ms = duration.text.toString().toLongOrNull()
                val amp = amplitude.text.toString().toIntOrNull()
                if (ms == null || ms < 0) {
                    duration.error = getString(R.string.haptic_duration_error)
                    return@setOnClickListener
                }
                if (amp == null || amp !in 0..255) {
                    amplitude.error = getString(R.string.haptic_amplitude_error)
                    return@setOnClickListener
                }
                val step = HapticStep(ms, amp)
                if (index == null) append(step) else { steps[index] = step; renderRows() }
                dialog.dismiss()
            }
        }
        dialog.show()
        // The step editor is owned by this dialog; avoid leaking its window on rotation/dismissal.
        stepDialog?.dismiss()
        stepDialog = dialog
    }

    private var stepDialog: AlertDialog? = null
    override fun onDestroyView() {
        stepDialog?.dismiss()
        stepDialog = null
        addButtons.clear()
        super.onDestroyView()
    }

    private fun column() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    private fun button(label: String, action: () -> Unit) = Button(requireContext()).apply {
        text = label
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_KIND = "kind"
        private const val STATE_DRAFT = "draft"
        fun newInstance(kind: HapticPatternKind) = HapticPatternEditorDialog().apply {
            arguments = Bundle().apply { putString(ARG_KIND, kind.name) }
        }
        fun titleFor(kind: HapticPatternKind): Int = when (kind) {
            HapticPatternKind.NORMAL_FLICK -> R.string.haptic_normal
            HapticPatternKind.TWO_STEP_FLICK -> R.string.haptic_two_step
            HapticPatternKind.SPECIAL_KEY -> R.string.haptic_special
        }
    }
}
