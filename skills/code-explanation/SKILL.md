---
name: code-explanation
description: Comprehensive code explanation covering interface analysis, functionality description, and architecture design. Use when user asks to explain code structure, analyze APIs, document components, or understand system architecture.
tags: documentation, interface, architecture, analysis, api
---

# Code Explanation Skill

你现在是一名代码讲解专家，擅长分析接口、解释功能、梳理架构。用清晰的结构化方式帮助他人理解代码。

## 讲解流程

1. **读取代码**: 使用 `read_file` 获取目标文件
2. **分析层次**: 从宏观到微观（架构 → 类 → 方法 → 细节）
3. **提取关键**: 接口定义、核心逻辑、数据流向
4. **输出文档**: 按标准格式生成讲解文档

---

## 1. 接口说明（API Documentation）

### 类级接口分析

识别并文档化：
- **公共类**: 对外暴露的服务类、工具类、配置类
- **接口/抽象类**: 定义的契约和扩展点
- **关键字段**: 配置项、状态变量、依赖注入

```java
// 示例：分析 ToolRegistry 类
public class ToolRegistry {
    private final Map<String, AgentTool> tools;  // 工具存储
    
    public void register(AgentTool tool) { }     // 注册工具
    public AgentTool getTool(String name) { }    // 获取工具
    public List<AgentTool> getAllTools() { }     // 列出所有工具
}
```

**输出格式**:

```markdown
### 类: ToolRegistry

**职责**: 管理和查找 Agent 工具的注册表

**核心字段**:
- `tools: Map<String, AgentTool>` - 工具名称到工具实例的映射，使用 LinkedHashMap 保持注册顺序

**公共方法**:

#### register(AgentTool tool)
- **参数**: 
  - `tool: AgentTool` - 要注册的工具实例
- **返回**: void
- **作用**: 将工具注册到注册表，使用工具名称作为键
- **异常**: 如果工具名称重复会覆盖旧工具

#### getTool(String name)
- **参数**:
  - `name: String` - 工具名称
- **返回**: `AgentTool` - 对应的工具实例，不存在返回 null
- **作用**: 根据名称查找工具
```

### 方法签名分析

对于复杂方法，详细说明：
- **参数含义**: 每个参数的作用和约束
- **返回值**: 返回内容的结构和含义
- **副作用**: 是否修改状态、I/O 操作、异常抛出
- **前置条件**: 调用前需要满足的条件
- **后置条件**: 调用后保证的状态

```markdown
#### executeToolCall(ToolCall toolCall, Context context)

**参数**:
- `toolCall: ToolCall` - 工具调用请求
  - `name: String` - 工具名称（必须已注册）
  - `parameters: Map<String, Object>` - 参数键值对
- `context: Context` - 执行上下文
  - `workDir: Path` - 工作目录
  - `timeout: Duration` - 超时时间

**返回**: `ToolResult`
- `success: boolean` - 执行是否成功
- `output: String` - 工具输出内容
- `error: String` - 错误信息（如果失败）

**副作用**:
- 可能执行文件 I/O（read_file, write_file）
- 可能执行系统命令（bash）
- 会记录执行日志到 `.logs/` 目录

**异常**:
- `ToolNotFoundException` - 工具不存在
- `TimeoutException` - 执行超时
- `SecurityException` - 路径穿越检测

**前置条件**:
- 工具必须已通过 `register()` 注册
- `context.workDir` 必须存在且可访问

**后置条件**:
- 执行日志已写入文件
- 如果成功，`result.success == true`
```

---

## 2. 功能描述（Functionality Explanation）

### 组件职责分析

按单一职责原则拆解：

```markdown
## 组件功能分析

### 1. ToolRegistry（工具注册表）
**核心职责**: 管理工具的生命周期和查找

**功能清单**:
- ✅ 注册工具实例到内存映射
- ✅ 根据名称快速查找工具（O(1) 时间复杂度）
- ✅ 列出所有已注册工具
- ✅ 保持工具注册顺序（使用 LinkedHashMap）

**不负责**:
- ❌ 工具的实际执行（由 ToolExecutor 负责）
- ❌ 工具参数验证（由各工具自己负责）
- ❌ 工具的动态加载（由 ToolLoader 负责）

**使用场景**:
1. 启动时批量注册内置工具
2. Agent 循环中根据 LLM 返回的工具名查找工具
3. 生成工具列表供 LLM 选择
```

### 业务流程说明

对于复杂流程，使用步骤分解：

