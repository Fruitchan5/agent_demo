package cn.edu.agent.tool.impl;

import cn.edu.agent.teammate.TeammateManager;
import cn.edu.agent.tool.AgentTool;

import java.util.Map;

public class ListTeammatesTool implements AgentTool {
    private final TeammateManager manager;
    
    public ListTeammatesTool(TeammateManager manager) {
        this.manager = manager;
    }
    
    @Override
    public String getName() {
        return "list_teammates";
    }
    
    @Override
    public String getDescription() {
        return "List all teammates and their current status (WORKING, IDLE, SHUTDOWN).";
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
        return manager.listAll();
    }
}
