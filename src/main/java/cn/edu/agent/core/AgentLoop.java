package cn.edu.agent.core;

import cn.edu.agent.compact.ContextCompactor;
import cn.edu.agent.config.AppConfig;
import cn.edu.agent.monitor.MonitorLogger;
import cn.edu.agent.pojo.AgentContext;
import cn.edu.agent.pojo.ContentBlock;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.AgentRole;
import cn.edu.agent.tool.ToolManager;
import cn.edu.agent.tool.impl.CompactTool;

import java.util.*;

// AgentLoop 是整个 Agent 的核心循环，负责管理对话历史、调用 LLM、执行工具，并根据 LLM 的回复决定下一步行动。
public class AgentLoop {
    private final LlmClient llmClient;
    private final ToolManager toolManager;
    private final String systemPrompt;
    private final List<Map<String, Object>> chatHistory = new ArrayList<>();

    // s06：上下文压缩器
    private final ContextCompactor compactor = new ContextCompactor();

    public AgentLoop() {
        this(new ToolManager(), "你是 Claude，一个高级软工 AI 助手。你可以使用工具来完成任务。");
    }

    public AgentLoop(ToolManager toolManager) {
        this(toolManager, "你是 Claude，一个高级软工 AI 助手。你可以使用工具来完成任务。");
    }

    public AgentLoop(ToolManager toolManager, String systemPrompt) {
        this.llmClient = new LlmClient();
        this.toolManager = toolManager;
        this.systemPrompt = systemPrompt;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println(" [Claude Agent Java Edition] 已启动! (s06: context compact)");

        while (true) {
            System.out.print("\n你 >> ");
            String userInput = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(userInput)) {
                MonitorLogger.flushToFile();
                break;
            }
            if ("/stats".equalsIgnoreCase(userInput)) {
                MonitorLogger.printStats();
                continue;
            }

            String contentForHistory = toolManager.prefixUserMessageIfNeeded(userInput);
            chatHistory.add(Map.of("role", "user", "content", contentForHistory));

            runAgentCycle();
        }
    }

    // Agent 的自主思考循环（集成三层压缩）
    private void runAgentCycle() {
        boolean turnFinished = false;

        while (!turnFinished) {
            try {
                // ── Layer 1：每轮静默执行 micro compact ──
                int keepRecent = AppConfig.getCompactKeepRecent();
                int beforeLayer1 = compactor.estimateTokens(chatHistory);
                compactor.microCompact(chatHistory, keepRecent);
                int afterLayer1 = compactor.estimateTokens(chatHistory);
                System.out.println(String.format("[TOKEN] Layer1 前: %d tokens, Layer1 后: %d tokens, 压缩: %d tokens (%.1f%%)",
                        beforeLayer1, afterLayer1, beforeLayer1 - afterLayer1,
                        beforeLayer1 > 0 ? (beforeLayer1 - afterLayer1) * 100.0 / beforeLayer1 : 0));

                // ── Layer 2：token 超阈值时自动压缩 ──
                int threshold = AppConfig.getCompactTokenThreshold();
                if (compactor.estimateTokens(chatHistory) > threshold) {
                    int beforeLayer2 = compactor.estimateTokens(chatHistory);
                    System.out.println("[AgentLoop] token 超阈值，触发 Layer 2 自动压缩...");
                    List<Map<String, Object>> compressed = compactor.autoCompact(chatHistory);
                    chatHistory.clear();
                    chatHistory.addAll(compressed);
                    int afterLayer2 = compactor.estimateTokens(chatHistory);
                    System.out.println(String.format("[TOKEN] Layer2 压缩: %d → %d tokens (压缩率 %.1f%%)",
                            beforeLayer2, afterLayer2, (beforeLayer2 - afterLayer2) * 100.0 / beforeLayer2));
                }

                LlmResponse response = llmClient.call(
                        chatHistory,
                        toolManager.getToolsForLlm(AgentRole.PARENT),
                        systemPrompt
                );

                chatHistory.add(Map.of("role", "assistant", "content", response.getContent()));

                if ("tool_use".equals(response.getStopReason())) {
                    List<Map<String, Object>> toolResults = new ArrayList<>();

                    for (ContentBlock block : response.getContent()) {
                        if ("text".equals(block.getType()) && block.getText() != null) {
                            System.out.println("🤔 思考: " + block.getText());
                        } else if ("tool_use".equals(block.getType())) {
                            AgentTool tool = toolManager.getTool(block.getName());
                            String toolName = block.getName();
                            if ("todo".equals(toolName)) {
                                toolManager.resetTodoCounter();
                            }

                            String toolOutput;
                            if (tool != null) {
                                toolOutput = tool.execute(block.getInput());
                            } else {
                                toolOutput = "Error: Tool " + block.getName() + " not found.";
                            }

                            // ── Layer 3：LLM 主动触发压缩 ──
                            if (CompactTool.COMPACT_SIGNAL.equals(toolOutput)) {
                                System.out.println("[AgentLoop] 收到 compact 信号，触发 Layer 3 强制压缩...");
                                List<Map<String, Object>> compressed = compactor.autoCompact(chatHistory);
                                chatHistory.clear();
                                chatHistory.addAll(compressed);
                                toolOutput = "[Context compacted successfully]";
                            }

                            System.out.println("    输出: \n" + toolOutput);
                            toolResults.add(Map.of(
                                    "type", "tool_result",
                                    "tool_use_id", block.getId(),
                                    "content", toolOutput
                            ));
                        }
                    }
                    chatHistory.add(Map.of("role", "user", "content", toolResults));

                    toolManager.incrementRoundCounter();
                } else {
                    toolManager.incrementRoundCounter();
                    for (ContentBlock block : response.getContent()) {
                        if ("text".equals(block.getType())) {
                            System.out.println("Claude >> " + block.getText());
                        }
                    }
                    turnFinished = true;
                }
            } catch (Exception e) {
                System.err.println("❌ 发生错误: " + e.getMessage());
                turnFinished = true;
            }
        }
    }
}
