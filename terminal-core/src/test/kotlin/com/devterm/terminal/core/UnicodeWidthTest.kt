package com.devterm.terminal.core

import com.devterm.terminal.core.unicode.UnicodeWidthCache
import org.junit.Assert.*
import org.junit.Test

/**
 * UnicodeWidthCache 单元测试。
 *
 * 验证字符宽度计算的正确性，包括：
 * - ASCII 字符宽度 = 1
 * - CJK 字符宽度 = 2
 * - 控制字符宽度 = 0
 * - 组合字符宽度 = 0
 * - 宽字符检测
 * - 缓存命中
 */
class UnicodeWidthTest {

    @Test
    fun testAsciiCharactersWidth() {
        // 普通 ASCII 字符宽度应为 1
        assertEquals(1, UnicodeWidthCache.width('A'.code))
        assertEquals(1, UnicodeWidthCache.width('z'.code))
        assertEquals(1, UnicodeWidthCache.width('0'.code))
        assertEquals(1, UnicodeWidthCache.width(' '.code))
        assertEquals(1, UnicodeWidthCache.width('@'.code))
    }

    @Test
    fun testCjkCharactersWidth() {
        // CJK 字符宽度应为 2
        assertEquals(2, UnicodeWidthCache.width('中'.code))
        assertEquals(2, UnicodeWidthCache.width('文'.code))
        assertEquals(2, UnicodeWidthCache.width('あ'.code))  // 日文平假名
        assertEquals(2, UnicodeWidthCache.width('ア'.code))  // 日文片假名
        assertEquals(2, UnicodeWidthCache.width('한'.code))  // 韩文
    }

    @Test
    fun testCjkPunctuationWidth() {
        // CJK 标点符号宽度应为 2
        assertEquals(2, UnicodeWidthCache.width('。'.code))
        assertEquals(2, UnicodeWidthCache.width('、'.code))
        assertEquals(2, UnicodeWidthCache.width('「'.code))
        assertEquals(2, UnicodeWidthCache.width('」'.code))
    }

    @Test
    fun testFullwidthAsciiWidth() {
        // 全角 ASCII 字符宽度应为 2
        assertEquals(2, UnicodeWidthCache.width(0xFF21))  // 全角 A
        assertEquals(2, UnicodeWidthCache.width(0xFF31))  // 全角 1
    }

    @Test
    fun testControlCharactersWidth() {
        // 控制字符宽度应为 0
        assertEquals(0, UnicodeWidthCache.width(0x00))  // NUL
        assertEquals(0, UnicodeWidthCache.width(0x07))  // BEL
        assertEquals(0, UnicodeWidthCache.width(0x0A))  // LF
        assertEquals(0, UnicodeWidthCache.width(0x1B))  // ESC
        assertEquals(0, UnicodeWidthCache.width(0x1F))  // US
        assertEquals(0, UnicodeWidthCache.width(0x7F))  // DEL
    }

    @Test
    fun testZeroWidthCharacters() {
        // 零宽字符
        assertEquals(0, UnicodeWidthCache.width(0x200B))  // ZERO WIDTH SPACE
        assertEquals(0, UnicodeWidthCache.width(0x200C))  // ZERO WIDTH NON-JOINER
        assertEquals(0, UnicodeWidthCache.width(0x200D))  // ZERO WIDTH JOINER
        assertEquals(0, UnicodeWidthCache.width(0xFEFF))  // ZERO WIDTH NO-BREAK SPACE
    }

    @Test
    fun testHighSurrogatesWidth() {
        // UTF-16 高代理项，不应作为独立字符
        assertEquals(0, UnicodeWidthCache.width(0xD800))
        assertEquals(0, UnicodeWidthCache.width(0xDBFF))
    }

    @Test
    fun testCacheConsistency() {
        // 同一字符多次查询应返回相同结果
        val codepoint = 'A'.code
        val result1 = UnicodeWidthCache.width(codepoint)
        val result2 = UnicodeWidthCache.width(codepoint)
        val result3 = UnicodeWidthCache.width(codepoint)
        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun testWideCharacterRange() {
        // CJK 统一表意文字基本区
        assertTrue(UnicodeWidthCache.width(0x4E00).toInt() == 2)  // 一
        assertTrue(UnicodeWidthCache.width(0x9FFF).toInt() == 2)  // 龿

        // 韩文字母区
        assertTrue(UnicodeWidthCache.width(0xAC00).toInt() == 2)
        assertTrue(UnicodeWidthCache.width(0xD7A3).toInt() == 2)

        // 日文平假名
        assertTrue(UnicodeWidthCache.width(0x3041).toInt() == 2)
        assertTrue(UnicodeWidthCache.width(0x3096).toInt() == 2)

        // 日文片假名
        assertTrue(UnicodeWidthCache.width(0x30A1).toInt() == 2)
        assertTrue(UnicodeWidthCache.width(0x30FF).toInt() == 2)
    }

    @Test
    fun testNarrowCharacters() {
        // 半角字符宽度应为 1
        assertEquals(1, UnicodeWidthCache.width('a'.code))
        assertEquals(1, UnicodeWidthCache.width('1'.code))
        assertEquals(1, UnicodeWidthCache.width('.'.code))
        assertEquals(1, UnicodeWidthCache.width('-'.code))
        assertEquals(1, UnicodeWidthCache.width('/'.code))
    }

    @Test
    fun testCombiningCharacters() {
        // 组合字符宽度应为 0
        assertEquals(0, UnicodeWidthCache.width(0x0300))  // COMBINING GRAVE ACCENT
        assertEquals(0, UnicodeWidthCache.width(0x0301))  // COMBINING ACUTE ACCENT
        assertEquals(0, UnicodeWidthCache.width(0x0308))  // COMBINING DIAERESIS
    }

    @Test
    fun testEmojiWidth() {
        // 部分 emoji 属于宽字符
        assertTrue(UnicodeWidthCache.width(0x1F004).toInt() == 2)  // MAHJONG TILE EAST WIND
        assertTrue(UnicodeWidthCache.width(0x1F200).toInt() == 2)  // SQUARED HIRAgana KOKO
    }
}
