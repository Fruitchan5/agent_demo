package cn.edu.agent.tool;

/**
 * 工具管理器 - s03：在 {@link ToolRegistry} 上增加 todo 问责式提醒与轮次计数。
 *
 * <p>rounds_since_todo：自上次模型在回复中声明调用 todo 工具以来，经过了多少次 LLM 往返；
 * 当计数 ≥ 阈值（默认 3）时，在下一条用户消息正文开头注入
 * {@value #TODO_NAG_REMINDER}，督促模型更新待办。</p>
 */
public class ToolManager {

    /** 与规范一致的提醒片段（注入到用户消息开头） */
    public static final String TODO_NAG_REMINDER = "<reminder>Update your todos.</reminder>";

    private final ToolRegistry registry;
    private int rounds_since_todo = 0;
    private int nag_threshold = 3;

    public ToolManager() {
        this(new ToolRegistry());
        System.out.println("ToolManager 已初始化 (s03)，Nag 阈值: " + nag_threshold);
    }

    public ToolManager(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolManager(int nag_threshold) {
        this(new ToolRegistry());
        if (nag_threshold < 1) {
            throw new IllegalArgumentException("提醒阈值必须 >= 1");
        }
        this.nag_threshold = nag_threshold;
        System.out.println("ToolManager 已初始化 (s03)，Nag 阈值: " + nag_threshold);
    }

    /**
     * 若已连续多轮未调用 todo，在用户输入前注入问责提醒。
     */
    public String prefixUserMessageIfNeeded(String userMessage) {
        if (rounds_since_todo >= nag_threshold) {
            System.out.println("\n[ToolManager] 注入问责提醒 (rounds_since_todo=" + rounds_since_todo + ")");
            return TODO_NAG_REMINDER + "\n" + userMessage;
        }
        return userMessage;
    }

    /** @return 当前是否达到提醒阈值（无副作用） */
    public boolean checkTodoReminder() {
        return rounds_since_todo >= nag_threshold;
    }

    public void resetTodoCounter() {
        if (rounds_since_todo > 0) {
            System.out.println("[ToolManager] 重置 todo 计数器 (原值: " + rounds_since_todo + ")");
        }
        rounds_since_todo = 0;
    }

    public void incrementRoundCounter() {
        rounds_since_todo++;
        System.out.println("[ToolManager] rounds_since_todo=" + rounds_since_todo + " / " + nag_threshold);
    }

    public int getRoundsSinceTodo() {
        return rounds_since_todo;
    }

    public int getNagThreshold() {
        return nag_threshold;
    }

    public void setNagThreshold(int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("提醒阈值必须 >= 1");
        }
        this.nag_threshold = threshold;
        System.out.println("[ToolManager] 提醒阈值已更新: " + threshold);
    }

    public int getToolCount() {
        return getToolNames().size();
    }

    public String getStatus() {
        return String.format(
                "ToolManager 状态:\n"
                        + "  - 已注册工具数: %d\n"
                        + "  - rounds_since_todo: %d\n"
                        + "  - 提醒阈值: %d",
                getToolCount(),
                rounds_since_todo,
                nag_threshold
        );
    }

    public void resetAll() {
        rounds_since_todo = 0;
        getTodoManager().clearAll();
        System.out.println("[ToolManager] 计数器与待办已重置");
    }

    public ToolRegistry getRegistry() {
        return registry;
    }

    public AgentTool getTool(String name) {
        return registry.getTool(name);
    }

    public java.util.Set<String> getToolNames() {
        return registry.getToolNames();
    }

    public java.util.List<java.util.Map<String, Object>> getToolsForLlm(AgentRole role) {
        return registry.getToolsForLlm(role);
    }

    public cn.edu.agent.todo.TodoManager getTodoManager() {
        return registry.getTodoManager();
    }
}
