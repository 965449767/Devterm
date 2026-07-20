package com.devterm.benchmark

import com.devterm.terminal.core.TerminalCore
import com.devterm.terminal.core.backend.*
import kotlin.system.measureTimeMillis

object TerminalBenchmark {

    data class BenchmarkResult(
        val name: String,
        val iterations: Int,
        val totalMs: Long,
        val opsPerSec: Double
    )

    fun benchmarkWriteChars(iterations: Int = 100_000): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        val text = "A".repeat(80) + "\n"
        val bytes = text.encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                core.onBackendOutput(bytes)
                core.emitFrame()
            }
        }
        val opsPerSec = iterations.toDouble() / (ms / 1000.0)
        return BenchmarkResult("writeChars", iterations, ms, opsPerSec)
    }

    fun benchmarkEraseDisplay(iterations: Int = 10_000): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        val eraseCmd = "\u001b[2J".encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                core.onBackendOutput(eraseCmd)
                core.emitFrame()
            }
        }
        val opsPerSec = iterations.toDouble() / (ms / 1000.0)
        return BenchmarkResult("eraseDisplay", iterations, ms, opsPerSec)
    }

    fun benchmarkScroll(iterations: Int = 10_000): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        val line = ("X".repeat(80) + "\n").encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                core.onBackendOutput(line)
                core.emitFrame()
            }
        }
        val opsPerSec = iterations.toDouble() / (ms / 1000.0)
        return BenchmarkResult("scroll", iterations, ms, opsPerSec)
    }

    fun benchmarkMixedOutput(iterations: Int = 1_000): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        val mixed = buildString {
            append("\u001b[31m")  // red
            append("print(\"hello\")\n")
            append("\u001b[0m")    // reset
            append("\u001b[2J")    // clear
            append("done\n")
        }.encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                core.onBackendOutput(mixed)
                core.emitFrame()
            }
        }
        val opsPerSec = iterations.toDouble() / (ms / 1000.0)
        return BenchmarkResult("mixedOutput", iterations, ms, opsPerSec)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== DevTerm Benchmark Suite ===")

        val results = listOf(
            benchmarkWriteChars(),
            benchmarkEraseDisplay(),
            benchmarkScroll(),
            benchmarkMixedOutput()
        )

        for (r in results) {
            println("${r.name.padEnd(16)} | ${r.iterations} ops | ${r.totalMs}ms | ${"%.0f".format(r.opsPerSec)} ops/sec")
        }
    }
}
