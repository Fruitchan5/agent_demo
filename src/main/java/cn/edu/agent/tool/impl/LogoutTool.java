package cn.edu.agent.tool.impl;

import cn.edu.agent.auth.AuthenticationService;
import cn.edu.agent.tool.AgentTool;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool for user logout
 */
public class LogoutTool implements AgentTool {
    private final AuthenticationService authService;

    public LogoutTool(AuthenticationService authService) {
        this.authService = authService;
    }

    @Override
    public String getName() {
        return "logout";
    }

    @Override
    public String getDescription() {
        return "Logout a user by invalidating their session";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> sessionId = new HashMap<>();
        sessionId.put("type", "string");
        sessionId.put("description", "Session ID to invalidate");
        properties.put("session_id", sessionId);
        
        schema.put("properties", properties);
        schema.put("required", new String[]{"session_id"});
        
        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) {
        String sessionId = (String) input.get("session_id");

        if (sessionId == null || sessionId.isBlank()) {
            return "Error: Session ID is required";
        }

        boolean success = authService.logout(sessionId);
        
        if (success) {
            return String.format("Successfully logged out session: %s", sessionId);
        } else {
            return String.format("Failed to logout. Session not found: %s", sessionId);
        }
    }
}
