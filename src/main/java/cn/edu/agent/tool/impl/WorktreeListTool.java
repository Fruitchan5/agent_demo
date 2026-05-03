package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.worktree.WorktreeInfo;
import cn.edu.agent.worktree.WorktreeManager;

import java.util.List;
import java.util.Map;

/**
 * 列出所有worktree的Tool
 */
public class WorktreeListTool implements AgentTool {

    private final WorktreeManager worktreeManager;

    public WorktreeListTool(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }

    @Override
    public String getName() {
        return "worktree_list";
    }

    @Override
    public String getDescription() {
        return "List all worktrees with their status and bound tasks.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", new String[0]
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        List<WorktreeInfo> worktrees = worktreeManager.listAll();

        if (worktrees.isEmpty()) {
            return "No worktrees found.";
        }

        StringBuilder result = new StringBuilder("Worktrees:\n");
        for (WorktreeInfo wt : worktrees) {
            result.append("  - ").append(wt.getName())
                  .append(" (").append(wt.getStatus()).append(")");
            if (wt.getTaskId() != null) {
                result.append(" → task #").append(wt.getTaskId());
            }
            result.append("\n    Path: ").append(wt.getPath())
                  .append("\n    Branch: ").append(wt.getBranch())
                  .append("\n");
        }

        return result.toString();
    }
}
