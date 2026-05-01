package cn.edu.agent.tool.impl;

import cn.edu.agent.background.BackgroundTaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

public class BackgroundCancelTool implements AgentTool {

    private final BackgroundTaskManager taskManager;

    public BackgroundCancelTool(BackgroundTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public String getName() {
        return "background_cancel";
    }

    @Override
    public String getDescription() {
        return "取消正在运行的后台任务。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> taskId = new LinkedHashMap<>();
        taskId.put("type", "string");
        taskId.put("description", "要取消的任务 ID");
        properties.put("task_id", taskId);

        schema.put("properties", properties);
        schema.put("required", new String[]{"task_id"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String taskId = (String) input.get("task_id");

        System.out.println("❌ 取消任务: " + taskId);
        return taskManager.cancelTask(taskId);
    }
}
