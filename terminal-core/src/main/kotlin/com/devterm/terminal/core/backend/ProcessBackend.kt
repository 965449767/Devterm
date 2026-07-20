package com.devterm.terminal.core.backend

import java.io.OutputStream

/**
 * 基于 ProcessBuilder 的管道模式 Backend。
 *
 * 特点：
 * - 不需要原生代码，纯 Kotlin 实现
 * - 无 PTY，shell 的 isatty() 返回 false
 * - 不支持 SIGINT（Ctrl+C 只能作为 \x03 字符发送给 shell stdin）
 * - 不支持 SIGWINCH（resize 是 no-op）
 * - 需要 App 层 localEcho 补偿回显
 *
 * 这是当前 Android SELinux 限制下的默认实现。
 * 未来 PtyBackend 可用时，应优先使用 PtyBackend。
 */
class ProcessBackend(
    private val command: List<String>,
    private val environment: Map<String, String> = emptyMap(),
    private val workingDir: String? = null
) : Backend {

    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var callback: BackendCallback? = null
    private var running = false
    private val buffer = ByteArray(8192)

    /** 管道模式的能力：无 PTY、需要 localEcho、无信号支持 */
    override val capabilities: BackendCapabilities = BackendCapabilities.PIPE

    override fun start(callback: BackendCallback) {
        this.callback = callback
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        if (workingDir != null) {
            pb.directory(java.io.File(workingDir))
        }
        environment.forEach { (k, v) -> pb.environment()[k] = v }
        val p = pb.start()
        process = p
        stdin = p.outputStream
        running = true

        Thread { readerLoop() }.apply {
            name = "backend-reader"
            isDaemon = true
            start()
        }

        Thread { waiterLoop() }.apply {
            name = "backend-waiter"
            isDaemon = true
            start()
        }
    }

    override fun write(data: ByteArray) {
        try {
            stdin?.write(data)
            stdin?.flush()
        } catch (_: Exception) {
            // 进程可能已退出，忽略写入错误
        }
    }

    /** 管道模式不支持 resize 通知（无 SIGWINCH） */
    override fun resize(cols: Int, rows: Int) {
        // no-op：管道模式无法通知子进程窗口大小变化
    }

    override fun stop() {
        running = false
        process?.destroy()
        process = null
        stdin = null
    }

    private fun readerLoop() {
        val inputStream = process?.inputStream ?: return
        try {
            while (running) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    callback?.onOutput(chunk)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun waiterLoop() {
        try {
            val exitCode = process?.waitFor() ?: 0
            running = false
            callback?.onExit(exitCode)
        } catch (_: Exception) {
        }
    }
}
