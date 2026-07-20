package com.devterm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devterm.ui.theme.CatppuccinThemes
import com.devterm.ui.theme.TerminalTheme
import com.devterm.terminal.core.renderer.RenderFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: TerminalTheme,
    currentFontSize: Int,
    currentCursorStyle: RenderFrame.CursorStyle,
    currentCursorBlink: Boolean,
    onThemeChanged: (TerminalTheme) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onCursorStyleChanged: (RenderFrame.CursorStyle) -> Unit,
    onCursorBlinkChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = 16.dp,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ThemeCard(
                    currentTheme = currentTheme,
                    themes = CatppuccinThemes,
                    onThemeChanged = onThemeChanged
                )
            }

            item {
                FontSizeCard(
                    currentSize = currentFontSize,
                    onSizeChanged = onFontSizeChanged
                )
            }

            item {
                CursorStyleCard(
                    currentStyle = currentCursorStyle,
                    onStyleChanged = onCursorStyleChanged
                )
            }

            item {
                CursorBlinkCard(
                    currentBlink = currentCursorBlink,
                    onBlinkChanged = onCursorBlinkChanged
                )
            }
        }
    }
}

@Composable
private fun ThemeCard(
    currentTheme: TerminalTheme,
    themes: List<TerminalTheme>,
    onThemeChanged: (TerminalTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "主题",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        modifier = Modifier.width(40.dp),
                        colors = CardDefaults.cardColors(containerColor = currentTheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            Spacer(modifier = Modifier.weight(1f))
                            Row {
                                Card(
                                    modifier = Modifier.weight(1f).padding(1.dp),
                                    colors = CardDefaults.cardColors(containerColor = currentTheme.primary)
                                ) {}
                                Card(
                                    modifier = Modifier.weight(1f).padding(1.dp),
                                    colors = CardDefaults.cardColors(containerColor = currentTheme.onSurface)
                                ) {}
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = currentTheme.name)
                }
                Card(
                    modifier = Modifier.clickable { expanded = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "选择", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        themes.forEach { theme ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Card(
                            modifier = Modifier.width(24.dp).padding(end = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface)
                        ) {}
                        Text(text = theme.name)
                    }
                },
                onClick = {
                    onThemeChanged(theme)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun FontSizeCard(
    currentSize: Int,
    onSizeChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "字体大小",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = "${currentSize}sp")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = currentSize.toFloat(),
                onValueChange = { onSizeChanged(it.toInt()) },
                valueRange = 10f..24f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CursorStyleCard(
    currentStyle: RenderFrame.CursorStyle,
    onStyleChanged: (RenderFrame.CursorStyle) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val styleNames = mapOf(
        RenderFrame.CursorStyle.BLOCK to "块状",
        RenderFrame.CursorStyle.UNDERLINE to "下划线",
        RenderFrame.CursorStyle.BAR to "竖线"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "光标样式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = styleNames[currentStyle] ?: "块状")
                Card(
                    modifier = Modifier.clickable { expanded = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "选择", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        styleNames.forEach { (style, name) ->
            DropdownMenuItem(
                text = { Text(text = name) },
                onClick = {
                    onStyleChanged(style)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun CursorBlinkCard(
    currentBlink: Boolean,
    onBlinkChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "光标闪烁",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = currentBlink,
                onCheckedChange = onBlinkChanged
            )
        }
    }
}