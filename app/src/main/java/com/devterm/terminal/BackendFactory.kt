package com.devterm.terminal

import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.ProcessBackend

/**
 * Backend 工厂：根据可用性选择最佳 Backend 实现。
 *
 * 选择策略：
 * 1. 若 libpty.so 可加载 → 使用 PtyBackend（真正 PTY，支持信号/resize/颜色）
 * 2. 否则 → 使用 ProcessBackend（管道模式，fallback）
 *
 * 这样设计的好处：
 * - 调用方无需关心 Backend 类型，工厂自动选择
 * - 未来添加 .so 后，无需修改 TabManagerNew 等上层代码
 * - 便于测试（可注入 mock factory）
 *
 * @param shell shell 可执行路径（如 /system/bin/sh）
 * @param environment 环境变量
 * @param workingDir 工作目录
 * @param cols 初始列数（PTY 模式下传递给子进程）
 * @param rows 初始行数（PTY 模式下传递给子进程）
 */
class BackendFactory(
    private val filesDirPath: String,
    private val nodeRuntime: NodeJsRuntime? = null
) {
    /**
     * 创建 Backend 实例。
     *
     * @param shell shell 路径
     * @param environment 环境变量
     * @param workingDir 工作目录
     * @param cols 初始列数
     * @param rows 初始行数
     * @return Backend 实例（PtyBackend 或 ProcessBackend）
     */
    fun create(
        shell: String,
        environment: Map<String, String>,
        workingDir: String,
        cols: Int = 80,
        rows: Int = 24
    ): Backend {
        // 尝试 PTY（需要 libpty.so，当前不可用）
        if (PtyBackend.isAvailable()) {
            return PtyBackend(
                command = listOf(shell, "-i"),
                environment = environment,
                workingDir = workingDir,
                cols = cols,
                rows = rows
            )
        }

        // Fallback：管道模式
        return ProcessBackend(
            command = listOf(shell, "-i"),
            environment = environment,
            workingDir = workingDir
        )
    }

    /**
     * 构建 shell 环境变量（TERM、HOME、PATH 等）。
     * PTY 模式下 TERM=xterm-256color 会让程序输出颜色；
     * 管道模式下程序检测到非终端，可能不输出颜色。
     */
    fun buildEnvironment(shell: String): MutableMap<String, String> {
        val env = mutableMapOf<String, String>()
        env["TERM"] = "xterm-256color"
        env["HOME"] = "${filesDirPath}/home"
        env["TMPDIR"] = "${filesDirPath}/cache"
        env["PATH"] = "/sbin:/system/sbin:/system/bin:/system/xbin"
        env["PS1"] = "$ "
        env["SHELL"] = shell
        env["ENV"] = "${filesDirPath}/home/.profile"
        if (nodeRuntime?.isReady == true) {
            env.putAll(nodeRuntime.envVars(shell))
        }
        return env
    }
}
