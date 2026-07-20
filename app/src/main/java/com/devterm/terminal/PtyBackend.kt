package com.devterm.terminal

import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.BackendCapabilities
import com.devterm.terminal.core.backend.BackendCallback
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * PTY（伪终端）Backend 实现。
 *
 * **当前状态：骨架实现，等待 .so 原生代码支持。**
 *
 * 真正的 PTY 需要：
 * 1. `openpty()` 或 `open("/dev/ptmx")` + `grantpt()` + `unlockpt()` + `ptsname()`
 * 2. `fork()` + `setsid()` + 将 slave fd 设为 stdin/stdout/stderr + `execve()`
 * 3. `ioctl(masterFd, TIOCSWINSZ, &winsize)` 通知窗口大小变化
 *
 * 这些系统调用在纯 Java/Kotlin 中不可用（Android 的 `android.system.Os` 不暴露 openpty/ioctl），
 * 必须通过 JNI 或交叉编译的 .so 库实现。
 *
 * **实现路线**：
 * - Phase 5a：交叉编译 `libpty.so`（C 代码封装 openpty + fork + exec）
 * - Phase 5b：通过 `System.loadLibrary("pty")` 加载
 * - Phase 5c：PtyBackend 调用 native 方法
 *
 * 当前此类提供完整的接口骨架，native 方法标记为 external，待 .so 就绪后填充。
 */
class PtyBackend(
    private val command: List<String>,
    private val environment: Map<String, String> = emptyMap(),
    private val workingDir: String? = null,
    private val cols: Int = 80,
    private val rows: Int = 24
) : Backend {

    /** PTY 模式的完整能力 */
    override val capabilities: BackendCapabilities = BackendCapabilities.PTY

    private var masterFd: Int = -1
    private var masterInput: FileInputStream? = null
    private var masterOutput: FileOutputStream? = null
    private var callback: BackendCallback? = null
    private var running = false
    private val buffer = ByteArray(8192)
    private var childPid: Int = -1

    override fun start(callback: BackendCallback) {
        this.callback = callback

        // 尝试通过 native 创建 PTY
        if (!nativeCreatePty()) {
            throw IllegalStateException(
                "PTY 不可用：需要 libpty.so 原生库。" +
                "请先交叉编译 libpty.so 并放入 jniLibs 目录。"
            )
        }

        running = true

        // 读取 master fd 的输出
        Thread { readerLoop() }.apply {
            name = "pty-reader"
            isDaemon = true
            start()
        }

        // 等待子进程退出
        Thread { waiterLoop() }.apply {
            name = "pty-waiter"
            isDaemon = true
            start()
        }
    }

    override fun write(data: ByteArray) {
        try {
            masterOutput?.write(data)
            masterOutput?.flush()
        } catch (_: Exception) {
            // 子进程可能已退出
        }
    }

    override fun resize(cols: Int, rows: Int) {
        if (masterFd >= 0) {
            nativeSetWindowSize(masterFd, cols, rows)
        }
    }

    override fun stop() {
        running = false
        if (childPid > 0) {
            nativeKillChild(childPid)
        }
        nativeClosePty(masterFd)
        masterFd = -1
        masterInput = null
        masterOutput = null
    }

    private fun readerLoop() {
        val input = masterInput ?: return
        try {
            while (running) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    callback?.onOutput(buffer.copyOf(read))
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun waiterLoop() {
        try {
            val exitCode = nativeWaitForChild(childPid)
            running = false
            callback?.onExit(exitCode)
        } catch (_: Exception) {
        }
    }

    // ===== Native 方法（待 .so 实现） =====

    /**
     * 创建 PTY 并 fork 子进程。
     *
     * 成功时设置：
     * - masterFd：master 端的文件描述符
     * - masterInput/masterOutput：包装后的流
     * - childPid：子进程 PID
     *
     * @return true 如果创建成功
     *
     * 注意：此方法由 libpty.so 实现。.so 未加载时不应被调用
     * （调用前应先通过 [isAvailable] 检查）。
     */
    private external fun nativeCreatePty(): Boolean

    private external fun nativeSetWindowSize(fd: Int, cols: Int, rows: Int)
    private external fun nativeKillChild(pid: Int)
    private external fun nativeClosePty(fd: Int)
    private external fun nativeWaitForChild(pid: Int): Int

    companion object {
        /**
         * 检测 PTY 是否可用（libpty.so 是否已加载）。
         */
        fun isAvailable(): Boolean = try {
            System.loadLibrary("pty")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
