package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.worktree.WorktreeManager;

import java.util.Map;

/**
 * 删除worktree的Tool
 */
public class WorktreeRemoveTool implements AgentTool {

    private final WorktreeManager worktreeManager;

    public WorktreeRemoveTool(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }

    @Override
    public String getName() {
        return "worktree_remove";
    }

    @Override
    public String getDescription() {
        return "Remove a worktree. Optionally complete the bound task. " +
               "Use force=true to ignore uncommitted changes.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "name", Map.of(
                    "type", "string",
                    "description", "Worktree name to remove"
                ),
                "force", Map.of(
                    "type", "boolean",
                    "description", "Force removal even with uncommitted changes (default: false)"
                ),
                "complete_task", Map.of(
                    "type", "boolean",
                    "description", "Mark bound task as completed (default: false)"
                )
            ),
            "required", new String[]{"name"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String name = (String) input.get("name");
        boolean force = (boolean) input.getOrDefault("force", false);
        boolean completeTask = (boolean) input.getOrDefault("complete_task", false);

        worktreeManager.remove(name, force, completeTask);

        StringBuilder result = new StringBuilder();
        result.append("Removed worktree '").append(name).append("'");
        if (completeTask) {
            result.append(" and completed bound task");
        }

        return result.toString();
    }
}
