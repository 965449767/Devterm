package com.devterm.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.devterm.terminal.renderer.TerminalCanvas

@Composable
fun TerminalScreenNew(
    devTermCore: DevTermCore,
    keyboardHandler: KeyboardHandlerNew,
    modifier: Modifier = Modifier,
    defaultBg: Int = 0xFF1A1A2E.toInt(),
    cursorColor: Int = 0xFF89B4FA.toInt(),
    onColsRows: (Int, Int) -> Unit = { _, _ -> }
) {
    val focusRequester = remember { FocusRequester() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(defaultBg))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent ?: return@onKeyEvent false
                keyboardHandler.onKeyEvent(keyEvent)
            }
    ) {
        TerminalCanvas(
            snapshot = devTermCore.frame,
            modifier = Modifier.fillMaxSize(),
            defaultBg = defaultBg,
            cursorColor = cursorColor,
            onSizeChanged = { size ->
                canvasSize = size
                val charWidthPx = 8.4f * density.density
                val charHeightPx = 14.4f * density.density
                val cols = (size.width / charWidthPx).toInt().coerceAtLeast(20)
                val rows = (size.height / charHeightPx).toInt().coerceAtLeast(5)
                onColsRows(cols, rows)
            }
        )
    }
}
