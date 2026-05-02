package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

public class ListPendingRequestsTool implements AgentTool {
    private final ProtocolManager protocolManager;

    public ListPendingRequestsTool(ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
    }

    @Override
    public String getName() {
        return "list_pending_requests";
    }

    @Override
    public String getDescription() {
        return "List all pending protocol requests (shutdown, plan approval). " +
               "Shows request ID, type, target, and age.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", List.of()
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        List<ProtocolManager.Request> pending = protocolManager.getPendingRequests();

        if (pending.isEmpty()) {
            return "No pending requests";
        }

        StringBuilder sb = new StringBuilder("Pending requests:\n");
        for (ProtocolManager.Request req : pending) {
            long ageSeconds = (System.currentTimeMillis() - req.getTimestamp()) / 1000;
            sb.append(String.format("- %s: %s to %s (age: %ds)\n",
                req.getId(), req.getType(), req.getTarget(), ageSeconds));
        }
        return sb.toString();
    }
}
