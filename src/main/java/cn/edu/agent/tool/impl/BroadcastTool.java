package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.tool.AgentTool;

import java.util.Map;

public class BroadcastTool implements AgentTool {
    private final TeammateManager manager;
    
    public BroadcastTool(TeammateManager manager) {
        this.manager = manager;
    }
    
    @Override
    public String getName() {
        return "broadcast";
    }
    
    @Override
    public String getDescription() {
        return "Broadcast a message to all teammates (excluding the sender).";
    }
    
    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "content", Map.of(
                    "type", "string",
                    "description", "Message content to broadcast"
                )
            ),
            "required", new String[]{"content"}
        );
    }
    
    @Override
    public String execute(Map<String, Object> input) {
        String content = (String) input.get("content");
        
        if (content == null || content.isBlank()) {
            return "Error: 'content' is required";
        }
        
        return manager.getMessageBus().broadcast("lead", content, manager.getMemberNames());
    }
}
