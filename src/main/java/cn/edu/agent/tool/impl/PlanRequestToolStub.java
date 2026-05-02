package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * Stub tool for plan_request - actual execution happens in TeammateManager.executeTeammateTool
 */
public class PlanRequestToolStub implements AgentTool {
    @Override
    public String getName() {
        return "plan_request";
    }

    @Override
    public String getDescription() {
        return "Submit a plan for approval before executing high-risk operations. " +
               "The lead will review and either approve or reject the plan.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "plan", Map.of(
                    "type", "string",
                    "description", "Detailed description of the plan to be executed"
                )
            ),
            "required", List.of("plan")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        throw new UnsupportedOperationException("This tool should be intercepted by TeammateManager");
    }
}
