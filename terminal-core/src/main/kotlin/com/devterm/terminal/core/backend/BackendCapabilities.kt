package com.devterm.terminal.core.backend

/**
 * Backend 能力描述。
 *
 * 不同的 Backend 实现具有不同的能力：
 * - ProcessBackend：管道模式，无 PTY，需要 localEcho 补偿
 * - PtyBackend：真正的 PTY，支持信号、回显、resize 通知
 *
 * Renderer 和 KeyboardHandler 根据这些能力调整行为。
 */
data class BackendCapabilities(
    /** 是否为真正的 PTY（伪终端） */
    val isPty: Boolean = false,
    /** 是否需要 App 层本地回显（PTY 模式下 shell 会回显，不需要） */
    val needsLocalEcho: Boolean = true,
    /** 是否支持作业控制信号（Ctrl+C → SIGINT 等） */
    val supportsSignals: Boolean = false,
    /** 是否支持窗口大小变更通知（SIGWINCH） */
    val supportsResize: Boolean = false,
    /** 是否支持 ANSI 颜色查询（isatty 返回 true 时程序会输出颜色） */
    val supportsColor: Boolean = false,
) {
    companion object {
        /** 管道模式（ProcessBackend）的默认能力 */
        val PIPE = BackendCapabilities(
            isPty = false,
            needsLocalEcho = true,
            supportsSignals = false,
            supportsResize = false,
            supportsColor = false,
        )

        /** PTY 模式的完整能力 */
        val PTY = BackendCapabilities(
            isPty = true,
            needsLocalEcho = false,
            supportsSignals = true,
            supportsResize = true,
            supportsColor = true,
        )
    }
}
