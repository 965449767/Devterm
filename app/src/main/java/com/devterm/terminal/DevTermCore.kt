package com.devterm.terminal

import com.devterm.terminal.core.TerminalCore
import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.TerminalSession
import kotlinx.coroutines.flow.StateFlow

/**
 * App 层适配器：包装 TerminalCore，对外暴露简化的 API。
 * Backend 直接复用 terminal-core 模块的 ProcessBackend，避免代码重复。
 */
class DevTermCore(
    cols: Int = 80,
    rows: Int = 24
) {
    val core = TerminalCore(cols, rows)
    val frame: StateFlow<com.devterm.terminal.core.renderer.RenderFrame> get() = core.frame

    private var session: TerminalSession? = null

    fun attachBackend(backend: Backend) {
        val terminalSession = TerminalSession(
            backend = backend,
            outputCallback = { data -> core.onBackendOutput(data) }
        )
        core.setSession(terminalSession)
        session = terminalSession
    }

    fun start() {
        core.startRenderLoop()
        core.startSession()
    }

    fun stop() {
        core.stopSession()
    }

    fun write(text: String) {
        core.writeInput(text)
    }

    fun write(data: ByteArray) {
        core.writeInput(data)
    }

    fun resize(cols: Int, rows: Int) {
        core.resize(cols, rows)
    }

    fun reset() {
        core.reset()
    }
}
