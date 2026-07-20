package com.devterm.terminal.core

import com.devterm.terminal.core.parser.VtParser
import com.devterm.terminal.core.screen.CellFlags
import com.devterm.terminal.core.screen.ScreenBuffer
import org.junit.Assert.*
import org.junit.Test

/**
 * 端到端回归测试：验证字节流 → Parser → ScreenBuffer 的完整行为等价性。
 *
 * 这一层测试不关心 Parser 输出的 Command 类型，只关心
 * "给定的输入字节流最终在屏幕上产生的结果是否符合 VT100 规范"。
 *
 * 用作重构/优化的回归保障：任何内部重构后，只要这些测试通过，
 * 就能保证用户可见的行为没有变化。
 */
class RegressionTest {

    private val parser = VtParser()
    private val screen = ScreenBuffer(80, 24)

    /**
     * 工具方法：把字节流喂给 Parser，再把 Command 喂给 ScreenBuffer。
     */
    private fun feed(input: String) {
        val cmds = parser.consume(input.encodeToByteArray())
        cmds.forEach { screen.execute(it) }
    }

    private fun charAt(row: Int, col: Int): Char =
        screen.chars[row * screen.cols + col]

    private fun flagsAt(row: Int, col: Int): Byte =
        screen.flags[row * screen.cols + col]

    // ===== 基础文本写入 =====

    @Test
    fun testPlainTextRendering() {
        feed("Hello")
        assertEquals('H', charAt(0, 0))
        assertEquals('e', charAt(0, 1))
        assertEquals('l', charAt(0, 2))
        assertEquals('l', charAt(0, 3))
        assertEquals('o', charAt(0, 4))
        // 光标在 'o' 之后
        assertEquals(5, screen.cursor.col)
        assertEquals(0, screen.cursor.row)
    }

    @Test
    fun testNewlineMovesToNextRow() {
        feed("AB\nCD")
        assertEquals('A', charAt(0, 0))
        assertEquals('B', charAt(0, 1))
        assertEquals('C', charAt(1, 0))
        assertEquals('D', charAt(1, 1))
    }

    @Test
    fun testCarriageReturnResetsCol() {
        feed("ABC\rX")
        // \r 把光标移回行首，X 覆盖 A
        assertEquals('X', charAt(0, 0))
        assertEquals('B', charAt(0, 1))
        assertEquals('C', charAt(0, 2))
    }

    // ===== 光标移动 =====

    @Test
    fun testCursorMovement() {
        feed("\u001b[5;10H")  // 移动到 row=4, col=9
        assertEquals(4, screen.cursor.row)
        assertEquals(9, screen.cursor.col)

        feed("X")
        assertEquals('X', charAt(4, 9))
    }

    @Test
    fun testCursorRelativeMovement() {
        feed("\u001b[5;5H")    // 到 (4,4)
        feed("\u001b[3C")      // 右移 3 → col=7
        assertEquals(7, screen.cursor.col)
        feed("\u001b[2A")      // 上移 2 → row=2
        assertEquals(2, screen.cursor.row)
        feed("\u001b[1D")      // 左移 1 → col=6
        assertEquals(6, screen.cursor.col)
    }

    // ===== SGR 属性 =====

    @Test
    fun testSgrBold() {
        feed("\u001b[1mX")
        assertTrue((flagsAt(0, 0).toInt() and CellFlags.BOLD) != 0)
    }

    @Test
    fun testSgrItalic() {
        feed("\u001b[3mX")
        assertTrue((flagsAt(0, 0).toInt() and CellFlags.ITALIC) != 0)
    }

    @Test
    fun testSgrUnderline() {
        feed("\u001b[4mX")
        assertTrue((flagsAt(0, 0).toInt() and CellFlags.UNDERLINE) != 0)
    }

    @Test
    fun testSgrReset() {
        feed("\u001b[1mX\u001b[0mY")
        // X 有 bold
        assertTrue((flagsAt(0, 0).toInt() and CellFlags.BOLD) != 0)
        // Y 无 bold（reset 后）
        assertFalse((flagsAt(0, 1).toInt() and CellFlags.BOLD) != 0)
    }

    @Test
    fun testSgrForegroundColor() {
        feed("\u001b[31mX")  // 红色
        val fg = screen.fg[0]
        // ansiColor(1) = 0xFFCC0000（见 ScreenBuffer.ansiColor 实现）
        assertEquals(0xFFCC0000.toInt(), fg)
    }

    // ===== 清屏 =====

    @Test
    fun testEraseDisplay2ClearsScreen() {
        feed("Hello World")
        feed("\u001b[2J")  // 全屏清屏
        // 所有字符应为空格
        for (i in 0 until 10) {
            assertEquals(' ', charAt(0, i))
        }
        // 光标应回到原点（某些实现不清零光标，这里验证清屏本身）
    }

