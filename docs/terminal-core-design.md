# Terminal Core 设计文档

## 模块边界

```
terminal-core/
├── parser/          # VT100/xterm escape 序列解析
│   ├── VtParser.kt     # 状态机
│   ├── ScreenCommand.kt # 输出 Command
│   └── OscHandler.kt   # OSC 序列
├── screen/          # 屏幕缓冲区
│   ├── ScreenBuffer.kt # 1D SoA
│   ├── DirtyTracker.kt # BitSet
│   ├── CellFlags.kt    # 位标记
│   └── CursorState.kt  # 光标
├── scrollback/      # 历史滚动
│   └── ScrollbackBuffer.kt # RingBuffer
├── unicode/         # Unicode 工具
│   └── UnicodeWidth.kt # Width Cache
├── renderer/        # 渲染接口
│   ├── TerminalRenderer.kt
│   ├── RenderFrame.kt
│   └── FrameQueue.kt
├── backend/         # 后端进程
│   ├── Backend.kt
│   ├── ProcessBackend.kt
│   └── TerminalSession.kt
└── TerminalCore.kt  # 协调器
```

## 禁止依赖链

```
parser → screen ✓
screen → parser ✗
screen → renderer ✗
parser → renderer ✗
screen → backend ✗
parser → Android SDK ✗
terminal-core → Compose ✗
```

## ScreenBuffer SoA 内存布局

```kotlin
class ScreenBuffer(width: Int, height: Int) {
    val chars = CharArray(width * height) { ' ' }
    val fg    = IntArray(width * height)  { 0xFFFFFFFF.toInt() }
    val bg    = IntArray(width * height)  { 0xFF000000.toInt() }
    val flags = ByteArray(width * height) { 0 }
}
```

### CellFlags 位标记

```kotlin
object CellFlags {
    const val BOLD      = 0x01
    const val ITALIC    = 0x02
    const val UNDERLINE = 0x04
    const val BLINK     = 0x08
    const val REVERSE   = 0x10
    const val CONCEAL   = 0x20
    const val WIDTH_1   = 0x00  // width encoding: 2 bits
    const val WIDTH_2   = 0x40  // 01 = 2 cells
    const val WIDTH_4   = 0x80  // 10 = 4 cells

    fun width(flags: Byte): Int = when (flags.toInt() shr 6) {
        1 -> 2
        2 -> 4
        else -> 1
    }
    fun setWidth(flags: Byte, w: Int): Byte = ((flags.toInt() and 0x3F) or (when (w) {
        2 -> 0x40
        4 -> 0x80
        else -> 0x00
    })).toByte()
}
```

## DirtyTracker

```kotlin
class DirtyTracker(private val rows: Int) {
    private val dirty = BitSet(rows)

    fun mark(row: Int) {
        if (row in 0 until rows) dirty.set(row)
    }

    fun markAll() {
        dirty.set(0, rows)
    }

    fun consume(): List<Int> {
        val list = mutableListOf<Int>()
        var i = dirty.nextSetBit(0)
        while (i >= 0) {
            list.add(i)
            i = dirty.nextSetBit(i + 1)
        }
        dirty.clear()
        return list
    }

    fun isEmpty(): Boolean = dirty.isEmpty()
}
```

## ScrollbackBuffer RingBuffer

```kotlin
class ScrollbackBuffer(capacity: Int = 50_000) {
    private val lines = arrayOfNulls<ScreenLine>(capacity)
    private var head = 0
    private var count = 0

    fun push(line: ScreenLine) {
        lines[head] = line
        head = (head + 1) % lines.size
        if (count < lines.size) count++
    }

    fun get(index: Int): ScreenLine? {
        if (index < 0 || index >= count) return null
        val pos = (head - count + index).mod(lines.size)
        return lines[pos]
    }

    fun clear() {
        lines.fill(null)
        head = 0
        count = 0
    }
}
```
