# S12 Worktree 任务隔离 - Java 实现设计方案

## 一、功能分析

### 1.1 核心问题
- **s11 的问题**：所有任务共享一个工作目录，多个 Agent 并发执行时会互相污染（文件冲突、状态混乱）
- **解决目标**：为每个任务分配独立的 git worktree，实现任务级别的文件系统隔离

### 1.2 核心功能需求

#### Control Plane（控制平面）
- 任务生命周期管理：创建、认领、完成
- 任务状态追踪：pending → in_progress → completed
- 任务与 worktree 的绑定关系

#### Execution Plane（执行平面）
- Worktree 生命周期管理：创建、激活、删除/保留
- 命令执行隔离：在指定 worktree 中执行命令
- Worktree 状态追踪：absent → active → removed/kept

#### Event System（事件系统）
- 记录所有关键操作：任务创建、worktree 创建、命令执行、状态变更
- 支持审计和调试

### 1.3 状态机设计

```
Task State Machine:
  pending ──claim──> in_progress ──complete──> completed
                          │
                          └──abandon──> pending

Worktree State Machine:
  absent ──create──> active ──remove──> removed
                       │
                       └──keep──> kept
```

---

## 二、接口设计

### 2.1 核心接口

#### WorktreeManager（Worktree 管理器）
```java
package cn.edu.agent.worktree;

public interface WorktreeManager {
    
    /**
     * 为任务创建独立的 worktree
     * @param taskId 任务 ID
     * @param branch 基于的分支（默认 main）
     * @return worktree 路径
     */
    Path createWorktree(int taskId, String branch);
    
    /**
     * 获取任务对应的 worktree 路径
     * @param taskId 任务 ID
     * @return worktree 路径，不存在返回 null
     */
    Path getWorktreePath(int taskId);
    
    /**
     * 在指定 worktree 中执行命令
     * @param taskId 任务 ID
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    CommandResult executeInWorktree(int taskId, String command);
    
    /**
     * 删除 worktree（任务完成后清理）
     * @param taskId 任务 ID
     * @param force 是否强制删除（即使有未提交的更改）
     */
    void removeWorktree(int taskId, boolean force);
    
    /**
     * 保留 worktree（用于调试或后续使用）
     * @param taskId 任务 ID
     */
    void keepWorktree(int taskId);
    
    /**
     * 列出所有活跃的 worktree
     * @return worktree 列表
     */
    List<WorktreeInfo> listWorktrees();
    
    /**
     * 清理所有已完成任务的 worktree
     */
    void cleanupCompletedWorktrees();
}
```

#### WorktreeInfo（Worktree 信息）
```java
package cn.edu.agent.worktree;

public class WorktreeInfo {
    private int taskId;
    private Path path;
    private String branch;
    private WorktreeState state;
    private Instant createdAt;
    private Instant lastAccessedAt;
    
    // getters and setters
}

public enum WorktreeState {
    ABSENT,   // 尚未创建
    ACTIVE,   // 活跃使用中
    REMOVED,  // 已删除
    KEPT      // 保留（不自动清理）
}
```

#### TaskWorktreeBinding（任务-Worktree 绑定）
```java
package cn.edu.agent.worktree;

public class TaskWorktreeBinding {
    private int taskId;
    private Path worktreePath;
    private String branch;
    private Instant boundAt;
    
    // getters and setters
}
```

### 2.2 扩展现有接口

#### TaskManager（扩展）
```java
package cn.edu.agent.task;

public interface TaskManager {
    
    // 现有方法...
    
    /**
     * 创建任务并自动分配 worktree
     * @param subject 任务标题
     * @param description 任务描述
     * @param autoWorktree 是否自动创建 worktree
     * @return 任务 ID
     */
    int createTask(String subject, String description, boolean autoWorktree);
    
    /**
     * 认领任务并激活 worktree
     * @param taskId 任务 ID
     * @param agentName Agent 名称
     * @return 是否认领成功
     */
    boolean claimTaskWithWorktree(int taskId, String agentName);
    
    /**
     * 完成任务并清理 worktree
     * @param taskId 任务 ID
     * @param keepWorktree 是否保留 worktree
     */
    void completeTaskWithCleanup(int taskId, boolean keepWorktree);
}
```

