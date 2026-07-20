package com.devterm.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.devterm.terminal.core.backend.Backend
import java.io.File

data class TabDataNew(
    val id: Int,
    var title: String,
    val core: DevTermCore,
    val keyboardHandler: KeyboardHandlerNew,
    val backend: Backend
)

/**
 * 多 Tab 管理器。
 *
 * 职责：
 * 1. 创建/切换/关闭终端 Tab
 * 2. 通过 BackendFactory 创建 Backend（自动选择 PTY 或 Process）
 * 3. 初始化 shell 环境（.profile、TERM、PATH 等）
 *
 * Backend 类型由 BackendFactory 决定：
 * - 若 libpty.so 可用 → PtyBackend（真正 PTY）
 * - 否则 → ProcessBackend（管道模式，fallback）
 */
class TabManagerNew(
    private val filesDirPath: String,
    private val nodeRuntime: NodeJsRuntime?
) {
    private val _tabs = mutableListOf<TabDataNew>()
    private var _tabsState by mutableStateOf(emptyList<TabDataNew>())
    private var _activeIndex by mutableIntStateOf(0)
    private var _nextId by mutableIntStateOf(0)

    /** Backend 工厂：自动选择 PTY 或 Process */
    private val backendFactory = BackendFactory(filesDirPath, nodeRuntime)

    val tabs: List<TabDataNew> get() = _tabsState
    val activeIndex: Int get() = _activeIndex
    val activeTab: TabDataNew? get() = _tabs.getOrNull(_activeIndex)

    private fun syncTabs() {
        _tabsState = _tabs.toList()
    }

    fun createTab(): TabDataNew {
        val id = _nextId++
        val core = DevTermCore(80, 24)

        // 创建 Backend（PTY 或 Process，由工厂决定）
        val backend = buildBackend(core)
        core.attachBackend(backend)

        // 把 Backend 能力传给 KeyboardHandler
        val keyboardHandler = KeyboardHandlerNew(core, core.capabilities)

        core.start()

        val tab = TabDataNew(id, "Terminal ${_tabs.size + 1}", core, keyboardHandler, backend)
        _tabs.add(tab)
        syncTabs()
        _activeIndex = _tabs.size - 1
        return tab
    }

    /**
     * 构建 Backend 实例。
     * 使用 BackendFactory 自动选择最佳实现。
     */
    private fun buildBackend(core: DevTermCore): Backend {
        // 初始化 home/cache 目录
        File("${filesDirPath}/home").mkdirs()
        File("${filesDirPath}/cache").mkdirs()

        // 创建 .profile（首次启动）
        val profileFile = File("${filesDirPath}/home", ".profile")
        if (!profileFile.exists()) {
            profileFile.writeText("alias clear=\"printf '\\033[2J\\033[H'\"\n")
        }

        val shell = "/system/bin/sh"
        val env = backendFactory.buildEnvironment(shell)

        return backendFactory.create(
            shell = shell,
            environment = env,
            workingDir = "${filesDirPath}/home",
            cols = 80,
            rows = 24
        )
    }

    fun switchTab(index: Int) {
        if (index in 0 until _tabs.size) {
            _activeIndex = index
        }
    }

    fun closeTab(id: Int) {
        val idx = _tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        _tabs[idx].core.stop()
        _tabs.removeAt(idx)
        syncTabs()
        if (_tabs.isEmpty()) {
            createTab()
        } else if (_activeIndex >= _tabs.size) {
            _activeIndex = _tabs.size - 1
        }
    }

    fun destroyAll() {
        _tabs.forEach { it.core.stop() }
        _tabs.clear()
        syncTabs()
    }
}
