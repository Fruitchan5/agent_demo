package cn.edu.agent.skill;

import lombok.Value;

@Value
public class SkillEntry {
    SkillMeta meta;
    String body;
    String path;
}
