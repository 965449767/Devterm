package com.devterm

import android.os.Bundle
import android.view.KeyEvent
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.devterm.terminal.NodeJsRuntime
import com.devterm.terminal.TabManagerNew
import com.devterm.terminal.TerminalTabsNew
import com.devterm.terminal.core.renderer.RenderFrame
import com.devterm.service.TerminalService
import com.devterm.ui.settings.AppSettings
import com.devterm.ui.settings.SettingsScreen
import com.devterm.ui.theme.DevTermTheme
import com.devterm.ui.theme.TerminalTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var tabManager: TabManagerNew? = null
    private var nodeRuntime: NodeJsRuntime? = null
    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appSettings = AppSettings(this)
        TerminalService.start(this)

        lifecycleScope.launch {
            nodeRuntime = NodeJsRuntime.extract(this@MainActivity)
            val filesDirPath = filesDir.absolutePath
            tabManager = TabManagerNew(filesDirPath, nodeRuntime)
            tabManager?.createTab()

            setContent {
                // 从持久化读取设置
                var theme by remember { mutableStateOf(appSettings.getTheme()) }
                var fontSize by remember { mutableStateOf(appSettings.getFontSize()) }
                var cursorStyle by remember { mutableStateOf(appSettings.getCursorStyle()) }
                var cursorBlink by remember { mutableStateOf(appSettings.getCursorBlinkEnabled()) }
                var showSettings by remember { mutableStateOf(false) }

                // 使用当前选择的主题
                DevTermTheme(terminalTheme = theme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (showSettings) {
                            SettingsScreen(
                                currentTheme = theme,
                                currentFontSize = fontSize,
                                currentCursorStyle = cursorStyle,
                                currentCursorBlink = cursorBlink,
                                onThemeChanged = { newTheme ->
                                    theme = newTheme
                                    appSettings.setTheme(newTheme)
                                },
                                onFontSizeChanged = { newSize ->
                                    fontSize = newSize
                                    appSettings.setFontSize(newSize)
                                },
                                onCursorStyleChanged = { newStyle ->
                                    cursorStyle = newStyle
                                    appSettings.setCursorStyle(newStyle)
                                },
                                onCursorBlinkChanged = { newBlink ->
                                    cursorBlink = newBlink
                                    appSettings.setCursorBlinkEnabled(newBlink)
                                },
                                onBack = { showSettings = false }
                            )
                        } else {
                            TerminalTabsNew(
                                tabManager = tabManager!!,
                                defaultBg = theme.defaultBg,
                                cursorColor = theme.cursorColor,
                                fontSize = fontSize,
                                cursorBlinkEnabled = cursorBlink,
                                cursorStyle = cursorStyle,
                                onOpenSettings = { showSettings = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            val handled = tabManager?.activeTab?.keyboardHandler?.onKeyEvent(event) ?: false
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        tabManager?.destroyAll()
        super.onDestroy()
        TerminalService.stop(this)
    }
}