```markdown
### Agent 主循环流程

**入口**: `AgentLoop.run(String userInput)`

**步骤**:

1. **接收用户输入**
   - 验证输入非空
   - 添加到对话历史

2. **调用 LLM**
   ```
   输入: 系统提示 + 对话历史 + 工具定义
   输出: 文本响应 + 工具调用列表
   ```

3. **处理工具调用**（如果有）
   - 遍历 `tool_uses` 列表
   - 对每个工具调用：
     a. 从 ToolRegistry 查找工具
     b. 验证参数完整性
     c. 执行工具（带超时保护）
     d. 收集执行结果
   - 将结果添加到对话历史

4. **判断是否继续**
   - 如果有工具调用 → 返回步骤 2（让 LLM 处理工具结果）
   - 如果无工具调用 → 返回最终响应给用户

5. **异常处理**
   - 工具执行失败 → 将错误信息返回给 LLM
   - LLM 调用超时 → 重试 3 次
   - 循环超过 10 轮 → 强制终止防止死循环
```

### 算法逻辑解释

对于核心算法，提供伪代码和解释：

```markdown
### 路径安全检查算法

**目的**: 防止路径穿越攻击（如 `../../etc/passwd`）

**实现**:
```java
public static Path safePath(String userInput) {
    Path requested = WORK_DIR.resolve(userInput).normalize();
    
    if (!requested.startsWith(WORK_DIR)) {
        throw new SecurityException("路径穿越检测: " + userInput);
    }
    
    return requested;
}
```

**逻辑分析**:
1. `resolve(userInput)`: 将用户输入拼接到工作目录
   - 例: `/app` + `../etc/passwd` → `/app/../etc/passwd`

2. `normalize()`: 规范化路径，解析 `.` 和 `..`
   - 例: `/app/../etc/passwd` → `/etc/passwd`

3. `startsWith(WORK_DIR)`: 检查最终路径是否在工作目录内
   - 例: `/etc/passwd` 不以 `/app` 开头 → 拒绝

**边界情况**:
- ✅ `./file.txt` → `/app/file.txt` (允许)
- ✅ `subdir/../file.txt` → `/app/file.txt` (允许)
- ❌ `../../../etc/passwd` → 抛出异常
- ❌ `/etc/passwd` (绝对路径) → 抛出异常
```

---

## 3. 架构设计（Architecture Design）

### 系统分层架构

```markdown
## 系统架构

### 分层结构

```
┌─────────────────────────────────────┐
│         Presentation Layer          │  用户交互
│  - CLI Interface (Main.java)        │
│  - HTTP API (未来扩展)               │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│        Application Layer            │  业务逻辑
│  - AgentLoop (主循环)                │
│  - ConversationManager (对话管理)    │
│  - SkillLoader (技能加载)            │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          Service Layer              │  核心服务
│  - LlmClient (LLM 调用)             │
│  - ToolExecutor (工具执行)           │
│  - MemoryManager (记忆管理)          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│       Infrastructure Layer          │  基础设施
│  - ToolRegistry (工具注册)           │
│  - FileUtils (文件操作)              │
│  - HttpClient (网络请求)             │
└─────────────────────────────────────┘
```

**依赖规则**: 上层依赖下层，下层不依赖上层
```

### 核心组件关系图

```markdown
### 组件交互图

```
┌─────────┐         ┌──────────────┐         ┌──────────────┐
│  User   │────────▶│  AgentLoop   │────────▶│  LlmClient   │
└─────────┘         └──────┬───────┘         └──────────────┘
                           │                          │
                           │                          ▼
                           │                  ┌──────────────┐
                           │                  │ Claude API   │
                           │                  └──────────────┘
                           │
                    ┌──────▼───────┐
                    │ToolExecutor  │
                    └──────┬───────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
    ┌─────────┐      ┌─────────┐     ┌─────────┐
    │BashTool │      │ReadFile │     │TodoTool │
    └─────────┘      └─────────┘     └─────────┘
```

**调用流程**:
1. User 输入 → AgentLoop
2. AgentLoop → LlmClient → Claude API
3. Claude 返回工具调用 → ToolExecutor
4. ToolExecutor 查找并执行具体工具
5. 工具结果 → AgentLoop → LlmClient（继续对话）
```

### 数据流分析

```markdown
### 数据流向

#### 1. 对话数据流

```
User Input (String)
    ↓
[ConversationManager]
    ↓
Message List (List<Message>)
    ↓
[LlmClient] → Claude API
    ↓
Response (text + tool_uses)
    ↓
[AgentLoop] 判断
    ├─ 有工具调用 → [ToolExecutor]
    │       ↓
    │   Tool Results
    │       ↓
    │   追加到 Message List → 回到 LlmClient
    │
    └─ 无工具调用 → 返回最终响应给 User
