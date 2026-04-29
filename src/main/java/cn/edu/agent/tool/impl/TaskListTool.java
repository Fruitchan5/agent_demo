package cn.edu.agent.tool.impl;

import cn.edu.agent.task.TaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.Map;

public class TaskListTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskListTool(TaskManager taskManager) { this.taskManager = taskManager; }

    @Override public String getName() { return "task_list"; }

    @Override
    public String getDescription() {
        return "列出所有任务，含状态、前置依赖（blockedBy）和后继任务（blocks）。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        return taskManager.toJson(taskManager.listAll());
    }
}
