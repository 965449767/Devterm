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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.lifecycle.lifecycleScope
import com.devterm.terminal.NodeJsRuntime
import com.devterm.terminal.TabManagerNew
import com.devterm.terminal.TerminalTabsNew
import com.devterm.service.TerminalService
import com.devterm.ui.theme.DevTermTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var tabManager: TabManagerNew? = null
    private var nodeRuntime: NodeJsRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TerminalService.start(this)

        lifecycleScope.launch {
            nodeRuntime = NodeJsRuntime.extract(this@MainActivity)
            val filesDirPath = filesDir.absolutePath
            tabManager = TabManagerNew(filesDirPath, nodeRuntime)
            tabManager?.createTab()

            setContent {
                DevTermTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TerminalTabsNew(
                            tabManager = tabManager!!,
                            modifier = Modifier.fillMaxSize()
                        )
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