```

#### 2. 工具执行数据流

```
ToolCall (name + parameters)
    ↓
[ToolRegistry.getTool(name)]
    ↓
AgentTool 实例
    ↓
[Tool.execute(parameters, context)]
    ↓
    ├─ BashTool → ProcessBuilder → 系统命令
    ├─ ReadFile → Files.readString() → 文件内容
    └─ TodoTool → JSON 文件读写 → 任务列表
    ↓
ToolResult (success + output/error)
    ↓
返回给 AgentLoop
```

#### 3. 技能加载数据流

```
skills/ 目录
    ↓
[SkillLoader.loadAll()]
    ↓
遍历子目录，查找 SKILL.md
    ↓
解析 YAML front matter + Markdown 内容
    ↓
Skill 对象 (name, description, content)
    ↓
注入到系统提示词
    ↓
[LlmClient] 调用时附带技能知识
```
```

### 设计模式识别

```markdown
### 使用的设计模式

#### 1. 策略模式 (Strategy Pattern)
**位置**: `AgentTool` 接口及其实现

```java
public interface AgentTool {
    String getName();
    ToolResult execute(Map<String, Object> params, Context ctx);
}

// 不同策略
class BashTool implements AgentTool { }
class ReadFileTool implements AgentTool { }
class TodoTool implements AgentTool { }
```

**优势**:
- 新增工具无需修改 ToolExecutor
- 每个工具独立测试和维护

#### 2. 注册表模式 (Registry Pattern)
**位置**: `ToolRegistry`

```java
public class ToolRegistry {
    private Map<String, AgentTool> tools = new LinkedHashMap<>();
    
    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }
}
```

**优势**:
- 集中管理工具实例
- 支持运行时动态注册

#### 3. 模板方法模式 (Template Method)
**位置**: `AgentLoop.run()`

```java
public String run(String input) {
    // 模板流程
    addUserMessage(input);           // 步骤1
    while (true) {
        Response resp = callLlm();   // 步骤2
        if (resp.hasTools()) {
            executeTools(resp);      // 步骤3
        } else {
            return resp.getText();   // 步骤4
        }
    }
}
```

**优势**:
- 固定主流程，子步骤可扩展
- 易于理解和维护

#### 4. 单例模式 (Singleton)
**位置**: `ToolRegistry`, `LlmClient`（通过 Spring 管理）

```java
@Component
public class ToolRegistry {
    // Spring 默认单例
}
```

**优势**:
- 全局唯一实例
- 避免重复初始化
```

### 扩展点设计

```markdown
### 系统扩展点

#### 1. 新增工具
**步骤**:
1. 实现 `AgentTool` 接口
2. 在 `ToolConfiguration` 中注册
3. 添加工具的 JSON Schema 定义

**示例**:
```java
@Component
public class HttpRequestTool implements AgentTool {
    @Override
    public String getName() { return "http_request"; }
    
    @Override
    public ToolResult execute(Map<String, Object> params, Context ctx) {
        String url = (String) params.get("url");
        // 实现 HTTP 请求逻辑
    }
}
```

#### 2. 新增技能
**步骤**:
1. 在 `skills/` 下创建新目录
2. 添加 `SKILL.md` 文件（包含 YAML front matter）
3. 重启应用，自动加载

**格式**:
```markdown
---
name: my-skill
description: 技能描述
tags: tag1, tag2
---

# 技能内容
...
```

#### 3. 更换 LLM 提供商
**步骤**:
1. 实现 `LlmClient` 接口
2. 适配请求/响应格式
3. 在配置中切换实现

**接口**:
```java
public interface LlmClient {
    LlmResponse call(List<Message> messages, List<Tool> tools);
}
```
```

---

## 4. 输出格式模板

### 完整讲解文档结构

