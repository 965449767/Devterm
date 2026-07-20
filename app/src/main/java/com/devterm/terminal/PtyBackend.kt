package com.devterm.terminal

import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.BackendCapabilities
import com.devterm.terminal.core.backend.BackendCallback
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * PTY（伪终端）Backend 实现。
 *
 * **工作原理**：
 * 1. 通过 `/dev/ptmx` 创建伪终端主设备
 * 2. `grantpt()` + `unlockpt()` 获取从设备路径
 * 3. `fork()` 创建子进程，`setsid()` 创建新会话
 * 4. 将从设备绑定到子进程的 stdin/stdout/stderr
 * 5. `execve()` 执行 shell
 * 6. 通过主设备文件描述符与子进程通信
 *
 * **编译方式**：
 * - 使用 Android NDK CMake 构建：`./gradlew :app:externalNativeBuildDebug`
 * - 或手动交叉编译：参考 `scripts/build-pty.sh`
 *
 * **使用方式**：
 * ```kotlin
 * if (PtyBackend.isAvailable()) {
 *     val backend = PtyBackend(listOf("/system/bin/sh"), env)
 *     backend.start(callback)
 * }
 * ```
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

    init {
        // 将参数传递给 native 层
        val cmdArray = command.toTypedArray()
        val envArray = environment.map { "${it.key}=${it.value}" }.toTypedArray()
        nativeInitialize(cmdArray, envArray, workingDir, cols, rows)
    }

    override fun start(callback: BackendCallback) {
        this.callback = callback

        // 尝试通过 native 创建 PTY
        if (!nativeCreatePty()) {
            nativeCleanup()
            throw IllegalStateException(
                "PTY 创建失败：可能原因包括 SELinux 限制、/dev/ptmx 不可访问或权限不足。"
            )
        }

        // 包装 master fd 为流
        masterInput = FileInputStream(masterFd)
        masterOutput = FileOutputStream(masterFd)

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
            childPid = -1
        }
        nativeClosePty(masterFd)
        masterFd = -1
        masterInput = null
        masterOutput = null
        nativeCleanup()
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

    // ===== Native 方法（由 libpty.so 实现） =====

    /**
     * 初始化 native 层参数。
     * 在构造函数中调用，将 command、environment、workingDir、cols、rows 传递给 C 层。
     */
    private external fun nativeInitialize(
        command: Array<String>,
        envVars: Array<String>,
        workingDir: String?,
        cols: Int,
        rows: Int
    )

    /**
     * 创建 PTY 并 fork 子进程。
     *
     * 成功时设置：
     * - masterFd：master 端的文件描述符
     * - childPid：子进程 PID
     *
     * @return true 如果创建成功
     */
    private external fun nativeCreatePty(): Boolean

    private external fun nativeSetWindowSize(fd: Int, cols: Int, rows: Int)
    private external fun nativeKillChild(pid: Int)
    private external fun nativeClosePty(fd: Int)
    private external fun nativeWaitForChild(pid: Int): Int

    /**
     * 清理 native 层资源（释放 malloc 分配的内存）。
     */
    private external fun nativeCleanup()

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
