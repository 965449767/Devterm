package com.devterm.benchmark

import org.junit.Assert.*
import org.junit.Test

/**
 * 基准测试的 JUnit 入口。
 * 每个测试只验证"能正常运行且指标为正"，不验证具体数值（避免 flaky）。
 */
class BenchmarkTest {

    @Test
    fun testBenchmarkRunsWithoutError() {
        val result = TerminalBenchmark.benchmarkWriteChars(1000)
        assertTrue("opsPerSec should be positive", result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testEraseDisplayBenchmark() {
        val result = TerminalBenchmark.benchmarkEraseDisplay(1000)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testScrollBenchmark() {
        val result = TerminalBenchmark.benchmarkScroll(1000)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testMixedOutputBenchmark() {
        val result = TerminalBenchmark.benchmarkMixedOutput(100)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    // ===== Phase 3 新增基准测试 =====

    @Test
    fun testLargeFileCatBenchmark() {
        // 用较小的文件大小避免测试超时
        val result = TerminalBenchmark.benchmarkLargeFileCat(sizeBytes = 256 * 1024)
        assertTrue("opsPerSec should be positive", result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testVtSequenceParsingBenchmark() {
        val result = TerminalBenchmark.benchmarkVtSequenceParsing(10_000)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testScreenBufferOnlyBenchmark() {
        val result = TerminalBenchmark.benchmarkScreenBufferOnly(50_000)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testDirtyTrackerBenchmark() {
        val result = TerminalBenchmark.benchmarkDirtyTracker(10_000)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testRapidScrollBenchmark() {
        val result = TerminalBenchmark.benchmarkRapidScroll(1000)
        assertTrue(result.opsPerSec > 0)
        println(result)
    }

    @Test
    fun testMemoryUsageBenchmark() {
        val result = TerminalBenchmark.benchmarkMemoryUsage()
        assertTrue("extraInfo should contain comparison", result.extraInfo.contains("节省"))
        println(result)
    }
}
