# DevTerm Terminal Engine - 项目交接文档

## 一、项目概述

**DevTerm** 是一个 Android 终端引擎，定位为 Android 上性能最强的终端模拟器。采用模块化架构，分为：
- `terminal-core/` - 纯净 Kotlin，零 Android 依赖（核心引擎）
- `renderer-compose/` - Compose Canvas 渲染实现
- `benchmark/` - 性能基准测试
- `app/` - 薄壳 App

**项目状态**：Phase 1-4 已完成，代码质量良好，测试覆盖率高。

**远程仓库**：`https://github.com/965449767/Devterm.git`

---

## 二、已实现功能清单

### 2.1 核心引擎（terminal-core）

| 功能 | 状态 | 文件 |
|------|------|------|
| SoA 屏幕缓冲区 | ✅ | [ScreenBuffer.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/screen/ScreenBuffer.kt) |
| Dirty Region 脏行追踪 | ✅ | [DirtyTracker.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/screen/DirtyTracker.kt) |
| Scrollback Ring Buffer | ✅ | [ScrollbackBuffer.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/scrollback/ScrollbackBuffer.kt) |
| VT100/xterm 解析器 | ✅ | [VtParser.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/parser/VtParser.kt) |
| OSC 标题传递 | ✅ | [TerminalCore.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/TerminalCore.kt) |
| 制表位系统（Tab stops） | ✅ | ScreenBuffer.kt |
| Bell 响铃回调 | ✅ | ScreenBuffer.kt |
| 备用屏幕（Alternate Screen） | ✅ | ScreenBuffer.kt |
| 光标样式（DECSCUSR） | ✅ | ScreenBuffer.kt |
| 括号粘贴模式 | ✅ | ScreenBuffer.kt |
| 光标保存/恢复（含 SGR） | ✅ | ScreenBuffer.kt |
| CSI 查询响应（6n/5n） | ✅ | TerminalCore.kt |
| Unicode 宽度缓存 | ✅ | [UnicodeWidth.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/unicode/UnicodeWidth.kt) |
| Frame Queue 60fps 节流 | ✅ | [FrameQueue.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/renderer/FrameQueue.kt) |
| Backend 能力抽象 | ✅ | [BackendCapabilities.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/backend/BackendCapabilities.kt) |

### 2.2 渲染层（renderer-compose）

