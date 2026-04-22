# S06 Context Compact — 修改说明文档

> 适用项目：`agent_demo`（Java 17 / Maven，无 Spring）  
> 本次迭代目标：为 Agent 引入三层上下文压缩能力，防止长对话撑爆 LLM 上下文窗口。

---

## 一、文件变更清单

### 新增文件

| 文件路径 | 负责人 | 说明 |
|----------|--------|------|
| `src/main/java/cn/edu/agent/compact/ContextCompactor.java` | A | 三层压缩核心逻辑 |
| `src/main/java/cn/edu/agent/compact/TranscriptManager.java` | A | 对话历史 JSONL 落盘 |
| `src/main/java/cn/edu/agent/tool/impl/CompactTool.java` | A | Layer 3 触发工具 |
| `src/test/java/cn/edu/agent/test/ContextCompactorTest.java` | B | 三层压缩单元测试 |

### 修改文件

| 文件路径 | 负责人 | 变更内容 |
|----------|--------|----------|
| `src/main/java/cn/edu/agent/core/AgentLoop.java` | A | 在 `runAgentCycle()` 中集成三层压缩；新增 `autoCompactIfNeeded()`、`forceCompact()` 方法 |
| `src/main/java/cn/edu/agent/config/AppConfig.java` | B | 新增三个配置读取方法：`getCompactTokenThreshold()`、`getCompactKeepRecent()`、`getTranscriptDir()`；`getApiKey()` 改为优先读系统环境变量 |
| `src/main/java/cn/edu/agent/pojo/AgentContext.java` | B | 新增 `compactCount` 字段和 `incrementCompactCount()` 方法 |
| `src/main/java/cn/edu/agent/tool/ToolRegistry.java` | B | 构造器中通过反射注册 `CompactTool`（类不存在时静默忽略） |
| `pom.xml` | B | 新增 JUnit 5（`junit-jupiter 5.10.2`）和 `maven-surefire-plugin 3.2.5` |

---

## 二、各文件改动详情

### 2.1 `AppConfig.java` — 新增三项配置

```java
// 新增（原有方法未改动）
public static int getCompactTokenThreshold() { ... }  // 默认 50000
public static int getCompactKeepRecent()      { ... }  // 默认 3
public static String getTranscriptDir()        { ... }  // 默认 ".transcripts"
```

`getApiKey()` 的读取逻辑也做了加固：**系统环境变量优先于 `.env` 文件**（原版只读 dotenv）。

配置可通过 `.env` 文件或系统环境变量覆盖：

```
COMPACT_TOKEN_THRESHOLD=80000
COMPACT_KEEP_RECENT=5
TRANSCRIPT_DIR=.my_transcripts
```

---

### 2.2 `AgentContext.java` — 新增压缩计数字段

```java
// 新增字段
private int compactCount;          // 本次会话触发压缩的总次数

// 新增方法
public void incrementCompactCount() { compactCount++; }
// getCompactCount() 由 @Getter 自动生成
```

子 Agent（`DefaultSubAgentRunner`）使用 `AgentContext` 管理自己的对话历史，`compactCount` 可用于日志统计与监控。

---

### 2.3 `ToolRegistry.java` — 注册 CompactTool

```java
public ToolRegistry() {
    registerBase(new BashTool());
    // ... 原有工具 ...
    registerOptionalBase("cn.edu.agent.tool.impl.CompactTool"); // ← 新增
}

// 新增私有方法：反射加载，类不存在时静默跳过
private void registerOptionalBase(String className) { ... }
```

使用反射注册而非直接 `new`，是为了让 B 同学的代码在 A 同学的 `CompactTool` 合并前也能正常编译运行。

---

### 2.4 `ContextCompactor.java` — 三层压缩核心（全新）

#### Layer 1 — `microCompact(messages, keepRecent)`

- **触发时机**：每轮 LLM 调用前，自动执行，无感知
- **效果**：找出所有含 `tool_result` 的历史消息，只保留最近 `keepRecent` 条完整内容，更早的替换为占位符
- **替换前**：`{"type":"tool_result","tool_use_id":"xxx","content":"（原始输出，可能很长）"}`
- **替换后**：`{"type":"tool_result","tool_use_id":"xxx","content":"[Previous: used read_file]"}`

#### Layer 2 — `autoCompact(messages)`

- **触发时机**：`estimateTokens(messages) > TOKEN_THRESHOLD` 时，由 `AgentLoop` 自动触发
- **效果**：
  1. 调用 `TranscriptManager` 将完整历史落盘为 JSONL
  2. 调用 LLM 生成对话摘要
  3. 整个 `chatHistory` 被替换为单条摘要消息

#### Layer 3 — 由 `CompactTool` 触发

