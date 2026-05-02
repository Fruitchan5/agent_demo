package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.MessageBus;
import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

public class PlanResponseTool implements AgentTool {
    private final ProtocolManager protocolManager;
    private final MessageBus messageBus;

    public PlanResponseTool(ProtocolManager protocolManager, MessageBus messageBus) {
        this.protocolManager = protocolManager;
        this.messageBus = messageBus;
    }

    @Override
    public String getName() {
        return "plan_response";
    }

    @Override
    public String getDescription() {
        return "Approve or reject a teammate's plan request. " +
               "Use this after reviewing a plan submitted via plan_request.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "request_id", Map.of(
                    "type", "string",
                    "description", "The request ID from the plan_request message"
                ),
                "approve", Map.of(
                    "type", "boolean",
                    "description", "true to approve, false to reject"
                ),
                "feedback", Map.of(
                    "type", "string",
                    "description", "Feedback or reason for the decision"
                )
            ),
            "required", List.of("request_id", "approve")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        String requestId = (String) input.get("request_id");
        boolean approve = (boolean) input.get("approve");
        String feedback = (String) input.getOrDefault("feedback", "");

        ProtocolManager.Request req = protocolManager.getRequest(requestId);
        if (req == null) {
            return "Error: Request " + requestId + " not found";
        }

        if (!"pending".equals(req.getStatus())) {
            return "Error: Request " + requestId + " is already " + req.getStatus();
        }

        protocolManager.handleResponse(requestId, approve, feedback);

        String targetTeammate = (String) req.getData().get("from");
        messageBus.send(
            "lead",
            targetTeammate,
            feedback,
            "PLAN_RESPONSE:v1",
            Map.of(
                "request_id", requestId,
                "approve", approve,
                "protocol_version", "1.0"
            )
        );

        return "Plan " + requestId + " " + (approve ? "approved" : "rejected") +
               (feedback.isEmpty() ? "" : ": " + feedback);
    }
}
