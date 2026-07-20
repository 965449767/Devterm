# DevTerm 架构文档

## 总览

DevTerm 是一个 **Android Terminal Engine**。架构分五层，严格单向依赖：

```
┌──────────────────────────────────────────────────────┐
│ Layer 1: Compose UI                                  │
│  collectAsState → Canvas { drawText }                │
│  只负责 Viewport 布局和手势                           │
├──────────────────────────────────────────────────────┤
│ Layer 2: Renderer (renderer-compose)                 │
│  ComposeTerminalRenderer implements TerminalRenderer │
│  GlyphCache + DirtyLineIterator + FrameQueue         │
│  负责文本布局、位图缓存、脏行重绘                      │
├──────────────────────────────────────────────────────┤
│ Layer 3: Screen + Core (terminal-core)               │
│  ScreenBuffer: 1D SoA (CharArray+IntArray×2+ByteArray)│
│  DirtyTracker: BitSet 标记脏行                        │
│  ScrollbackBuffer: RingBuffer (50000 行)              │
│  CursorState + SgrState                              │
├──────────────────────────────────────────────────────┤
│ Layer 4: Parser (terminal-core)                      │
│  VtParser: VT100/xterm 状态机                        │
│  输出 ScreenCommand sealed class (不可变)             │
│  完全不感知 ScreenBuffer / UI                         │
├──────────────────────────────────────────────────────┤
│ Layer 5: Backend (terminal-core)                     │
│  Backend 接口：start()/write()/resize()/destroy()    │
│  ProcessBackend: ProcessBuilder 实现                 │
│  (未来) PtyBackend: JNI openpty 实现                 │
└──────────────────────────────────────────────────────┘
```

## 核心设计：ScreenCommand

Parser 不直接操作屏幕，而是发射不可变 Command：

```
VT Byte Stream
  ↓
VtParser.consume(byte) → ScreenCommand
  ↓
ScreenBuffer.execute(ScreenCommand)
  ↓
DirtyTracker.mark(row)     ← 只标记被改的行
  ↓
FrameQueue.notifyDirty()   ← 通知渲染线程
```

```kotlin
sealed class ScreenCommand {
    data class WriteGlyph(val c: Char, val fg: Int, val bg: Int, val flags: Byte)
    data class MoveCursor(val row: Int, val col: Int)
    data class EraseDisplay(val mode: Int)
    data class EraseLine(val mode: Int)
    object ScrollUp
    object CarriageReturn
    data class SetSgr(val params: List<Int>)
    data class SetCursorStyle(val style: Int)
    // ...
}
```

**收益**：Renderer、ScreenBuffer、Backend 可独立替换，Parser 永远不变。

## 核心设计：SoA 屏幕缓冲区

不创建 Cell 对象，改为 Struct of Arrays（连续内存，零 GC）：

```kotlin
class ScreenBuffer(
    val cols: Int,
    val rows: Int
) {
    val chars = CharArray(cols * rows) { ' ' }
    val fg    = IntArray(cols * rows)  { 0xFFFFFFFF.toInt() }  // ARGB
    val bg    = IntArray(cols * rows)  { 0xFF000000.toInt() }  // ARGB
    val flags = ByteArray(cols * rows) { 0 }
}
```

索引计算：`index = row * cols + col`

位标记 (`CellFlags`)：
```
bit 0    — bold
bit 1    — italic
bit 2    — underline
bit 3    — blink
bit 4    — reverse
bit 5    — conceal
bit 6-7  — width (0=1, 1=2, 2=4)
```

## 核心设计：Dirty Region

不再全量复制 Snapshot，改为追踪脏行：

```
Parser → WriteGlyph(Row=5, Col=3)
  ↓
ScreenBuffer 更新 chars/fg/bg/flags
  ↓
DirtyTracker.mark(5)
  ↓
FrameQueue.notifyDirty()

Renderer 每 16ms:
  ↓
FrameQueue.consume() → true 表示有更新
  ↓
DirtyTracker.consume() → List<Int> = [5, 7, 12]
  ↓
只渲染这几行 + 光标
```

