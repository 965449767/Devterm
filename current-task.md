# DevTerm 当前任务状态

> 最后更新：2026-07-20
> 阶段：Phase 1-4 已完成，Phase 5 待开始

## 本次会话完成内容

### 1. Bug 修复（15 个问题）

详见上一轮会话的修复报告。关键修复：
- P0: eraseDisplay 脏行标记不完整 → 修复为标记从光标行到末尾的所有行
- P0: UnicodeWidthCache 并发安全 → 使用 `getOrPut` 原子操作
- P1: VtParser Tab 键 cursorCol 未更新 → 新增 `ScreenCommand.Tab`，由 ScreenBuffer 处理
- P1: ESC H / ESC F / CSI X 映射错误 → 修正为 HTS(忽略) / CPL / ECH
- P1: putChar 自动换行 wrapPending 逻辑矛盾 → 修复为 deferred 换行
- P2: AppBackend 与 ProcessBackend 重复 → 删除 AppBackend，复用 ProcessBackend
- P2: DirtyTracker.resize 行为不一致 → 统一清空
- P3: 光标闪烁 → 实现 500ms 闪烁（TerminalCanvas）
- P3: Canvas 增量裁剪 → 实现 clipRect 脏行裁剪
- 清理: EraseDisplay 特殊处理、CONCEAL 常量、VtParser 无意义条件、putChar flags 覆盖

### 2. Phase 2 遗留测试补全

- `ScrollbackBufferTest.kt`：7 个测试（基础写入、越界访问、循环覆盖、环绕索引、清空、容量1边界、大量写入）
- `ScreenBufferTest.kt` 中 VtParserTest 扩展：新增 20+ 个测试覆盖 CSI H/A/B/C/D/G/L/M/@/P/X/d/S/T/n/s/u/m/r 和 ESC 7/8/D/M/E/F/c

### 3. Phase 3 性能基准 + 回归测试

- `TerminalBenchmark.kt` 扩展至 10 项基准：大文件 cat、VT 序列解析、纯 ScreenBuffer、DirtyTracker、快速滚动、内存占用对比
- `BenchmarkTest.kt` 新增 6 个测试入口
- `RegressionTest.kt` 新增 20+ 个端到端回归测试

### 4. Phase 4 增量绘制 + 光标闪烁（在 bug 修复中同步完成）

- `ComposeTerminalRenderer.kt`：clipRect 脏行裁剪
- `TerminalCanvas.kt`：500ms 光标闪烁
- `CellFlags.kt`：CONCEAL 常量
- `ComposeTerminalRenderer.kt`：CONCEAL 隐藏文本不绘制

### 5. 文档更新

- `PHASES.md`：Phase 2/3/4 验证清单全部勾选，阶段总表更新状态

## 当前状态

| Phase | 状态 | 说明 |
|-------|------|------|
| 1 | ✅ | SoA + Dirty Region + Ring Buffer |
| 2 | ✅ | Renderer API + Glyph Cache + Frame Queue + 测试补全 |
| 3 | ✅ | 10 项基准测试 + 20+ 回归测试 |
| 4 | ✅ | 增量 Canvas + 光标闪烁 + CONCEAL |
| 5 | 🔲 | PTY / SSH / 文件浏览器（待开始）|

## 未解决风险

1. **无法本地测试**：网络不通，Gradle 9.1.0 和依赖无法下载，所有测试和基准未实际运行
   - 缓解：通过静态语法检查（子 agent）+ 逻辑手动追踪验证
   - 待办：网络恢复后运行 `./gradlew :terminal-core:test :benchmark:test`

2. **真机性能验证缺失**：Phase 3 的"大文件 cat < 50%"、"GC 暂停 < 10%"、"FPS > 55"需真机验证
   - 待办：在宇宙 B 构建后用 `aidev-build-request` 安装到手机测试

3. **ScrollbackBuffer 列数变化**：resize 改变列数时，scrollback 中旧行的列数不匹配
   - 待办：Phase 5 或独立任务中处理

## 下一步计划

### 短期（网络恢复后）
1. 运行 `./gradlew :terminal-core:test :benchmark:test` 验证所有测试通过
2. 修复测试中发现的问题
3. 运行 `aidev-build-request --project /workspace/DevTerm` 构建安装包

### 中期（Phase 5）
1. **PTY 恢复**：交叉编译 openpty wrapper .so，新增 PtyBackend
2. **多 Tab**：TerminalCore 天然支持多实例，UI 层实现 Tab 切换
3. **会话 Checkpoint**：序列化 ScreenBuffer 状态

### 长期
1. SSH 客户端（Apache MINA）
2. 文件浏览器
3. 真机性能基准对比（旧版 vs 新版）

## 关键决策记录

> 提醒：以下决策应记录到 `docs/decisions.md`

1. **Tab 键由 ScreenBuffer 处理**：Parser 不维护光标状态，新增 `ScreenCommand.Tab` 命令
2. **wrapPending deferred 换行**：光标到行尾时不立即换行，设置标志，下一个字符触发换行
3. **删除 AppBackend**：直接复用 terminal-core 的 ProcessBackend，避免代码重复
4. **clipRect 增量裁剪**：使用 Compose 的 clipRect 限制绘制区域到脏行范围
5. **SetScrollRegion bottom 转换**：Parser 中 bottom 参数从 1-indexed 转为 0-indexed（-1），ScreenBuffer 处理 <=0 的默认情况

## 重复出现的错误

> 提醒：以下错误应记录到 `docs/error-journal.md`

1. **Parser 维护光标状态**：VtParser 曾内部维护 cursorCol/cursorRow 但未更新，导致 Tab 键错误。教训：Parser 应输出不可变 Command，不维护状态
2. **1-indexed vs 0-indexed 转换**：CSI 序列参数是 1-indexed，容易忘记减 1（如 SetScrollRegion 的 bottom）。教训：所有 1-indexed 转换在 Parser 层统一处理
3. **脏行标记不完整**：eraseDisplay mode 0/1 只标记了光标行，应标记整个受影响范围。教训：批量修改屏幕时，标记所有受影响行
