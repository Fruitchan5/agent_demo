package cn.edu.agent.tool;

import cn.edu.agent.todo.TodoManager;
import cn.edu.agent.tool.impl.*;

import java.util.*;

public class ToolRegistry {
    private final Map<String, AgentTool> tools = new HashMap<>();
    private final TodoManager todoManager = new TodoManager();

    public ToolRegistry() {
        register(new BashTool());
        register(new ReadFileTool());
        register(new WriteFileTool());
        register(new EditFileTool());
        register(new TodoTool(todoManager));
    }

    public TodoManager getTodoManager() {
        return todoManager;
    }

    private void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    // 将工具列表转换成 LLM 接口需要的格式
    public List<Map<String, Object>> getToolsForLlm() {
        List<Map<String, Object>> llmTools = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            llmTools.add(Map.of(
                    "name", tool.getName(),
                    "description", tool.getDescription(),
                    "input_schema", tool.getInputSchema()
            ));
        }
        return llmTools;
    }
}