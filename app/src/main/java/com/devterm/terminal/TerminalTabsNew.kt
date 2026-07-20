package com.devterm.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalTabsNew(
    tabManager: TabManagerNew,
    modifier: Modifier = Modifier
) {
    val tabs = tabManager.tabs
    val activeIndex = tabManager.activeIndex
    val activeTab = tabManager.activeTab

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TabBarNew(
            tabs = tabs,
            activeIndex = activeIndex,
            onTabClick = { tabManager.switchTab(it) },
            onTabClose = { tabManager.closeTab(it) },
            onNewTab = { tabManager.createTab() }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeTab != null) {
                key(activeTab.id) {
                    TerminalScreenNew(
                        devTermCore = activeTab.core,
                        keyboardHandler = activeTab.keyboardHandler,
                        modifier = Modifier.fillMaxSize(),
                        onColsRows = { cols, rows -> activeTab.core.resize(cols, rows) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBarNew(
    tabs: List<TabDataNew>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewTab: () -> Unit
) {
    val activeBg = Color(0xFF45475A)
    val inactiveBg = Color(0xFF2D2D3F)
    val activeFg = Color(0xFFCDD6F4)
    val inactiveFg = Color(0xFF6C7086)
    val accentColor = Color(0xFF89B4FA)
    val closeColor = Color(0xFFF38BA8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(inactiveBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val isActive = index == activeIndex
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) activeBg else inactiveBg)
                    .clickable { onTabClick(index) }
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tab.title,
                    color = if (isActive) activeFg else inactiveFg,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { onTabClose(tab.id) }
                        .padding(2.dp)
                ) {
                    Text(
                        text = "\u00D7",
                        color = if (isActive) closeColor else inactiveFg,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onNewTab() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "+",
                color = accentColor,
                fontSize = 16.sp
            )
        }
    }
}
