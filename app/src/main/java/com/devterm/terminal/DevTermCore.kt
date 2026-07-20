package com.devterm.terminal

import com.devterm.terminal.core.TerminalCore
import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.BackendCapabilities
import com.devterm.terminal.core.backend.TerminalSession
import kotlinx.coroutines.flow.StateFlow

/**
 * App 层适配器：包装 TerminalCore，对外暴露简化的 API。
 *
 * 职责：
 * 1. 持有 TerminalCore 实例（屏幕缓冲、Parser、Renderer）
 * 2. 管理 Backend 生命周期
 * 3. 暴露 Backend 能力给 UI 层（KeyboardHandler、Canvas）
 *
 * Backend 类型（ProcessBackend 或 PtyBackend）由 BackendFactory 决定，
 * 调用方通过 [capabilities] 查询能力，据此调整行为（如 localEcho）。
 */
class DevTermCore(
    cols: Int = 80,
    rows: Int = 24
) {
    val core = TerminalCore(cols, rows)
    val frame: StateFlow<com.devterm.terminal.core.renderer.RenderFrame> get() = core.frame

    private var session: TerminalSession? = null
    private var _capabilities: BackendCapabilities = BackendCapabilities.PIPE

    /** 当前 Backend 的能力描述 */
    val capabilities: BackendCapabilities get() = _capabilities

    fun attachBackend(backend: Backend) {
        _capabilities = backend.capabilities
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
        session?.resize(cols, rows)
    }

    fun reset() {
        core.reset()
    }
}
