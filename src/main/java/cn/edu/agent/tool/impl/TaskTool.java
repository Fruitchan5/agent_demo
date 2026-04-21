package cn.edu.agent.tool.impl;

import cn.edu.agent.core.SubAgentRunner;
import cn.edu.agent.tool.AgentTool;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class TaskTool implements AgentTool {
    private final SubAgentRunner subAgentRunner;

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public String getDescription() {
        return "Spawn a subagent with a fresh context to complete a subtask. \n"
                + "The subagent has access to all base tools (bash, read_file, write_file, edit_file, todo).\n"
                + "Only the final summary is returned. Use this for context-heavy subtasks to keep the main context clean.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "prompt", Map.of(
                                "type", "string",
                                "description", "The task description for the subagent"
                        )
                ),
                "required", new String[]{"prompt"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        String prompt = (String) input.get("prompt");
        if (prompt == null) {
            prompt = "";
        }
        String preview = prompt.length() <= 50 ? prompt : prompt.substring(0, 50);
        System.out.println("[TaskTool] Spawning subagent: " + preview + "...");
        return subAgentRunner.run(prompt);
    }
}

