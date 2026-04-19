package cn.edu.agent.tool;

import cn.edu.agent.tool.impl.BashTool;
import cn.edu.agent.tool.impl.EditFileTool;
import cn.edu.agent.tool.impl.ReadFileTool;
import cn.edu.agent.tool.impl.WriteFileTool;

import java.util.*;

public class ToolRegistry {
    private final Map<String, AgentTool> tools = new HashMap<>();

    public ToolRegistry() {
        // 在这里注册所有的工具
        register(new BashTool());
        register(new ReadFileTool());
        register(new WriteFileTool());
        register(new EditFileTool());
    }

    private void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    public AgentTool getTool(String name) {
        return tools.get(name);
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