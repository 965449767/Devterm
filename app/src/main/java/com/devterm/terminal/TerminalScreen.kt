package com.devterm.terminal

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlinx.coroutines.withTimeoutOrNull
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CharMetrics(
    val width: Float,
    val height: Float
)

@OptIn(ExperimentalComposeUiApi::class)
@Deprecated("Use TerminalScreenNew instead", ReplaceWith("TerminalScreenNew"))
@Composable
fun TerminalScreen(
    terminalEmulator: TerminalEmulator,
    keyboardHandler: KeyboardHandler,
    modifier: Modifier = Modifier
) {
    val snapshot by terminalEmulator.snapshot.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences("devterm", Context.MODE_PRIVATE) }
    var fontSizeSp by remember { mutableStateOf(prefs.getFloat("font_size", 14f)) }
    var lastZoomTimeMs by remember { mutableStateOf(0L) }
    val fontSize = fontSizeSp.sp
    val lineHeight = 1.2f

    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        color = Color.White
    )

    val charMetrics = remember(textMeasurer, fontSize) {
        val result = textMeasurer.measure(text = "M", style = textStyle)
        CharMetrics(
            width = result.size.width.toFloat(),
            height = result.size.height.toFloat() * lineHeight
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(canvasSize, charMetrics) {
        delay(16)
        if (canvasSize.width > 0 && canvasSize.height > 0
            && charMetrics.width > 0 && charMetrics.height > 0) {
            val cols = (canvasSize.width / charMetrics.width).toInt().coerceAtLeast(20)
            val rows = (canvasSize.height / charMetrics.height).toInt().coerceAtLeast(5)
            terminalEmulator.resize(rows, cols)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val defaultBg = Color(0xFF1E1E2E)

    var cursorBlink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorBlink = !cursorBlink
        }
    }

    var inputView by remember { mutableStateOf<TerminalInputView?>(null) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectionEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showToolbar by remember { mutableStateOf(true) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    fun cancelSelection() {
        selectionMode = false
        selectionStart = null
        selectionEnd = null
    }

    fun showKeyboard() {
        inputView?.let { view ->
            view.requestFocus()
            view.post {
                val imm = context.getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager
                imm.showSoftInput(view, 0)
            }
        }
    }

    fun copySelection() {
        val s = selectionStart ?: return
        val e = selectionEnd ?: return
        val text = terminalEmulator.getScreenText(
            s.first, s.second, e.first, e.second
        )
        if (text.isNotEmpty()) {
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText("terminal", text)
            )
        }
        cancelSelection()
    }

    fun selectAll() {
        val lastRow = snapshot.lines.indexOfLast { line ->
            line.cells.any { it.char != ' ' }
        }.coerceAtLeast(0)
        selectionMode = true
        selectionStart = Pair(0, 0)
        selectionEnd = Pair(lastRow, terminalEmulator.cols - 1)
    }

    val handleRadius = 16f

    val measureCache = remember(fontSizeSp) { hashMapOf<String, TextLayoutResult>() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(defaultBg)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent ?: return@onKeyEvent false
                keyboardHandler.onKeyEvent(keyEvent)
            }
    ) {
        ImeInputView(
            terminalEmulator = terminalEmulator,
            onViewCreated = { inputView = it }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            LaunchedEffect(fontSizeSp) {
                if (fontSizeSp != prefs.getFloat("font_size", 14f)) {
                    delay(500)
                    prefs.edit().putFloat("font_size", fontSizeSp).apply()
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val downPos = down.position
                            val col = (downPos.x / charMetrics.width).toInt()
                            val row = (downPos.y / charMetrics.height).toInt()

                            if (selectionMode) {
                                val s = selectionStart
                                val e = selectionEnd
                                val tol = max(charMetrics.width * 3f, 48f)
                                var draggingStart = false
                                var draggingEnd = false
                                if (s != null) {
                                    val sx = s.second * charMetrics.width
                                    val sy = s.first * charMetrics.height
                                    if (abs(downPos.x - sx) < tol && abs(downPos.y - sy) < tol) {
                                        draggingStart = true
                                    }
                                }
                                if (e != null && !draggingStart) {
                                    val ex = e.second * charMetrics.width
                                    val ey = e.first * charMetrics.height
                                    if (abs(downPos.x - ex) < tol && abs(downPos.y - ey) < tol) {
                                        draggingEnd = true
                                    }
                                }

                                if (!draggingStart && !draggingEnd && s != null && e != null) {
                                    val cr = (downPos.y / charMetrics.height).toInt()
                                        .coerceIn(0, terminalEmulator.rows - 1)
                                    val cc = (downPos.x / charMetrics.width).toInt()
                                        .coerceIn(0, terminalEmulator.cols - 1)
                                    if (isInSelection(cr, cc, s, e)) {
                                        val ds = abs(downPos.x - s.second * charMetrics.width) +
                                                abs(downPos.y - s.first * charMetrics.height)
                                        val de = abs(downPos.x - e.second * charMetrics.width) +
                                                abs(downPos.y - e.first * charMetrics.height)
                                        if (ds <= de) draggingStart = true
                                        else draggingEnd = true
                                    }
                                }

                                if (draggingStart || draggingEnd) {
                                    showToolbar = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull {
                                            it.id == down.id && it.pressed
                                        } ?: break
                                        val dc = (change.position.x / charMetrics.width).toInt()
                                            .coerceIn(0, terminalEmulator.cols - 1)
                                        val dr = (change.position.y / charMetrics.height).toInt()
                                            .coerceIn(0, terminalEmulator.rows - 1)
                                        if (draggingStart) {
                                            selectionStart = Pair(dr, dc)
                                        } else {
                                            selectionEnd = Pair(dr, dc)
                                        }
                                        change.consume()
                                    }
                                    showToolbar = true
                                } else {
                                    waitForUpOrCancellation()
                                    cancelSelection()
                                }
                            } else {
                                val col = (downPos.x / charMetrics.width).toInt()
                                    .coerceIn(0, terminalEmulator.cols - 1)
                                val row = (downPos.y / charMetrics.height).toInt()
                                    .coerceIn(0, terminalEmulator.rows - 1)
                                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                }
                                if (up == null) {
                                    val multi = awaitPointerEvent(
                                        PointerEventPass.Main
                                    )
                                    if (multi.changes.count { it.pressed } > 1) {
                                        return@awaitEachGesture
                                    }

                                    val change = multi.changes.firstOrNull { it.id == down.id }
                                        ?: return@awaitEachGesture
                                    val dx = change.position.x - downPos.x
                                    val dy = change.position.y - downPos.y
                                    val slop = max(charMetrics.width * 2f, 16f)
                                    if (dx * dx + dy * dy > slop * slop) {
                                        return@awaitEachGesture
                                    }

                                    val snap = snapshot
                                    val char = snap.lines.getOrNull(row)
                                        ?.cells?.getOrNull(col)?.char
                                    if (char != null && char != ' ') {
                                        selectionMode = true
                                        showToolbar = true
                                        val bounds = terminalEmulator.getWordBoundary(row, col)
                                        selectionStart = bounds.first
                                        selectionEnd = bounds.second
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull {
                                                it.id == down.id && it.pressed
                                            } ?: break
                                            val dc = (change.position.x / charMetrics.width).toInt()
                                                .coerceIn(0, terminalEmulator.cols - 1)
                                            val dr = (change.position.y / charMetrics.height).toInt()
                                                .coerceIn(0, terminalEmulator.rows - 1)
                                            selectionEnd = Pair(dr, dc)
                                            showToolbar = false
                                            change.consume()
                                        }
                                        showToolbar = true
                                    } else {
                                        val clip = clipboardManager.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString()
                                            if (!text.isNullOrEmpty()) {
                                                terminalEmulator.paste(text)
                                            }
                                        }
                                    }
                                } else {
                                    if (System.currentTimeMillis() - lastZoomTimeMs > 200) {
                                        showKeyboard()
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newSize = (fontSizeSp * zoom).coerceIn(10f, 28f)
                            if (newSize != fontSizeSp) {
                                fontSizeSp = newSize
                                lastZoomTimeMs = System.currentTimeMillis()
                                if (selectionMode) cancelSelection()
                            }
                        }
                    }
            ) {
                if (charMetrics.width <= 0f || charMetrics.height <= 0f) return@Canvas

                drawRect(color = defaultBg, size = size)

                val rows = minOf(snapshot.rows, (size.height / charMetrics.height).toInt())
                val cols = snapshot.cols

                for (row in 0 until rows) {
                    if (row >= snapshot.lines.size) break
                    val line = snapshot.lines[row]
                    val y = row * charMetrics.height

                    var x = 0f
                    for ((col, cell) in line.cells.withIndex()) {
                        val cellWidth = charMetrics.width * cell.width.coerceAtLeast(1)

                        val isSelected = selectionMode && selectionStart != null && selectionEnd != null &&
                            isInSelection(row, col, selectionStart!!, selectionEnd!!)

                        val bg = when {
                            isSelected -> 0x4089B4FA
                            !cell.reverse -> cell.bgColor
                            else -> cell.fgColor
                        }
                        val fg = if (!cell.reverse) cell.fgColor else cell.bgColor

                        val drawBg = Color(bg)
                        if (drawBg != defaultBg) {
                            drawRect(
                                color = drawBg,
                                topLeft = Offset(x, y),
                                size = Size(cellWidth, charMetrics.height)
                            )
                        }

                        if (cell.char != ' ') {
                            val cellStyle = textStyle.copy(
                                color = Color(fg),
                                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )
                            val text = cell.char.toString()
                            val measured = measureCache.getOrPut(
                                "${cell.char}_${fg}_${cell.bold}_${cell.italic}"
                            ) { textMeasurer.measure(text, cellStyle) }
                            val charX = if (cell.width > 1) {
                                x + (cellWidth - measured.size.width) / 2f
                            } else {
                                x
                            }
                            val vy = y + (charMetrics.height - measured.size.height) / 2f
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(charX, vy)
                            )
                        }

                        val lineColor = Color(fg)

                        if (cell.underline > 0) {
                            val underlineY = y + charMetrics.height - 2f
                            drawLine(
                                color = lineColor,
                                start = Offset(x, underlineY),
                                end = Offset(x + cellWidth, underlineY),
                                strokeWidth = 1f
                            )
                        }

                        if (cell.strike) {
                            val strikeY = y + charMetrics.height / 2
                            drawLine(
                                color = lineColor,
                                start = Offset(x, strikeY),
                                end = Offset(x + cellWidth, strikeY),
                                strokeWidth = 1f
                            )
                        }

                        x += cellWidth
                    }
                }

                if (selectionMode) {
                    val s = selectionStart
                    val e = selectionEnd
                    if (s != null) {
                        drawHandle(
                            centerX = s.second * charMetrics.width,
                            centerY = s.first * charMetrics.height,
                            radius = handleRadius,
                            color = Color(0xFF89B4FA)
                        )
                    }
                    if (e != null) {
                        drawHandle(
                            centerX = e.second * charMetrics.width,
                            centerY = e.first * charMetrics.height,
                            radius = handleRadius,
                            color = Color(0xFFF38BA8)
                        )
                    }
                }

                if (snapshot.cursorVisible && cursorBlink) {
                    val cursorX = snapshot.cursorCol * charMetrics.width
                    val cursorY = snapshot.cursorRow * charMetrics.height
                    if (snapshot.cursorRow < rows) {
                        drawRect(
                            color = Color(0xFF89B4FA),
                            topLeft = Offset(cursorX, cursorY),
                            size = Size(charMetrics.width, charMetrics.height),
                            alpha = 0.5f
                        )
                    }
                }
            }
        }

        if (selectionMode && showToolbar) {
            val s = selectionStart
            val e = selectionEnd
            if (s != null && e != null) {
                val density = LocalDensity.current
                var toolbarSize by remember { mutableStateOf(IntSize.Zero) }

                val tw = toolbarSize.width.toFloat().coerceAtLeast(1f)
                val th = toolbarSize.height.toFloat().coerceAtLeast(1f)

                val r1 = minOf(s.first, e.first)
                val r2 = maxOf(s.first, e.first)
                val selectionTopY = r1 * charMetrics.height
                val selectionBottomY = (r2 + 1) * charMetrics.height

                val selectionCenterX = if (r1 == r2) {
                    val leftCol = minOf(s.second, e.second)
                    val rightCol = maxOf(s.second, e.second)
                    (leftCol + rightCol + 1) * charMetrics.width / 2f
                } else {
                    canvasSize.width / 2f
                }

                val gapPx = with(density) { 8.dp.toPx() }
                val belowY = selectionBottomY + gapPx
                val aboveY = selectionTopY - th - gapPx

                val offsetY = when {
                    belowY + th <= canvasSize.height -> belowY.toInt()
                    aboveY >= 0 -> aboveY.toInt()
                    else -> 0
                }
                val offsetX = (selectionCenterX - tw / 2f).toInt()
                    .coerceIn(0, (canvasSize.width - tw).toInt().coerceAtLeast(0))

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .onSizeChanged { toolbarSize = it }
                        .background(Color(0xFF2D2D3F), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ToolbarButton("Copy") { copySelection() }
                        ToolbarButton("Select All") { selectAll() }
                        val clip = clipboardManager.primaryClip
                        val hasClip = clip != null && clip.itemCount > 0 && clip.getItemAt(0).text != null
                        if (hasClip) {
                            ToolbarButton("Paste") {
                                val text = clip!!.getItemAt(0).text.toString()
                                terminalEmulator.paste(text)
                                cancelSelection()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color(0xFFCDD6F4), fontSize = 13.sp)
    }
}

private fun DrawScope.drawHandle(centerX: Float, centerY: Float, radius: Float, color: Color) {
    drawCircle(color = color, radius = radius, center = Offset(centerX, centerY), alpha = 0.8f)
    drawCircle(color = Color.White, radius = radius * 0.5f, center = Offset(centerX, centerY))
}

private fun isInSelection(
    row: Int,
    col: Int,
    selStart: Pair<Int, Int>,
    selEnd: Pair<Int, Int>
): Boolean {
    val r1 = minOf(selStart.first, selEnd.first)
    val r2 = maxOf(selStart.first, selEnd.first)
    val c1 = if (selStart.first <= selEnd.first) selStart.second else selEnd.second
    val c2 = if (selStart.first <= selEnd.first) selEnd.second else selStart.second
    return if (row < r1 || row > r2) false
    else if (row == r1 && row == r2) col in c1..c2
    else if (row == r1) col >= c1
    else if (row == r2) col <= c2
    else true
}
