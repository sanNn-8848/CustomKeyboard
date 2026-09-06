package com.romannepali.keyboard.emoji

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EmojiKeyboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var onEmojiClickListener: ((String) -> Unit)? = null
    
    private val emojis = listOf(
        // Smileys
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
        "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🫡",
        
        // Hands
        "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏",
        "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
        "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛",
        "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "💪",
        
        // Hearts
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
        "💘", "💝", "💟", "♥️", "🫶", "💑", "💏", "👩‍❤️‍👨",
        
        // Animals
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵",
        "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
        "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄",
        
        // Food
        "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
        "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
        "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑",
        "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🥐",
        
        // Activities
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
        "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🥅", "⛳",
        "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹",
        "🛼", "🛷", "⛸️", "🥌", "🎿", "🎯", "🪃", "🪄",
        
        // Objects
        "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️",
        "🖲️", "🕹️", "🗜️", "💽", "💾", "💿", "📀", "📼",
        "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞", "☎️",
        "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭",
        
        // Symbols
        "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪",
        "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷", "🔳",
        "🔲", "▪️", "▫️", "◾", "◽", "◼️", "◻️", "🟥",
        "🟧", "🟨", "🟩", "🟦", "🟪", "⬛", "⬜", "🟫",
        
        // Flags
        "🏳️", "🏴", "🏁", "🚩", "🎌", "🏴‍☠️", "🏳️‍🌈", "🏳️‍⚧️",
        "🇺🇸", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹", "🇪🇸", "🇷🇺", "🇨🇳",
        "🇯🇵", "🇰🇷", "🇮🇳", "🇳🇵", "🇧🇷", "🇨🇦", "🇦🇺", "🇲🇽"
    )

    init {
        layoutManager = GridLayoutManager(context, 8)
        adapter = EmojiAdapter()
    }

    private inner class EmojiAdapter : RecyclerView.Adapter<EmojiViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return EmojiViewHolder(view)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.bind(emojis[position])
        }

        override fun getItemCount() = emojis.size
    }

    private inner class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val textView: TextView = itemView as TextView
        
        fun bind(emoji: String) {
            textView.text = emoji
            textView.textSize = 24f
            textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textView.setOnClickListener {
                onEmojiClickListener?.invoke(emoji)
            }
        }
    }

    fun getEmojiByCategory(category: String): List<String> {
        return when (category) {
            "smileys" -> emojis.take(32)
            "hands" -> emojis.drop(32).take(32)
            "hearts" -> emojis.drop(64).take(24)
            "animals" -> emojis.drop(88).take(32)
            "food" -> emojis.drop(120).take(32)
            "activities" -> emojis.drop(152).take(32)
            "objects" -> emojis.drop(184).take(32)
            "symbols" -> emojis.drop(216).take(32)
            "flags" -> emojis.drop(248)
            else -> emojis
        }
    }
}
