package cn.edu.agent.tool.impl;

import cn.edu.agent.background.BackgroundTaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

public class BackgroundCheckTool implements AgentTool {

    private final BackgroundTaskManager taskManager;

    public BackgroundCheckTool(BackgroundTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public String getName() {
        return "background_check";
    }

    @Override
    public String getDescription() {
        return "检查后台任务状态和输出。可以检查单个任务或列出所有任务。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> taskId = new LinkedHashMap<>();
        taskId.put("type", "string");
        taskId.put("description", "任务 ID（可选，不提供则列出所有任务）");
        properties.put("task_id", taskId);

        schema.put("properties", properties);

        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String taskId = (String) input.get("task_id");

        if (taskId == null || taskId.isEmpty()) {
            System.out.println("📋 列出所有后台任务");
            return taskManager.listTasks();
        } else {
            System.out.println("🔍 检查任务: " + taskId);
            return taskManager.checkTask(taskId);
        }
    }
}