### 2.3 事件系统接口

#### WorktreeEventRecorder（事件记录器）
```java
package cn.edu.agent.worktree;

public interface WorktreeEventRecorder {
    
    /**
     * 记录 worktree 创建事件
     */
    void recordWorktreeCreated(int taskId, Path path, String branch);
    
    /**
     * 记录命令执行事件
     */
    void recordCommandExecuted(int taskId, String command, CommandResult result);
    
    /**
     * 记录 worktree 删除事件
     */
    void recordWorktreeRemoved(int taskId, boolean forced);
    
    /**
     * 记录 worktree 保留事件
     */
    void recordWorktreeKept(int taskId);
    
    /**
     * 查询任务的所有事件
     */
    List<WorktreeEvent> getTaskEvents(int taskId);
}

public class WorktreeEvent {
    private int taskId;
    private WorktreeEventType type;
    private String details;
    private Instant timestamp;
    
    // getters and setters
}

public enum WorktreeEventType {
    WORKTREE_CREATED,
    COMMAND_EXECUTED,
    WORKTREE_REMOVED,
    WORKTREE_KEPT,
    WORKTREE_ERROR
}
```

---

## 三、架构设计

### 3.1 目录结构

```
project-root/
├── .tasks/                    # Control Plane
│   ├── task_1.json           # 任务元数据
│   ├── task_2.json
│   └── ...
├── .worktrees/               # Execution Plane
│   ├── task_1/               # 任务 1 的独立工作树
│   ├── task_2/               # 任务 2 的独立工作树
│   └── ...
├── .worktree-bindings/       # 绑定关系
│   └── bindings.json         # taskId -> worktreePath 映射
└── .worktree-events/         # 事件日志
    ├── task_1.jsonl          # 任务 1 的事件流
    ├── task_2.jsonl
    └── ...
```

### 3.2 类图

```
┌─────────────────────┐
│   TaskManager       │
│  (existing + ext)   │
└──────────┬──────────┘
           │ uses
           ▼
┌─────────────────────┐
│  WorktreeManager    │◄──────┐
│  (new interface)    │       │
└──────────┬──────────┘       │
           │                  │
           │ uses             │ uses
           ▼                  │
┌─────────────────────┐       │
│ WorktreeManagerImpl │       │
│  (implementation)   │       │
└──────────┬──────────┘       │
           │                  │
           │ uses             │
           ▼                  │
┌─────────────────────┐       │
│ WorktreeEventRecorder│◄──────┘
│  (event logging)    │
└─────────────────────┘
```

### 3.3 数据流

```
1. 创建任务流程：
   User → TaskManager.createTask(autoWorktree=true)
        → WorktreeManager.createWorktree(taskId)
        → git worktree add .worktrees/task_N
        → WorktreeEventRecorder.recordWorktreeCreated()
        → 返回 taskId

2. 执行命令流程：
   Agent → WorktreeManager.executeInWorktree(taskId, command)
         → 切换到 .worktrees/task_N
         → 执行命令
         → WorktreeEventRecorder.recordCommandExecuted()
         → 返回结果

3. 完成任务流程：
   Agent → TaskManager.completeTaskWithCleanup(taskId, keepWorktree=false)
         → TaskManager.updateTaskStatus(completed)
         → WorktreeManager.removeWorktree(taskId)
         → git worktree remove .worktrees/task_N
         → WorktreeEventRecorder.recordWorktreeRemoved()
```

---

## 四、实现方案

### 4.1 核心实现类