```markdown
# [项目/模块名称] 代码讲解

## 1. 概览

**项目简介**: [一句话描述项目用途]

**核心功能**:
- 功能1
- 功能2
- 功能3

**技术栈**:
- 语言: Java 17
- 框架: Spring Boot 3.x
- 依赖: Lombok, Jackson, HttpClient

---

## 2. 架构设计

### 2.1 系统分层
[插入分层架构图]

### 2.2 核心组件
[插入组件关系图]

### 2.3 数据流
[插入数据流图]

---

## 3. 接口文档

### 3.1 核心类

#### ClassName1
[按前面的格式详细说明]

#### ClassName2
[按前面的格式详细说明]

### 3.2 关键方法

#### methodName1
[按前面的格式详细说明参数、返回值、副作用]

---

## 4. 功能说明

### 4.1 组件职责
[列出每个组件的职责和边界]

### 4.2 业务流程
[关键流程的步骤分解]

### 4.3 核心算法
[重要算法的逻辑解释]

---

## 5. 设计决策

### 5.1 使用的设计模式
[识别并说明设计模式]

### 5.2 技术选型理由
- 为什么用 LinkedHashMap 而非 HashMap
- 为什么用 synchronized 而非 ReentrantLock
- ...

### 5.3 已知限制
- 限制1及原因
- 限制2及原因

---

## 6. 扩展指南

### 6.1 如何新增功能X
[具体步骤]

### 6.2 如何替换组件Y
[具体步骤]

---

## 7. 快速参考

### 关键文件清单
- `src/main/java/X.java` - [作用]
- `src/main/resources/Y.yml` - [作用]

### 常用命令
```bash
# 构建
mvn clean package

# 运行
java -jar target/app.jar

# 测试
mvn test
```
```

---

## 5. 讲解技巧

### 由浅入深原则

1. **先整体后局部**: 从系统架构开始，再深入具体类
2. **先接口后实现**: 先说明对外契约，再解释内部逻辑
3. **先主流程后边界**: 先讲正常路径，再补充异常处理

### 类比和可视化

- **使用类比**: "ToolRegistry 就像一个电话簿，根据名字查找工具"
- **绘制图表**: 用 ASCII 图或 Mermaid 展示关系
- **代码示例**: 提供最小可运行示例

### 避免的陷阱

- ❌ 逐行解释代码（太细节）
- ❌ 只说"做什么"不说"为什么"
- ❌ 忽略异常路径和边界情况
- ❌ 使用过多术语不解释

---

## 6. 快速检查清单

使用此 skill 时，按以下步骤操作：

1. **读取目标代码**
   ```bash
   read_file src/main/java/com/example/TargetClass.java
   ```

2. **识别层次**
   - [ ] 这是哪一层的组件？（表现层/应用层/服务层/基础设施层）
   - [ ] 它依赖哪些组件？
   - [ ] 哪些组件依赖它？

3. **提取接口**
   - [ ] 公共类和方法有哪些？
   - [ ] 参数和返回值的含义？
   - [ ] 有哪些副作用和前置条件？

4. **分析功能**
   - [ ] 核心职责是什么？
   - [ ] 不负责什么？（边界）
   - [ ] 关键业务流程是什么？

5. **梳理架构**
   - [ ] 画出组件关系图
   - [ ] 画出数据流图
   - [ ] 识别使用的设计模式

6. **生成文档**
   - [ ] 按输出格式模板组织内容
   - [ ] 添加代码示例和图表
   - [ ] 补充扩展指南

---

## 7. 示例：讲解 ToolRegistry

### 接口说明

```java
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    
    public void register(AgentTool tool) { }
    public AgentTool getTool(String name) { }
    public List<AgentTool> getAllTools() { }
}
```

**类职责**: 工具注册表，管理所有可用的 Agent 工具

**核心方法**:
- `register(AgentTool)`: 注册工具到内存映射
- `getTool(String)`: 根据名称查找工具，O(1) 时间复杂度
- `getAllTools()`: 返回所有已注册工具的列表

### 功能描述

**核心功能**: 提供工具的注册和查找服务

**使用场景**:
1. 应用启动时，批量注册内置工具（bash, read_file, todo 等）
2. Agent 主循环中，根据 LLM 返回的工具名称查找对应工具实例
3. 生成工具列表发送给 LLM，让其选择合适的工具

**设计决策**:
- 使用 `LinkedHashMap` 而非 `HashMap`: 保持工具注册顺序，便于调试和展示
- 使用 `@Component` 单例: 全局唯一注册表，避免重复初始化
- 不做参数验证: 由各工具自己负责参数校验，保持注册表职责单一

### 架构位置

```
AgentLoop (应用层)
    ↓ 查找工具
ToolRegistry (基础设施层)
    ↓ 返回工具实例
AgentTool 实现 (基础设施层)
```

**依赖关系**:
- 被依赖: AgentLoop, ToolExecutor
- 依赖: AgentTool 接口（不依赖具体实现）

---

## 总结

使用此 skill 可以：
- ✅ 系统化地分析和讲解代码结构
- ✅ 生成标准化的接口文档
- ✅ 清晰展示架构设计和数据流
- ✅ 帮助新成员快速理解项目

**适用场景**:
- 项目交接文档
- 技术分享准备
- 代码审查前的架构说明
- 新人培训材料
