package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.Message;
import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.tool.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class ReadInboxTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TeammateManager manager;
    private final String ownerName;
    
    public ReadInboxTool(TeammateManager manager, String ownerName) {
        this.manager = manager;
        this.ownerName = ownerName;
    }
    
    @Override
    public String getName() {
        return "read_inbox";
    }
    
    @Override
    public String getDescription() {
        return "Read and clear your inbox. Returns all messages since last read.";
    }
    
    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", new String[]{}
        );
    }
    
    @Override
    public String execute(Map<String, Object> input) {
        try {
            List<Message> inbox = manager.getMessageBus().readInbox(ownerName);
            if (inbox.isEmpty()) {
                return "[]";
            }
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(inbox);
        } catch (Exception e) {
            return "Error reading inbox: " + e.getMessage();
        }
    }
}