#### WorktreeManagerImpl
```java
package cn.edu.agent.worktree.impl;

public class WorktreeManagerImpl implements WorktreeManager {
    
    private final Path projectRoot;
    private final Path worktreesDir;
    private final Path bindingsFile;
    private final WorktreeEventRecorder eventRecorder;
    private final Map<Integer, TaskWorktreeBinding> bindings;
    
    public WorktreeManagerImpl(Path projectRoot, WorktreeEventRecorder eventRecorder) {
        this.projectRoot = projectRoot;
        this.worktreesDir = projectRoot.resolve(".worktrees");
        this.bindingsFile = projectRoot.resolve(".worktree-bindings/bindings.json");
        this.eventRecorder = eventRecorder;
        this.bindings = loadBindings();
        
        // 确保目录存在
        Files.createDirectories(worktreesDir);
        Files.createDirectories(bindingsFile.getParent());
    }
    
    @Override
    public Path createWorktree(int taskId, String branch) {
        // 1. 检查是否已存在
        if (bindings.containsKey(taskId)) {
            throw new IllegalStateException("Worktree already exists for task " + taskId);
        }
        
        // 2. 生成 worktree 路径
        Path worktreePath = worktreesDir.resolve("task_" + taskId);
        
        // 3. 执行 git worktree add
        String command = String.format("git worktree add %s %s", 
            worktreePath.toString(), branch);
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
        pb.directory(projectRoot.toFile());
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Failed to create worktree: " + readError(process));
        }
        
        // 4. 记录绑定关系
        TaskWorktreeBinding binding = new TaskWorktreeBinding();
        binding.setTaskId(taskId);
        binding.setWorktreePath(worktreePath);
        binding.setBranch(branch);
        binding.setBoundAt(Instant.now());
        bindings.put(taskId, binding);
        saveBindings();
        
        // 5. 记录事件
        eventRecorder.recordWorktreeCreated(taskId, worktreePath, branch);
        
        return worktreePath;
    }
    
    @Override
    public CommandResult executeInWorktree(int taskId, String command) {
        // 1. 获取 worktree 路径
        Path worktreePath = getWorktreePath(taskId);
        if (worktreePath == null) {
            throw new IllegalStateException("No worktree found for task " + taskId);
        }
        
        // 2. 在 worktree 目录中执行命令
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
        pb.directory(worktreePath.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        String output = readOutput(process);
        int exitCode = process.waitFor();
        
        CommandResult result = new CommandResult(exitCode, output);
        
        // 3. 记录事件
        eventRecorder.recordCommandExecuted(taskId, command, result);
        
        return result;
    }
    
    @Override
    public void removeWorktree(int taskId, boolean force) {
        Path worktreePath = getWorktreePath(taskId);
        if (worktreePath == null) {
            return; // 已经不存在
        }
        
        // 执行 git worktree remove
        String command = force ? 
            "git worktree remove --force " + worktreePath :
            "git worktree remove " + worktreePath;
            
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
        pb.directory(projectRoot.toFile());
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Failed to remove worktree: " + readError(process));
        }
        
        // 移除绑定
        bindings.remove(taskId);
        saveBindings();
        
        // 记录事件
        eventRecorder.recordWorktreeRemoved(taskId, force);
    }
    
    // 其他方法实现...
}
```

