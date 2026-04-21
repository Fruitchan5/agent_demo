---
name: java-agent-builder
description: Design and extend the Java agent in this project. Use when user asks to add tools/skills, understand architecture/data flow, modify AgentLoop/ToolRegistry/SkillLoader/LlmClient, or implement s05+ skill-loading sessions. Keywords: agent, tool, skill, loop, registry, subagent, LlmClient
tags: java, agent, architecture
---

# Java Agent Builder

你现在是本项目的架构师。理解项目结构后，按"最小改动原则"扩展功能。

## 项目架构速览

```
AgentApplication (入口)
  └── AgentLoop          # 主对话循环，管理 chatHistory
        ├── LlmClient    # HTTP 调用 Anthropic API
        └── ToolManager  # 包装 ToolRegistry，附加 todo 问责逻辑
              └── ToolRegistry
                    ├── baseTools       # bash, read_file, write_file, edit_file, todo, load_skill
                    ├── parentOnlyTools # task (派生子 Agent)
                    └── SkillLoader     # 扫描 skills/*/SKILL.md，两层注入
```

## 两层 Skill 注入机制（s05 核心）

```
Layer 1（启动时）: SkillLoader.getDescriptions()
  → 注入系统提示：告诉模型有哪些 skill 可用（~100 token/skill）

Layer 2（按需）: 模型调用 load_skill("pdf")
  → LoadSkillTool.execute() → SkillLoader.getContent("pdf")
  → tool_result 返回完整 SKILL.md body（按需加载，不撑上下文）
```

## 添加新工具（标准步骤）

### 1. 新建工具类
```java
// src/main/java/cn/edu/agent/tool/impl/MyTool.java
@RequiredArgsConstructor
public class MyTool implements AgentTool {
    @Override public String getName() { return "my_tool"; }
    @Override public String getDescription() { return "做某件事，当用户...时使用"; }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "param1", Map.of("type", "string", "description", "参数说明")
            ),
            "required", new String[]{"param1"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String param1 = (String) input.get("param1");
        // 实现逻辑
        return "结果";
    }
}
```

### 2. 注册到 ToolRegistry
```java
// 基础工具（父子 Agent 共用）：
registerBase(new MyTool());

// 父端专属（只有主 Agent 能用）：
registry.registerParentOnly(new MyTool());
```

### 3. 验证
```bash
bash(command="mvn compile -q && echo OK")
```

## 添加新 Skill（标准步骤）

1. 创建目录：`skills/<skill-name>/`
2. 新建 `SKILL.md`，格式：
```markdown
---
name: skill-name
description: 一句话描述，模型据此决定是否调用 load_skill
tags: 可选标签
---

# Skill 标题

你现在是 XX 专家。按以下步骤操作：

## 步骤 / checklist / 代码示例
...
```
3. **重启 Agent** —— SkillLoader 在 `init()` 时扫描，运行期不热加载

## 关键数据结构

| 类 | 注解 | 用途 |
|----|------|------|
| `SkillMeta` | `@Data @NoArgsConstructor` | YAML frontmatter |
| `SkillEntry` | `@Value` | meta + body + path，不可变 |
| `ContentBlock` | `@Data` | LLM 返回的 text / tool_use 块 |
| `LlmResponse` | `@Data` | API 响应，含 stopReason |
| `SubAgentResult` | `@Data @Builder` | 子 Agent 执行结果 |

## 常见扩展场景

### 修改系统提示
在 `AgentApplication.main()` 里改 `systemPrompt` 字符串，或让 `SkillLoader.getDescriptions()` 返回不同内容。

### 调整 LLM 参数
在 `LlmClient.call()` 里改 `requestMap.put("max_tokens", ...)` 或添加 `temperature`。

### 子 Agent 隔离
`DefaultSubAgentRunner` 每次 `run()` 都用全新 `AgentContext`，子 Agent 看不到父 Agent 的 chatHistory —— 这是设计意图，不要改。

## 反模式（避免）

| 做法 | 问题 | 正确方式 |
|------|------|----------|
| 所有知识塞进系统提示 | 上下文爆炸 | 用 SkillLoader 按需加载 |
| 工具 execute() 里直接 throw | AgentLoop 会崩 | catch 后返回错误字符串 |
| 子 Agent 共享父 ToolRegistry 状态 | TodoManager 混乱 | 已隔离，不要传引用 |
| 手写 getter/setter | 代码冗长 | 用 Lombok `@Data` / `@Value` |
