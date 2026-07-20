package com.devterm.terminal.core

import com.devterm.terminal.core.parser.*
import org.junit.Assert.*
import org.junit.Test

class VtParserTest {

    private fun parse(s: String): List<ScreenCommand> {
        val parser = VtParser()
        return parser.consume(s.encodeToByteArray())
    }

    // ===== 基础文本 =====

    @Test
    fun testSimpleText() {
        val cmds = parse("Hello")
        assertEquals(5, cmds.size)
        assertTrue(cmds.all { it is ScreenCommand.WriteGlyph })
    }

    // ===== 光标移动 =====

    @Test
    fun testCsiCursorUp() {
        val cmds = parse("\u001b[3A")
        assertTrue(cmds.any { it is ScreenCommand.CursorUp && it.n == 3 })
    }

    @Test
    fun testCsiCursorDown() {
        val cmds = parse("\u001b[2B")
        assertTrue(cmds.any { it is ScreenCommand.CursorDown && it.n == 2 })
    }

    @Test
    fun testCsiCursorForward() {
        val cmds = parse("\u001b[5C")
        assertTrue(cmds.any { it is ScreenCommand.CursorForward && it.n == 5 })
    }

    @Test
    fun testCsiCursorBack() {
        val cmds = parse("\u001b[3D")
        assertTrue(cmds.any { it is ScreenCommand.CursorBack && it.n == 3 })
    }

    @Test
    fun testCsiCursorPositionH() {
        val cmds = parse("\u001b[10;20H")
        assertTrue(cmds.any { it is ScreenCommand.MoveCursor && it.row == 9 && it.col == 19 })
    }

    @Test
    fun testCsiCursorPositionDefault() {
        val cmds = parse("\u001b[H")
        assertTrue(cmds.any { it is ScreenCommand.MoveCursor && it.row == 0 && it.col == 0 })
    }

