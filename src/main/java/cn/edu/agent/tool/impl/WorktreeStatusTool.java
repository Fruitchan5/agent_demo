package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.worktree.WorktreeManager;

import java.util.Map;

/**
 * 查看worktree状态的Tool
 */
public class WorktreeStatusTool implements AgentTool {

    private final WorktreeManager worktreeManager;

    public WorktreeStatusTool(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }

    @Override
    public String getName() {
        return "worktree_status";
    }

    @Override
    public String getDescription() {
        return "Get git status of a worktree (shows uncommitted changes, branch info).";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "name", Map.of(
                    "type", "string",
                    "description", "Worktree name"
                )
            ),
            "required", new String[]{"name"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String name = (String) input.get("name");
        String status = worktreeManager.status(name);
        return "Status of worktree '" + name + "':\n" + status;
    }
}
