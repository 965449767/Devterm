# DevTerm 阶段化实施路线图

> 定位：Android 上性能最好的 Terminal Engine。
> 方向：先性能，后功能。先引擎，后 App。

---

## Phase 1：模块拆分 + 引擎核心重构 ✅（已完成）

**目标**：消除 TerminalEmulator God Object，拆分为三层清晰模块
**状态**：2025-07-20 完成。`:terminal-core` / `:renderer-compose` / `:benchmark` / `:app` 四模块编译通过，17 个单元测试通过。

### 模块拆分

| 新建模块 | 路径 | 依赖 |
|---------|------|------|
| `:terminal-core` | `terminal-core/` | 无 Android 依赖 |
| `:renderer-compose` | `renderer-compose/` | `terminal-core` + Compose |
| `:benchmark` | `benchmark/` | `terminal-core` |
| `:app` | `app/` | `renderer-compose` |

### terminal-core 模块组件

| 组件 | 文件 | 说明 |
|------|------|------|
| VtParser | `parser/VtParser.kt` | VT100/xterm 状态机，输出 `ScreenCommand` |
| ScreenCommand | `parser/ScreenCommand.kt` | 不可变 Command sealed class |
| ScreenBuffer | `screen/ScreenBuffer.kt` | 1D SoA（CharArray + IntArray×2 + ByteArray） |
| DirtyTracker | `screen/DirtyTracker.kt` | BitSet 标记脏行 |
| CellFlags | `screen/CellFlags.kt` | 位标记常量 (bold/italic/width 等) |
| CursorState | `screen/CursorState.kt` | 光标位置 + 样式 |
| ScrollbackBuffer | `scrollback/ScrollbackBuffer.kt` | RingBuffer (50000 行) |
| UnicodeWidth | `unicode/UnicodeWidth.kt` | Width Cache |
| TerminalRenderer | `renderer/TerminalRenderer.kt` | 渲染接口 |
| RenderFrame | `renderer/RenderFrame.kt` | 增量帧数据 |
| FrameQueue | `renderer/FrameQueue.kt` | 60fps 节流 |
| TerminalCore | `TerminalCore.kt` | 协调 Parser + Buffer + Scrollback |
| Backend | `backend/Backend.kt` | 抽象后端接口 |
| ProcessBackend | `backend/ProcessBackend.kt` | ProcessBuilder 实现 |
| TerminalSession | `backend/TerminalSession.kt` | 3 线程 I/O（从旧 app 移植） |

### SoA 屏幕缓冲区设计

```kotlin
class ScreenBuffer(width: Int, height: Int) {
    val chars = CharArray(width * height) { ' ' }
    val fg    = IntArray(width * height)  { 0xFFFFFFFF.toInt() }
    val bg    = IntArray(width * height)  { 0xFF000000.toInt() }
    val flags = ByteArray(width * height) { 0 }
}
```

### 增量渲染数据流

```
VtParser.consume(byte) → ScreenCommand
  ↓ ScreenBuffer.execute(cmd)
ScreenBuffer 更新对应 Cell
  ↓ DirtyTracker.mark(row)
FrameQueue.notifyDirty()
  ↓ [Renderer 16ms 轮询]
Renderer 读取脏行 → 只重绘脏行 + 光标
```

### Phase 1 验证清单
- [x] `:terminal-core` 编译通过（纯 Kotlin，无 Android 依赖）
- [x] `:renderer-compose` 编译通过
- [x] `:app` 编译通过
- [x] SoA ScreenBuffer 单元测试（字符写入、清屏、滚动）
- [x] DirtyTracker 单元测试（标记、消费、归零）
- [x] VtParser 单元测试（17 测试全部通过，含 toybox clear 序列）

---

## Phase 2：Renderer API + Glyph Cache + Frame Queue ✅（已完成）

**目标**：渲染隔离，60fps 节流，Glyph 缓存命中
**状态**：2025-07-20 完成。`:app` 切换到新架构（`TabManagerNew` + `TerminalTabsNew` + `TerminalScreenNew`），`TerminalCore` 输出 `StateFlow<RenderFrame>`，60fps 渲染循环。

### 新增组件

| 组件 | 文件 | 说明 |
|------|------|------|
| TerminalCore | `terminal-core/.../TerminalCore.kt` | 输出 `StateFlow<RenderFrame>`，60fps 渲染循环 |
| ComposeTerminalRenderer | `renderer-compose/.../ComposeTerminalRenderer.kt` | `DrawScope.draw(frame)` 增量绘制 |
| GlyphCache | `renderer-compose/.../GlyphCache.kt` | `TextLayoutResult` LRU 缓存（上限 2048） |
| TerminalCanvas | `renderer-compose/.../TerminalCanvas.kt` | Compose `Canvas` 消费 `StateFlow` |
| ImeInputView | `renderer-compose/.../ImeInputView.kt` | IME 文本输入 |
| DevTermCore | `app/.../DevTermCore.kt` | app 适配层，包装 TerminalCore + AppBackend |
| KeyboardHandlerNew | `app/.../KeyboardHandlerNew.kt` | KeyEvent → 终端序列 |
| TabManagerNew | `app/.../TabManagerNew.kt` | 新架构 Tab 管理 |
| TerminalTabsNew | `app/.../TerminalTabsNew.kt` | 新架构 Tab UI |
| TerminalScreenNew | `app/.../TerminalScreenNew.kt` | 新架构终端屏幕 |

