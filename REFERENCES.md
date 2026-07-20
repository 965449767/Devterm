# DevTerm 参考资源

## 架构参考项目

### Ghostty（核心架构思想来源）
- **仓库**: https://github.com/ghostty-org/ghostty
- **关键借鉴**:
  - **Command 架构**：Parser 输出 `TerminalCommand`，Screen 执行
  - **SoA（Struct of Arrays）**：连续内存代替 Cell 对象
  - **Dirty Region**：BitSet 追踪脏行，Renderer 只重绘变化部分
  - **Frame-based 渲染**：16ms 窗口合并渲染请求，不逐字刷新
  - **分层设计**：`libghostty` 可嵌入不同 UI（macOS/GTK/Headless）
- **参考价值**: 当前 DevTerm 架构调整的第一参考

### Alacritty（性能标杆）
- **仓库**: https://github.com/alacritty/alacritty
- **关键借鉴**:
  - Rust 实现，纯 CPU 渲染也达到极高性能
  - 专用 PTY 线程 + 专用渲染线程
  - Vi-mode 内容选择
- **参考价值**: 验证 SoA + Dirty Region 路线的正确性

### Rio（GPU 终端）
- **仓库**: https://github.com/raphamorim/rio
- **关键借鉴**:
  - GPU 加速渲染（WebGPU）
  - Frame-based 渲染节流
  - Ligature 字体支持
- **参考价值**: 未来 GPU 渲染方向参考

### xterm.js（前端终端引擎）
- **仓库**: https://github.com/xtermjs/xterm.js
- **关键借鉴**:
  - Parser → Screen → Renderer 三层解耦
  - 可插件化的 Addon 系统
  - 字节流 + 事件驱动的 VT 解析
- **参考价值**: 前端架构类比

### ConnectBot termlib（Compose Canvas 渲染模式）
- **仓库**: https://github.com/pepperpepperpepper/termlib
- **关键借鉴**:
  - `Canvas {}` 渲染循环（Phase 1 原型参考）
  - 损伤回调 → Handler.post → StateFlow 发射
- **参考价值**: Compose Canvas 渲染模式参考

### Termux terminal-emulator（PTY + 进程管理）
- **仓库**: https://github.com/termux/termux-app
- **关键借鉴**:
  - `ByteQueue.java`：线程安全环形缓冲区
  - 3 线程 I/O 模型（Reader/Writer/Waiter）
  - PTY 创建逻辑（`termux.c` 242 行）
- **参考价值**: Backend 层 PTY 实现参考

### Rin Terminal（Rust PTY + Compose）
- **仓库**: https://github.com/pavelc4/Rin
- **关键借鉴**: Rust `portable-pty` crate + 30fps 轮询渲染
- **参考价值**: 替代 PTY 实现的备选路线

### Termi（Checkpoint/Restore）
- **仓库**: https://github.com/MannanSaood/termi
- **关键借鉴**: Rust `CheckpointManager` 30s 间隔序列化终端状态
- **核心理念**: "Expect to die, not hope to survive"

## 性能基准

### 终端渲染速度（大文件 cat 测试）
| 终端 | 5.5MB 文件耗时 | 说明 |
|------|---------------|------|
| Jackpal | 1.3s | 旧版基准 |
| Termux | 67s | 慢 50x |
| tmux (under Termux) | 1.3s | 瓶颈在 Termux 渲染层 |

来源: https://github.com/termux/termux-app/issues/603

### Emacs 性能（Pixel 9 Pro XL）
| 环境 | Benchmark 总分 | 说明 |
|------|---------------|------|
| Termux emacs | 68.62s | 无 native-comp, Bionic libc |
| AVF VM Linux | 33.22s | 完整 glibc + native-comp, 2x 快 |

来源: https://paste.sr.ht/~gnomon/72d1410af2a280b3c9da539b64f1bc4850faff57

### Samsung 后台大核（OneUI 8）
- 普通后台：4 核可用，吞吐量低
- NotificationListenerService 绑定：6 核可用，提升 50%

来源: https://github.com/termux/termux-app/issues/5086

### Clang 编译速度（Termux vs 静态链接）
- Hello World: Termux 1.0s, 静态 0.85s（15% 提升）
- fmt 库: Termux 3:30, 静态 2:20（33% 提升）

来源: https://github.com/termux/termux-packages/issues/27004

## SoA（Struct of Arrays）参考

- **Ghostty src/screen/Grid.zig**: SoA 连续内存屏幕缓冲区
- **Intel ISPC 文档**: SoA vs AoS 性能对比
- **Game Programming Patterns（数据局部性章节）**: 连续内存 = 高速缓存

## Unicode Width 参考

- **Unicode East Asian Width**: https://www.unicode.org/reports/tr11/
- **wcwidth(3)**: POSIX 字符宽度函数
- **xterm.js UnicodeWidth.ts**: 缓存 + 分级回退

## VT100 / ANSI Escape Sequences

- **VT100 User Guide**: https://vt100.net/docs/vt100-ug/chapter3.html
- **xterm ctlseqs**: https://invisible-island.net/xterm/ctlseqs/ctlseqs.html
- **Termbench**: https://github.com/mintty/termbench

## Compose Canvas

- **官方文档**: https://developer.android.com/jetpack/compose/graphics/draw
- **TextMeasurer**: https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextMeasurer

## Android 终端开发

- **ForegroundService**: https://developer.android.com/develop/background-work/services/foreground-service
- **AVF**: https://source.android.com/docs/core/virtualization
- **Node.js Android 交叉编译**: Node.js 源码 `android-configure`
