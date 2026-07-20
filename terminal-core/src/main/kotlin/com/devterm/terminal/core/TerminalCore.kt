package com.devterm.terminal.core

import com.devterm.terminal.core.backend.TerminalSession
import com.devterm.terminal.core.parser.ScreenCommand
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
import kotlin.concurrent.thread

class TerminalCore(
    var cols: Int = 80,
    var rows: Int = 24
) {
    val screen = ScreenBuffer(cols, rows)
    val parser = VtParser()
    val frameQueue = FrameQueue()

    private val _frame = MutableStateFlow(buildEmptyFrame())
    val frame: StateFlow<RenderFrame> = _frame.asStateFlow()

    private var session: TerminalSession? = null
    private var sessionStarted = false
    private var renderJob: Job? = null
    private val renderScope = CoroutineScope(Dispatchers.Default)

    init {
        _frame.value = buildFrame(emptyList())
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
            cursorVisible = screen.cursor.visible
        )
    }

    fun setSession(session: TerminalSession) {
        this.session = session
    }

    fun onBackendOutput(data: ByteArray) {
        val commands = parser.consume(data)
        for (cmd in commands) {
            screen.execute(cmd)
            if (cmd is ScreenCommand.EraseDisplay && (cmd.mode == 2 || cmd.mode == 3)) {
                frameQueue.notifyDirty()
                emitFrame()
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
