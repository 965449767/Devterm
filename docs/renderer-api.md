# Renderer API 设计文档

## 接口定义 (terminal-core)

```kotlin
interface TerminalRenderer {
    fun render(frame: RenderFrame)
    fun setSize(width: Int, height: Int)
    fun setFont(font: FontDescription)
    fun invalidateAll()
}
```

## RenderFrame 数据结构

```kotlin
data class RenderFrame(
    val dirtyRows: List<Int>,
    val chars: CharArray,       // 全屏 chars（只读引用）
    val fg: IntArray,
    val bg: IntArray,
    val flags: ByteArray,
    val cols: Int,
    val rows: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val cursorStyle: Int
)
```

Renderer 通过 `dirtyRows` 知道需要重绘哪些行。
`chars`/`fg`/`bg`/`flags` 是 ScreenBuffer 的内部数组的只读引用（不复制）。

## FrameQueue 实现

```kotlin
class FrameQueue {
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)

    fun notifyDirty() {
        pending.incrementAndGet()
    }

    fun consume(): Boolean {
        val v = pending.getAndSet(0)
        return v > 0
    }
}
```

- Parser 线程调用 `notifyDirty()`（无锁、无对象分配）
- Renderer 线程每 16ms 调用 `consume()`
- `consume()` 返回 true → 读取 DirtyTracker + 构建 RenderFrame

## ComposeTerminalRenderer 实现 (renderer-compose)

```kotlin
class ComposeTerminalRenderer(
    private val textMeasurer: TextMeasurer,
    private val charMetrics: CharMetrics
) : TerminalRenderer {

    private val glyphCache = GlyphCache(textMeasurer)

    override fun render(frame: RenderFrame) {
        // 只遍历 dirtyRows
        for (row in frame.dirtyRows) {
            drawRow(row, frame)
        }
        // 绘制光标
        drawCursor(frame)
    }

    private fun drawRow(row: Int, frame: RenderFrame) {
        val y = row * charMetrics.height
        var x = 0f
        for (col in 0 until frame.cols) {
            val index = row * frame.cols + col
            val c = frame.chars[index]
            val fgColor = frame.fg[index]
            val bgColor = frame.bg[index]
            val w = CellFlags.width(frame.flags[index])
            val cellWidth = charMetrics.width * w

            // 背景
            if (bgColor != 0xFF000000.toInt()) {
                drawRect(bgColor, x, y, cellWidth, charMetrics.height)
            }

            // 前景
            if (c != ' ') {
                val glyph = glyphCache.get(c, fgColor, frame.flags[index])
                drawText(glyph, x, y)
            }

            x += cellWidth
        }
    }

    private fun drawCursor(frame: RenderFrame) {
        if (!frame.cursorVisible) return
        val x = frame.cursorCol * charMetrics.width
        val y = frame.cursorRow * charMetrics.height
        drawRect(cursorColor, x, y, charMetrics.width, charMetrics.height)
    }
}
```

## GlyphCache

```kotlin
class GlyphCache(private val textMeasurer: TextMeasurer) {
    private val cache = LinkedHashMap<String, TextLayoutResult>(
        initialCapacity = 1024,
        loadFactor = 0.75f,
        accessOrder = true
    ) {
        override fun removeEldestEntry(oldest: MutableMap.MutableEntry<String, TextLayoutResult>?): Boolean {
            return size > 2048
        }
    }

    fun get(char: Char, fgColor: Int, flags: Byte, style: TextStyle = defaultStyle): TextLayoutResult {
        val bold = (flags.toInt() and CellFlags.BOLD) != 0
        val italic = (flags.toInt() and CellFlags.ITALIC) != 0
        val key = "${char}_${fgColor}_${bold}_${italic}"
        return cache.getOrPut(key) {
            val cellStyle = style.copy(
                color = Color(fgColor),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
            )
            textMeasurer.measure(char.toString(), cellStyle)
        }
    }

    fun invalidate() {
        cache.clear()
    }
}
```

## Canvas 增量绘制

```kotlin
@Composable
fun TerminalCanvas(renderer: ComposeTerminalRenderer, snapshot: StateFlow<RenderFrame>) {
    val frame by snapshot.collectAsState()

    Canvas(modifier = Modifier.fillMaxSize()) {
        renderer.render(frame)
    }
}
```
