package com.romannepali.keyboard.emoji

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.Prefs
import com.romannepali.keyboard.R

/**
 * The one Emoji panel in the keyboard. Replaces the old limited strip and the
 * QuickTools rail emoji so there is a single dataset behind every entry point.
 *
 * Layout (top -> bottom): search line, category chips, scrolling grid.
 * Typing is simulated through local key routing: the search line never grabs
 * the input session, so it stays focused without stealing the app field.
 */
class EmojiSearchPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val root: LinearLayout
    private val searchInput: EditText
    private val clearButton: TextView
    private val chipContainer: LinearLayout
    private val grid: GridView

    private var dark = true
    private var onEmojiSelected: ((String) -> Unit)? = null
    private var activeGroup = ALL_GROUP
    private var query = ""
    private var searchTarget = true

    private val data = EmojiDataSet.EMOJIS

    init {
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        // Search line.
        searchInput = EditText(context).apply {
            hint = "Search emoji…"
            textSize = 13f
            setSingleLine(true)
            setIncludeFontPadding(false)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(6), dp(7))
            isFocusable = true
            isFocusableInTouchMode = false
            setOnClickListener { searchTarget = true; refreshSearchUi() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable) {
                    query = s?.toString()?.trim().orEmpty()
                    clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                    rebuildGrid()
                }
            })
            // Slight dark-glass background so the line reads as the emoji panel.
            background = resources.getDrawable(
                if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light,
                null
            )
        }

        clearButton = TextView(context).apply {
            text = "✕"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener {
                query = ""
                searchInput.setText("")
                activeGroup = ALL_GROUP
                hightlightChips()
            }
        }

        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(36), 1f))
        clearButton.visibility = View.GONE
        searchRow.addView(clearButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)))

        // Category chips.
        chipContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(chipContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        EMOTION_CHIPS.forEach { group ->
            val chip = TextView(context).apply {
                text = group.first
                textSize = 12f
                gravity = Gravity.CENTER
                setIncludeFontPadding(false)
                setPadding(dp(9), dp(5), dp(9), dp(5))
                background = resources.getDrawable(R.drawable.bg_suggestion_chip_dark, null)
                setOnClickListener {
                    activeGroup = group.second
                    searchInput.setText("")
                    rebuildGrid()
                    hightlightChips()
                }
            }
            chipContainer.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(5)
            })
        }

        // Grid.
        grid = GridView(context).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(52)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(2)
            verticalSpacing = dp(2)
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            adapter = null
        }

        root.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
        root.addView(chipsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private val expandedQuery: String
        get() = if (activeGroup == ALL_GROUP) "" else activeGroup

    private fun hightlightChips() {
        for (i in 0 until chipContainer.childCount) {
            val chip = chipContainer.getChildAt(i) as TextView
            val selected = EMOTION_CHIPS[i].second == activeGroup
            chip.background = resources.getDrawable(
                if (selected)
                    (if (dark) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_light_selected)
                else
                    (if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light),
                null
            )
            // Selection is carried by the chip's tinted background + stroke;
            // the text stays in the theme's ink colour so it never ends up
            // white-on-peach in the light theme.
            chip.setTextColor(
                resources.getColor(
                    if (dark) R.color.letter_text_dark else R.color.letter_text_light,
                    null
                )
            )
        }
    }

    private fun rebuildGrid() {
        val q = query.lowercase().trim()
        val matched = data.filter { emoji ->
            val inGroup = activeGroup == ALL_GROUP || emoji.group == activeGroup
            if (!inGroup) return@filter false
            if (q.isEmpty()) return@filter true
            emoji.names.any { it.contains(q) } || emoji.group.contains(q)
        }
        val emojis = matched.map { it.emoji }

        val existing = grid.adapter as? EmojiAdapter
        // Recreate when the theme flipped: the text colour is baked in at
        // construction, so a reused adapter would keep the old ink.
        if (existing == null || existing.dark != dark) {
            grid.adapter = EmojiAdapter(context, emojis, dark) { emoji ->
                onEmojiSelected?.invoke(emoji)
            }
        } else {
            if (existing.items == emojis) return
            // Reuse the adapter: rebuilding it on every keystroke re-inflates the
            // whole grid and stutters on low-end hardware.
            existing.setItems(emojis)
            existing.notifyDataSetChanged()
        }
    }

    /**
     * Resets the transient search/group state so the panel opens fresh each
     * time, then shows the requested list against the given commit callback.
     */
    fun open(onEmoji: (String) -> Unit) {
        onEmojiSelected = onEmoji
        dark = Prefs.darkTheme(context)
        query = ""
        activeGroup = ALL_GROUP
        searchInput.setText("")
        searchInput.setTextColor(resources.getColor(if (dark) R.color.letter_text_dark else R.color.letter_text_light, null))
        searchInput.setHintTextColor(
            if (dark) resources.getColor(android.R.color.white, null) and 0x55FFFFFF
            else resources.getColor(android.R.color.black, null) and 0x55FFFFFF
        )
        searchInput.background = resources.getDrawable(
            if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light,
            null
        )
        searchTarget = false
        refreshSearchUi()
        hightlightChips()
        rebuildGrid()
    }

    /**
     * Local key routing: the IME forwards pressed keys here while the search
     * line is the active "field" so tabbing into it never grabs the app input.
     */
    fun isSearchTarget(): Boolean = searchTarget && visibility == View.VISIBLE

    fun consumeKey(text: String) {
        when (text) {
            "⌫" -> if (searchInput.text?.isNotEmpty() == true) {
                searchInput.text.delete(searchInput.text.length - 1, searchInput.text.length)
            }
            "↵" -> {
                searchTarget = false
                refreshSearchUi()
            }
            else -> if (text.length == 1) {
                searchInput.text.append(text)
            }
        }
    }

    fun deactivateSearch() {
        searchTarget = false
        refreshSearchUi()
    }

    /** Shows whether the keyboard is feeding the search line or the app:
     *  the search field glows with the selected style while it is the target. */
    private fun refreshSearchUi() {
        searchInput.background = resources.getDrawable(
            if (searchTarget)
                (if (dark) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_light_selected)
            else
                (if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light),
            null
        )
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private class EmojiAdapter(
        context: Context,
        initialItems: List<String>,
        val dark: Boolean,
        val onClick: (String) -> Unit
    ) : android.widget.BaseAdapter() {

        private val ctx = context
        var items: List<String> = initialItems
            private set

        fun setItems(next: List<String>) {
            items = next
        }

        private val textColor = context.resources.getColor(
            if (dark) R.color.letter_text_dark else R.color.letter_text_light, null
        )

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(ctx).apply {
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            tv.text = items[position]
            tv.textSize = 22f
            tv.setOnClickListener { onClick(items[position]) }
            return tv
        }
    }

    private companion object {
        const val ALL_GROUP = "all"
        val EMOTION_CHIPS = listOf(
            "All" to "all",
            "🙂 Smileys" to "smileys",
            "❤️ Hearts" to "hearts",
            "✋ Hands" to "hands",
            "🍴 Food" to "food",
            "🐾 Animals" to "animals",
            "⭐ Symbols" to "symbols",
            "🚗 Travel" to "travel",
            "🏆 Activity" to "activity"
        )
    }
}

