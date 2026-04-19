package cn.edu.agent.tool.impl;

import cn.edu.agent.todo.TodoManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * Todo 工具：委托 {@link TodoManager} 管理带状态待办，并配合单 in_progress 约束。
 */
public class TodoTool implements AgentTool {

    private final TodoManager todoManager;

    public TodoTool(TodoManager todoManager) {
        this.todoManager = todoManager;
    }

    @Override
    public String getName() {
        return "todo";
    }

    @Override
    public String getDescription() {
        return "管理带状态的多步待办（pending / in_progress / done）。同一时间仅允许一项 in_progress。"
                + "用于规划与追踪任务，避免跳步或丢失进度。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "操作类型：add、update、list、delete",
                                "enum", List.of("add", "update", "list", "delete")
                        ),
                        "id", Map.of(
                                "type", "string",
                                "description", "待办 ID（update / delete 必填）"
                        ),
                        "title", Map.of(
                                "type", "string",
                                "description", "标题（add 必填；update 时可选，用于改名）"
                        ),
                        "status", Map.of(
                                "type", "string",
                                "description", "状态（update 可选）：PENDING / IN_PROGRESS / COMPLETED（语义同 pending / in_progress / done）",
                                "enum", List.of("PENDING", "IN_PROGRESS", "COMPLETED")
                        )
                ),
                "required", new String[]{"action"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String action = (String) input.get("action");
        if (action == null || action.trim().isEmpty()) {
            return "❌ 错误: 必须指定操作类型（action）";
        }
        System.out.println("\n[TodoTool] 📝 执行操作: " + action);
        try {
            return switch (action.toLowerCase()) {
                case "add" -> todoManager.add((String) input.get("title"));
                case "update" -> {
                    try {
                        yield todoManager.update(
                                (String) input.get("id"),
                                (String) input.get("status"),
                                (String) input.get("title"));
                    } catch (IllegalStateException e) {
                        yield "❌ 状态不合法: " + e.getMessage();
                    } catch (IllegalArgumentException e) {
                        yield "❌ " + e.getMessage();
                    }
                }
                case "list" -> todoManager.listRendered();
                case "delete" -> todoManager.delete((String) input.get("id"));
                default -> "❌ 错误: 未知的操作类型: " + action + "。支持的类型：add, update, list, delete";
            };
        } catch (Exception e) {
            return "❌ 执行失败: " + e.getMessage();
        }
    }
}
