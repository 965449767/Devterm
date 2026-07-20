package com.devterm.benchmark

import org.junit.Assert.*
import org.junit.Test

class BenchmarkTest {

    @Test
    fun testBenchmarkRunsWithoutError() {
        val result = TerminalBenchmark.benchmarkWriteChars(1000)
        assertTrue("opsPerSec should be positive", result.opsPerSec > 0)
        println("writeChars: ${"%.0f".format(result.opsPerSec)} ops/sec")
    }

    @Test
    fun testEraseDisplayBenchmark() {
        val result = TerminalBenchmark.benchmarkEraseDisplay(1000)
        assertTrue(result.opsPerSec > 0)
        println("eraseDisplay: ${"%.0f".format(result.opsPerSec)} ops/sec")
    }

    @Test
    fun testScrollBenchmark() {
        val result = TerminalBenchmark.benchmarkScroll(1000)
        assertTrue(result.opsPerSec > 0)
        println("scroll: ${"%.0f".format(result.opsPerSec)} ops/sec")
    }
}
