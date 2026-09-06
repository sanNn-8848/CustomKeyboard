package com.romannepali.keyboard.editing

import android.view.inputmethod.InputConnection

class TextEditor {
    
    fun moveCursorLeft(ic: InputConnection, steps: Int = 1) {
        repeat(steps) {
            ic.sendKeyEvent(android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT
            ))
        }
    }
    
    fun moveCursorRight(ic: InputConnection, steps: Int = 1) {
        repeat(steps) {
            ic.sendKeyEvent(android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            ))
        }
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
        ic.deleteSurroundingText(20, 0)
    }
}