## 核心设计：Frame Queue

Parser 线程不直接触发 Compose：
```
Parser Thread
  ↓  writeInput()
FrameQueue.notifyDirty()    ← atomic 计数器++
  ↓
Renderer Thread (16ms 循环)
  ↓  FrameQueue.consume()
  ↓  DirtyTracker.consume()
  ↓  for (row in dirtyLines) drawRow(row)
  ↓  drawCursor()
  ↓  _snapshot.value = RenderFrame(...)
  ↓  [StateFlow]
Compose collectAsState → Canvas 增量重绘
```

## 核心设计：Unicode Width Cache

```kotlin
object UnicodeWidthCache {
    private val cache = ConcurrentHashMap<Int, Byte>()

    fun width(codepoint: Int): Byte =
        cache.getOrPut(codepoint) { computeWidth(codepoint) }

    private fun computeWidth(codepoint: Int): Byte = when {
        codepoint in 0x20..0x7E -> 1     // ASCII
        codepoint in 0x1100..0x11FF -> 2 // Hangul Jamo
        codepoint in 0x2E80..0x9FFF -> 2 // CJK
        codepoint in 0xAC00..0xD7AF -> 2 // Hangul Syllables
        codepoint in 0xFE30..0xFE6F -> 2 // CJK Compatibility
        codepoint in 0xFF01..0xFF60 -> 2 // Fullwidth
        codepoint in 0x1F000..0x1FFFF -> 2 // Emoji etc
        codepoint in 0x20000..0x2FA1F -> 2 // CJK Ext B-F
        codepoint in 0x30000..0x3134F -> 2 // CJK Ext G-H
        else -> if (codepoint <= 0x10FFFF) 1 else 0
    }
}
```

## 设计约束（宇宙 B 环境）

| 约束 | 影响 | 替代方案 |
|------|------|---------|
| NDK/cmake 仅为 x86_64，无法在 ARM64 执行 | 不能编译任何 C/C++ | 纯 Kotlin + SoA 接近原生性能 |
| `android.system.Os` 为隐藏 API | 不能直接 fork/execve | `ProcessBuilder` |
| CMake 3.22.1 无法通过 QEMU 运行 | 不能使用 NDK | 移除 NDK 编译配置 |

## 数据流

### 输出路径 (Shell → 屏幕)

```
Shell 进程 stdout+stderr
  ↓ Reader Thread
TerminalCore.writeInput(bytes)
  ↓ forEach byte
VtParser.consume(byte) → ScreenCommand
  ↓
ScreenBuffer.execute(command) → DirtyTracker.mark(row)
  ↓
FrameQueue.notifyDirty()
  ↓ [16ms 间隔]
Renderer Thread
  ↓ FrameQueue.consume() → dirtyRows
  ↓ for row in dirtyRows: drawRow(row)
  ↓
_snapshot.value = RenderFrame(dirtyLines, cursor)
  ↓ [StateFlow]
Compose collectAsState → Canvas 增量绘制
```

### 输入路径 (键盘 → Shell)

```
软件键盘 / 硬件键盘
  ↓
TerminalEmulator.dispatchCharacter()
  ↓ localEcho
ScreenBuffer.writeGlyph(char) → DirtyTracker.mark(row)
  ↓
TerminalSession.write(bytes)
  ↓ [ByteQueue]
Writer Thread → process.outputStream → Shell stdin
```

## 关键性能指标

| 指标 | 目标 | 验证方法 |
|------|------|---------|
| 渲染帧率 | 60 FPS 稳定 | benchmark 模块 |
| 大文件 cat (5MB) | < 2s | benchmark 模块 |
| GC 暂停 | 无感知（零对象分配） | 无新 Cell 对象 |
| 启动到提示符 | < 1s | 手动 |
| 内存占用 | < 50MB | adb dumpsys meminfo |
| CPU 空闲占用 | < 1% | adb top |
