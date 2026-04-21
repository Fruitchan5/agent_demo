# S04: Subagents 测试用例（Java 实现）

## 功能概述

Subagent（子代理）功能允许父 Agent 创建独立的子 Agent 来处理复杂子任务，保持父 Agent 的上下文清晰。

### 核心特性
- **独立上下文**：子 Agent 拥有全新的对话历史
- **工具权限分级**：子 Agent 只能访问基础工具（bash, read_file, write_file, edit_file, todo），不能创建孙 Agent
- **迭代限制**：子 Agent 有最大迭代次数限制，防止无限循环
- **结果摘要**：子 Agent 完成后只返回摘要给父 Agent

---

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    AgentApplication                          │
│  - 注册 TaskTool 到 parentOnlyTools                          │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                      TaskTool                                │
│  - subAgentRunner: SubAgentRunner                            │
│                                                              │
│  + execute(input): String                                    │
│    → 调用 subAgentRunner.run(prompt)                         │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────┐
│              DefaultSubAgentRunner                           │
│  - llmClient: LlmClient                                      │
│  - toolRegistry: ToolRegistry                                │
│  - maxIterations: int                                        │
│                                                              │
│  + run(prompt): String                                       │
│    1. 创建 AgentContext (role=CHILD)                         │
│    2. 循环调用 LLM + 执行工具                                │
│    3. 返回 SubAgentResult.getSummary()                       │
└─────────────────────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                   AgentContext                               │
│  - role: AgentRole (PARENT / CHILD)                          │
│  - messages: List<Map<String, Object>>                       │
│  - currentIteration: int                                     │
│  - maxIterations: int                                        │
│                                                              │
│  + hasReachedLimit(): boolean                                │
│  + appendMessage(role, content): void                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 测试用例

### 测试 1：基础子任务执行

**目标**：验证父 Agent 能成功创建子 Agent 并获取结果

**步骤**：
```bash
cd D:\ProgramData\Agent\agent_demo
mvn exec:java -Dexec.mainClass="cn.edu.agent.AgentApplication"
```

**输入**：
```
请用 task 工具创建一个子 Agent，让它读取 pom.xml 文件并总结项目依赖
```

**预期输出**：
```
[TaskTool] Spawning subagent: 请用 task 工具创建一个子 Agent，让它读取 pom.xml 文件并总结项目依赖...
[SubAgent] 🤔 思考: 我需要读取 pom.xml 文件
[SubAgent] 🔧 调用工具: read_file
[SubAgent]    输出: 
<?xml version="1.0" encoding="UTF-8"?>
...
Claude >> 子任务已完成。项目使用了以下依赖：
- OkHttp 4.12.0 (HTTP 客户端)
- Jackson 2.17.0 (JSON 处理)
- dotenv-java 3.0.0 (环境变量)
- Lombok 1.18.32 (代码简化)
```

**验证点**：
- ✅ 看到 `[TaskTool]` 日志
- ✅ 看到 `[SubAgent]` 前缀的日志
- ✅ 父 Agent 收到子 Agent 的摘要结果

---

### 测试 2：子 Agent 工具权限限制

**目标**：验证子 Agent 不能调用 task 工具（防止递归）

**输入**：
```
请用 task 工具创建一个子 Agent，让它再创建一个孙 Agent 来读取 README.md
```

**预期输出**：
```
[SubAgent] 🔧 调用工具: task
[SubAgent]    输出: 
Error: Tool task not found.
```

**验证点**：
- ✅ 子 Agent 尝试调用 task 工具失败
- ✅ 返回 "Tool task not found" 错误

---

### 测试 3：子 Agent 迭代限制

**目标**：验证子 Agent 达到最大迭代次数后会停止

**配置**：
在 `.env` 中设置：
```
SUBAGENT_MAX_ITERATIONS=3
```

**输入**：
```
请用 task 工具创建一个子 Agent，让它执行一个需要很多步骤的复杂任务：
1. 读取 pom.xml
2. 读取 README.md
3. 读取 .gitignore
4. 统计所有文件的总行数
5. 创建报告文件
```

**预期输出**：
```
[SubAgent] 🔧 调用工具: read_file
[SubAgent] 🔧 调用工具: read_file
[SubAgent] 🔧 调用工具: read_file
Claude >> 子任务未完成，已达到迭代限制（3 次）
```

