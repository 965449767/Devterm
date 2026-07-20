package com.devterm.terminal.core.backend

/**
 * 终端后端抽象接口。
 *
 * 实现类：
 * - [ProcessBackend]：基于 ProcessBuilder 的管道模式（默认）
 * - PtyBackend：基于 openpty 的 PTY 模式（未来通过 .so 实现）
 */
interface Backend {
    /** 启动后端进程，注册回调 */
    fun start(callback: BackendCallback)

    /** 向后端写入数据（用户输入） */
    fun write(data: ByteArray)

    /** 通知后端终端尺寸变化（PTY 模式下会发送 SIGWINCH） */
    fun resize(cols: Int, rows: Int)

    /** 停止后端进程 */
    fun stop()

    /**
     * 后端能力描述。
     * 调用方据此决定是否启用 localEcho、如何处理 Ctrl+C 等。
     */
    val capabilities: BackendCapabilities
}

/**
 * 后端事件回调
 */
interface BackendCallback {
    /** 后端输出数据（stdout/stderr） */
    fun onOutput(data: ByteArray)

    /** 后端进程退出 */
    fun onExit(exitCode: Int)

    /** 后端请求修改窗口标题（OSC 序列） */
    fun onTitleChanged(title: String)
}
