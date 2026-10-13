package com.romannepali.keyboard.editing

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class TextEditor {

    /** Sends a complete DOWN+UP pair; a bare DOWN is ignored by many editors. */
    private fun tap(ic: InputConnection, keyCode: Int) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun moveCursorLeft(ic: InputConnection, steps: Int = 1) {
        repeat(steps) { tap(ic, KeyEvent.KEYCODE_DPAD_LEFT) }
    }

    fun moveCursorRight(ic: InputConnection, steps: Int = 1) {
        repeat(steps) { tap(ic, KeyEvent.KEYCODE_DPAD_RIGHT) }
    }

    fun moveCursorUp(ic: InputConnection, steps: Int = 1) {
        repeat(steps) { tap(ic, KeyEvent.KEYCODE_DPAD_UP) }
    }

    fun moveCursorDown(ic: InputConnection, steps: Int = 1) {
        repeat(steps) { tap(ic, KeyEvent.KEYCODE_DPAD_DOWN) }
    }

    fun selectAll(ic: InputConnection) {
        ic.performContextMenuAction(android.R.id.selectAll)
    }

    fun copy(ic: InputConnection) {
        ic.performContextMenuAction(android.R.id.copy)
    }

    fun paste(ic: InputConnection) {
        ic.performContextMenuAction(android.R.id.paste)
    }

    fun cut(ic: InputConnection) {
        ic.performContextMenuAction(android.R.id.cut)
    }

    fun deleteWordBackward(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            return
        }
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        val count = (before.length - i).coerceAtLeast(1)
        ic.deleteSurroundingText(count, 0)
    }
}
