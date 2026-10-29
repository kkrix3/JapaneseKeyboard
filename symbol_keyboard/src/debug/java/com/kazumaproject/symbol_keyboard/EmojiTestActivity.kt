package com.kazumaproject.symbol_keyboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener

/** Disposable debug-only host; recreated with the same data and saved preferences. */
class EmojiTestActivity : AppCompatActivity() {
    lateinit var keyboard: CustomSymbolKeyboardView
    var lastClicked: ClickedSymbol? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        keyboard = CustomSymbolKeyboardView(this).apply {
            setLifecycleOwner(this@EmojiTestActivity)
            setOnSymbolRecyclerViewItemClickListener(object : SymbolRecyclerViewItemClickListener {
                override fun onClick(symbol: ClickedSymbol) { lastClicked = symbol }
            })
            setSymbolLists(listOf(Emoji("👍", EmojiCategory.PEOPLE_BODY)),
                listOf(Emoticon("(^_^)", EmoticonCategory.SMILE)), emptyList(), emptyList(),
                listOf(ClickedSymbol(mode = SymbolMode.EMOJI, symbol = "👩🏽‍💻")))
        }
        setContentView(keyboard)
    }
}
