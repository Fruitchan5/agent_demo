package cn.edu.agent.mcp;

import cn.edu.agent.tool.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 将单个 MCP tool 包装为 AgentTool，使其对 ToolRegistry 完全透明。
 */
public class McpToolAdapter implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpClient client;
    private final McpClient.McpToolDef def;

    public McpToolAdapter(McpClient client, McpClient.McpToolDef def) {
        this.client = client;
        this.def = def;
    }

    @Override
    public String getName() { return "mcp_" + def.name(); }

    @Override
    public String getDescription() {
        return "[MCP] " + def.description();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInputSchema() {
        try {
            return MAPPER.treeToValue(def.inputSchema(), Map.class);
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        return client.callTool(def.name(), input);
    }
}
