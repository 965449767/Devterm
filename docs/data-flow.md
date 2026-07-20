# 数据流文档

## 输出路径（Shell → 屏幕）

```
Shell (stdout+stderr)
  │
  ▼ [pipe]
Reader Thread (blocking read 8192)
  │
  ▼ TerminalCore.writeInput(bytes)
Parser Thread
  │ forEach byte
  ▼ VtParser.consume(byte) → ScreenCommand
  │
  ▼ ScreenBuffer.execute(command)
  │  → chars[row*cols+col] = c
  │  → fg[row*cols+col] = color
  │  → bg[row*cols+col] = color
  │  → flags[row*cols+col] = f
  │  → DirtyTracker.mark(row)
  │
  ▼ FrameQueue.notifyDirty()
  │  → pending.incrementAndGet()
  │
  ▼ [16ms 间隔]
Renderer Thread
  │  FrameQueue.consume() → true?
  │  DirtyTracker.consume() → [rows]
  │
  ▼ for each row in dirtyRows
  │  TextMeasurer.measure(line)
  │  drawText()
  │
  ▼ drawCursor()
  │
  ▼ _snapshot.value = RenderFrame(dirtyLines, cursor)
  │
  ▼ [StateFlow]
Compose collectAsState()
  │
  ▼ Canvas { }
```

## 输入路径（键盘 → Shell）

```
Software Keyboard / Hardware Keyboard
  │
  ▼ ImeInputView → TerminalInputConnection
  │
  ▼ dispatchCharacter(modifiers, charCode)
  │
  ├── localEcho: ScreenBuffer.writeGlyph(char)
  │   → DirtyTracker.mark(row)
  │   → FrameQueue.notifyDirty()
  │
  └── sendBytes(data)
      → TerminalSession.write(data)
      → ByteQueue.write(data)
      → Writer Thread → process.outputStream
      → Shell stdin
```

## 关键时序

### 输出路径时序（正常输出）

```
Reader:   -----read(buf)-----read(buf)-----
Parser:   -----consume()-------------------
Buffer:   -----update cells-----mark(row)--
FrameQ:   -----notifyDirty()---------------
Renderer: ---------------------consume()---
          0ms                 16ms        32ms
```

### 输出路径时序（密集型输出如 npm install）

```
Reader:   -read-read-read-read-read-read---
Parser:   -c-c-c-c-c-c-c-c-c-c-c-c-c-------
Buffer:   -m-m-m-m-m-m-m-m-m-m-m-m-m-------
FrameQ:   -n-n-n-n-n-n-n-n-n-n-n-n-n-------
Renderer: ---------consume()------consume()
          0ms                  16ms      32ms
```

Reader 高速读取 → Parser 持续处理 → FrameQueue 累积标记
Renderer 每 16ms 消费一次 → 最多 60fps

### 输出路径时序（cat 大文件）

```
Reader:   [-----8192 bytes-----][-----8192...
Parser:   [-----consume--------][-----cons...
Buffer:   [--mark rows 0-23-----][--mark 0-...
FrameQ:   [--notify-------------][--notify--
Renderer: [--consume--draw 0-23]
          0ms                  16ms
```

每次 `writeInput()` 处理完 8192 字节后调用 `notifyDirty()`。
16ms 内无论多少次 notify → 合并为一次 consume。
