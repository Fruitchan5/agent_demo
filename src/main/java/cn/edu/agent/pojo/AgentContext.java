package cn.edu.agent.pojo;

import cn.edu.agent.tool.AgentRole;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class AgentContext {
    private final AgentRole role;
    private final List<Map<String, Object>> messages;
    private final int maxIterations;
    private int currentIteration;
    private int compactCount;

    public AgentContext(AgentRole role, int maxIterations) {
        this.role = role;
        this.messages = new ArrayList<>();
        this.maxIterations = maxIterations;
        this.currentIteration = 0;
        this.compactCount = 0;
    }

    public void appendMessage(String role, Object content) {
        messages.add(Map.of("role", role, "content", content));
    }

    public boolean hasReachedLimit() {
        return currentIteration >= maxIterations;
    }

    public void incrementIteration() {
        currentIteration++;
    }

    public void incrementCompactCount() {
        compactCount++;
    }
}

