package com.devterm.terminal.core

import com.devterm.terminal.core.screen.ScreenLine
import com.devterm.terminal.core.scrollback.ScrollbackBuffer
import org.junit.Assert.*
import org.junit.Test

/**
 * ScrollbackBuffer 环形缓冲区单元测试。
 * 覆盖：基础写入、循环覆盖、越界访问、顺序读取、清空。
 */
class ScrollbackBufferTest {

    private fun makeLine(c: Char, cols: Int = 80): ScreenLine =
        ScreenLine(CharArray(cols) { c }, IntArray(cols), IntArray(cols), ByteArray(cols))

    @Test
    fun testPushAndGetBasic() {
        val sb = ScrollbackBuffer(capacity = 5)
        assertEquals(0, sb.size)

        sb.push(makeLine('A'))
        sb.push(makeLine('B'))
        assertEquals(2, sb.size)

        // 按 push 顺序读取
        assertEquals('A', sb.get(0)?.chars?.get(0))
        assertEquals('B', sb.get(1)?.chars?.get(0))
    }

    @Test
    fun testGetOutOfBounds() {
        val sb = ScrollbackBuffer(capacity = 5)
        sb.push(makeLine('A'))

        // 越界访问应返回 null
        assertNull(sb.get(-1))
        assertNull(sb.get(1))
        assertNull(sb.get(100))
    }

    @Test
    fun testCircularOverwrite() {
        // 容量 3，写入 5 条，应只保留最后 3 条
        val sb = ScrollbackBuffer(capacity = 3)
        sb.push(makeLine('A'))
        sb.push(makeLine('B'))
        sb.push(makeLine('C'))
        sb.push(makeLine('D'))
        sb.push(makeLine('E'))

        assertEquals(3, sb.size)
        // 最旧的 A/B 应被覆盖，保留 C/D/E
        assertEquals('C', sb.get(0)?.chars?.get(0))
        assertEquals('D', sb.get(1)?.chars?.get(0))
        assertEquals('E', sb.get(2)?.chars?.get(0))
    }

    @Test
    fun testGetOrderAfterWrap() {
        // 验证环形缓冲区 head 指针绕回后 get 索引仍正确
        val sb = ScrollbackBuffer(capacity = 4)
        // 先填满
        for (c in 'A'..'D') sb.push(makeLine(c))
        // 再多写 2 条，触发环绕
        sb.push(makeLine('E'))
        sb.push(makeLine('F'))

        assertEquals(4, sb.size)
        assertEquals('C', sb.get(0)?.chars?.get(0))
        assertEquals('D', sb.get(1)?.chars?.get(0))
        assertEquals('E', sb.get(2)?.chars?.get(0))
        assertEquals('F', sb.get(3)?.chars?.get(0))
    }

    @Test
    fun testClear() {
        val sb = ScrollbackBuffer(capacity = 5)
        sb.push(makeLine('A'))
        sb.push(makeLine('B'))
        assertEquals(2, sb.size)

        sb.clear()
        assertEquals(0, sb.size)
        assertNull(sb.get(0))

        // 清空后可继续使用
        sb.push(makeLine('X'))
        assertEquals(1, sb.size)
        assertEquals('X', sb.get(0)?.chars?.get(0))
    }

    @Test
    fun testCapacityOneEdgeCase() {
        // 容量为 1 的边界情况
        val sb = ScrollbackBuffer(capacity = 1)
        sb.push(makeLine('A'))
        assertEquals(1, sb.size)
        assertEquals('A', sb.get(0)?.chars?.get(0))

        // 覆盖
        sb.push(makeLine('B'))
        assertEquals(1, sb.size)
        assertEquals('B', sb.get(0)?.chars?.get(0))
    }

    @Test
    fun testLargeNumberOfPushes() {
        // 大量写入，验证不会内存泄漏或索引错乱
        val sb = ScrollbackBuffer(capacity = 100)
        for (i in 0 until 1000) {
            sb.push(makeLine((i % 26 + 'A'.code).toChar()))
        }
        assertEquals(100, sb.size)
        // 最后写入的应该是 (999 % 26) = 5 → 'F'
        val last = (999 % 26 + 'A'.code).toChar()
        assertEquals(last, sb.get(99)?.chars?.get(0))
    }
}
