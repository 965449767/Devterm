# DevTerm Terminal Engine — 项目规则

## 项目定位

DevTerm 是一个 **Android 终端引擎**，定位为 Android 上性能最强的终端模拟器。
不是"一个终端 App"，而是"一个终端 Core，别人可以嵌入"。

```
devterm/
├── terminal-core/       # 纯净 Kotlin，零 Android 依赖
├── renderer-compose/    # Compose Canvas 渲染实现
├── benchmark/           # 性能基准测试
└── app/                 # 薄壳 App
```

## 架构

```
┌─────────────────────────────────────────────────────┐
│  Compose UI                                          │
│  collectAsState → Canvas { drawText }                │
├─────────────────────────────────────────────────────┤
│  Renderer (renderer-compose)                         │
│  GlyphCache + DirtyLineIterator + FrameQueue @60fps  │
├─────────────────────────────────────────────────────┤
│  ScreenBuffer + TerminalCore (terminal-core)         │
│  1D SoA: CharArray + IntArray×2 + ByteArray          │
│  DirtyTracker: BitSet 标记脏行                        │
│  Scrollback: RingBuffer (50000 行预分配)              │
├─────────────────────────────────────────────────────┤
│  Parser (terminal-core)                              │
│  VT100/xterm 状态机 → ScreenCommand sealed class     │
├─────────────────────────────────────────────────────┤
│  Backend (terminal-core)                             │
│  ProcessBuilder / (未来) PTY / SSH                   │
└─────────────────────────────────────────────────────┘
```

### 关键设计决策

#### ScreenCommand：Parser 不直接操作屏幕
Parser 输出不可变 Command，由 ScreenBuffer 执行：
```kotlin
sealed class ScreenCommand {
    data class WriteGlyph(val c: Char, val width: Int) : ScreenCommand()
    data class MoveCursor(val row: Int, val col: Int) : ScreenCommand()
    data class EraseDisplay(val mode: Int) : ScreenCommand()
    data class EraseLine(val mode: Int) : ScreenCommand()
    object ScrollUp : ScreenCommand()
    object CarriageReturn : ScreenCommand()
    data class SetSgr(...) : ScreenCommand()
    // ...
}
```

这样 Renderer (Compose, Skia, GPU) 可随意更换，Parser 完全不感知。

#### SoA（Struct of Arrays）代替 Cell 对象
不创建 `Cell` 对象数组，改为平行数组：
```kotlin
class ScreenBuffer(width: Int, height: Int) {
    val chars = CharArray(width * height)  // ' ' 初始化
    val fg = IntArray(width * height)      // ARGB 打包
    val bg = IntArray(width * height)
    val flags = ByteArray(width * height)  // bold, italic, blink, width 等位标记
}
```

- **连续内存** → CPU cache 命中率高
- **零 GC 压力** → 无对象分配/回收
- **索引计算**：`index = row * cols + col`

#### Dirty Region 替代全量 Snapshot
不再复制全部 Cell 生成 Snapshot，改为追踪脏行：
```kotlin
class DirtyTracker {
    private val dirty = BitSet()   // 标记脏行号
    fun mark(row: Int)             // 行内容变更
    fun markAll()                  // resize 或清屏
    fun consume(): List<Int>       // 取出并清除所有脏行
}
```

Renderer 只重绘脏行 + 光标。

#### Scrollback Ring Buffer
预分配固定容量循环数组，零分配：
```kotlin
class ScrollbackBuffer(capacity: Int = 50000) {
    private val lines = arrayOfNulls<ScreenLine>(capacity)
    private var head = 0
    private var count = 0
}
```

#### Frame Queue + 60fps 渲染节流
Parser 不直接通知 Compose，而是推送到 Frame Queue：
```kotlin
class FrameQueue {
    private val pending = atomic(0)
    fun notifyDirty()         // Parser 调用
    fun consume(): Boolean    // Renderer 以 16ms 间隔轮询
}
```

最高 60fps，杜绝万次/秒的 `emitSnapshot()`。

#### Unicode Width Cache
高频率使用的字符宽度查询，第一次计算后缓存：
```kotlin
object UnicodeWidthCache {
    private val cache = ConcurrentHashMap<Int, Byte>()
    fun width(codepoint: Int): Byte =
        cache.getOrPut(codepoint) { computeWidth(codepoint) }
}
```

### 保留的设计决策（从旧版继承）

#### 为什么不用 JNI / C 代码？
宇宙 B 编译器运行在 ARM64 Linux 上，但 NDK / cmake 工具链仅为 x86_64 架构，且 QEMU binfmt_misc 未注册。
因此全部用纯 Kotlin——但新的 SoA + Dirty Region + Ring Buffer 架构让纯 Kotlin 性能接近原生。

#### SELinux `execve` 禁令（Xiaomi/MIUI）
通过 shell 函数 + `/system/bin/linker64` 加载 node 二进制。策略不变。

