package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.worktree.WorktreeManager;

import java.util.Map;

/**
 * 保留worktree的Tool（标记为kept状态，不自动清理）
 */
public class WorktreeKeepTool implements AgentTool {

    private final WorktreeManager worktreeManager;

    public WorktreeKeepTool(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }

    @Override
    public String getName() {
        return "worktree_keep";
    }

    @Override
    public String getDescription() {
        return "Mark a worktree as 'kept' to prevent automatic cleanup.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "name", Map.of(
                    "type", "string",
                    "description", "Worktree name to keep"
                )
            ),
            "required", new String[]{"name"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String name = (String) input.get("name");
        worktreeManager.keep(name);
        return "Marked worktree '" + name + "' as kept (will not be auto-cleaned)";
    }
}