| 功能 | 状态 | 文件 |
|------|------|------|
| Compose Canvas 渲染 | ✅ | [ComposeTerminalRenderer.kt](file:///workspace/renderer-compose/src/main/kotlin/com/devterm/terminal/renderer/ComposeTerminalRenderer.kt) |
| GlyphCache 缓存 | ✅ | [GlyphCache.kt](file:///workspace/renderer-compose/src/main/kotlin/com/devterm/terminal/renderer/GlyphCache.kt) |
| 动态字体大小 | ✅ | [TerminalCanvas.kt](file:///workspace/renderer-compose/src/main/kotlin/com/devterm/terminal/renderer/TerminalCanvas.kt) |
| 三种光标样式 | ✅ | ComposeTerminalRenderer.kt |
| IME 输入支持 | ✅ | [ImeInputView.kt](file:///workspace/renderer-compose/src/main/kotlin/com/devterm/terminal/renderer/ImeInputView.kt) |

### 2.3 App 层

| 功能 | 状态 | 文件 |
|------|------|------|
| 主题系统（4 种 Catppuccin） | ✅ | [TerminalTheme.kt](file:///workspace/app/src/main/java/com/devterm/ui/theme/TerminalTheme.kt) |
| 设置界面 | ✅ | [SettingsScreen.kt](file:///workspace/app/src/main/java/com/devterm/ui/settings/SettingsScreen.kt) |
| 设置持久化（SharedPreferences） | ✅ | [AppSettings.kt](file:///workspace/app/src/main/java/com/devterm/ui/settings/AppSettings.kt) |
| Tab 多标签 | ✅ | [TabManagerNew.kt](file:///workspace/app/src/main/java/com/devterm/terminal/TabManagerNew.kt) |
| OSC 标题同步到 Tab | ✅ | TabManagerNew.kt |
| 键盘事件处理 | ✅ | [KeyboardHandlerNew.kt](file:///workspace/app/src/main/java/com/devterm/terminal/KeyboardHandlerNew.kt) |
| BackendFactory | ✅ | [BackendFactory.kt](file:///workspace/app/src/main/java/com/devterm/terminal/BackendFactory.kt) |
| PtyBackend 骨架 | ✅ | [PtyBackend.kt](file:///workspace/app/src/main/java/com/devterm/terminal/PtyBackend.kt) |

---

## 三、未实现功能清单

### 3.1 核心功能（需要网络/编译）

| # | 功能 | 依赖条件 | 说明 |
|---|------|----------|------|
| 1 | **libpty.so 交叉编译** | Android NDK 或 `aarch64-linux-gnu-gcc` | PTY 功能的核心 native 代码 |
| 2 | **PTY 真机测试** | 编译后的 libpty.so | PtyBackend 的实际功能验证 |
| 3 | **SSH 客户端** | JSch 或 Apache MINA SSHD 库 | 需要网络下载依赖 |
| 4 | **CI/CD** | GitHub Actions | 需要网络配置 |

### 3.2 核心功能（纯代码，可立即实现）

| # | 功能 | 说明 | 建议优先级 |
|---|------|------|------------|
| 5 | **文本选择/复制** | 手势检测 + 选择区域高亮 + ClipboardManager | 高 |
| 6 | **滑动滚动** | 手势检测 + scrollback 区域计算 + 滑动距离转换 | 高 |
| 7 | **搜索功能** | 在 scrollback 历史中搜索关键字 | 中 |
| 8 | **鼠标支持** | 通过 TouchEvent 模拟鼠标点击/拖拽 | 中 |
| 9 | **焦点跟踪** | 跟踪当前焦点窗口（tmux/screen） | 低 |

### 3.3 UI 功能（纯代码，可立即实现）

| # | 功能 | 说明 | 建议优先级 |
|---|------|------|------------|
| 10 | **快捷键提示** | 显示常用快捷键列表 | 低 |
| 11 | **关于页面** | 显示版本信息和开源协议 | 低 |
| 12 | **导出日志** | 将终端输出导出到文件 | 低 |

---

## 四、提前写好的测试（等待实现）

### 4.1 PtyBackendTest

**文件**：[PtyBackendTest.kt](file:///workspace/app/src/androidTest/kotlin/com/devterm/PtyBackendTest.kt)

**等待条件**：`libpty.so` 编译完成并打包到 APK

**测试内容**：

| 测试方法 | 说明 | 跳过条件 |
|----------|------|----------|
| `testPtyAvailability` | 检查 PTY 是否可用 | 无 |
| `testPtyCapabilities` | 验证 BackendCapabilities | PTY 不可用时跳过 |
| `testPtyProcessStart` | shell 进程启动并接收输出 | PTY 不可用时跳过 |
| `testPtyResize` | 窗口大小变更 | PTY 不可用时跳过 |
| `testPtyWrite` | echo 命令输出验证 | PTY 不可用时跳过 |
| `testPtySignal` | Ctrl+C 信号发送 | PTY 不可用时跳过 |
| `testPtyStop` | 多次 stop 不抛异常 | PTY 不可用时跳过 |
| `testBackendFactoryPrefersPty` | BackendFactory 优先选择 PTY | PTY 不可用时跳过 |

**运行命令**：
```sh
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devterm.PtyBackendTest
```

---

## 五、关键设计决策

### 5.1 架构分层

```
┌─────────────────────────────────────────────────────┐
│  Compose UI (app/)                                  │
│  MainActivity → TerminalTabsNew → TerminalScreenNew │
├─────────────────────────────────────────────────────┤
│  Renderer (renderer-compose/)                       │
│  ComposeTerminalRenderer + GlyphCache + FrameQueue  │
├─────────────────────────────────────────────────────┤
│  ScreenBuffer + TerminalCore (terminal-core/)       │
│  SoA: CharArray + IntArray×2 + ByteArray            │
│  DirtyTracker: BitSet 标记脏行                       │
│  Scrollback: RingBuffer (50000 行预分配)            │
├─────────────────────────────────────────────────────┤
│  Parser (terminal-core/)                            │
│  VT100/xterm 状态机 → ScreenCommand sealed class    │
├─────────────────────────────────────────────────────┤
│  Backend (terminal-core/)                           │
│  ProcessBuilder / PTY (待实现) / SSH (待实现)       │
└─────────────────────────────────────────────────────┘
```

### 5.2 ScreenCommand 模式

Parser 不直接操作屏幕，输出不可变 Command，由 ScreenBuffer 执行：

```kotlin
sealed class ScreenCommand {
    data class WriteGlyph(val c: Char, val width: Int) : ScreenCommand()
    data class MoveCursor(val row: Int, val col: Int) : ScreenCommand()
    data class SetSgr(val params: List<Int>) : ScreenCommand()
    object Tab : ScreenCommand()
    object Bell : ScreenCommand()
    data class SetPrivateMode(val mode: Int, val set: Boolean) : ScreenCommand()
    data class SetCursorStyle(val style: Int) : ScreenCommand()
    // ...
}
```

### 5.3 SoA 数据结构

不创建 Cell 对象数组，使用平行数组：

```kotlin
class ScreenBuffer(width: Int, height: Int) {
    val chars = CharArray(width * height)  // ' ' 初始化
    val fg = IntArray(width * height)      // ARGB 打包
    val bg = IntArray(width * height)
    val flags = ByteArray(width * height)  // bold, italic, blink, width 等位标记
}
```

### 5.4 Backend 能力抽象

通过 `BackendCapabilities` 描述不同 Backend 的能力：

```kotlin
data class BackendCapabilities(
    val isPty: Boolean = false,
    val needsLocalEcho: Boolean = true,
    val supportsSignals: Boolean = false,
    val supportsResize: Boolean = false,
    val supportsColor: Boolean = false,
)
```

---

## 六、已修复的 Bug

| # | Bug | 修复文件 |
|---|-----|----------|
| 1 | Tab 键使用未维护的 cursorCol | [VtParser.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/parser/VtParser.kt) |
| 2 | ESC H 映射错误 | VtParser.kt |
| 3 | ESC F 映射错误 | VtParser.kt |
| 4 | CSI X 映射错误 | VtParser.kt |
| 5 | eraseDisplay mode 0/1 脏行标记不完整 | [ScreenBuffer.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/screen/ScreenBuffer.kt) |
| 6 | putChar 自动换行逻辑与 wrapPending 矛盾 | ScreenBuffer.kt |
| 7 | UnicodeWidthCache 并发安全问题 | [UnicodeWidth.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/unicode/UnicodeWidth.kt) |
| 8 | DirtyTracker.resize 行为不一致 | [DirtyTracker.kt](file:///workspace/terminal-core/src/main/kotlin/com/devterm/terminal/core/screen/DirtyTracker.kt) |
| 9 | 保存/恢复光标不包含 SGR 状态 | ScreenBuffer.kt |
| 10 | reset() 不重置制表位和备用屏幕状态 | ScreenBuffer.kt |

---

## 七、测试套件清单

### 7.1 terminal-core 测试

| 文件 | 测试内容 | 数量 |
|------|----------|------|
| [ScreenBufferTest.kt](file:///workspace/terminal-core/src/test/kotlin/com/devterm/terminal/core/ScreenBufferTest.kt) | 基础屏幕操作 | ~20 |
| [VtParserTest.kt](file:///workspace/terminal-core/src/test/kotlin/com/devterm/terminal/core/VtParserTest.kt) | VT100/xterm 序列解析 | ~50 |
| [ScreenBufferAdvancedTest.kt](file:///workspace/terminal-core/src/test/kotlin/com/devterm/terminal/core/ScreenBufferAdvancedTest.kt) | 备用屏幕/光标样式/括号粘贴/制表位 | ~30 |
| [UnicodeWidthTest.kt](file:///workspace/terminal-core/src/test/kotlin/com/devterm/terminal/core/UnicodeWidthTest.kt) | 字符宽度计算 | 15 |
| [ScrollbackBufferTest.kt](file:///workspace/terminal-core/src/test/kotlin/com/devterm/terminal/core/ScrollbackBufferTest.kt) | 滚动历史 | ~10 |
| [RegressionTest.kt](file:///workspace/terminal-core/src/test/kotlin/com/devterm/terminal/core/RegressionTest.kt) | 回归测试 | ~10 |

### 7.2 benchmark 测试

| 文件 | 测试内容 |
|------|----------|
| [BenchmarkTest.kt](file:///workspace/benchmark/src/test/kotlin/com/devterm/benchmark/BenchmarkTest.kt) | 性能基准 |

### 7.3 app 测试（待运行）

| 文件 | 测试内容 | 运行条件 |
|------|----------|----------|
| [PtyBackendTest.kt](file:///workspace/app/src/androidTest/kotlin/com/devterm/PtyBackendTest.kt) | PTY 后端 | 需要 libpty.so |

---

## 八、构建命令

```sh
# 多模块编译
./gradlew :terminal-core:build :renderer-compose:build :app:assembleDebug

# 运行单元测试
./gradlew :terminal-core:test :renderer-compose:test

# 运行所有测试
./gradlew test

# 性能基准
./gradlew :benchmark:test --benchmark

# 提交宇宙B构建安装
aidev-build-request --project /workspace/DevTerm

# 查看构建日志（手机上）
cat /sdcard/AIDev/last-build.log
```

---

## 九、开发原则

1. **版本号不动**：Gradle 9.1.0、AGP 9.0.1、Kotlin 2.0.21、compileSdk 36、minSdk 26
2. **不写模块级 `repositories {}`**：统一在 `settings.gradle.kts`
3. **不改 `gradle-wrapper.properties`、`local.properties`、`settings.gradle.kts` 仓库块、`gradle.properties` 的 aapt2 设置**
4. **所有项目建在 `/workspace/` 下**
5. **原生代码限制**：仅允许在 `app/src/main/cpp/` 目录下添加 C 代码用于 PTY 实现
6. **terminal-core 模块不允许依赖 Android / Compose / 任何 UI 框架**

---

## 十、下一步建议

### 10.1 立即处理（高优先级）

1. **交叉编译 libpty.so**：安装 Android NDK 或 `aarch64-linux-gnu-gcc`，编译 [app/src/main/cpp/pty.c](file:///workspace/app/src/main/cpp/pty.c)
2. **运行 PtyBackendTest**：验证 PTY 功能是否正常
3. **实现文本选择/复制**：添加手势检测和剪贴板操作

### 10.2 中期处理（中优先级）

4. **实现滑动滚动**：支持 scrollback 历史浏览
5. **集成 SSH 客户端**：添加 JSch 依赖，实现 SSH 连接
6. **实现搜索功能**：在 scrollback 中搜索

### 10.3 长期处理（低优先级）

7. **CI/CD 配置**：GitHub Actions 自动构建测试
8. **UI 测试**：添加 Compose UI 测试
9. **国际化**：字符串资源化

---

## 十一、注意事项

### 11.1 SELinux execve 禁令（Xiaomi/MIUI）

通过 shell 函数 + `/system/bin/linker64` 加载 node 二进制，策略不变。

### 11.2 PTY 缺失的影响

无 Ctrl+C/Z 等作业控制信号，shell 回显由 `localEcho` 补偿。通过 `.so` 恢复。

### 11.3 旧代码标记

`TabManager`、`TerminalTabs`、`TerminalScreen` 已标记 `@Deprecated`，建议后续删除。

---

**文档生成时间**：2026-07-20  
**代码版本**：`b0314f5`  
**最后修改者**：AI 助手
