package com.devterm.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class TabData(
    val id: Int,
    var title: String,
    val emulator: TerminalEmulator,
    val keyboardHandler: KeyboardHandler,
    val session: TerminalSession
)

@Deprecated("Use TabManagerNew instead", ReplaceWith("TabManagerNew"))
class TabManager(
    private val filesDirPath: String,
    private val nodeRuntime: NodeJsRuntime?
) {
    private val _tabs = mutableListOf<TabData>()
    private var _tabsState by mutableStateOf(emptyList<TabData>())
    private var _activeIndex by mutableIntStateOf(0)
    private var _nextId by mutableIntStateOf(0)

    val tabs: List<TabData> get() = _tabsState
    val activeIndex: Int get() = _activeIndex
    val activeTab: TabData? get() = _tabs.getOrNull(_activeIndex)

    private fun syncTabs() {
        _tabsState = _tabs.toList()
    }

    fun createTab(): TabData {
        val id = _nextId++
        var sessionRef: TerminalSession? = null
        val emulator = TerminalEmulator(
            callbacks = object : TerminalEmulatorCallbacks {
                override fun onKeyboardOutput(data: ByteArray) {
                    sessionRef?.write(data)
                }
            }
        )
        val keyboardHandler = KeyboardHandler(emulator)
        val session = TerminalSession(
            filesDirPath = filesDirPath,
            nodeRuntime = nodeRuntime
        )
        sessionRef = session
        session.start(24, 80, emulator)
        session.startAutoCheckpoint(emulator)

        val tab = TabData(id, "Terminal ${_tabs.size + 1}", emulator, keyboardHandler, session)
        _tabs.add(tab)
        syncTabs()
        _activeIndex = _tabs.size - 1
        return tab
    }

    fun switchTab(index: Int) {
        if (index in 0 until _tabs.size) {
            _activeIndex = index
        }
    }

    fun closeTab(id: Int) {
        val idx = _tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        _tabs[idx].session.destroy()
        _tabs.removeAt(idx)
        syncTabs()
        if (_tabs.isEmpty()) {
            createTab()
        } else if (_activeIndex >= _tabs.size) {
            _activeIndex = _tabs.size - 1
        }
    }

    fun peekSessionFor(id: Int): TerminalSession? =
        _tabs.find { it.id == id }?.session

    fun destroyAll() {
        _tabs.forEach { it.session.destroy() }
        _tabs.clear()
        syncTabs()
    }
}
