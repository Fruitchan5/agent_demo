package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.MessageBus;
import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentTool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ShutdownRequestTool implements AgentTool {
    private final TeammateManager manager;
    private final ProtocolManager protocolManager;

    public ShutdownRequestTool(TeammateManager manager, ProtocolManager protocolManager) {
        this.manager = manager;
        this.protocolManager = protocolManager;
    }

    @Override
    public String getName() {
        return "shutdown_request";
    }

    @Override
    public String getDescription() {
        return "Request a teammate to shut down gracefully. " +
               "The teammate will receive the request and can approve or reject it.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "teammate", Map.of(
                    "type", "string",
                    "description", "Name of the teammate to shut down"
                )
            ),
            "required", List.of("teammate")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        String target = (String) input.get("teammate");

        String requestId = protocolManager.initiateRequest(
            "shutdown",
            target,
            Map.of(),
            Duration.ofSeconds(30)
        );

        manager.getMessageBus().send(
            "lead",
            target,
            "Please shut down gracefully.",
            "SHUTDOWN_REQUEST:v1",
            Map.of("request_id", requestId, "protocol_version", "1.0")
        );

        return "Shutdown request " + requestId + " sent to " + target + " (status: pending)";
    }
}