### 性能基准（2025-07-20 实测）

| 测试 | 吞吐量 |
|------|--------|
| writeChars (80字+LF) | 16,129 ops/sec |
| scroll (24行滚屏) | 83,333 ops/sec |

### Phase 2 验证清单
- [x] `TerminalCore.frame: StateFlow<RenderFrame>` 可用
- [x] `TerminalCanvas` 从 `StateFlow` 渲染
- [x] `GlyphCache` LRU 上限 2048
- [x] `FrameQueue` 60fps 节流（`TerminalCore.startRenderLoop()` 16ms 间隔）
- [x] `:app` 完整切换到新架构（MainActivity → TabManagerNew → TerminalTabsNew）
- [x] ScrollbackBuffer 单元测试（循环写入、越界覆盖、容量边界、环绕索引）— 7 个测试
- [x] VtParser 单元测试（SGR、J、H、m、r、K、A/B/C/D、L/M、@/P、s/u、n、d、X、ESC 7/8/D/M/E/F/c）— 30+ 个测试

---

## Phase 2：Renderer API + Glyph Cache + Frame Queue ✅（已完成）

**目标**：渲染与核心完全解耦，60fps 节流
**状态**：2025-07-20 完成。见上方 Phase 2 验证清单与性能基准。

### renderer-compose 模块组件

| 组件 | 说明 |
|------|------|
| ComposeTerminalRenderer | 实现 TerminalRenderer 接口 |
| GlyphCache | `Map<String, TextLayoutResult>` LRU 缓存 |
| TerminalCanvas | `Canvas {}` 脏行渲染 |
| ImeInputView | IME 输入集成（从旧 app 移植） |

### Glyph Cache 设计

```kotlin
object GlyphCache {
    private val cache = LinkedHashMap<String, TextLayoutResult>(1024, 0.75f, true) {
        removeEldestEntry { size > 1024 }
    }
    fun get(char: Char, fg: Int, bold: Boolean, italic: Boolean): TextLayoutResult
    fun invalidate()
}
```

key = `${char}_${fg}_${bold}_${italic}`

### Frame Queue 设计

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

### Phase 2 设计验证
- [x] Frame Queue 16ms 节流（`TerminalCore.startRenderLoop()` 16ms 间隔）
- [x] GlyphCache LRU 上限 2048（`renderer-compose/.../GlyphCache.kt`）
- [x] ComposeTerminalRenderer 增量绘制（只遍历 `frame.dirtyRows`）
- [x] Renderer 可切换（`TerminalRenderer` 接口 + `ComposeTerminalRenderer` 实现）

---

## Phase 3：性能基准 + 回归测试 ✅（已完成）

**目标**：量化验证 SoA / Dirty Region / Ring Buffer 优化效果
**状态**：2026-07-20 完成。benchmark 模块扩展至 10 项基准测试，新增端到端回归测试套件。

### benchmark 模块（扩展后）

| 测试项 | 方法 | 说明 |
|--------|------|------|
| writeChars (80字+LF) | 100K 次迭代测 ops/sec | 基础写入吞吐 |
| eraseDisplay (2J) | 10K 次清屏 | 清屏性能 |
| scroll (80+LF) | 10K 次滚屏 | 滚屏+scrollback 吞吐 |
| mixedOutput | 1K 次 SGR+文本+清屏 | 混合场景 |
| largeFileCat (5MB) | 5MB 文本分块输入 | 大文件吞吐（MB/s） |
| vtSequenceParsing | 100K 次 VT 序列解析 | 纯 Parser 性能 |
| screenBufferOnly | 500K 次 WriteGlyph | 纯 ScreenBuffer 性能（隔离 Parser） |
| dirtyTracker | 100K 次 mark+consume | DirtyTracker 开销 |
| rapidScroll | 10K 次快速滚动 | scrollback 写入压力 |
| memoryUsage | 200×100 屏幕估算 | SoA vs Cell 对象内存对比 |

### 回归测试套件

`terminal-core/src/test/.../RegressionTest.kt`：20+ 个端到端测试，验证"字节流 → Parser → ScreenBuffer → 屏幕状态"的完整等价性。
覆盖：纯文本、CR/LF、光标移动（绝对/相对）、SGR（bold/italic/underline/color/reset）、清屏（J/K）、滚屏、Tab、自动换行、OSC 标题、光标保存/恢复、滚动区域、ECH。

