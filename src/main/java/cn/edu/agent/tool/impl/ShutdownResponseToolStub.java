package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * Stub tool for shutdown_response - actual execution happens in TeammateManager.executeTeammateTool
 */
public class ShutdownResponseToolStub implements AgentTool {
    @Override
    public String getName() {
        return "shutdown_response";
    }

    @Override
    public String getDescription() {
        return "Respond to a shutdown request from the lead. " +
               "Approve to shut down gracefully, or reject to continue working.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "request_id", Map.of(
                    "type", "string",
                    "description", "The request ID from the shutdown_request message"
                ),
                "approve", Map.of(
                    "type", "boolean",
                    "description", "true to approve shutdown, false to reject"
                ),
                "reason", Map.of(
                    "type", "string",
                    "description", "Reason for the decision"
                )
            ),
            "required", List.of("request_id", "approve")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        throw new UnsupportedOperationException("This tool should be intercepted by TeammateManager");
    }
}
