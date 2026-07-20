package com.devterm.benchmark

import com.devterm.terminal.core.TerminalCore
import com.devterm.terminal.core.parser.VtParser
import com.devterm.terminal.core.screen.ScreenBuffer
import kotlin.system.measureTimeMillis

/**
 * 终端性能基准测试套件。
 *
 * 测试维度：
 *  1. 大文件 cat 吞吐（模拟 5MB 输出）
 *  2. VT 序列解析吞吐（SGR/Cursor/J 混合）
 *  3. 纯 Parser 吞吐（隔离 ScreenBuffer 开销）
 *  4. 滚屏性能（触发 scrollback 写入）
 *  5. 内存占用估算（SoA vs 传统 Cell 对象）
 *  6. DirtyTracker 消费性能
 */
object TerminalBenchmark {

    data class BenchmarkResult(
        val name: String,
        val iterations: Int,
        val totalMs: Long,
        val opsPerSec: Double,
        val extraInfo: String = ""
    ) {
        override fun toString(): String =
            "${name.padEnd(28)} | ${iterations.toString().padStart(8)} ops | " +
            "${totalMs.toString().padStart(6)}ms | " +
            "${"%,.0f".format(opsPerSec).padStart(12)} ops/sec" +
            (if (extraInfo.isNotEmpty()) " | $extraInfo" else "")
    }

