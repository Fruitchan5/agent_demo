package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.MessageBus;
import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentTool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class PlanRequestTool implements AgentTool {
    private final ProtocolManager protocolManager;
    private final MessageBus messageBus;
    private final String senderName;

    public PlanRequestTool(ProtocolManager protocolManager, MessageBus messageBus, String senderName) {
        this.protocolManager = protocolManager;
        this.messageBus = messageBus;
        this.senderName = senderName;
    }

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
        String plan = (String) input.get("plan");

        String requestId = protocolManager.initiateRequest(
            "plan_approval",
            "lead",
            Map.of("plan", plan, "from", senderName),
            Duration.ofMinutes(5)
        );

        messageBus.send(
            senderName,
            "lead",
            "Plan approval request:\n" + plan,
            "PLAN_REQUEST:v1",
            Map.of("request_id", requestId, "protocol_version", "1.0")
        );

        return "Plan request " + requestId + " submitted (status: pending). " +
               "Waiting for lead approval before proceeding.";
    }
}
