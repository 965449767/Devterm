package com.devterm.terminal.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.devterm.terminal.core.renderer.RenderFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

@Composable
fun TerminalCanvas(
    snapshot: StateFlow<RenderFrame>,
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit = {},
    defaultBg: Int = 0xFF1A1A2E.toInt(),
    cursorColor: Int = 0xFF89B4FA.toInt(),
    cursorBlinkEnabled: Boolean = true
) {
    val frame by snapshot.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    // 光标闪烁状态：500ms 切换一次
    var cursorBlink by remember { mutableStateOf(true) }
    LaunchedEffect(cursorBlinkEnabled) {
        if (cursorBlinkEnabled) {
            while (isActive) {
                delay(500)
                cursorBlink = !cursorBlink
            }
        } else {
            cursorBlink = true
        }
    }

    val renderer = remember(textMeasurer) {
        ComposeTerminalRenderer(
            textMeasurer = textMeasurer,
            defaultBg = defaultBg,
            cursorColor = cursorColor
        ).also { it.initMetrics() }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(defaultBg))
            .focusRequester(focusRequester)
            .focusable()
            .onSizeChanged { size -> onSizeChanged(size) }
    ) {
        renderer.draw(this, frame, cursorBlink)
    }
}
