package cn.edu.agent;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.core.AgentLoop;
import cn.edu.agent.core.DefaultSubAgentRunner;
import cn.edu.agent.core.LlmClient;
import cn.edu.agent.core.SubAgentRunner;
import cn.edu.agent.skill.SkillLoader;
import cn.edu.agent.tool.ToolManager;
import cn.edu.agent.tool.ToolRegistry;
import cn.edu.agent.tool.impl.TaskTool;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AgentApplication {
    public static void main(String[] args) {
        Path skillsDir = Paths.get("skills");
        SkillLoader skillLoader = new SkillLoader(skillsDir);
        skillLoader.init();

        String systemPrompt = """
                你是一个高级软工 AI 助手，工作目录: %s
                遇到不熟悉的领域，请先调用 load_skill 获取专项知识。
                
                Skills available:
                %s""".formatted(Paths.get("").toAbsolutePath(), skillLoader.getDescriptions());

        LlmClient llmClient = new LlmClient();
        ToolRegistry registry = new ToolRegistry(skillLoader);
        int maxIter = AppConfig.getSubagentMaxIterations();
        SubAgentRunner runner = new DefaultSubAgentRunner(llmClient, registry, maxIter);
        registry.registerParentOnly(new TaskTool(runner));
        ToolManager toolManager = new ToolManager(registry);
        new AgentLoop(toolManager, systemPrompt).start();
    }
} 