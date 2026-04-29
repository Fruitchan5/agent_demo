package cn.edu.agent.tool.impl;

import cn.edu.agent.task.TaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.Map;

public class TaskGetTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskGetTool(TaskManager taskManager) { this.taskManager = taskManager; }

    @Override public String getName() { return "task_get"; }

    @Override
    public String getDescription() {
        return "获取指定任务详情，含 blockedBy（前置）和 blocks（后继）。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "task_id", Map.of("type", "integer", "description", "任务 ID（必填）")
            ),
            "required", new String[]{"task_id"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        Object idObj = input.get("task_id");
        if (idObj == null) return "Error: task_id is required";
        return taskManager.toJson(taskManager.get(((Number) idObj).intValue()));
    }
}