- **触发时机**：LLM 主动调用 `compact` 工具（感觉上下文过长时自主决策）
- **效果**：与 Layer 2 完全相同（调用同一个 `autoCompact` 方法）

#### `estimateTokens(messages)`

- 粗估 token 数：遍历所有消息的字符数，除以 4
- 无网络调用，纯本地计算，性能开销极低

---

### 2.5 `TranscriptManager.java` — 历史落盘（全新）

```
落盘路径：.transcripts/transcript_{unix_timestamp}.jsonl
格式：每行一条消息的 JSON（JSONL）
```

示例文件内容：
```jsonl
{"role":"user","content":"帮我读取所有 Python 文件"}
{"role":"assistant","content":[{"type":"tool_use","id":"toolu_01","name":"read_file","input":{"path":"a.py"}}]}
{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_01","content":"（文件内容）"}]}
```

目录不存在时自动创建，落盘失败不会中断压缩流程（降级处理）。

---

### 2.6 `CompactTool.java` — Layer 3 触发工具（全新）

```java
getName()        → "compact"
getDescription() → "当你感觉对话历史过长、影响思考效率时，主动调用此工具压缩上下文..."
execute(input)   → 返回常量字符串 "__COMPACT__"（信号值，不做实际压缩）
```

`execute()` 只返回信号字符串 `__COMPACT__`，真正的压缩由 `AgentLoop` 检测到该信号后调用 `forceCompact()` 执行。

工具参数（可选）：

| 参数 | 类型 | 说明 |
|------|------|------|
| `reason` | string | 触发压缩的原因，仅用于日志，不影响行为 |

---

### 2.7 `AgentLoop.java` — 集成三层压缩

`runAgentCycle()` 的执行流程变更如下：

```
每轮循环开始
  │
  ├─ [Layer 1] microCompact(chatHistory, keepRecent)   ← 新增
  │
  ├─ [Layer 2] autoCompactIfNeeded()                   ← 新增
  │    └─ estimateTokens > 50000 → autoCompact()
  │
  ├─ llmClient.call(...)
  │
  └─ 处理工具调用
       └─ 若工具输出 == "__COMPACT__"
            └─ [Layer 3] forceCompact()                ← 新增
```

新增的两个包级可见方法（供测试反射调用）：

```java
void autoCompactIfNeeded(AgentContext context)  // Layer 2 入口
void forceCompact(AgentContext context)         // Layer 3 入口
```

---

## 三、配置项速查

| 环境变量 / .env 键 | 默认值 | 说明 |
|--------------------|--------|------|
| `COMPACT_TOKEN_THRESHOLD` | `50000` | 触发 Layer 2 的 token 估算阈值 |
| `COMPACT_KEEP_RECENT` | `3` | Layer 1 保留最近几条 tool_result 不压缩 |
| `TRANSCRIPT_DIR` | `.transcripts` | 历史落盘目录路径 |
| `ANTHROPIC_API_KEY` | — | API 密钥（系统环境变量优先） |
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com/v1` | API 地址（中转时修改） |
| `MODEL_ID` | `claude-3-5-sonnet-20240620` | 使用的模型 |
| `SUBAGENT_MAX_ITERATIONS` | `30` | 子 Agent 最大迭代轮次 |

---

## 四、包结构与类图

```
cn.edu.agent
│
├── AgentApplication              程序入口，组装所有组件并启动 AgentLoop
│
├── config/
│   └── AppConfig                 读取 .env / 系统环境变量，提供全局配置
│
├── core/
│   ├── AgentLoop                 主循环：接收用户输入 → 调用 LLM → 执行工具 → 三层压缩
│   ├── LlmClient                 封装 HTTP 调用 Anthropic API
│   ├── SubAgentRunner            «interface» 子 Agent 执行契约
│   └── DefaultSubAgentRunner     子 Agent 实现：独立上下文运行子任务
│
├── compact/                      ★ s06 新增包
│   ├── ContextCompactor          三层压缩器（microCompact / autoCompact / estimateTokens）
│   └── TranscriptManager         对话历史 JSONL 落盘
│
├── tool/
│   ├── AgentTool                 «interface» 所有工具的统一契约
│   ├── AgentRole                 «enum» PARENT / CHILD
│   ├── ToolRegistry              工具注册表（base 工具 + parent-only 工具）
│   ├── ToolManager               在 ToolRegistry 上封装 todo 问责计数逻辑
│   └── impl/
│       ├── BashTool              执行 shell 命令
│       ├── ReadFileTool          读取文件内容
│       ├── WriteFileTool         写入文件
│       ├── EditFileTool          按字符串替换编辑文件
│       ├── TodoTool              管理待办列表
│       ├── LoadSkillTool         按需加载 Skill 知识文档
│       ├── TaskTool              父 Agent 专属：派发子任务给 SubAgentRunner
│       └── CompactTool           ★ s06 新增：Layer 3 压缩触发工具
│
├── pojo/
│   ├── AgentContext              子 Agent 上下文（消息历史 + 迭代计数 + 压缩计数★）
│   ├── ContentBlock              LLM 响应内容块（text / tool_use）
│   ├── LlmResponse               LLM 完整响应（content + stop_reason）
│   ├── SubAgentResult            子 Agent 执行结果（摘要 + 状态）
│   ├── TodoItem                  待办条目（id / title / status / createdAt）
│   └── chatMessage               （保留字段，暂未启用）
│
├── todo/
│   └── TodoManager               待办 CRUD，强制同一时刻至多一个 IN_PROGRESS
│
└── skill/
    ├── SkillLoader               扫描 skills/ 目录，加载 SKILL.md 文件
    ├── SkillEntry                单个 Skill 的元数据 + 正文
    └── SkillMeta                 Skill frontmatter（name / description / tags）
