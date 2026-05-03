package cn.edu.agent.tool.impl;

import cn.edu.agent.auth.AuthenticationService;
import cn.edu.agent.tool.AgentTool;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool for user registration
 */
public class RegisterTool implements AgentTool {
    private final AuthenticationService authService;

    public RegisterTool(AuthenticationService authService) {
        this.authService = authService;
    }

    @Override
    public String getName() {
        return "register";
    }

    @Override
    public String getDescription() {
        return "Register a new user account with username, password, and email";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> username = new HashMap<>();
        username.put("type", "string");
        username.put("description", "Username for the new account");
        properties.put("username", username);
        
        Map<String, Object> password = new HashMap<>();
        password.put("type", "string");
        password.put("description", "Password for the new account");
        properties.put("password", password);
        
        Map<String, Object> email = new HashMap<>();
        email.put("type", "string");
        email.put("description", "Email address for the new account");
        properties.put("email", email);
        
        schema.put("properties", properties);
        schema.put("required", new String[]{"username", "password", "email"});
        
        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) {
        String username = (String) input.get("username");
        String password = (String) input.get("password");
        String email = (String) input.get("email");

        if (username == null || username.isBlank()) {
            return "Error: Username is required";
        }

        if (password == null || password.isBlank()) {
            return "Error: Password is required";
        }

        if (email == null || email.isBlank()) {
            return "Error: Email is required";
        }

        boolean success = authService.register(username, password, email);
        
        if (success) {
            return String.format("Successfully registered user '%s' with email '%s'", username, email);
        } else {
            return String.format("Failed to register user '%s'. Username may already exist.", username);
        }
    }
}
