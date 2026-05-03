package cn.edu.agent.tool;

import cn.edu.agent.auth.AuthenticationService;
import cn.edu.agent.auth.UserRepository;
import cn.edu.agent.background.BackgroundTaskManager;
import cn.edu.agent.monitor.MonitoredTool;
import cn.edu.agent.task.TaskManager;
import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.todo.TodoManager;
import cn.edu.agent.skill.SkillLoader;
import cn.edu.agent.tool.impl.*;
import cn.edu.agent.worktree.WorktreeManager;
import cn.edu.agent.worktree.WorktreeManagerImpl;

import java.nio.file.Paths;

import java.util.*;
import java.util.stream.Collectors;

public class ToolRegistry {
    // 基础工具：父子共享
    private final Map<String, AgentTool> baseTools = new LinkedHashMap<>();
    // 父端专属工具（如 task）
    private final Map<String, AgentTool> parentOnlyTools = new LinkedHashMap<>();

    private final TodoManager todoManager = new TodoManager();
    private final TaskManager taskManager = new TaskManager(Paths.get(".tasks"));
    private final BackgroundTaskManager backgroundTaskManager = new BackgroundTaskManager();
    private final AuthenticationService authenticationService;
    private final WorktreeManager worktreeManager;

    // s09: teammate manager (initialized later to avoid circular dependency)
    private TeammateManager teammateManager;

    public ToolRegistry() {
        // Initialize authentication service
        UserRepository userRepository = new UserRepository(".auth/users.json");
        this.authenticationService = new AuthenticationService(userRepository);

        // s12: Initialize worktree manager
        this.worktreeManager = new WorktreeManagerImpl(Paths.get(System.getProperty("user.dir")), taskManager);

        registerBase(new BashTool());
        registerBase(new ReadFileTool());
        registerBase(new WriteFileTool());
        registerBase(new EditFileTool());
        registerBase(new TodoTool(todoManager));
        registerBase(new TaskCreateTool(taskManager));
        registerBase(new TaskUpdateTool(taskManager));
        registerBase(new TaskListTool(taskManager));
        registerBase(new TaskGetTool(taskManager));
        registerBase(new BackgroundRunTool(backgroundTaskManager));
        registerBase(new BackgroundCheckTool(backgroundTaskManager));
        registerBase(new BackgroundCancelTool(backgroundTaskManager));
        
        // Register authentication tools
        registerBase(new LoginTool(authenticationService));
        registerBase(new LogoutTool(authenticationService));
        registerBase(new RegisterTool(authenticationService));
        registerBase(new ValidateSessionTool(authenticationService));
        
        // s06：尝试注册 CompactTool（若类不存在则静默忽略）
        registerOptionalBase("cn.edu.agent.tool.impl.CompactTool");
        // s09：初始化 TeammateManager 并注册所有团队工具
        // s11：传入 TaskManager 以支持自主任务认领
        // s12：传入 WorktreeManager 以支持任务隔离
        this.teammateManager = new TeammateManager(Paths.get(".team"), this, taskManager, worktreeManager);
        registerBase(new SendMessageTool(teammateManager, "lead"));
        registerBase(new ReadInboxTool(teammateManager, "lead"));
        registerParentOnly(new SpawnTeammateTool(teammateManager));
        registerParentOnly(new ListTeammatesTool(teammateManager));
        registerParentOnly(new BroadcastTool(teammateManager));

        // s10：注册协议工具
        // Lead专属工具
        registerParentOnly(new ShutdownRequestTool(teammateManager, teammateManager.getProtocolManager()));
        registerParentOnly(new PlanResponseTool(teammateManager.getProtocolManager(), teammateManager.getMessageBus()));
        registerParentOnly(new ListPendingRequestsTool(teammateManager.getProtocolManager()));
        registerParentOnly(new CancelRequestTool(teammateManager.getProtocolManager(), teammateManager.getMessageBus()));

        // Teammate可用的协议工具（注册stub，实际执行在TeammateManager中）
        registerBase(new PlanRequestToolStub());
        registerBase(new ShutdownResponseToolStub());

        // s11：注册 idle 工具（teammate 可用）
        registerBase(new IdleTool());

        // s12：注册 worktree 工具
        registerBase(new WorktreeCreateTool(worktreeManager));
        registerBase(new WorktreeListTool(worktreeManager));
        registerBase(new WorktreeStatusTool(worktreeManager));
        registerBase(new WorktreeRemoveTool(worktreeManager));
        registerBase(new WorktreeKeepTool(worktreeManager));
    }

    public ToolRegistry(SkillLoader skillLoader) {
        this();
        registerBase(new LoadSkillTool(skillLoader));
    }

    public ToolRegistry(SkillLoader skillLoader, TeammateManager teammateManager) {
        this(skillLoader);
        this.teammateManager = teammateManager;
        // s09: register teammate tools
        registerBase(new SendMessageTool(teammateManager, "lead"));
        registerBase(new ReadInboxTool(teammateManager, "lead"));
        registerParentOnly(new SpawnTeammateTool(teammateManager));
        registerParentOnly(new ListTeammatesTool(teammateManager));
        registerParentOnly(new BroadcastTool(teammateManager));
    }

    public TodoManager getTodoManager() {
        return todoManager;
    }

    protected void registerBase(AgentTool tool) {
        baseTools.put(tool.getName(), new MonitoredTool(tool));
    }

    private void registerOptionalBase(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof AgentTool tool) {
                registerBase(tool);
            }
        } catch (Exception ignored) {
            // CompactTool may not be available before related feature branch is merged.
        }
    }

    public void registerParentOnly(AgentTool tool) {
        parentOnlyTools.put(tool.getName(), new MonitoredTool(tool));
    }

    public AgentTool getTool(String name) {
        AgentTool tool = baseTools.get(name);
        if (tool != null) {
            return tool;
        }
        return parentOnlyTools.get(name);
    }

    public Set<String> getToolNames() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(baseTools.keySet());
        names.addAll(parentOnlyTools.keySet());
        return Collections.unmodifiableSet(names);
    }

    // 将工具列表转换成 LLM 接口需要的格式
    public List<Map<String, Object>> getToolsForLlm(AgentRole role) {
        Map<String, AgentTool> result = new LinkedHashMap<>(baseTools);
        if (role == AgentRole.PARENT) {
            result.putAll(parentOnlyTools);
        }
        return result.values().stream()
                .map(t -> Map.of(
                        "name", t.getName(),
                        "description", t.getDescription(),
                        "input_schema", t.getInputSchema()
                ))
                .collect(Collectors.toList());
    }

    public BackgroundTaskManager getBackgroundTaskManager() {
        return backgroundTaskManager;
    }

    public TeammateManager getTeammateManager() {
        return teammateManager;
    }
}