```

### 核心依赖关系图

```
AgentApplication
    │ 组装
    ▼
AgentLoop ──uses──► LlmClient
    │                   │ HTTP
    │                   ▼
    │             Anthropic API
    │
    ├──uses──► ToolManager
    │               │ 委托
    │               ▼
    │          ToolRegistry ──注册──► AgentTool (接口)
    │                                    ├── BashTool
    │                                    ├── ReadFileTool
    │                                    ├── WriteFileTool
    │                                    ├── EditFileTool
    │                                    ├── TodoTool ──► TodoManager
    │                                    ├── LoadSkillTool ──► SkillLoader
    │                                    ├── TaskTool ──► SubAgentRunner
    │                                    └── CompactTool ★
    │
    └──uses──► ContextCompactor ★
                    ├──uses──► LlmClient（生成摘要）
                    └──uses──► TranscriptManager ★（落盘）


SubAgentRunner (interface)
    └── DefaultSubAgentRunner
            ├──uses──► LlmClient
            ├──uses──► ToolRegistry
            └──uses──► AgentContext（独立上下文，含 compactCount★）
```

> ★ 标注为 s06 新增或修改的部分

---

## 五、三层压缩触发条件速查

| 层级 | 名称 | 触发时机 | 触发方 | 效果 |
|------|------|----------|--------|------|
| Layer 1 | micro_compact | 每轮 LLM 调用前，必执行 | AgentLoop 自动 | 旧 tool_result → `[Previous: used xxx]` 占位符 |
| Layer 2 | auto_compact | `estimateTokens > 50000` | AgentLoop 自动 | 全量历史 → LLM 摘要 + 落盘 |
| Layer 3 | compact tool | LLM 主动调用 `compact` 工具 | LLM 自主决策 | 同 Layer 2 |

---

## 六、快速上手

### 运行项目

```bash
# 1. 配置环境变量（或创建 .env 文件）
export ANTHROPIC_API_KEY=your_key
export ANTHROPIC_BASE_URL=https://api.anthropic.com/v1   # 中转时替换

# 2. 编译运行
mvn compile exec:java -Dexec.mainClass="cn.edu.agent.AgentApplication"
```

### 运行测试

```bash
mvn test
```

测试类 `ContextCompactorTest` 覆盖以下场景：

| 测试方法 | 验证内容 |
|----------|----------|
| `configDefaults_shouldMatchExpectedValues` | 三个新配置项默认值正确 |
| `compactCount_shouldIncrementCorrectly` | `AgentContext.incrementCompactCount()` 累加正确 |
| `layer1_microCompact_shouldReplaceOlderToolResult` | Layer 1 将旧 tool_result 替换为占位符 |
| `layer2_autoCompact_shouldCompactAndCount` | Layer 2 执行后 `compactCount >= 1` |
| `layer3_compactTool_shouldTriggerSameFlow` | Layer 3 执行后 `compactCount >= 1` |
| `compactTool_shouldBeRegisteredWhenAvailable` | `compact` 工具已注册到 `ToolRegistry` |

> 注：Layer 2/3 测试需要有效的 `ANTHROPIC_API_KEY`，无 key 时测试会被 `assumeTrue` 跳过而非失败。

### 调整压缩参数

在项目根目录创建 `.env` 文件：

```env
# 上下文超过此 token 估算值时触发 Layer 2（默认 50000）
COMPACT_TOKEN_THRESHOLD=80000

# Layer 1 保留最近几条工具结果不压缩（默认 3）
COMPACT_KEEP_RECENT=5

# 历史落盘目录（默认 .transcripts）
TRANSCRIPT_DIR=.my_transcripts
```
