package com.devterm.terminal.core

import com.devterm.terminal.core.backend.TerminalSession
import com.devterm.terminal.core.parser.VtParser
import com.devterm.terminal.core.renderer.FrameQueue
import com.devterm.terminal.core.renderer.RenderFrame
import com.devterm.terminal.core.screen.ScreenBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TerminalCore(
    var cols: Int = 80,
    var rows: Int = 24
) {
    val screen = ScreenBuffer(cols, rows)
    val parser = VtParser()
    val frameQueue = FrameQueue()

    private val _frame = MutableStateFlow(buildEmptyFrame())
    val frame: StateFlow<RenderFrame> = _frame.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _bell = MutableStateFlow(0)
    val bell: StateFlow<Int> = _bell.asStateFlow()

    private var session: TerminalSession? = null
    private var sessionStarted = false
    private var renderJob: Job? = null
    private val renderScope = CoroutineScope(Dispatchers.Default)

    init {
        _frame.value = buildFrame(emptyList())
        // 绑定 ScreenBuffer 的 Bell 回调
        screen.onBell = {
            _bell.value = _bell.value + 1
        }
    }

    private fun buildEmptyFrame(): RenderFrame = buildFrame(emptyList())

    private fun buildFrame(dirtyRows: List<Int>): RenderFrame {
        return RenderFrame(
            dirtyRows = dirtyRows,
            chars = screen.chars,
            fg = screen.fg,
            bg = screen.bg,
            flags = screen.flags,
            cols = screen.cols,
            rows = screen.rows,
            cursorRow = screen.cursor.row,
            cursorCol = screen.cursor.col,
            cursorVisible = screen.cursor.visible,
            cursorStyle = when (screen.cursor.style) {
                com.devterm.terminal.core.screen.CursorState.CursorStyle.BLOCK -> RenderFrame.CursorStyle.BLOCK
                com.devterm.terminal.core.screen.CursorState.CursorStyle.UNDERLINE -> RenderFrame.CursorStyle.UNDERLINE
                com.devterm.terminal.core.screen.CursorState.CursorStyle.BAR -> RenderFrame.CursorStyle.BAR
            }
        )
    }

    fun setSession(session: TerminalSession) {
        this.session = session
    }

    fun onBackendOutput(data: ByteArray) {
        val commands = parser.consume(data)
        for (cmd in commands) {
            when (cmd) {
                is com.devterm.terminal.core.parser.ScreenCommand.SetTitle -> {
                    _title.value = cmd.title
                }
                is com.devterm.terminal.core.parser.ScreenCommand.SetIconName -> {
                    _title.value = cmd.name
                }
                is com.devterm.terminal.core.parser.ScreenCommand.RequestCursorPosition -> {
                    // 回传光标位置：CSI row;col R（1-indexed）
                    val row = screen.cursor.row + 1
                    val col = screen.cursor.col + 1
                    writeInput("\u001b[$row;${col}R")
                }
                is com.devterm.terminal.core.parser.ScreenCommand.DeviceStatusReport -> {
                    if (cmd.n == 5) {
                        // 回传设备就绪：CSI 0 n
                        writeInput("\u001b[0n")
                    }
                }
                else -> screen.execute(cmd)
            }
        }
        frameQueue.notifyDirty()
    }

    fun startRenderLoop() {
        if (renderJob?.isActive == true) return
        renderJob = renderScope.launch {
            while (isActive) {
                val pending = frameQueue.consume()
                if (pending) {
                    emitFrame()
                }
                delay(16)
            }
        }
    }

    fun stopRenderLoop() {
        renderJob?.cancel()
        renderJob = null
    }

    fun emitFrame() {
        val dirtyRows = screen.dirty.consume()
        if (dirtyRows.isEmpty()) return
        _frame.value = buildFrame(dirtyRows)
    }

    fun writeInput(text: String) {
        session?.write(text.encodeToByteArray())
    }

    fun writeInput(data: ByteArray) {
        session?.write(data)
    }

    fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
        screen.resize(cols, rows)
        session?.resize(cols, rows)
        emitFrame()
    }

    fun reset() {
        parser.reset()
        screen.eraseDisplay(2)
        emitFrame()
    }

    fun startSession() {
        if (sessionStarted) return
        sessionStarted = true
        session?.start()
    }

    fun stopSession() {
        sessionStarted = false
        session?.stop()
        stopRenderLoop()
    }
}