#### WorktreeEventRecorderImpl
```java
package cn.edu.agent.worktree.impl;

public class WorktreeEventRecorderImpl implements WorktreeEventRecorder {
    
    private final Path eventsDir;
    private final ObjectMapper objectMapper;
    
    public WorktreeEventRecorderImpl(Path projectRoot) {
        this.eventsDir = projectRoot.resolve(".worktree-events");
        this.objectMapper = new ObjectMapper();
        Files.createDirectories(eventsDir);
    }
    
    @Override
    public void recordWorktreeCreated(int taskId, Path path, String branch) {
        WorktreeEvent event = new WorktreeEvent();
        event.setTaskId(taskId);
        event.setType(WorktreeEventType.WORKTREE_CREATED);
        event.setDetails(String.format("Created worktree at %s from branch %s", path, branch));
        event.setTimestamp(Instant.now());
        
        appendEvent(taskId, event);
    }
    
    @Override
    public void recordCommandExecuted(int taskId, String command, CommandResult result) {
        WorktreeEvent event = new WorktreeEvent();
        event.setTaskId(taskId);
        event.setType(WorktreeEventType.COMMAND_EXECUTED);
        event.setDetails(String.format("Command: %s, Exit: %d", command, result.getExitCode()));
        event.setTimestamp(Instant.now());
        
        appendEvent(taskId, event);
    }
    
    private void appendEvent(int taskId, WorktreeEvent event) {
        Path eventFile = eventsDir.resolve("task_" + taskId + ".jsonl");
        try (FileWriter writer = new FileWriter(eventFile.toFile(), true)) {
            writer.write(objectMapper.writeValueAsString(event) + "\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to record event", e);
        }
    }
    
    @Override
    public List<WorktreeEvent> getTaskEvents(int taskId) {
        Path eventFile = eventsDir.resolve("task_" + taskId + ".jsonl");
        if (!Files.exists(eventFile)) {
            return Collections.emptyList();
        }
        
        List<WorktreeEvent> events = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(eventFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                events.add(objectMapper.readValue(line, WorktreeEvent.class));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read events", e);
        }
        
        return events;
    }
}
```

### 4.2 集成到现有系统

#### 扩展 TaskManager
```java
package cn.edu.agent.task.impl;

public class TaskManagerImpl implements TaskManager {
    
    private final WorktreeManager worktreeManager;
    
    // 现有字段...
    
    @Override
    public int createTask(String subject, String description, boolean autoWorktree) {
        // 1. 创建任务（现有逻辑）
        int taskId = createTask(subject, description);
        
        // 2. 如果需要，自动创建 worktree
        if (autoWorktree) {
            try {
                worktreeManager.createWorktree(taskId, "main");
            } catch (Exception e) {
                // 回滚任务创建
                deleteTask(taskId);
                throw new RuntimeException("Failed to create worktree for task " + taskId, e);
            }
        }
        
        return taskId;
    }
    
    @Override
    public boolean claimTaskWithWorktree(int taskId, String agentName) {
        // 1. 认领任务
        boolean claimed = claimTask(taskId, agentName);
        if (!claimed) {
            return false;
        }
        
        // 2. 确保 worktree 存在
        if (worktreeManager.getWorktreePath(taskId) == null) {
            worktreeManager.createWorktree(taskId, "main");
        }
        
        return true;
    }
    
    @Override
    public void completeTaskWithCleanup(int taskId, boolean keepWorktree) {
        // 1. 完成任务
        updateTaskStatus(taskId, TaskStatus.COMPLETED);
        
        // 2. 清理或保留 worktree
        if (keepWorktree) {
            worktreeManager.keepWorktree(taskId);
        } else {
            worktreeManager.removeWorktree(taskId, false);
        }
    }
}
```

### 4.3 Tool 接口扩展

为了让 Agent 能够使用 worktree 功能，需要添加新的 Tool：

```java
package cn.edu.agent.tool;

@Tool(name = "worktree_exec", description = "Execute command in task's isolated worktree")
public class WorktreeExecTool implements AgentTool {
    
    private final WorktreeManager worktreeManager;
    
    @Override
    public String execute(Map<String, Object> params) {
        int taskId = (int) params.get("task_id");
        String command = (String) params.get("command");
        
        CommandResult result = worktreeManager.executeInWorktree(taskId, command);
        
        return String.format("Exit code: %d\nOutput:\n%s", 
            result.getExitCode(), result.getOutput());
    }
}

@Tool(name = "worktree_cleanup", description = "Clean up task's worktree after completion")
public class WorktreeCleanupTool implements AgentTool {
    
    private final WorktreeManager worktreeManager;
    
    @Override
    public String execute(Map<String, Object> params) {
        int taskId = (int) params.get("task_id");
        boolean keep = (boolean) params.getOrDefault("keep", false);
        
        if (keep) {
            worktreeManager.keepWorktree(taskId);
            return "Worktree kept for task " + taskId;
        } else {
            worktreeManager.removeWorktree(taskId, false);
            return "Worktree removed for task " + taskId;
        }
    }
}
```

