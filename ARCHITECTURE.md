# agent_demo 项目架构与功能分析

## 一、项目概览

`agent_demo` 是一个基于 Java 17 实现的 Claude Agent 框架，模拟了 Claude Code 的核心运行机制：LLM 驱动的工具调用循环、子代理架构、上下文管理，以及可扩展的工具注册体系。

---

## 二、整体架构

```
AgentApplication
    └── AgentLoop（主循环）
            ├── LlmClient（API 调用 + 重试）
            ├── ToolManager（工具管理 + todo 问责）
            │       └── ToolRegistry（工具注册表）
            │               ├── baseTools（bash/read/write/edit/todo/...）
            │               └── parentOnlyTools（task/mcp_*）
            └── ContextCompactor（三层上下文压缩）
```

### 包结构

| 包 | 职责 |
|----|------|
| `core` | AgentLoop 主循环、LlmClient API 封装、SubAgentRunner 子代理 |
| `tool` | AgentTool 接口、ToolRegistry 注册表、ToolManager 管理器 |
| `tool/impl` | 内置工具实现（bash、read、write、edit、todo、task、compact） |
| `monitor` | 监控日志：工具调用记录、LLM 调用记录、统计聚合、文件落盘 |
| `mcp` | MCP stdio client：与外部 MCP server 通信，动态注册工具 |
| `compact` | 三层上下文压缩：micro/auto/manual |
| `skill` | Skill 动态加载：从 markdown 文件注入专项知识 |
| `todo` | TodoManager：任务列表管理 |
| `pojo` | 数据模型：LlmResponse、ContentBlock、AgentContext 等 |
| `config` | AppConfig：统一读取 .env 配置 |

---

## 三、核心运行流程

```
用户输入
  → ToolManager.prefixUserMessageIfNeeded()  // 注入 todo 问责提醒
  → chatHistory.add(user message)
  → AgentLoop.runAgentCycle()
        ├── ContextCompactor.microCompact()   // Layer 1：静默压缩旧 tool_result
        ├── token 超阈值 → autoCompact()      // Layer 2：LLM 生成摘要
        ├── LlmClient.call()                  // 调用 Claude API（含重试）
        ├── stop_reason == "tool_use"
        │     ├── 遍历 ContentBlock
        │     ├── MonitoredTool.execute()     // 工具执行（含监控埋点）
        │     └── CompactTool 信号 → Layer 3 压缩
        └── stop_reason == "end_turn" → 输出文本，结束本轮
```

---

## 四、工具体系

### AgentTool 接口

```java
public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    String execute(Map<String, Object> input) throws Exception;
}
```

所有工具（内置、MCP、子代理）统一实现此接口，对 AgentLoop 完全透明。

### 内置工具

| 工具名 | 类 | 说明 |
|--------|-----|------|
| `bash` | BashTool | 执行 shell 命令 |
| `read_file` | ReadFileTool | 读取文件内容 |
| `write_file` | WriteFileTool | 写入文件 |
| `edit_file` | EditFileTool | 字符串替换编辑 |
| `todo` | TodoTool | 任务列表增删查 |
| `task` | TaskTool | 启动子代理执行复杂任务 |
| `compact` | CompactTool | 触发 Layer 3 上下文压缩 |
| `load_skill` | LoadSkillTool | 加载专项 Skill 知识 |
| `mcp_*` | McpToolAdapter | MCP server 提供的动态工具 |

### 工具注册与装饰

`ToolRegistry` 注册时自动用 `MonitoredTool` 包装每个工具：

```
registerBase(tool) → baseTools.put(name, new MonitoredTool(tool))
```

`MonitoredTool` 是装饰器，在 `execute()` 前后埋点，记录耗时和成功/失败，对工具本身零侵入。

---

## 五、监控日志功能

### 设计目标

- 每次工具调用实时打印一行日志
- 支持 `/stats` 命令随时查看会话统计
- 退出时自动将完整记录写入 `.logs/session_{id}.json`
- LLM 调用失败时自动重试，并记录重试次数

### 数据模型

**ToolInvocationRecord**（单次工具调用）

| 字段 | 说明 |
|------|------|
| toolName | 工具名称 |
| startTime | 调用开始时间（ISO 8601） |
| durationMs | 耗时（毫秒） |
| success | 是否成功 |
| errorMsg | 失败原因（成功时为 null） |
| inputSummary | 入参摘要（前 50 字符） |
| outputSummary | 输出摘要（前 100 字符） |

**LlmInvocationRecord**（单次 LLM 调用）

| 字段 | 说明 |
|------|------|
| startTime | 调用开始时间 |
| durationMs | 耗时（毫秒） |
| success | 是否成功 |
| errorMsg | 失败原因 |
| stopReason | LLM 停止原因（tool_use / end_turn） |
| contentBlockCount | 返回的 content block 数量 |
| retryCount | 实际重试次数 |

