package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.MessageBus;
import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

public class ShutdownResponseTool implements AgentTool {
    private final ProtocolManager protocolManager;
    private final MessageBus messageBus;
    private final String senderName;

    public ShutdownResponseTool(ProtocolManager protocolManager, MessageBus messageBus, String senderName) {
        this.protocolManager = protocolManager;
        this.messageBus = messageBus;
        this.senderName = senderName;
    }

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
        String requestId = (String) input.get("request_id");
        boolean approve = (boolean) input.get("approve");
        String reason = (String) input.getOrDefault("reason", "");

        protocolManager.handleResponse(requestId, approve, reason);

        messageBus.send(
            senderName,
            "lead",
            reason,
            "SHUTDOWN_RESPONSE:v1",
            Map.of("request_id", requestId, "approve", approve, "protocol_version", "1.0")
        );

        if (approve) {
            return "SHUTDOWN_APPROVED:" + requestId;
        } else {
            return "Shutdown request rejected: " + reason;
        }
    }
}