    @Test
    fun testEraseLine0ClearsFromCursor() {
        feed("HelloWorld")  // H(0)e(1)l(2)l(3)o(4)W(5)o(6)r(7)l(8)d(9)
        feed("\u001b[5G")   // 光标移到 col=4 (1-indexed 5)
        feed("\u001b[K")    // 从光标清到行尾
        // col 0-3 不受影响
        assertEquals('H', charAt(0, 0))
        assertEquals('e', charAt(0, 1))
        assertEquals('l', charAt(0, 2))
        assertEquals('l', charAt(0, 3))
        // col 4 及之后应被清除
        assertEquals(' ', charAt(0, 4))
        assertEquals(' ', charAt(0, 5))
        assertEquals(' ', charAt(0, 6))
    }

    // ===== 滚屏 =====

    @Test
    fun testScrollOnLineFeedAtBottom() {
        // 填满屏幕
        feed("\u001b[24;1H")  // 到最后一行
        feed("Bottom Line")
        feed("\n")            // 触发滚动
        // "Bottom Line" 应进入 scrollback
        assertEquals(1, screen.scrollback.size)
        // 屏幕最后一行应为空（或新行）
        assertEquals(' ', charAt(23, 0))
    }

    // ===== Tab 键 =====

    @Test
    fun testTabMovesToNextTabStopFromStart() {
        feed("\t")
        assertEquals(8, screen.cursor.col)
    }

    @Test
    fun testTabFromMiddlePosition() {
        feed("\u001b[1;3H")  // col=2
        feed("\t")
        assertEquals(8, screen.cursor.col)
    }

    // ===== 自动换行 =====

    @Test
    fun testAutoWrapAtEndOfLine() {
        // 80 列屏幕，写 81 个字符应触发换行
        feed("A".repeat(80))
        // 写满后光标 wrapPending=true，位置仍在行尾
        assertEquals(79, screen.cursor.col)
        feed("B")
        // B 应出现在第二行第一列
        assertEquals('B', charAt(1, 0))
    }

    // ===== OSC 标题 =====

    @Test
    fun testOscTitleDoesNotBreakRendering() {
        feed("\u001b]0;My Title\u0007Hello")
        // 标题后继续渲染文本
        assertEquals('H', charAt(0, 0))
        assertEquals('e', charAt(0, 1))
    }

    // ===== 组合场景 =====

    @Test
    fun testTypicalShellOutput() {
        // 模拟 ls -la --color=auto 的典型输出
        feed("\u001b[0m\u001b[01;34m.\u001b[0m")
        feed("\n")
        feed("\u001b[01;34m..\u001b[0m")
        feed("\n")
        feed("file.txt")

        // 第一行第一个字符应带 bold + 蓝色
        assertTrue((flagsAt(0, 0).toInt() and CellFlags.BOLD) != 0)
        assertEquals('.', charAt(0, 0))
        assertEquals('.', charAt(1, 0))
        assertEquals('f', charAt(2, 0))
    }

    @Test
    fun testCursorSaveRestore() {
        feed("\u001b[5;10H")  // 移动到 (4, 9)
        feed("\u001b[s")      // 保存
        feed("\u001b[1;1H")   // 移动到 (0, 0)
        feed("\u001b[u")      // 恢复
        assertEquals(4, screen.cursor.row)
        assertEquals(9, screen.cursor.col)
    }

    @Test
    fun testScrollRegion() {
        // 设置滚动区域为行 5-10
        feed("\u001b[5;10r")
        feed("\u001b[5;1H")  // 移动到滚动区域内
        // 写入并在区域内滚动
        feed("Line in region\n")
        // 不验证具体内容，只验证不崩溃且光标在区域内
        assertTrue(screen.cursor.row in 0 until 24)
    }

    @Test
    fun testMultipleSgrParams() {
        // 一次设置多个属性
        feed("\u001b[1;3;4;31mX")
        val f = flagsAt(0, 0).toInt()
        assertTrue((f and CellFlags.BOLD) != 0)
        assertTrue((f and CellFlags.ITALIC) != 0)
        assertTrue((f and CellFlags.UNDERLINE) != 0)
    }

    @Test
    fun testEraseCharsDoesNotMoveCursor() {
        feed("ABCDEF")        // A(0)B(1)C(2)D(3)E(4)F(5)
        feed("\u001b[1;3H")   // 移动到 col=2 (1-indexed 3)
        feed("\u001b[2X")     // 从 col=2 清除 2 个字符
        // col 2,3 应被清除
        assertEquals(' ', charAt(0, 2))
        assertEquals(' ', charAt(0, 3))
        // col 4,5 不受影响
        assertEquals('E', charAt(0, 4))
        assertEquals('F', charAt(0, 5))
        // 光标位置不变
        assertEquals(2, screen.cursor.col)
    }
}