**SessionStats**（单例，线程安全）

聚合所有记录，提供按工具名统计（调用次数、成功数、平均耗时）和 LLM 整体统计。

### 实现方式

```
MonitoredTool（装饰器）
    execute(input)
        ├── 记录 startTime
        ├── 调用 delegate.execute(input)
        ├── 计算 durationMs
        ├── 写入 SessionStats
        └── 打印 [MONITOR] bash | 234ms | OK
```

### LLM 容错机制

`LlmClient` 内部实现指数退避重试：

```
第 1 次失败 → 等 1s 重试
第 2 次失败 → 等 2s 重试
第 3 次失败 → 等 3s 重试
第 4 次失败 → 抛出异常，写失败记录，AgentLoop 捕获后继续等待用户输入
```

### 使用方式

```
/stats    → 打印当前会话统计表
exit      → 打印统计 + 写 .logs/session_{timestamp}.json
```

输出示例：
```
========== /stats ==========
Session ID : 1745312345678
Duration   : 142s

LLM calls  : 8 total, 8 success
LLM avg ms : 2341ms

--- Tool Calls ---
Tool           Calls Success FailRate      AvgMs
--------------------------------------------------
bash               5       4    20.0%       312ms
read_file          3       3     0.0%        45ms
write_file         2       2     0.0%        38ms
=============================
```

---

## 六、MCP 功能

### 什么是 MCP

Model Context Protocol（MCP）是 Anthropic 提出的开放协议，允许 LLM 通过标准接口调用外部工具服务（MCP server）。MCP server 可以是文件系统、数据库、浏览器控制等任意能力。

### 接入方案

本项目采用 **stdio 模式**：启动 MCP server 子进程，通过 stdin/stdout 收发 JSON-RPC 2.0 消息。

```
AgentApplication
    └── McpClient（启动子进程，握手，获取工具列表）
            └── McpToolAdapter × N（每个 MCP tool 包装为 AgentTool）
                    └── 注册到 ToolRegistry.parentOnlyTools
```

### 通信协议

MCP 使用 JSON-RPC 2.0，本项目实现了三个必要方法：

| 方法 | 时机 | 作用 |
|------|------|------|
| `initialize` | 启动时 | 握手，声明客户端能力 |
| `tools/list` | 握手后 | 获取 server 提供的工具列表 |
| `tools/call` | 工具调用时 | 执行工具，传入参数，返回结果 |

### 配置方式

在 `.env` 文件中添加：

```
MCP_COMMAND=npx -y @modelcontextprotocol/server-filesystem D:/workspace
```

启动时自动检测，若未配置则跳过，不影响正常运行。

### 工具命名规则

MCP 工具注册后名称统一加 `mcp_` 前缀，避免与内置工具冲突。例如 MCP server 提供的 `read_file` 工具，注册后名称为 `mcp_read_file`。

### 核心类说明

**McpClient**

负责子进程生命周期管理和 JSON-RPC 通信：
- 构造时启动子进程并完成 `initialize` 握手
- `listTools()` 返回 `List<McpToolDef>`
- `callTool(name, input)` 发送 `tools/call` 并返回文本结果
- 实现 `Closeable`，`AgentApplication` 退出时自动关闭子进程

**McpToolAdapter**

将 `McpToolDef` 包装为 `AgentTool`：
- `getInputSchema()` 直接透传 MCP server 返回的 JSON Schema
- `execute()` 委托给 `McpClient.callTool()`
- 同样会被 `MonitoredTool` 包装，自动纳入监控统计

---

## 七、子代理架构

`TaskTool` 触发时，`DefaultSubAgentRunner` 启动一个独立的 AgentLoop（子代理），使用相同的 `ToolRegistry`（baseTools），但不包含 `task` 工具（防止无限递归）。子代理有独立的对话历史和最大迭代次数限制（默认 30 轮）。

---

## 八、配置项汇总

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `ANTHROPIC_API_KEY` | — | API 密钥（必填） |
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com/v1` | API 地址（支持中转） |
| `MODEL_ID` | `claude-3-5-sonnet-20240620` | 模型 ID |
| `SUBAGENT_MAX_ITERATIONS` | `30` | 子代理最大迭代轮次 |
| `COMPACT_TOKEN_THRESHOLD` | `50000` | Layer 2 压缩触发阈值（估算 token 数） |
| `COMPACT_KEEP_RECENT` | `3` | Layer 1 保留最近几条 tool_result |
| `TRANSCRIPT_DIR` | `.transcripts` | 对话记录存储目录 |
| `MCP_COMMAND` | — | MCP server 启动命令（可选） |
