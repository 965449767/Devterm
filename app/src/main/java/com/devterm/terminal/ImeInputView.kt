package com.devterm.terminal

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@Composable
fun ImeInputView(
    terminalEmulator: TerminalEmulator,
    onViewCreated: (TerminalInputView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            TerminalInputView(context, terminalEmulator).also { view ->
                onViewCreated(view)
            }
        },
        modifier = modifier.size(1.dp)
    )
}

class TerminalInputView(
    context: Context,
    private val terminalEmulator: TerminalEmulator
) : View(context) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        return TerminalInputConnection(this, terminalEmulator)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }
}

class TerminalInputConnection(
    targetView: View,
    private val terminalEmulator: TerminalEmulator
) : BaseInputConnection(targetView, false) {

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        for (ch in text.toString()) {
            terminalEmulator.dispatchCharacter(0, ch.code)
        }
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength.coerceIn(0, 100)) {
            terminalEmulator.dispatchKey(
                TerminalEmulator.MOD_NONE,
                TerminalEmulator.KEY_BACKSPACE
            )
        }
        repeat(afterLength.coerceIn(0, 100)) {
            terminalEmulator.dispatchKey(
                TerminalEmulator.MOD_NONE,
                TerminalEmulator.KEY_DELETE
            )
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> terminalEmulator.dispatchKey(
                    TerminalEmulator.MOD_NONE,
                    TerminalEmulator.KEY_BACKSPACE
                )
                KeyEvent.KEYCODE_ENTER -> terminalEmulator.dispatchCharacter(
                    TerminalEmulator.MOD_NONE, 0x0D
                )
            }
        }
        return true
    }

    override fun performEditorAction(actionCode: Int): Boolean = true
}
