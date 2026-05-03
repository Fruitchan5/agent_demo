package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.tool.AgentTool;

import java.util.Map;

public class SendMessageTool implements AgentTool {
    private final TeammateManager manager;
    private final String senderName;
    
    public SendMessageTool(TeammateManager manager, String senderName) {
        this.manager = manager;
        this.senderName = senderName;
    }
    
    @Override
    public String getName() {
        return "send_message";
    }
    
    @Override
    public String getDescription() {
        return "Send a message to a specific teammate or lead. The message is appended to their JSONL inbox.";
    }
    
    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "to", Map.of(
                    "type", "string",
                    "description", "Recipient name (teammate name or 'lead')"
                ),
                "content", Map.of(
                    "type", "string",
                    "description", "Message content"
                ),
                "msg_type", Map.of(
                    "type", "string",
                    "description", "Message type (default: MESSAGE)",
                    "enum", new String[]{"MESSAGE", "BROADCAST"}
                )
            ),
            "required", new String[]{"to", "content"}
        );
    }
    
    @Override
    public String execute(Map<String, Object> input) {
        String to = (String) input.get("to");
        String content = (String) input.get("content");
        String msgType = (String) input.getOrDefault("msg_type", "MESSAGE");
        
        if (to == null || to.isBlank()) {
            return "Error: 'to' is required";
        }
        if (content == null || content.isBlank()) {
            return "Error: 'content' is required";
        }
        
        return manager.getMessageBus().send(senderName, to, content, msgType);
    }
}
