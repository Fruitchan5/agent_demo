package cn.edu.agent.tool.impl;

import cn.edu.agent.auth.AuthenticationService;
import cn.edu.agent.auth.Session;
import cn.edu.agent.tool.AgentTool;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tool for user login
 */
public class LoginTool implements AgentTool {
    private final AuthenticationService authService;

    public LoginTool(AuthenticationService authService) {
        this.authService = authService;
    }

    @Override
    public String getName() {
        return "login";
    }

    @Override
    public String getDescription() {
        return "Authenticate a user with username and password, returns a session ID on success";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> username = new HashMap<>();
        username.put("type", "string");
        username.put("description", "Username to authenticate");
        properties.put("username", username);
        
        Map<String, Object> password = new HashMap<>();
        password.put("type", "string");
        password.put("description", "Password for authentication");
        properties.put("password", password);
        
        schema.put("properties", properties);
        schema.put("required", new String[]{"username", "password"});
        
        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) {
        String username = (String) input.get("username");
        String password = (String) input.get("password");

        if (username == null || username.isBlank()) {
            return "Error: Username is required";
        }

        if (password == null || password.isBlank()) {
            return "Error: Password is required";
        }

        Optional<Session> sessionOpt = authService.login(username, password);
        
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            return String.format("Login successful for user '%s'. Session ID: %s (expires at %s)",
                    username, session.getSessionId(), session.getExpiresAt());
        } else {
            return String.format("Login failed for user '%s'. Invalid credentials or account inactive.", username);
        }
    }
}
