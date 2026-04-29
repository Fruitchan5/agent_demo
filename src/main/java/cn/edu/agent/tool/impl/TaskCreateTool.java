package cn.edu.agent.tool.impl;

import cn.edu.agent.task.TaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TaskCreateTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskCreateTool(TaskManager taskManager) { this.taskManager = taskManager; }

    @Override public String getName() { return "task_create"; }

    @Override
    public String getDescription() {
        return "创建一个新任务，支持设置前置依赖（blocked_by）。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "subject",     Map.of("type", "string",  "description", "任务标题（必填）"),
                "description", Map.of("type", "string",  "description", "任务描述（可选）"),
                "blocked_by",  Map.of("type", "array", "items", Map.of("type", "integer"),
                                      "description", "前置任务 ID 列表（可选）")
            ),
            "required", new String[]{"subject"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String subject = (String) input.get("subject");
        if (subject == null || subject.isBlank()) return "Error: subject is required";
        return taskManager.toJson(taskManager.create(
            subject,
            (String) input.get("description"),
            toIntList(input.get("blocked_by"))
        ));
    }

    static List<Integer> toIntList(Object val) {
        if (!(val instanceof List<?> list)) return List.of();
        return list.stream().map(o -> ((Number) o).intValue()).collect(Collectors.toList());
    }
}
