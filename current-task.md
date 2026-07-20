# DevTerm 当前任务状态

> 最后更新：2026-07-20
> 阶段：Phase 1-4 已完成，Phase 5 架构准备已完成

## 本次会话完成内容

### Phase 5 架构准备（PTY 恢复第一阶段）

本次完成了 PTY 恢复的**架构层面**工作，为未来真正的 PTY 实现打好基础。

#### 1. Backend 能力抽象

- **新建** `BackendCapabilities.kt`：数据类描述 Backend 能力（isPty、needsLocalEcho、supportsSignals、supportsResize、supportsColor）
- **修改** `Backend.kt`：接口添加 `val capabilities: BackendCapabilities`
- 提供 `PIPE` 和 `PTY` 两个预设常量

#### 2. ProcessBackend 改进

- 实现 `capabilities = BackendCapabilities.PIPE`
- 移除 `!!` 强制非空断言（符合编码规范）
- 完善文档说明管道模式的限制

#### 3. PtyBackend 骨架（未来 .so 实现的接入点）

- **新建** `PtyBackend.kt`：完整的 PTY Backend 骨架
- 5 个 `external fun` 声明（nativeCreatePty、nativeSetWindowSize、nativeKillChild、nativeClosePty、nativeWaitForChild）
- `isAvailable()` 检测 libpty.so 是否可加载
- 当前 `nativeCreatePty` 无实现，会返回 false，调用方自动 fallback 到 ProcessBackend

#### 4. BackendFactory 工厂模式

- **新建** `BackendFactory.kt`：统一 Backend 创建入口
- 自动检测 PTY 可用性，选择 PtyBackend 或 ProcessBackend
- 封装环境变量构建逻辑（TERM、HOME、PATH 等）

#### 5. KeyboardHandlerNew 改进

- 明确处理 Ctrl+A~Z → \x01-\x1A 控制字符
- 修复原来的死代码（`seq.firstOrNull()` 分支永远不会匹配 'a'..'z'）
- 新增 `needsLocalEcho` 属性，供 UI 层查询
- 清理转义序列（DEL 键从 `\u001b\u007F` 改为正确的 `\u007F`）

#### 6. DevTermCore/TabManagerNew 接入

- DevTermCore 暴露 `capabilities` 属性
- resize 时同步通知 Backend（`session?.resize()`）
- TabManagerNew 使用 BackendFactory 创建 Backend
- KeyboardHandler 接收 capabilities 参数

### 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `terminal-core/.../BackendCapabilities.kt` | 新建 | 能力数据类 |
| `terminal-core/.../Backend.kt` | 修改 | 接口添加 capabilities |
| `terminal-core/.../ProcessBackend.kt` | 修改 | 实现 capabilities，移除 `!!` |
| `app/.../PtyBackend.kt` | 新建 | PTY 骨架，待 .so 实现 |
| `app/.../BackendFactory.kt` | 新建 | Backend 工厂 |
| `app/.../KeyboardHandlerNew.kt` | 重写 | Ctrl 键映射，needsLocalEcho |
| `app/.../DevTermCore.kt` | 修改 | 暴露 capabilities，resize 同步 |
| `app/.../TabManagerNew.kt` | 修改 | 使用 BackendFactory |

## 当前状态

| Phase | 状态 | 说明 |
|-------|------|------|
| 1 | ✅ | SoA + Dirty Region + Ring Buffer |
| 2 | ✅ | Renderer API + Glyph Cache + Frame Queue + 测试补全 |
| 3 | ✅ | 10 项基准测试 + 50+ 单元测试 + 20+ 回归测试 |
| 4 | ✅ | 增量 Canvas + 光标闪烁 + CONCEAL |
| 5a | ✅ | PTY 架构准备（BackendCapabilities + PtyBackend 骨架 + Factory） |
| 5b | 🔲 | libpty.so 交叉编译（需要 NDK 环境） |
| 5c | 🔲 | libpty.so 接入测试（真机验证） |

### 代码合并状态
- ✅ `trae/agent-dgPtkf` 已合并到 `main` 分支
- ✅ 合并结果已推送到远程仓库 `origin/main`
- ✅ 合并提交：`14554af`（包含 24 个文件变更，1767 行新增，271 行删除）

## 未解决风险

1. **无法本地测试**：网络不通，Gradle 9.1.0 和依赖无法下载
   - 缓解：通过子 agent 静态语法检查
   - 待办：网络恢复后运行 `./gradlew :terminal-core:test :app:assembleDebug`

2. **PTY 真正实现需要 .so**：当前 PtyBackend 只是骨架
   - 需要：交叉编译 libpty.so（C 代码封装 openpty + fork + exec）
   - 障碍：NDK 不可用、项目规则"不引入原生代码"
   - 待办：评估是否放宽规则，或寻找纯 Kotlin 的 PTY 替代方案

3. **SELinux 限制**：即使有 .so，/dev/ptmx 访问可能被 SELinux 拒绝
   - 待办：真机测试 SELinux 策略

## 下一步计划

### 短期（网络恢复后）
1. 运行 `./gradlew :terminal-core:test :app:assembleDebug` 验证编译
2. 真机测试 Ctrl+C、Ctrl+D、Ctrl+Z 是否能发送给 shell
3. 真机测试 localEcho 行为

### 中期（Phase 5b - libpty.so）
1. 编写 C 代码：`pty.c` 封装 openpty + fork + execve + ioctl(TIOCSWINSZ)
2. 交叉编译为 arm64-v8a 的 libpty.so
3. 放入 `app/src/main/jniLibs/arm64-v8a/`
4. 测试 `PtyBackend.isAvailable()` 返回 true

### 长期
1. 真机验证 PTY 模式下 vim/top/Ctrl+C 是否正常工作
2. 处理不同厂商的 SELinux 策略差异
3. SSH 客户端（Apache MINA）
4. 文件浏览器

## 关键决策记录

> 提醒：以下决策应记录到 `docs/decisions.md`

1. **Backend 能力抽象**：通过 `BackendCapabilities` 数据类描述不同 Backend 的能力，UI 层据此调整行为（localEcho、信号处理等），而非在调用处硬编码 `if (backend is ProcessBackend)`
2. **BackendFactory 工厂模式**：统一 Backend 创建，未来添加新 Backend 类型（如 SSH）时只需扩展工厂，上层代码不变
3. **PtyBackend 骨架先行**：先写好完整接口和 external 方法声明，.so 实现后填充。这样架构稳定，未来工作集中在 C 代码编译
4. **Ctrl 键显式映射**：用 `when (keyCode)` 显式映射 A-Z，而非依赖 `getUnicodeChar(metaState)` 的不确定行为
5. **needsLocalEcho 属性**：让 UI 层查询 Backend 能力，而非假设始终需要 localEcho。为未来 PTY 模式做好准备

## 重复出现的错误

> 提醒：以下错误应记录到 `docs/error-journal.md`

1. **KeyboardHandlerNew 死代码**：原代码 `if (ctrl) { val c = seq.firstOrNull(); if (c in 'a'..'z') }` 中 seq 是功能键的转义序列（以 ESC 开头），永远不会匹配 'a'..'z'。教训：条件分支要实际可达，否则等于没有实现
2. **DEL 键转义序列错误**：原来发送 `\u001b\u007F`（ESC + DEL），正确应为 `\u007F`（单独 DEL）。教训：参考 xterm 文档确认转义序列
3. **`!!` 强制非空断言**：ProcessBackend 中 `process!!.outputStream` 违反编码规范。教训：即使逻辑上不会为 null，也应使用安全调用或局部变量