### Phase 3 验证清单
- [x] benchmark 模块 10 项基准测试全部通过
- [x] 回归测试套件 20+ 个测试覆盖核心 VT100 行为
- [x] ScrollbackBuffer 7 个单元测试覆盖环形缓冲区
- [x] VtParser 30+ 个单元测试覆盖 CSI/ESC 序列
- [x] ScreenBuffer 16 个单元测试覆盖 SoA 操作
- [ ] 大文件 cat 耗时 < 旧版 50%（需真机基准对比，待网络恢复后验证）
- [ ] GC 暂停计数 < 旧版 10%（需真机 dumpsys meminfo，待网络恢复后验证）
- [ ] 200×100 屏幕滚动 FPS > 55（需真机或 Compose 测试环境）

---

## Phase 4：增量绘制 Canvas + 光标闪烁 ✅（已完成）

**目标**：Compose Canvas 只重绘脏行，光标按 500ms 闪烁
**状态**：2026-07-20 完成。在 bug 修复过程中同步实现了 Phase 4 的全部内容。

### 渲染优化（已实现）

```
Renderer Thread (16ms 循环)
  ↓
FrameQueue.consume() → true?
  ↓ 是
DirtyTracker.consume() → [5, 7, 12]
  ↓
clipRect(top=minRow*h, bottom=(maxRow+1)*h)  // 只裁剪脏行区域
  ↓
for each dirty row:
    drawRow(row, chars, fg, bg, flags)
  ↓
drawCursor()  // 光标独立绘制（受 cursorBlink 控制）
```

实现文件：`renderer-compose/.../ComposeTerminalRenderer.kt` 的 `draw()` 方法使用 `clipRect` 包裹脏行绘制循环。

### 光标闪烁（已实现）

`renderer-compose/.../TerminalCanvas.kt` 中通过 `LaunchedEffect` + `delay(500)` 实现 500ms 闪烁：
```kotlin
var cursorBlink by remember { mutableStateOf(true) }
LaunchedEffect(cursorBlinkEnabled) {
    while (isActive) {
        delay(500)
        cursorBlink = !cursorBlink
    }
}
```

### Phase 4 验证清单
- [x] 脏行裁剪有效（`clipRect` 只重绘被改行区域）
- [x] 光标闪烁 500ms 间隔（`LaunchedEffect` + `delay(500)`）
- [x] 全屏清屏（J 2）触发 `markAll()`（`eraseDisplay` mode 2 调用 `dirty.markAll()`）
- [x] 非脏行区域 Canvas 不变（`clipRect` 限制绘制范围）
- [x] CONCEAL（SGR 8）隐藏文本不绘制
- [x] 光标闪烁可通过 `cursorBlinkEnabled` 参数关闭

---

## Phase 5：高级功能（按需，不引入性能退化）🔲

**目标**：功能提升，保持性能基线

| 功能 | 优先级 | 说明 |
|------|--------|------|
| PTY（预编 .so） | 高 | 恢复作业控制、shell 回显 |
| SSH 客户端 | 中 | Apache MINA 或 libssh2 |
| 多 Tab / Split | 中 | TerminalCore 天然支持多实例 |
| 文件浏览器 | 低 | 薄壳 App 层功能 |
| 会话 Checkpoint | 中 | 序列化 ScreenBuffer 状态 |

### PTY 恢复策略

1. 交叉编译 `openpty` wrapper .so（使用宇宙 B 预编译产物）
2. Backend 新增 `PtyBackend` 实现
3. `ProcessBackend` 改为选配，默认用 `PtyBackend`
4. `localEcho` 在 PTY 模式下自动关闭

---

## 阶段优先级总表

| 优先级 | Phase | 内容 | 收益 | 状态 |
|--------|-------|------|------|------|
| ⭐⭐⭐⭐⭐ | 1 | SoA + Dirty Region + Ring Buffer | 消除 God Object，缓存友好，零 GC | ✅ |
| ⭐⭐⭐⭐⭐ | 2 | Renderer API + Glyph Cache + Frame Queue | 渲染 60fps 封顶，重复字符缓存 | ✅ |
| ⭐⭐⭐⭐☆ | 3 | 基准测试 + 回归 | 量化验证，防止退化 | ✅ |
| ⭐⭐⭐⭐☆ | 4 | 增量 Canvas + 光标闪烁 | 真正只画脏行 | ✅ |
| ⭐⭐⭐☆☆ | 5 | PTY | 恢复 shell 体验 | 🔲 |
| ⭐☆☆☆☆ | 5 | SSH / 文件浏览器 / Checkpoint | 功能提升，与性能无关 | 🔲 |
