package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;

import java.util.Map;

/**
 * Idle 工具
 * Teammate 调用此工具主动进入 IDLE 状态
 */
public class IdleTool implements AgentTool {

    @Override
    public String getName() {
        return "idle";
    }

    @Override
    public String getDescription() {
        return "Signal that you have no more work. Enters idle polling phase.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", new Object[0]
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        // 实际逻辑在 TeammateManager 中处理
        // 这里只是返回一个提示信息
        return "Entering idle phase. Will poll for new tasks.";
    }
}
