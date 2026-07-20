package com.devterm.terminal.core

import com.devterm.terminal.core.parser.*
import com.devterm.terminal.core.screen.CellFlags
import com.devterm.terminal.core.screen.CursorState
import com.devterm.terminal.core.screen.ScreenBuffer
import org.junit.Assert.*
import org.junit.Test

/**
 * ScreenBuffer 新增功能测试。
 *
 * 覆盖：
 * - Bell 响铃回调
 * - 备用屏幕切换（Alternate Screen）
 * - 光标样式设置（DECSCUSR）
 * - 括号粘贴模式
 * - 制表位系统
 * - 光标保存/恢复完整状态（含 SGR）
 * - Reset 功能完整性
 */
class ScreenBufferAdvancedTest {

    // ===== Bell 响铃 =====

    @Test
    fun testBellCallback() {
        val sb = ScreenBuffer(80, 24)
        var bellCount = 0
        sb.onBell = { bellCount++ }

        sb.execute(ScreenCommand.Bell)
        assertEquals(1, bellCount)

        sb.execute(ScreenCommand.Bell)
        assertEquals(2, bellCount)
    }

    @Test
    fun testBellWithoutCallback() {
        val sb = ScreenBuffer(80, 24)
        // 没有设置 onBell 回调时，不应抛出异常
        sb.execute(ScreenCommand.Bell)
    }

    // ===== 备用屏幕 =====

    @Test
    fun testEnterAlternateScreen() {
        val sb = ScreenBuffer(80, 24)
        // 先写一些内容到主屏幕
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.WriteGlyph('B', 1))

