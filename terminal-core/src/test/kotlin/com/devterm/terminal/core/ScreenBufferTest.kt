package com.devterm.terminal.core

import com.devterm.terminal.core.parser.*
import com.devterm.terminal.core.screen.ScreenBuffer
import org.junit.Assert.*
import org.junit.Test

class ScreenBufferTest {

    @Test
    fun testWriteChar() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        assertEquals('A', sb.chars[0])
        assertEquals(1, sb.cursor.col)
    }

    @Test
    fun testCarriageReturn() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.CarriageReturn)
        assertEquals(0, sb.cursor.col)
    }

    @Test
    fun testLineFeed() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.LineFeed)
        assertEquals(1, sb.cursor.row)
        assertEquals(1, sb.cursor.col)
    }

    @Test
    fun testCarriageReturnLineFeed() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.CarriageReturn)
        sb.execute(ScreenCommand.LineFeed)
        assertEquals(1, sb.cursor.row)
        assertEquals(0, sb.cursor.col)
    }

    @Test
    fun testEraseDisplay2() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.execute(ScreenCommand.WriteGlyph('B', 1))
        sb.execute(ScreenCommand.EraseDisplay(2))
        assertEquals(' ', sb.chars[0])
        assertEquals(' ', sb.chars[1])
        assertEquals(0, sb.cursor.row)
        assertEquals(0, sb.cursor.col)
    }

    @Test
    fun testEraseDisplay3() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('A', 1))
        sb.scrollback.push(com.devterm.terminal.core.screen.ScreenLine(
            CharArray(80) { 'X' }, IntArray(80), IntArray(80), ByteArray(80)
        ))
        assertEquals(1, sb.scrollback.size)
        sb.execute(ScreenCommand.EraseDisplay(3))
        assertEquals(0, sb.scrollback.size)
    }

    @Test
    fun testScrollUp() {
        val sb = ScreenBuffer(80, 24)
        for (r in 0 until 24) {
            sb.cursor = sb.cursor.withRow(r).withCol(0)
            sb.execute(ScreenCommand.WriteGlyph(('0' + r).toChar(), 1))
            sb.execute(ScreenCommand.CarriageReturn)
            sb.execute(ScreenCommand.LineFeed)
        }
        // After 24 line feeds, screen should have scrolled
        assertEquals(1, sb.scrollback.size)
        assertEquals('1', sb.chars[0])
    }

    @Test
    fun testCursorMovement() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.MoveCursor(5, 10))
        assertEquals(5, sb.cursor.row)
        assertEquals(10, sb.cursor.col)
    }

    @Test
    fun testSgrBold() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetSgr(listOf(1)))
        val cmd = ScreenCommand.WriteGlyph('A', 1)
        sb.execute(cmd)
        assertTrue((sb.flags[0].toInt() and com.devterm.terminal.core.screen.CellFlags.BOLD) != 0)
    }

    @Test
    fun testResizePreservesContent() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.WriteGlyph('H', 1))
        sb.execute(ScreenCommand.WriteGlyph('i', 1))
        sb.resize(100, 30)
        assertEquals('H', sb.chars[0])
        assertEquals('i', sb.chars[1])
        assertEquals(100, sb.cols)
        assertEquals(30, sb.rows)
    }

    @Test
    fun testTabMovesToNextTabStop() {
        val sb = ScreenBuffer(80, 24)
        // 从第 0 列按 Tab，应跳到第 8 列
        sb.execute(ScreenCommand.Tab)
        assertEquals(8, sb.cursor.col)
        // 从第 8 列按 Tab，应跳到第 16 列
        sb.execute(ScreenCommand.Tab)
        assertEquals(16, sb.cursor.col)
    }

    @Test
    fun testEraseChars() {
        val sb = ScreenBuffer(80, 24)
        // 写入 5 个字符
        repeat(5) { sb.execute(ScreenCommand.WriteGlyph('A', 1)) }
        // 从当前位置（col=5）向右清除 3 个字符
        sb.execute(ScreenCommand.EraseChars(3))
        // 光标不应移动
        assertEquals(5, sb.cursor.col)
        // col 5,6,7 应为空格
        assertEquals(' ', sb.chars[5])
        assertEquals(' ', sb.chars[6])
        assertEquals(' ', sb.chars[7])
    }

    @Test
    fun testSetCursorRow() {
        val sb = ScreenBuffer(80, 24)
        sb.execute(ScreenCommand.SetCursorRow(10))
        assertEquals(10, sb.cursor.row)
        // 列应保持不变
        assertEquals(0, sb.cursor.col)
    }

    @Test
    fun testEraseDisplayDirtyRows() {
        val sb = ScreenBuffer(80, 24)
        // 先写一些内容
        sb.execute(ScreenCommand.MoveCursor(5, 5))
        sb.execute(ScreenCommand.WriteGlyph('X', 1))
        // mode 0：从光标清到末尾，应标记从光标行到末尾的所有行
        sb.execute(ScreenCommand.MoveCursor(3, 3))
        sb.dirty.consume() // 清空脏标记
        sb.execute(ScreenCommand.EraseDisplay(0))
        val dirty0 = sb.dirty.consume()
        assertTrue(dirty0.contains(3))
        assertTrue(dirty0.contains(23))
        assertTrue(dirty0.size >= 21) // 行 3 到 23
    }

    @Test
    fun testAutoWrapPending() {
        // 测试自动换行的 deferred 逻辑
        val sb = ScreenBuffer(5, 3) // 5 列 3 行的小屏幕
        // 写入 5 个字符填满第一行
        repeat(5) { sb.execute(ScreenCommand.WriteGlyph('A', 1)) }
        // 光标应在行尾（col=4），wrapPending=true
        assertEquals(4, sb.cursor.col)
        // 再写一个字符，应触发换行
        sb.execute(ScreenCommand.WriteGlyph('B', 1))
        // 现在光标应在第二行
        assertEquals(1, sb.cursor.row)
        assertEquals(1, sb.cursor.col)
        // 第二行第一个字符应该是 'B'
        assertEquals('B', sb.chars[5])
    }
}

