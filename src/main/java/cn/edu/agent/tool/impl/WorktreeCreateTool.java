package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.worktree.WorktreeInfo;
import cn.edu.agent.worktree.WorktreeManager;

import java.util.Map;

/**
 * 创建worktree的Tool
 */
public class WorktreeCreateTool implements AgentTool {

    private final WorktreeManager worktreeManager;

    public WorktreeCreateTool(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }

    @Override
    public String getName() {
        return "worktree_create";
    }

    @Override
    public String getDescription() {
        return "Create a new git worktree for isolated work. " +
               "Optionally bind to a task. Returns worktree info.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "name", Map.of(
                    "type", "string",
                    "description", "Worktree name (1-40 chars: letters, numbers, ., _, -)"
                ),
                "task_id", Map.of(
                    "type", "integer",
                    "description", "Optional task ID to bind this worktree to"
                ),
                "base_ref", Map.of(
                    "type", "string",
                    "description", "Git reference to base the worktree on (default: HEAD)"
                )
            ),
            "required", new String[]{"name"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String name = (String) input.get("name");
        Integer taskId = input.containsKey("task_id")
            ? ((Number) input.get("task_id")).intValue()
            : null;
        String baseRef = (String) input.getOrDefault("base_ref", "HEAD");

        WorktreeInfo info = worktreeManager.create(name, taskId, baseRef);

        StringBuilder result = new StringBuilder();
        result.append("Created worktree '").append(info.getName()).append("'\n");
        result.append("  Path: ").append(info.getPath()).append("\n");
        result.append("  Branch: ").append(info.getBranch()).append("\n");
        if (info.getTaskId() != null) {
            result.append("  Bound to task: #").append(info.getTaskId()).append("\n");
        }
        result.append("  Status: ").append(info.getStatus());

        return result.toString();
    }
}
