package cn.edu.agent.tool.impl;

import cn.edu.agent.skill.SkillLoader;
import cn.edu.agent.tool.AgentTool;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class LoadSkillTool implements AgentTool {

    private final SkillLoader skillLoader;

    @Override
    public String getName() {
        return "load_skill";
    }

    @Override
    public String getDescription() {
        return "Load specialized knowledge by skill name before tackling unfamiliar topics.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Skill name to load")
                ),
                "required", new String[]{"name"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        String name = (String) input.get("name");
        if (name == null || name.isBlank()) {
            return "Error: skill name is required";
        }
        System.out.println("> load_skill: " + name);
        return skillLoader.getContent(name);
    }
}
