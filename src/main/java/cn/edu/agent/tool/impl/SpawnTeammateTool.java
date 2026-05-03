package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.tool.AgentTool;

import java.util.Map;

public class SpawnTeammateTool implements AgentTool {
    private final TeammateManager manager;
    
    public SpawnTeammateTool(TeammateManager manager) {
        this.manager = manager;
    }
    
    @Override
    public String getName() {
        return "spawn_teammate";
    }

    @Override
    public String getDescription() {
        return "Create a persistent teammate agent that runs in an independent thread. " +
               "The teammate can receive multiple instructions and maintain context across interactions.";
    }
    
    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "name", Map.of(
                    "type", "string",
                    "description", "Unique teammate name (e.g., 'alice', 'bob')"
                ),
                "role", Map.of(
                    "type", "string",
                    "description", "Role description (e.g., 'backend-dev', 'tester')"
                ),
                "prompt", Map.of(
                    "type", "string",
                    "description", "Initial task or instruction for the teammate"
                )
            ),
            "required", new String[]{"name", "role", "prompt"}
        );
    }
    
    @Override
    public String execute(Map<String, Object> input) {
        String name = (String) input.get("name");
        String role = (String) input.get("role");
        String prompt = (String) input.get("prompt");
        
        if (name == null || name.isBlank()) {
            return "Error: 'name' is required";
        }
        if (role == null || role.isBlank()) {
            return "Error: 'role' is required";
        }
        if (prompt == null || prompt.isBlank()) {
            return "Error: 'prompt' is required";
        }
        
        return manager.spawn(name, role, prompt);
    }
}
