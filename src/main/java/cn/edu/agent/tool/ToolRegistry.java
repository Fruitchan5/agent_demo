package cn.edu.agent.tool;

import cn.edu.agent.todo.TodoManager;
import cn.edu.agent.skill.SkillLoader;
import cn.edu.agent.tool.impl.*;

import java.util.*;
import java.util.stream.Collectors;

public class ToolRegistry {
    // 基础工具：父子共享
    private final Map<String, AgentTool> baseTools = new LinkedHashMap<>();
    // 父端专属工具（如 task）
    private final Map<String, AgentTool> parentOnlyTools = new LinkedHashMap<>();

    private final TodoManager todoManager = new TodoManager();

    public ToolRegistry() {
        registerBase(new BashTool());
        registerBase(new ReadFileTool());
        registerBase(new WriteFileTool());
        registerBase(new EditFileTool());
        registerBase(new TodoTool(todoManager));
        registerOptionalBase("cn.edu.agent.tool.impl.CompactTool");
    }

    public ToolRegistry(SkillLoader skillLoader) {
        this();
        registerBase(new LoadSkillTool(skillLoader));
    }

    public TodoManager getTodoManager() {
        return todoManager;
    }

    protected void registerBase(AgentTool tool) {
        baseTools.put(tool.getName(), tool);
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
        parentOnlyTools.put(tool.getName(), tool);
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
}