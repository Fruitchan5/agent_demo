package cn.edu.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class SkillLoader {

    private final Path skillsDir;
    private final Map<String, SkillEntry> skills = new LinkedHashMap<>();

    // 调用方在构造后手动 init()，便于测试时 mock
    public void init() {
        if (!Files.exists(skillsDir)) {
            return;
        }
        try (var stream = Files.walk(skillsDir)) {
            stream.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .forEach(this::loadSkill);
        } catch (IOException e) {
            log.warn("扫描 skills 目录失败: {}", e.getMessage());
        }
    }

    private void loadSkill(Path file) {
        try {
            String text = Files.readString(file).replace("\r\n", "\n").replace("\r", "\n");
            String[] parts = parseFrontmatter(text);
            SkillMeta meta = parseYaml(parts[0]);
            String name = (meta.getName() != null) ? meta.getName()
                    : file.getParent().getFileName().toString();
            skills.put(name, new SkillEntry(meta, parts[1], file.toString()));
        } catch (Exception e) {
            log.warn("加载 skill 失败 {}: {}", file, e.getMessage());
        }
    }

    /** 解析 ---\n...\n---\n 格式，返回 [frontmatter, body] */
    private String[] parseFrontmatter(String text) {
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---\n", 4);
            if (end != -1) {
                return new String[]{text.substring(4, end), text.substring(end + 5).strip()};
            }
        }
        return new String[]{"", text};
    }

    /** 简易 YAML 解析（仅 key: value，不引入重依赖） */
    private SkillMeta parseYaml(String yaml) {
        SkillMeta meta = new SkillMeta();
        for (String line : yaml.split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String val = line.substring(colon + 1).strip();
            switch (key) {
                case "name" -> meta.setName(val);
                case "description" -> meta.setDescription(val);
                case "tags" -> meta.setTags(val);
                default -> {
                    // ignore unknown keys
                }
            }
        }
        return meta;
    }

    /** Layer 1：注入系统提示的简短描述列表 */
    public String getDescriptions() {
        if (skills.isEmpty()) {
            return "(no skills available)";
        }
        var sb = new StringBuilder();
        skills.forEach((name, entry) -> {
            SkillMeta m = entry.getMeta();
            sb.append("  - ").append(name).append(": ")
                    .append(m.getDescription() != null ? m.getDescription() : "No description");
            if (m.getTags() != null && !m.getTags().isEmpty()) {
                sb.append(" [").append(m.getTags()).append("]");
            }
            sb.append("\n");
        });
        return sb.toString().stripTrailing();
    }

    /** Layer 2：返回完整 skill body，放入 tool_result */
    public String getContent(String name) {
        SkillEntry entry = skills.get(name);
        if (entry == null) {
            return "Error: Unknown skill '%s'. Available: %s"
                    .formatted(name, String.join(", ", skills.keySet()));
        }
        return "<skill name=\"%s\">\n%s\n</skill>".formatted(name, entry.getBody());
    }

    public Set<String> getSkillNames() {
        return Collections.unmodifiableSet(skills.keySet());
    }
}
