package com.devterm.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.ProcessBackend
import java.io.File

data class TabDataNew(
    val id: Int,
    var title: String,
    val core: DevTermCore,
    val keyboardHandler: KeyboardHandlerNew,
    val backend: Backend
)

class TabManagerNew(
    private val filesDirPath: String,
    private val nodeRuntime: NodeJsRuntime?
) {
    private val _tabs = mutableListOf<TabDataNew>()
    private var _tabsState by mutableStateOf(emptyList<TabDataNew>())
    private var _activeIndex by mutableIntStateOf(0)
    private var _nextId by mutableIntStateOf(0)

    val tabs: List<TabDataNew> get() = _tabsState
    val activeIndex: Int get() = _activeIndex
    val activeTab: TabDataNew? get() = _tabs.getOrNull(_activeIndex)

    private fun syncTabs() {
        _tabsState = _tabs.toList()
    }

    fun createTab(): TabDataNew {
        val id = _nextId++
        val core = DevTermCore(80, 24)
        val keyboardHandler = KeyboardHandlerNew(core)

        val backend = buildBackend(core)
        core.attachBackend(backend)
        core.start()

        val tab = TabDataNew(id, "Terminal ${_tabs.size + 1}", core, keyboardHandler, backend)
        _tabs.add(tab)
        syncTabs()
        _activeIndex = _tabs.size - 1
        return tab
    }

    private fun buildBackend(core: DevTermCore): Backend {
        File("${filesDirPath}/home").mkdirs()
        File("${filesDirPath}/cache").mkdirs()

        val profileFile = File("${filesDirPath}/home", ".profile")
        if (!profileFile.exists()) {
            profileFile.writeText("alias clear=\"printf '\\033[2J\\033[H'\"\n")
        }

        val shell = "/system/bin/sh"
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

        // 复用 terminal-core 模块的 ProcessBackend，避免重复实现
        return ProcessBackend(
            command = listOf(shell, "-i"),
            environment = env,
            workingDir = "${filesDirPath}/home"
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
