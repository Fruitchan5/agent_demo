package cn.edu.agent;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.core.AgentLoop;
import cn.edu.agent.core.DefaultSubAgentRunner;
import cn.edu.agent.core.LlmClient;
import cn.edu.agent.core.SubAgentRunner;
import cn.edu.agent.mcp.McpClient;
import cn.edu.agent.mcp.McpToolAdapter;
import cn.edu.agent.skill.SkillLoader;
import cn.edu.agent.tool.ToolManager;
import cn.edu.agent.tool.ToolRegistry;
import cn.edu.agent.tool.impl.TaskTool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AgentApplication {
    public static void main(String[] args) {
        Path skillsDir = Paths.get("skills");
        SkillLoader skillLoader = new SkillLoader(skillsDir);
        skillLoader.init();

        String systemPrompt = """
                你是一个高级软工 AI 助手，工作目录: %s
                遇到不熟悉的领域，请先调用 load_skill 获取专项知识。
                
                你可以创建和管理团队成员来协作完成复杂任务：
                - spawn_teammate: 创建/启动团队成员并分配任务
                - send_message: 向团队成员发送消息
                - read_inbox: 读取来自团队成员的消息
                - list_teammates: 查看所有团队成员状态
                - broadcast: 向所有团队成员广播消息
                
                Skills available:
                %s""".formatted(Paths.get("").toAbsolutePath(), skillLoader.getDescriptions());

        LlmClient llmClient = new LlmClient();
        ToolRegistry registry = new ToolRegistry(skillLoader);
        int maxIter = AppConfig.getSubagentMaxIterations();
        SubAgentRunner runner = new DefaultSubAgentRunner(llmClient, registry, maxIter);
        registry.registerParentOnly(new TaskTool(runner));

        // MCP：若配置了 MCP_COMMAND，启动 server 并注册所有工具
        String mcpCommand = AppConfig.getMcpCommand();
        McpClient mcpClient = null;
        if (mcpCommand != null && !mcpCommand.isBlank()) {
            try {
                mcpClient = new McpClient(mcpCommand);
                List<McpClient.McpToolDef> mcpTools = mcpClient.listTools();
                for (McpClient.McpToolDef def : mcpTools) {
                    registry.registerParentOnly(new McpToolAdapter(mcpClient, def));
                }
                System.out.println("[MCP] 已注册 " + mcpTools.size() + " 个工具来自: " + mcpCommand);
            } catch (Exception e) {
                System.err.println("[MCP] 启动失败，跳过: " + e.getMessage());
                if (mcpClient != null) mcpClient.close();
                mcpClient = null;
            }
        }

        ToolManager toolManager = new ToolManager(registry);
        new AgentLoop(toolManager, systemPrompt).start();

        // 关闭资源
        if (registry.getTeammateManager() != null) {
            registry.getTeammateManager().shutdown();
        }
        if (mcpClient != null) mcpClient.close();
    }
}
