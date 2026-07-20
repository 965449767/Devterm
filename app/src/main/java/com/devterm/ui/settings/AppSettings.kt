package com.devterm.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.devterm.terminal.core.renderer.RenderFrame
import com.devterm.ui.theme.CatppuccinThemes
import com.devterm.ui.theme.TerminalTheme

/**
 * 设置持久化管理器。
 *
 * 使用 SharedPreferences 保存用户设置：
 * - 主题名称
 * - 字体大小
 * - 光标样式
 * - 光标闪烁开关
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== 主题 =====

    fun getTheme(): TerminalTheme {
        val name = prefs.getString(KEY_THEME, DEFAULT_THEME_NAME) ?: DEFAULT_THEME_NAME
        return CatppuccinThemes.find { it.name == name } ?: CatppuccinThemes.first()
    }

    fun setTheme(theme: TerminalTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    // ===== 字体大小 =====

    fun getFontSize(): Int {
        return prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }

    fun setFontSize(size: Int) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply()
    }

    // ===== 光标样式 =====

    fun getCursorStyle(): RenderFrame.CursorStyle {
        val ordinal = prefs.getInt(KEY_CURSOR_STYLE, DEFAULT_CURSOR_STYLE.ordinal)
        return RenderFrame.CursorStyle.entries.getOrElse(ordinal) { DEFAULT_CURSOR_STYLE }
    }

    fun setCursorStyle(style: RenderFrame.CursorStyle) {
        prefs.edit().putInt(KEY_CURSOR_STYLE, style.ordinal).apply()
    }

    // ===== 光标闪烁 =====

    fun getCursorBlinkEnabled(): Boolean {
        return prefs.getBoolean(KEY_CURSOR_BLINK, DEFAULT_CURSOR_BLINK)
    }

    fun setCursorBlinkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CURSOR_BLINK, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "devterm_settings"

        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_CURSOR_STYLE = "cursor_style"
        private const val KEY_CURSOR_BLINK = "cursor_blink"

        private val DEFAULT_THEME_NAME = "Mocha"
        private const val DEFAULT_FONT_SIZE = 14
        private val DEFAULT_CURSOR_STYLE = RenderFrame.CursorStyle.BLOCK
        private const val DEFAULT_CURSOR_BLINK = true
    }
}
