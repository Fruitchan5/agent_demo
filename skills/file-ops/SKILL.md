---
name: file-ops
description: Safely read, write, and edit files within the workspace. Use when working with project files, configs, or source code in the Java agent project.
tags: file, workspace, io
---

# File Operations Skill

你现在是工作区文件操作专家。所有操作必须通过工具完成，且严格限制在 `WORKDIR` 内。

## 安全原则

**必须遵守**：所有路径在操作前都要经过 `safePath()` 校验，禁止访问 `../` 以外目录。

## 操作指南

### 读文件
```
read_file(path="src/main/java/cn/edu/agent/core/AgentLoop.java")
read_file(path="pom.xml", limit=50)   # 只读前 50 行
```
- 超过 50000 字符的文件会自动截断
- 用 `limit` 参数避免大文件撑爆上下文

### 写文件
```
write_file(path="skills/my-skill/SKILL.md", content="---\nname: my-skill\n...")
```
- 父目录不存在会自动创建
- 覆盖写，操作前确认路径正确

### 精确编辑（推荐用于改代码）
```
edit_file(
  path="src/.../ToolRegistry.java",
  old_text="registerBase(new BashTool());",
  new_text="registerBase(new BashTool());\n        registerBase(new LoadSkillTool(skillLoader));"
)
```
- 只替换**第一次**匹配，`old_text` 必须在文件中唯一
- 改代码优先用 `edit_file` 而非全量 `write_file`，减少误改风险

### 查看目录结构
```bash
bash(command="find src -name '*.java' | sort")
bash(command="ls -la skills/")
```

## 常用路径（本项目）

| 内容 | 路径 |
|------|------|
| 主入口 | `src/main/java/cn/edu/agent/AgentApplication.java` |
| AgentLoop | `src/main/java/cn/edu/agent/core/AgentLoop.java` |
| ToolRegistry | `src/main/java/cn/edu/agent/tool/ToolRegistry.java` |
| SkillLoader（新增） | `src/main/java/cn/edu/agent/skill/SkillLoader.java` |
| Skills 目录 | `skills/<skill-name>/SKILL.md` |
| 环境配置 | `.env` |

## 排查问题流程

1. `read_file` 读目标文件，确认问题位置
2. 用 `bash` 运行 `grep` 确认修改点唯一
3. `edit_file` 精确替换
4. `bash` 编译验证：`mvn compile -q`