---

## 五、实施步骤

### Phase 1: 基础设施（1-2天）
1. 创建 `worktree` 包结构
2. 实现 `WorktreeManager` 接口和基础实现
3. 实现 `WorktreeEventRecorder`
4. 添加单元测试

### Phase 2: 集成（1天）
1. 扩展 `TaskManager` 接口
2. 修改 `TaskManagerImpl` 集成 worktree 功能
3. 添加集成测试

### Phase 3: Tool 层（1天）
1. 实现 `WorktreeExecTool`
2. 实现 `WorktreeCleanupTool`
3. 注册到 `ToolRegistry`
4. 更新 Tool 文档

### Phase 4: 测试与优化（1-2天）
1. 端到端测试
2. 并发场景测试
3. 性能优化
4. 文档完善

---

## 六、关键技术点

### 6.1 Git Worktree 命令
```bash
# 创建 worktree
git worktree add <path> <branch>

# 列出所有 worktree
git worktree list

# 删除 worktree
git worktree remove <path>

# 强制删除（即使有未提交的更改）
git worktree remove --force <path>

# 清理过期的 worktree 记录
git worktree prune
```

### 6.2 并发安全
- 使用 `ConcurrentHashMap` 管理 bindings
- 文件操作使用文件锁（`FileLock`）
- Worktree 创建使用原子操作

### 6.3 错误处理
- Worktree 创建失败：回滚任务创建
- 命令执行失败：记录错误事件，不影响 worktree 状态
- Worktree 删除失败：标记为 KEPT，等待手动清理

### 6.4 资源清理
- 定期清理已完成任务的 worktree
- 提供手动清理接口
- 支持保留 worktree 用于调试

---

## 七、测试策略

### 7.1 单元测试
- `WorktreeManagerImpl` 各方法测试
- `WorktreeEventRecorder` 事件记录测试
- 边界条件测试（重复创建、删除不存在的 worktree）

### 7.2 集成测试
- 任务创建 + worktree 创建
- 命令执行隔离验证
- 任务完成 + worktree 清理

### 7.3 并发测试
- 多个 Agent 同时创建 worktree
- 多个 Agent 在不同 worktree 中执行命令
- 验证文件隔离性

### 7.4 性能测试
- 大量 worktree 创建/删除性能
- 磁盘空间占用
- 命令执行开销

---

## 八、风险与限制

### 8.1 风险
1. **磁盘空间**：每个 worktree 占用额外空间
   - 缓解：定期清理，设置最大 worktree 数量
   
2. **Git 性能**：大量 worktree 可能影响 git 性能
   - 缓解：限制并发 worktree 数量，使用 shallow clone

3. **Windows 路径长度限制**：深层目录可能超过 260 字符
   - 缓解：使用短路径，启用长路径支持

### 8.2 限制
1. 所有 worktree 必须基于同一个 git 仓库
2. 不支持嵌套 worktree
3. Worktree 删除需要确保没有进程占用

---

## 九、未来扩展

1. **Worktree 池化**：预创建 worktree 池，减少创建开销
2. **远程 worktree**：支持在远程机器上创建 worktree
3. **Worktree 快照**：支持保存和恢复 worktree 状态
4. **智能清理**：基于使用频率和时间自动清理
5. **Worktree 共享**：允许多个任务共享只读 worktree

---

## 十、总结

S12 的 Worktree 任务隔离方案通过以下设计实现了任务级别的文件系统隔离：

1. **清晰的职责分离**：Control Plane 管理任务，Execution Plane 管理 worktree
2. **完整的生命周期管理**：从创建到清理的全流程支持
3. **事件驱动的可观测性**：所有操作都有事件记录
4. **灵活的集成方式**：可选的 worktree 创建，支持手动和自动模式
5. **健壮的错误处理**：失败回滚，资源清理

该方案为多 Agent 并发执行提供了坚实的基础，解决了 s11 中的文件污染问题。