    @Test
    fun testCsiSetCol() {
        val cmds = parse("\u001b[20G")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorCol && it.col == 19 })
    }

    @Test
    fun testCsiVerticalPositionAbsolute() {
        val cmds = parse("\u001b[10d")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorRow && it.row == 9 })
    }

    @Test
    fun testCsiCursorUpDefaultN() {
        val cmds = parse("\u001b[A")
        assertTrue(cmds.any { it is ScreenCommand.CursorUp && it.n == 1 })
    }

    // ===== 清屏/清行 =====

    @Test
    fun testCsiEraseDisplay() {
        val cmds = parse("\u001b[2J")
        assertTrue(cmds.any { it is ScreenCommand.EraseDisplay && it.mode == 2 })
    }

    @Test
    fun testCsiEraseLine() {
        val cmds = parse("\u001b[K")
        assertTrue(cmds.any { it is ScreenCommand.EraseLine && it.mode == 0 })
    }

    @Test
    fun testCsiEraseChars() {
        val cmds = parse("\u001b[3X")
        assertTrue(cmds.any { it is ScreenCommand.EraseChars && it.n == 3 })
    }

    // ===== SGR 样式 =====

    @Test
    fun testCsiSgr() {
        val cmds = parse("\u001b[1;31m")
        assertTrue(cmds.any { it is ScreenCommand.SetSgr })
    }

    @Test
    fun testCsiSgrMultiParam() {
        val cmds = parse("\u001b[1;31m")
        val sgr = cmds.filterIsInstance<ScreenCommand.SetSgr>().firstOrNull()
        assertNotNull(sgr)
        assertEquals(listOf(1, 31), sgr!!.params)
    }

    @Test
    fun testCsiSgrReset() {
        val cmds = parse("\u001b[0m")
        val sgr = cmds.filterIsInstance<ScreenCommand.SetSgr>().firstOrNull()
        assertNotNull(sgr)
        assertEquals(listOf(0), sgr!!.params)
    }

    // ===== 滚动区域 =====

    @Test
    fun testCsiScrollRegion() {
        val cmds = parse("\u001b[1;24r")
        assertTrue(cmds.any { it is ScreenCommand.SetScrollRegion && it.top == 0 && it.bottom == 23 })
    }

    @Test
    fun testCsiScrollUp() {
        val cmds = parse("\u001b[S")
        assertTrue(cmds.any { it is ScreenCommand.ScrollUp })
    }

    @Test
    fun testCsiScrollDown() {
        val cmds = parse("\u001b[T")
        assertTrue(cmds.any { it is ScreenCommand.ScrollDown })
    }

    // ===== 插入/删除 =====

    @Test
    fun testCsiInsertLines() {
        val cmds = parse("\u001b[3L")
        assertTrue(cmds.any { it is ScreenCommand.InsertLines && it.n == 3 })
    }

    @Test
    fun testCsiDeleteLines() {
        val cmds = parse("\u001b[2M")
        assertTrue(cmds.any { it is ScreenCommand.DeleteLines && it.n == 2 })
    }

    @Test
    fun testCsiInsertChars() {
        val cmds = parse("\u001b[3@")
        assertTrue(cmds.any { it is ScreenCommand.InsertChars && it.n == 3 })
    }

    @Test
    fun testCsiDeleteChars() {
        val cmds = parse("\u001b[2P")
        assertTrue(cmds.any { it is ScreenCommand.DeleteChars && it.n == 2 })
    }

    // ===== 保存/恢复光标 =====

    @Test
    fun testCsiSaveRestoreCursor() {
        val cmds = parse("\u001b[s\u001b[u")
        assertTrue(cmds.any { it is ScreenCommand.SaveCursor })
        assertTrue(cmds.any { it is ScreenCommand.RestoreCursor })
    }

    @Test
    fun testEscDecscDecrc() {
        val cmds = parse("\u001b7\u001b8")
        assertTrue(cmds.any { it is ScreenCommand.SaveCursor })
        assertTrue(cmds.any { it is ScreenCommand.RestoreCursor })
    }

    // ===== 状态查询 =====

    @Test
    fun testCsiDeviceStatusReport() {
        val cmds = parse("\u001b[5n")
        assertTrue(cmds.any { it is ScreenCommand.DeviceStatusReport && it.n == 5 })
    }

    @Test
    fun testCsiRequestCursorPosition() {
        val cmds = parse("\u001b[6n")
        assertTrue(cmds.any { it is ScreenCommand.RequestCursorPosition })
    }

    // ===== OSC 标题 =====

    @Test
    fun testOscTitle() {
        val cmds = parse("\u001b]0;My Title\u0007")
        assertTrue(cmds.any { it is ScreenCommand.SetTitle && it.title == "My Title" })
    }

    @Test
    fun testOscTitleOsc2() {
        val cmds = parse("\u001b]2;Window Title\u0007")
        assertTrue(cmds.any { it is ScreenCommand.SetTitle && it.title == "Window Title" })
    }

    @Test
    fun testOscIconName() {
        val cmds = parse("\u001b]1;Icon Name\u0007")
        assertTrue(cmds.any { it is ScreenCommand.SetIconName && it.name == "Icon Name" })
    }

    // ===== 控制字符 =====

    @Test
    fun testCarriageReturnLineFeed() {
        val cmds = parse("\r\n")
        assertTrue(cmds.any { it is ScreenCommand.CarriageReturn })
        assertTrue(cmds.any { it is ScreenCommand.LineFeed })
    }

    @Test
    fun testTabKey() {
        val cmds = parse("\t")
        assertTrue(cmds.any { it is ScreenCommand.Tab })
    }

    @Test
    fun testBell() {
        val cmds = parse("\u0007")
        assertTrue(cmds.any { it is ScreenCommand.Bell })
    }

    // ===== ESC 序列 =====

    @Test
    fun testEscIndexAndReverseIndex() {
        val cmds = parse("\u001bD\u001bM")
        assertTrue(cmds.any { it is ScreenCommand.LineFeed })
        assertTrue(cmds.any { it is ScreenCommand.ReverseLineFeed })
    }

    @Test
    fun testEscNel() {
        val cmds = parse("\u001bE")
        assertTrue(cmds.any { it is ScreenCommand.CarriageReturn })
        assertTrue(cmds.any { it is ScreenCommand.LineFeed })
    }

    @Test
    fun testEscRisReset() {
        val cmds = parse("\u001bc")
        assertTrue(cmds.any { it is ScreenCommand.Reset })
    }

    @Test
    fun testEscCpl() {
        val cmds = parse("\u001bF")
        assertTrue(cmds.any { it is ScreenCommand.CarriageReturn })
        assertTrue(cmds.any { it is ScreenCommand.CursorUp && it.n == 1 })
    }

    @Test
    fun testEscSetHorizontalTabStop() {
        val cmds = parse("\u001bH")
        assertTrue(cmds.any { it is ScreenCommand.SetHorizontalTabStop })
    }

    // ===== 私有模式（DECSET/DECRST）=====

    @Test
    fun testCsiPrivateModeSet() {
        val cmds = parse("\u001b[?1049h")
        assertTrue(cmds.any { it is ScreenCommand.SetPrivateMode && it.mode == 1049 && it.set })
    }

    @Test
    fun testCsiPrivateModeReset() {
        val cmds = parse("\u001b[?1049l")
        assertTrue(cmds.any { it is ScreenCommand.SetPrivateMode && it.mode == 1049 && !it.set })
    }

    @Test
    fun testCsiBracketedPasteModeSet() {
        val cmds = parse("\u001b[?2004h")
        assertTrue(cmds.any { it is ScreenCommand.SetPrivateMode && it.mode == 2004 && it.set })
    }

    @Test
    fun testCsiBracketedPasteModeReset() {
        val cmds = parse("\u001b[?2004l")
        assertTrue(cmds.any { it is ScreenCommand.SetPrivateMode && it.mode == 2004 && !it.set })
    }

    // 普通模式（无 ? 前缀）不应生成 SetPrivateMode
    @Test
    fun testCsiNormalModeNotPrivate() {
        val cmds = parse("\u001b[25h")
        assertTrue(cmds.any { it is ScreenCommand.SetMode && it.mode == 25 && it.set })
        assertFalse(cmds.any { it is ScreenCommand.SetPrivateMode })
    }

    // ===== 光标样式（DECSCUSR）=====

    @Test
    fun testCsiCursorStyleBlock() {
        // CSI 1 SP q = 块状闪烁
        val cmds = parse("\u001b[1 q")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorStyle && it.style == 1 })
    }

    @Test
    fun testCsiCursorStyleUnderline() {
        val cmds = parse("\u001b[3 q")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorStyle && it.style == 3 })
    }

    @Test
    fun testCsiCursorStyleBar() {
        val cmds = parse("\u001b[5 q")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorStyle && it.style == 5 })
    }

    @Test
    fun testCsiCursorStyleDefault() {
        val cmds = parse("\u001b[0 q")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorStyle && it.style == 0 })
    }

    // ===== 组合序列 =====

    @Test
    fun testClearCommandFromToybox() {
        val cmds = parse("\u001b[2J\u001b[H")
        assertTrue(cmds.any { it is ScreenCommand.EraseDisplay && it.mode == 2 })
        assertTrue(cmds.any { it is ScreenCommand.MoveCursor && it.row == 0 && it.col == 0 })
    }

    @Test
    fun testComplexSequence() {
        val cmds = parse("\u001b[31mHello\u001b[0m\u001b[2J\u001b[H")
        assertTrue(cmds.any { it is ScreenCommand.SetSgr })
        assertTrue(cmds.any { it is ScreenCommand.WriteGlyph })
        assertTrue(cmds.any { it is ScreenCommand.EraseDisplay && it.mode == 2 })
        assertTrue(cmds.any { it is ScreenCommand.MoveCursor })
    }

    // ===== Vim 启动序列测试 =====

    @Test
    fun testVimStartupAlternateScreen() {
        // vim 启动时的典型序列：进入备用屏幕 + 隐藏光标
        val cmds = parse("\u001b[?1049h\u001b[?25l")
        assertTrue(cmds.any { it is ScreenCommand.SetPrivateMode && it.mode == 1049 && it.set })
        assertTrue(cmds.any { it is ScreenCommand.SetMode && it.mode == 25 && !it.set })
    }

    @Test
    fun testVimExitAlternateScreen() {
        // vim 退出时的典型序列：退出备用屏幕 + 显示光标
        val cmds = parse("\u001b[?1049l\u001b[?25h")
        assertTrue(cmds.any { it is ScreenCommand.SetPrivateMode && it.mode == 1049 && !it.set })
        assertTrue(cmds.any { it is ScreenCommand.SetMode && it.mode == 25 && it.set })
    }

    // ===== 解析器状态重置 =====

    @Test
    fun testParserReset() {
        val parser = VtParser()
        // 先解析一个不完整的序列
        parser.consume("\u001b[".encodeToByteArray())
        // 重置
        parser.reset()
        // 再解析一个完整序列，应该能正常解析
        val cmds = parser.consume("\u001b[2J".encodeToByteArray())
        assertTrue(cmds.any { it is ScreenCommand.EraseDisplay && it.mode == 2 })
    }

    // ===== 多字节 UTF-8 文本 =====

    @Test
    fun testUtf8TextAscii() {
        val cmds = parse("Hello World")
        assertEquals(11, cmds.size)
        assertTrue(cmds.all { it is ScreenCommand.WriteGlyph })
    }
}
