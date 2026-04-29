package cn.edu.agent.tool.impl;

import cn.edu.agent.task.TaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

public class TaskUpdateTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskUpdateTool(TaskManager taskManager) { this.taskManager = taskManager; }

    @Override public String getName() { return "task_update"; }

    @Override
    public String getDescription() {
        return "更新任务状态或依赖。status 可选：pending / in_progress / completed。"
             + "completed 时自动解锁依赖该任务的其他任务。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "task_id",          Map.of("type", "integer", "description", "任务 ID（必填）"),
                "status",           Map.of("type", "string",
                                           "enum", List.of("pending", "in_progress", "completed"),
                                           "description", "新状态（可选）"),
                "add_blocked_by",   Map.of("type", "array", "items", Map.of("type", "integer"),
                                           "description", "新增前置依赖 ID（可选）"),
                "remove_blocked_by",Map.of("type", "array", "items", Map.of("type", "integer"),
                                           "description", "移除前置依赖 ID（可选）")
            ),
            "required", new String[]{"task_id"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        Object idObj = input.get("task_id");
        if (idObj == null) return "Error: task_id is required";
        List<Integer> add = TaskCreateTool.toIntList(input.get("add_blocked_by"));
        List<Integer> remove = TaskCreateTool.toIntList(input.get("remove_blocked_by"));
        return taskManager.toJson(taskManager.update(
            ((Number) idObj).intValue(),
            (String) input.get("status"),
            add.isEmpty() ? null : add,
            remove.isEmpty() ? null : remove
        ));
    }
}