#### PTY 缺失的影响
无 Ctrl+C/Z 等作业控制信号，shell 回显由 `localEcho` 补偿。
未来通过 `.so` 恢复（Phase 5），架构上 PTY 只是 Backend 的另一种实现。

## 目录结构

```
/workspace/DevTerm/
├── AGENTS.md
├── ARCHITECTURE.md
├── PHASES.md
├── REFERENCES.md
├── docs/
│   ├── terminal-core-design.md
│   ├── data-flow.md
│   └── renderer-api.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── terminal-core/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/devterm/terminal/core/
│       ├── parser/
│       │   ├── VtParser.kt           # VT100/xterm 状态机（从旧 TerminalEmulator 剥离）
│       │   ├── ScreenCommand.kt      # Command sealed class
│       │   └── OscHandler.kt         # OSC 序列处理
│       ├── screen/
│       │   ├── ScreenBuffer.kt       # 1D SoA 缓冲区
│       │   ├── DirtyTracker.kt       # BitSet 脏行追踪
│       │   ├── CellFlags.kt          # 位标记常量
│       │   └── CursorState.kt        # 光标状态
│       ├── scrollback/
│       │   └── ScrollbackBuffer.kt   # RingBuffer
│       ├── unicode/
│       │   └── UnicodeWidth.kt       # Width Cache
│       ├── renderer/
│       │   ├── TerminalRenderer.kt   # 接口
│       │   ├── RenderFrame.kt        # 增量帧数据
│       │   └── FrameQueue.kt         # 60fps 节流
│       ├── TerminalCore.kt           # 协调器
│       └── backend/
│           ├── Backend.kt            # 抽象后端
│           ├── ProcessBackend.kt     # ProcessBuilder 实现
│           └── TerminalSession.kt    # 3 线程 I/O
├── renderer-compose/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/devterm/terminal/renderer/
│       ├── ComposeTerminalRenderer.kt
│       ├── GlyphCache.kt             # TextLayoutResult 缓存
│       ├── TerminalCanvas.kt         # Canvas draw
│       └── ImeInputView.kt           # IME 输入
├── benchmark/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/devterm/benchmark/
│       └── TerminalBenchmark.kt
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── java/com/devterm/
        │   ├── MainActivity.kt       # 启动 + 设置
        │   ├── service/
        │   │   └── TerminalService.kt # ForegroundService
        │   └── ui/theme/
        │       └── Theme.kt          # Catppuccin Mocha
        └── res/
            ├── values/strings.xml
            └── values/themes.xml
```

## 开发原则

1. **版本号不动**：Gradle 9.1.0、AGP 9.0.1、Kotlin 2.0.21、compileSdk 36、minSdk 26
2. **不写模块级 `repositories {}`**：统一在 `settings.gradle.kts`
3. **不改 `gradle-wrapper.properties`、`local.properties`、`settings.gradle.kts` 仓库块、`gradle.properties` 的 aapt2 设置**
4. **所有项目建在 `/workspace/` 下**
5. **不引入原生代码**
6. **terminal-core 模块不允许依赖 Android / Compose / 任何 UI 框架**

## 构建命令

```sh
# 多模块编译
./gradlew :terminal-core:build :renderer-compose:build :app:assembleDebug

# 运行单元测试
./gradlew :terminal-core:test :renderer-compose:test

# 性能基准
./gradlew :benchmark:test --benchmark

# 提交宇宙B 构建安装
aidev-build-request --project /workspace/DevTerm

# 查看构建日志（手机上）
cat /sdcard/AIDev/last-build.log
```

## 阶段路线

| Phase | 内容 | 目标 | 状态 |
|-------|------|------|------|
| 1 ✅ | 模块拆分 + SoA ScreenBuffer + Dirty Region + Ring Buffer | 重构引擎核心，消除 God Object | 已完成 |
| 2 ✅ | Renderer API + Glyph Cache + Frame Queue | 渲染隔离，60fps 节流 | 已完成 |
| 3 🔲 | 性能基准 + 回归测试 | 验证 SoA / Dirty Region / Ring Buffer 优化效果 | 待开始 |
| 4 🔲 | 增量绘制 Canvas + 光标闪烁 | Compose 管片只重绘脏行 | 待开始 |
| 5 🔲 | PTY / SSH / 文件浏览器（按需） | 功能提升，不引入性能退化 | 待开始 |

## 参考项目

- **Ghostty**：Command 架构 + SoA + Dirty Region 思想来源
- **Alacritty**：纯 Rust 终端，性能标杆
- **Rio**：GPU 终端，Frame-based 渲染参考
- **xterm.js**：前端终端引擎，分层架构参考
- **ConnectBot termlib**：Compose Canvas 渲染模式
- **Termux**：PTY + ByteQueue + 3 线程 I/O 模式