        // 进入备用屏幕
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))

        assertTrue(sb.useAlternateScreen)
        // 备用屏幕应该是空的
        assertEquals(' ', sb.chars[0])
        assertEquals(' ', sb.chars[1])
        // 光标应该在原点
        assertEquals(0, sb.cursor.row)
        assertEquals(0, sb.cursor.col)
    }

    @Test
    fun testExitAlternateScreenRestoresContent() {
        val sb = ScreenBuffer(80, 24)
        // 主屏幕写内容
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.WriteGlyph('B', 1))

        // 进入备用屏幕
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))
        // 在备用屏幕写内容
        sb.execute(ScreenCommand.WriteGlyph('X', 1))
        assertEquals('X', sb.chars[0])

        // 退出备用屏幕
        sb.execute(ScreenCommand.SetPrivateMode(1049, false))

        assertFalse(sb.useAlternateScreen)
        // 主屏幕内容应该被恢复
        assertEquals('A', sb.chars[0])
        assertEquals('B', sb.chars[1])
    }

    @Test
    fun testAlternateScreenMultipleSwitches() {
        val sb = ScreenBuffer(80, 24)

        // 主屏幕写内容
        sb.execute(ScreenCommand.WriteGlyph('M', 1))

        // 第一次切换
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.SetPrivateMode(1049, false))
        assertEquals('M', sb.chars[0])

        // 第二次切换，备用屏幕内容应该保留
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))
        assertEquals('A', sb.chars[0])
    }

    @Test
    fun testEnteringAlternateScreenTwice() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))
        // 第二次进入应该不产生副作用
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))
        assertTrue(sb.useAlternateScreen)
    }

    @Test
    fun testExitingAlternateScreenTwice() {
        val sb = ScreenBuffer(80, 24)
        // 没有进入过备用屏幕，退出应该不产生副作用
        sb.execute(ScreenCommand.SetPrivateMode(1049, false))
        assertFalse(sb.useAlternateScreen)
    }

    // ===== 光标样式 =====

    @Test
    fun testCursorStyleBlock() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorStyle(1))
        assertEquals(CursorState.CursorStyle.BLOCK, sb.cursor.style)
    }

    @Test
    fun testCursorStyleUnderline() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorStyle(3))
        assertEquals(CursorState.CursorStyle.UNDERLINE, sb.cursor.style)
    }

    @Test
    fun testCursorStyleBar() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorStyle(5))
        assertEquals(CursorState.CursorStyle.BAR, sb.cursor.style)
    }

    @Test
    fun testCursorStyleDefault() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorStyle(0))
        assertEquals(CursorState.CursorStyle.BLOCK, sb.cursor.style)
    }

    @Test
    fun testCursorStyleUnknownDefaultsToBlock() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorStyle(999))  // 未知样式
        assertEquals(CursorState.CursorStyle.BLOCK, sb.cursor.style)
    }

    // ===== 括号粘贴模式 =====

    @Test
    fun testBracketedPasteModeSet() {
        val sb = ScreenBuffer(80, 24)
        assertFalse(sb.bracketedPasteMode)

        sb.execute(ScreenCommand.SetPrivateMode(2004, true))
        assertTrue(sb.bracketedPasteMode)

        sb.execute(ScreenCommand.SetPrivateMode(2004, false))
        assertFalse(sb.bracketedPasteMode)
    }

    // ===== 制表位系统 =====

    @Test
    fun testDefaultTabStops() {
        val sb = ScreenBuffer(80, 24)
        // 默认制表位应在每 8 列：0, 8, 16, 24, ...
        sb.execute(ScreenCommand.Tab)
        assertEquals(8, sb.cursor.col)
        sb.execute(ScreenCommand.Tab)
        assertEquals(16, sb.cursor.col)
        sb.execute(ScreenCommand.Tab)
        assertEquals(24, sb.cursor.col)
    }

    @Test
    fun testCustomTabStop() {
        val sb = ScreenBuffer(80, 24)
        // 移动到第 5 列并设置自定义制表位
        sb.execute(ScreenCommand.MoveCursor(0, 5))
        sb.execute(ScreenCommand.SetHorizontalTabStop)

        // 从第 0 列按 Tab 应该到第 5 列
        sb.execute(ScreenCommand.MoveCursor(0, 0))
        sb.execute(ScreenCommand.Tab)
        assertEquals(5, sb.cursor.col)

        // 再按 Tab 应该到默认的第 8 列
        sb.execute(ScreenCommand.Tab)
        assertEquals(8, sb.cursor.col)
    }

    @Test
    fun testTabStopsAtScreenEdge() {
        val sb = ScreenBuffer(40, 24)
        // 移动到最后一列附近
        sb.execute(ScreenCommand.MoveCursor(0, 38))
        sb.execute(ScreenCommand.Tab)
        // 应该停在最后一列
        assertEquals(39, sb.cursor.col)
    }

    // ===== 光标保存/恢复完整状态 =====

    @Test
    fun testSaveRestoreCursorWithSgr() {
        val sb = ScreenBuffer(80, 24)

        // 设置 SGR 状态：红色粗体
        sb.execute(ScreenCommand.SetSgr(listOf(1, 31)))
        // 移动光标
        sb.execute(ScreenCommand.MoveCursor(5, 10))

        // 保存光标（含 SGR）
        sb.execute(ScreenCommand.SaveCursor)

        // 修改 SGR 和光标位置
        sb.execute(ScreenCommand.SetSgr(listOf(0)))  // 重置
        sb.execute(ScreenCommand.MoveCursor(0, 0))
        assertEquals(0, sb.cursor.row)
        assertEquals(0, sb.cursor.col)

        // 恢复光标
        sb.execute(ScreenCommand.RestoreCursor)
        assertEquals(5, sb.cursor.row)
        assertEquals(10, sb.cursor.col)
        // SGR 状态应该被恢复
        assertTrue((sb.currentFlags.toInt() and CellFlags.BOLD) != 0)
    }

    @Test
    fun testSaveRestoreCursorEscSequence() {
        // ESC 7 和 ESC 8 也应该保存/恢复 SGR
        val sb = ScreenBuffer(80, 24)

        sb.execute(ScreenCommand.SetSgr(listOf(4)))  // 下划线
        sb.execute(ScreenCommand.MoveCursor(3, 7))
        sb.execute(ScreenCommand.SaveCursor)  // ESC 7

        sb.execute(ScreenCommand.SetSgr(listOf(0)))
        sb.execute(ScreenCommand.MoveCursor(0, 0))

        sb.execute(ScreenCommand.RestoreCursor)  // ESC 8
        assertEquals(3, sb.cursor.row)
        assertEquals(7, sb.cursor.col)
        assertTrue((sb.currentFlags.toInt() and CellFlags.UNDERLINE) != 0)
    }

    // ===== Reset 功能完整性 =====

    @Test
    fun testResetClearsAlternateScreen() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetPrivateMode(1049, true))
        assertTrue(sb.useAlternateScreen)

        sb.execute(ScreenCommand.Reset)
        assertFalse(sb.useAlternateScreen)
    }

    @Test
    fun testResetClearsBracketedPasteMode() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetPrivateMode(2004, true))
        assertTrue(sb.bracketedPasteMode)

        sb.execute(ScreenCommand.Reset)
        assertFalse(sb.bracketedPasteMode)
    }

    @Test
    fun testResetResetsCursorStyle() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorStyle(5))  // BAR
        assertEquals(CursorState.CursorStyle.BAR, sb.cursor.style)

        sb.execute(ScreenCommand.Reset)
        assertEquals(CursorState.CursorStyle.BLOCK, sb.cursor.style)
    }

    @Test
    fun testResetResetsSgrState() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetSgr(listOf(1, 31, 42)))  // 粗体+红字+绿底

        sb.execute(ScreenCommand.Reset)
        assertEquals(sb.defaultFg, sb.currentFg)
        assertEquals(sb.defaultBg, sb.currentBg)
        assertEquals(0.toByte(), sb.currentFlags)
    }

    // ===== 私有模式和普通模式的区别 =====

    @Test
    fun testNormalModeNotAffectingAlternateScreen() {
        val sb = ScreenBuffer(80, 24)
        // 普通模式 25 控制光标可见性，不应影响备用屏幕状态
        sb.execute(ScreenCommand.SetMode(25, false))  // 隐藏光标
        assertFalse(sb.cursor.visible)
        assertFalse(sb.useAlternateScreen)

        sb.execute(ScreenCommand.SetMode(25, true))  // 显示光标
        assertTrue(sb.cursor.visible)
    }

    @Test
    fun testOriginMode() {
        val sb = ScreenBuffer(80, 24)
        // 设置滚动区域和 origin mode
        sb.execute(ScreenCommand.SetScrollRegion(5, 20))
        sb.execute(ScreenCommand.SetMode(6, true))  // origin mode

        // 光标应该限制在滚动区域内
        sb.execute(ScreenCommand.MoveCursor(0, 0))
        assertEquals(5, sb.cursor.row)  // 在 origin mode 下，row 0 = scrollRegionTop
    }

    // ===== 滚动区域测试 =====

    @Test
    fun testScrollRegionLimitsCursor() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetScrollRegion(5, 15))

        // 光标移动应该被限制在滚动区域内
        sb.execute(ScreenCommand.MoveCursor(20, 0))
        assertEquals(15, sb.cursor.row)  // 被裁剪到 scrollRegionBottom

        sb.execute(ScreenCommand.MoveCursor(2, 0))
        assertEquals(5, sb.cursor.row)  // 被裁剪到 scrollRegionTop
    }
}
