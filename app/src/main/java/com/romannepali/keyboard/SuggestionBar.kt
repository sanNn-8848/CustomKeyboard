package com.romannepali.keyboard

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.settings.SettingsActivity

class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var suggestionViews: List<TextView> = emptyList()
    private var undoButton: ImageButton? = null
    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onUndoClickListener: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        inflate(context, R.layout.suggestion_bar, this)

        suggestionViews = listOf(
            findViewById(R.id.suggestion_1),
            findViewById(R.id.suggestion_2),
            findViewById(R.id.suggestion_3)
        )
        undoButton = findViewById(R.id.clock_tap_undo)

        findViewById<View>(R.id.settings_gear).setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        undoButton?.setOnClickListener {
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

    fun setUndoVisible(visible: Boolean) {
        undoButton?.visibility = if (visible) View.VISIBLE else View.GONE
    }
}