class VtParserTest {

    private fun parse(s: String): List<ScreenCommand> {
        val parser = VtParser()
        return parser.consume(s.encodeToByteArray())
    }

    @Test
    fun testSimpleText() {
        val cmds = parse("Hello")
        assertEquals(5, cmds.size)
        assertTrue(cmds.all { it is ScreenCommand.WriteGlyph })
    }

    @Test
    fun testCsiCursorUp() {
        val cmds = parse("\u001b[3A")
        assertTrue(cmds.any { it is ScreenCommand.CursorUp && it.n == 3 })
    }

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
    fun testCsiSgr() {
        val cmds = parse("\u001b[1;31m")
        assertTrue(cmds.any { it is ScreenCommand.SetSgr })
    }

    @Test
    fun testOscTitle() {
        val cmds = parse("\u001b]0;My Title\u0007")
        assertTrue(cmds.any { it is ScreenCommand.SetTitle && it.title == "My Title" })
    }

    @Test
    fun testCarriageReturnLineFeed() {
        val cmds = parse("\r\n")
        assertTrue(cmds.any { it is ScreenCommand.CarriageReturn })
        assertTrue(cmds.any { it is ScreenCommand.LineFeed })
    }

    @Test
    fun testClearCommandFromToybox() {
        // toybox clear outputs: ESC [ 2 J  ESC [ H
        val cmds = parse("\u001b[2J\u001b[H")
        assertTrue(cmds.any { it is ScreenCommand.EraseDisplay && it.mode == 2 })
        assertTrue(cmds.any { it is ScreenCommand.MoveCursor && it.row == 0 && it.col == 0 })
    }

    @Test
    fun testTabKey() {
        val cmds = parse("\t")
        assertTrue(cmds.any { it is ScreenCommand.Tab })
    }

    @Test
    fun testCsiEraseChars() {
        // CSI 3 X = ECH (Erase 3 Characters)
        val cmds = parse("\u001b[3X")
        assertTrue(cmds.any { it is ScreenCommand.EraseChars && it.n == 3 })
    }

    @Test
    fun testCsiVerticalPositionAbsolute() {
        // CSI 10 d = VPA (Vertical Position Absolute, row 10)
        val cmds = parse("\u001b[10d")
        assertTrue(cmds.any { it is ScreenCommand.SetCursorRow && it.row == 9 })
    }

    @Test
    fun testEscCpl() {
        // ESC F = CPL (Cursor Preceding Line)
        val cmds = parse("\u001bF")
        assertTrue(cmds.any { it is ScreenCommand.CarriageReturn })
        assertTrue(cmds.any { it is ScreenCommand.CursorUp && it.n == 1 })
    }
}
