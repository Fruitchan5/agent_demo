package cn.edu.agent.tool.impl;

import cn.edu.agent.background.BackgroundTaskManager;
import cn.edu.agent.tool.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

public class BackgroundRunTool implements AgentTool {

    private final BackgroundTaskManager taskManager;

    public BackgroundRunTool(BackgroundTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public String getName() {
        return "background_run";
    }

    @Override
    public String getDescription() {
        return "在后台执行 shell 命令，立即返回任务 ID。用于长时间运行的命令（构建、测试、服务器启动等）。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("type", "string");
        command.put("description", "要执行的 shell 命令");
        properties.put("command", command);

        Map<String, Object> workDir = new LinkedHashMap<>();
        workDir.put("type", "string");
        workDir.put("description", "工作目录（可选，默认当前目录）");
        properties.put("work_dir", workDir);

        Map<String, Object> timeout = new LinkedHashMap<>();
        timeout.put("type", "integer");
        timeout.put("description", "超时时间（秒，可选）");
        properties.put("timeout_seconds", timeout);

        schema.put("properties", properties);
        schema.put("required", new String[]{"command"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String command = (String) input.get("command");
        String workDir = (String) input.get("work_dir");
        Integer timeout = input.containsKey("timeout_seconds")
            ? ((Number) input.get("timeout_seconds")).intValue()
            : null;

        System.out.println("🚀 启动后台任务: " + command);
        return taskManager.runInBackground(command, workDir, timeout);
    }
}