    // ===== 基础基准 =====

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
        return BenchmarkResult("writeChars(80+LF)", iterations, ms, iterations.toDouble() / (ms / 1000.0))
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
        return BenchmarkResult("eraseDisplay(2J)", iterations, ms, iterations.toDouble() / (ms / 1000.0))
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
        return BenchmarkResult("scroll(80+LF)", iterations, ms, iterations.toDouble() / (ms / 1000.0))
    }

    fun benchmarkMixedOutput(iterations: Int = 1_000): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        val mixed = buildString {
            append("\u001b[31m")
            append("print(\"hello\")\n")
            append("\u001b[0m")
            append("\u001b[2J")
            append("done\n")
        }.encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                core.onBackendOutput(mixed)
                core.emitFrame()
            }
        }
        return BenchmarkResult("mixedOutput", iterations, ms, iterations.toDouble() / (ms / 1000.0))
    }

    // ===== Phase 3 新增基准 =====

    /**
     * 大文件 cat：模拟 5MB 文本输出。
     * 测量终端处理大输出的总耗时和吞吐量。
     */
    fun benchmarkLargeFileCat(sizeBytes: Int = 5 * 1024 * 1024): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        // 构造 80 列文本，每行末尾加 LF
        val line = "A".repeat(80) + "\n"
        val lineBytes = line.encodeToByteArray()
        val totalLines = sizeBytes / lineBytes.size
        val chunkSize = 4096  // 模拟 Process read 的 4KB 块
        val chunk = ByteArray(chunkSize).also { buf ->
            var p = 0
            while (p + lineBytes.size <= chunkSize) {
                System.arraycopy(lineBytes, 0, buf, p, lineBytes.size)
                p += lineBytes.size
            }
        }

        val ms = measureTimeMillis {
            var sent = 0
            while (sent < totalLines) {
                core.onBackendOutput(chunk)
                sent += chunkSize / lineBytes.size
            }
            core.emitFrame()
        }
        val mbPerSec = (sizeBytes / 1024.0 / 1024.0) / (ms / 1000.0)
        return BenchmarkResult(
            "largeFileCat(${sizeBytes / 1024 / 1024}MB)",
            totalLines, ms, totalLines.toDouble() / (ms / 1000.0),
            extraInfo = "${"%.2f".format(mbPerSec)} MB/s"
        )
    }

    /**
     * VT 序列解析吞吐：纯 SGR/Cursor/J 序列（不触发实际渲染）。
     * 隔离 Parser 性能。
     */
    fun benchmarkVtSequenceParsing(iterations: Int = 100_000): BenchmarkResult {
        val parser = VtParser()
        // 典型彩色输出序列
        val seq = buildString {
            append("\u001b[31m")      // 红色
            append("hello")
            append("\u001b[0m")       // 重置
            append("\u001b[10;20H")   // 移动光标
            append("\u001b[2J")       // 清屏
            append("\u001b[K")        // 清行
        }.encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                parser.consume(seq)
            }
        }
        return BenchmarkResult("vtSequenceParsing", iterations, ms, iterations.toDouble() / (ms / 1000.0))
    }

    /**
     * 纯 ScreenBuffer 写入吞吐（绕过 Parser，直接 execute）。
     * 对比 Parser 开销。
     */
    fun benchmarkScreenBufferOnly(iterations: Int = 500_000): BenchmarkResult {
        val sb = ScreenBuffer(80, 24)
        val cmd = com.devterm.terminal.core.parser.ScreenCommand.WriteGlyph('A', 1)

        val ms = measureTimeMillis {
            repeat(iterations) {
                if (sb.cursor.col >= 79) {
                    sb.execute(com.devterm.terminal.core.parser.ScreenCommand.CarriageReturn)
                    sb.execute(com.devterm.terminal.core.parser.ScreenCommand.LineFeed)
                }
                sb.execute(cmd)
            }
        }
        return BenchmarkResult("screenBufferOnly", iterations, ms, iterations.toDouble() / (ms / 1000.0))
    }

    /**
     * DirtyTracker 批量标记和消费性能。
     */
    fun benchmarkDirtyTracker(iterations: Int = 100_000): BenchmarkResult {
        val sb = ScreenBuffer(80, 24)
        val ms = measureTimeMillis {
            repeat(iterations) {
                // 标记所有行
                for (r in 0 until 24) sb.dirty.mark(r)
                // 消费
                sb.dirty.consume()
            }
        }
        return BenchmarkResult("dirtyTracker(mark+consume)", iterations, ms, iterations.toDouble() / (ms / 1000.0))
    }

    /**
     * 估算 ScreenBuffer 内存占用。
     * SoA 结构 vs 假想的 Cell 对象数组。
     */
    fun benchmarkMemoryUsage(): BenchmarkResult {
        val cols = 200
        val rows = 100

        // SoA 内存占用（粗略估算）：
        // - CharArray: cols * rows * 2 bytes
        // - IntArray fg: cols * rows * 4 bytes
        // - IntArray bg: cols * rows * 4 bytes
        // - ByteArray flags: cols * rows * 1 byte
        val soaBytes = cols * rows * (2 + 4 + 4 + 1)

        // 假想 Cell 对象数组（每个 Cell 对象约 32 bytes + 引用 8 bytes）
        val cellBytes = cols * rows * 40

        val ratio = cellBytes.toDouble() / soaBytes.toDouble()
        return BenchmarkResult(
            "memoryUsage(${cols}x$rows)",
            1, 0, 0.0,
            extraInfo = "SoA=${soaBytes / 1024}KB vs Cell=${cellBytes / 1024}KB (${"%.1f".format(ratio)}x 节省)"
        )
    }

    /**
     * 快速滚动测试：模拟 PageDown × 1000。
     * 测试 scrollback 写入性能。
     */
    fun benchmarkRapidScroll(iterations: Int = 10_000): BenchmarkResult {
        val core = TerminalCore()
        core.screen.resize(80, 24)
        // 每次满屏后滚动 10 行
        val output = buildString {
            repeat(10) { append("line $it ").append(" ".repeat(71)).append("\n") }
        }.encodeToByteArray()

        val ms = measureTimeMillis {
            repeat(iterations) {
                core.onBackendOutput(output)
            }
            core.emitFrame()
        }
        val scrollbackSize = core.screen.scrollback.size
        return BenchmarkResult(
            "rapidScroll",
            iterations, ms, iterations.toDouble() / (ms / 1000.0),
            extraInfo = "scrollback=$scrollbackSize lines"
        )
    }

    /**
     * 运行全部基准测试并打印报告。
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== DevTerm Benchmark Suite ===")
        println()

        val results = listOf(
            // 基础基准
            benchmarkWriteChars(),
            benchmarkEraseDisplay(),
            benchmarkScroll(),
            benchmarkMixedOutput(),
            // Phase 3 新增
            benchmarkLargeFileCat(),
            benchmarkVtSequenceParsing(),
            benchmarkScreenBufferOnly(),
            benchmarkDirtyTracker(),
            benchmarkRapidScroll(),
            benchmarkMemoryUsage()
        )

        for (r in results) {
            println(r)
        }

        println()
        println("=== 内存占用对比 ===")
        println(benchmarkMemoryUsage().extraInfo)
    }
}
