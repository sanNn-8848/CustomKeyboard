package com.romannepali.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.romannepali.keyboard.settings.SettingsActivity

class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var suggestionViews: List<TextView> = emptyList()
    private var undoButton: ImageButton? = null
    private var centerUndoButton: ImageButton? = null
    private var gearButton: ImageButton? = null
    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onUndoClickListener: (() -> Unit)? = null

    init {
        inflate(context, R.layout.suggestion_bar, this)

        suggestionViews = listOf(
            findViewById(R.id.suggestion_1),
            findViewById(R.id.suggestion_2),
            findViewById(R.id.suggestion_3)
        )
        undoButton = findViewById(R.id.clock_tap_undo)
        centerUndoButton = findViewById(R.id.undo_center)
        gearButton = findViewById(R.id.settings_gear)

        gearButton?.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        undoButton?.setOnClickListener {
            onUndoClickListener?.invoke()
        }

        centerUndoButton?.setOnClickListener {
            onUndoClickListener?.invoke()
        }

        suggestionViews.forEach { view ->
            view.setOnClickListener {
                val suggestion = view.text.toString()
                if (suggestion.isNotEmpty()) {
                    onSuggestionClickListener?.invoke(suggestion)
                }
            }
        }
    }

    fun showSuggestions(suggestions: List<String>) {
        suggestionViews.forEachIndexed { index, textView ->
            if (index < suggestions.size) {
                textView.text = suggestions[index]
                textView.visibility = View.VISIBLE
            } else {
                textView.text = ""
                textView.visibility = View.INVISIBLE
            }
        }
    }

    fun clearSuggestions() {
        suggestionViews.forEach {
            it.text = ""
            it.visibility = View.INVISIBLE
        }
    }

    fun setUndoState(available: Boolean, centered: Boolean) {
        if (centered) {
            undoButton?.visibility = GONE
            gearButton?.visibility = GONE
            suggestionViews.forEach { it.visibility = GONE }
            centerUndoButton?.visibility = VISIBLE
        } else {
            centerUndoButton?.visibility = GONE
            gearButton?.visibility = VISIBLE
            undoButton?.visibility = if (available) VISIBLE else GONE
            suggestionViews.forEach {
                it.visibility = if (it.text.isNotEmpty()) VISIBLE else INVISIBLE
            }
        }
    }

    @Deprecated("Use setUndoState instead", replaceWith = ReplaceWith("setUndoState(visible, false)"))
    fun setUndoVisible(visible: Boolean) = setUndoState(visible, false)

    /** FrostGlass theme switch for the prediction chips and bar icons. */
    fun applyTheme(dark: Boolean) {
        val chip = if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light
        val selected =
            if (dark) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_light_selected
        val textRes = if (dark) R.color.letter_text_dark else R.color.letter_text_light
        val icon = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)
        val tint = ColorStateList.valueOf(icon)

        suggestionViews.forEachIndexed { index, view ->
            view.setBackgroundResource(if (index == 0) selected else chip)
            view.setTextColor(resources.getColor(textRes, null))
        }

        undoButton?.setBackgroundResource(chip)
        undoButton?.imageTintList = tint
        centerUndoButton?.setBackgroundResource(selected)
        centerUndoButton?.imageTintList = tint
        gearButton?.imageTintList = tint
    }
}
