package cn.edu.agent;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.core.AgentLoop;
import cn.edu.agent.core.DefaultSubAgentRunner;
import cn.edu.agent.core.LlmClient;
import cn.edu.agent.core.SubAgentRunner;
import cn.edu.agent.tool.ToolManager;
import cn.edu.agent.tool.ToolRegistry;
import cn.edu.agent.tool.impl.TaskTool;

public class AgentApplication {
    public static void main(String[] args) {
        LlmClient llmClient = new LlmClient();
        ToolRegistry registry = new ToolRegistry();
        int maxIter = AppConfig.getSubagentMaxIterations();
        SubAgentRunner runner = new DefaultSubAgentRunner(llmClient, registry, maxIter);
        registry.registerParentOnly(new TaskTool(runner));
        ToolManager toolManager = new ToolManager(registry);
        new AgentLoop(toolManager).start();
    }
}