**验证点**：
- ✅ 子 Agent 最多执行 3 次工具调用
- ✅ 返回 truncated 状态的摘要

---

### 测试 4：子 Agent 独立上下文

**目标**：验证子 Agent 的上下文与父 Agent 隔离

**步骤**：
1. 先与父 Agent 对话建立上下文：
```
你 >> 我的名字是张三，请记住
Claude >> 好的，我记住了你的名字是张三
```

2. 然后创建子 Agent：
```
你 >> 请用 task 工具创建一个子 Agent，问它我的名字是什么
```

**预期输出**：
```
[SubAgent] 🤔 思考: 我不知道你的名字，因为我是新创建的子 Agent
Claude >> 子 Agent 表示它不知道你的名字，因为它有独立的上下文
```

**验证点**：
- ✅ 子 Agent 无法访问父 Agent 的对话历史
- ✅ 证明上下文隔离有效

---

### 测试 5：子 Agent 错误处理

**目标**：验证子 Agent 执行出错时的处理

**输入**：
```
请用 task 工具创建一个子 Agent，让它读取一个不存在的文件 nonexistent.txt
```

**预期输出**：
```
[SubAgent] 🔧 调用工具: read_file
[SubAgent]    输出: 
❌ 文件不存在: nonexistent.txt
Claude >> 子任务执行失败：文件不存在
```

**验证点**：
- ✅ 子 Agent 能正常处理工具执行错误
- ✅ 错误信息正确传递给父 Agent

---

### 测试 6：复杂多步骤子任务

**目标**：验证子 Agent 能完成需要多个工具调用的复杂任务

**输入**：
```
请用 task 工具创建一个子 Agent 完成以下任务：
1. 读取 README.md 文件
2. 统计字数
3. 创建 word_count.txt 文件写入统计结果
4. 读取刚创建的文件验证内容
```

**预期输出**：
```
[SubAgent] 🔧 调用工具: read_file
[SubAgent] 🔧 调用工具: bash
[SubAgent] 🔧 调用工具: write_file
[SubAgent] 🔧 调用工具: read_file
Claude >> 子任务已完成。README.md 共有 5 个单词，结果已写入 word_count.txt 并验证成功。
```

**验证点**：
- ✅ 子 Agent 能按顺序执行多个工具
- ✅ 子 Agent 能使用前一步的结果
- ✅ 最终返回完整的任务摘要

---

## 配置说明

### 环境变量（.env）

```properties
# 子 Agent 最大迭代次数（默认 10）
SUBAGENT_MAX_ITERATIONS=10
```

### 代码配置

在 `AppConfig.java` 中：
```java
public static int getSubagentMaxIterations() {
    String value = dotenv.get("SUBAGENT_MAX_ITERATIONS", "10");
    return Integer.parseInt(value);
}
```

---

## 实现检查清单

- [x] TaskTool 实现
- [x] SubAgentRunner 接口
- [x] DefaultSubAgentRunner 实现
- [x] AgentContext 数据结构
- [x] SubAgentResult 数据结构
- [x] AgentRole 枚举（PARENT / CHILD）
- [x] ToolRegistry 支持角色过滤
- [x] 父 Agent 注册 TaskTool
- [x] 子 Agent 迭代限制
- [x] 子 Agent 日志前缀标识
- [x] 错误处理和结果摘要

---

## 常见问题

### Q1: 为什么子 Agent 不能创建孙 Agent？
**A**: 防止递归调用导致的复杂性和资源消耗。通过 `AgentRole` 限制工具访问权限。

### Q2: 子 Agent 的最大迭代次数如何确定？
**A**: 默认 10 次，可通过环境变量配置。需要平衡任务复杂度和 API 成本。

### Q3: 子 Agent 失败会影响父 Agent 吗？
**A**: 不会。子 Agent 的错误会被捕获并作为工具结果返回给父 Agent，由父 Agent 决定如何处理。

### Q4: 子 Agent 能访问父 Agent 的 todo 列表吗？
**A**: 能。todo 工具是基础工具，父子共享同一个 `TodoManager` 实例。

---

*生成时间: 2026-04-21*  
*版本: S04 - Subagents 功能测试*
