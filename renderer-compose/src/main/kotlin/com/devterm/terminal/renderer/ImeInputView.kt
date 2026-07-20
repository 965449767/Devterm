package com.devterm.terminal.renderer

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class TerminalInputConnection(
    view: View,
    private val onCommitText: (String) -> Unit,
    private val onDeleteSurrounding: (Int, Int) -> Unit
) : BaseInputConnection(view, false) {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val result = super.commitText(text, newCursorPosition)
        text?.toString()?.let { onCommitText(it) }
        return result
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        onDeleteSurrounding(beforeLength, afterLength)
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val result = super.setComposingText(text, newCursorPosition)
        text?.toString()?.let { onCommitText(it) }
        return result
    }

    override fun finishComposingText(): Boolean {
        return true
    }
}

@Composable
fun ImeInputView(
    onCommitText: (String) -> Unit = {},
    onDeleteSurrounding: (Int, Int) -> Unit = { _, _ -> },
    onViewCreated: (View) -> Unit = {}
) {
    val context = LocalContext.current
    val view = remember {
        object : View(context) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
                outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                outAttrs.inputType = EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                return TerminalInputConnection(
                    this,
                    onCommitText = onCommitText,
                    onDeleteSurrounding = onDeleteSurrounding
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onViewCreated(view)
        onDispose {}
    }
}
