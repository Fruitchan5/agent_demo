package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.MessageBus;
import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

public class CancelRequestTool implements AgentTool {
    private final ProtocolManager protocolManager;
    private final MessageBus messageBus;

    public CancelRequestTool(ProtocolManager protocolManager, MessageBus messageBus) {
        this.protocolManager = protocolManager;
        this.messageBus = messageBus;
    }

    @Override
    public String getName() {
        return "cancel_request";
    }

    @Override
    public String getDescription() {
        return "Cancel a pending protocol request (shutdown or plan approval). " +
               "Can only cancel requests that are still in pending status.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "request_id", Map.of(
                    "type", "string",
                    "description", "The request ID to cancel"
                )
            ),
            "required", List.of("request_id")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        String requestId = (String) input.get("request_id");
        ProtocolManager.Request req = protocolManager.getRequest(requestId);

        if (req == null) {
            return "Request not found: " + requestId;
        }

        if (!"pending".equals(req.getStatus())) {
            return "Cannot cancel: already " + req.getStatus();
        }

        protocolManager.cancelRequest(requestId);

        messageBus.send(
            "lead",
            req.getTarget(),
            "Request cancelled",
            "REQUEST_CANCELLED:v1",
            Map.of("request_id", requestId, "protocol_version", "1.0")
        );

        return "Cancelled request " + requestId;
    }
}
