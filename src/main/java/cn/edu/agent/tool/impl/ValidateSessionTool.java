package cn.edu.agent.tool.impl;

import cn.edu.agent.auth.AuthenticationService;
import cn.edu.agent.auth.Session;
import cn.edu.agent.auth.User;
import cn.edu.agent.tool.AgentTool;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tool for validating user sessions
 */
public class ValidateSessionTool implements AgentTool {
    private final AuthenticationService authService;

    public ValidateSessionTool(AuthenticationService authService) {
        this.authService = authService;
    }

    @Override
    public String getName() {
        return "validate_session";
    }

    @Override
    public String getDescription() {
        return "Validate a session ID and return user information if valid";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> sessionId = new HashMap<>();
        sessionId.put("type", "string");
        sessionId.put("description", "Session ID to validate");
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

        boolean isValid = authService.validateSession(sessionId);
        
        if (!isValid) {
            return String.format("Session is invalid or expired: %s", sessionId);
        }

        Optional<Session> sessionOpt = authService.getSession(sessionId);
        Optional<User> userOpt = authService.getUserFromSession(sessionId);
        
        if (sessionOpt.isPresent() && userOpt.isPresent()) {
            Session session = sessionOpt.get();
            User user = userOpt.get();
            return String.format("Session is valid.\nSession ID: %s\nUsername: %s\nExpires at: %s\nUser: %s",
                    session.getSessionId(), session.getUsername(), session.getExpiresAt(), user.toSafeString());
        } else {
            return String.format("Session validation failed: %s", sessionId);
        }
    }
}
