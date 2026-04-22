package cn.edu.agent.core;

import cn.edu.agent.compact.ContextCompactor;
import cn.edu.agent.config.AppConfig;
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
            String userInput = scanner.nextLine();
            if ("exit".equalsIgnoreCase(userInput)) break;

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
                compactor.microCompact(chatHistory, keepRecent);

                // ── Layer 2：token 超阈值时自动压缩 ──
                autoCompactIfNeeded(null);

                LlmResponse response = llmClient.call(
                        chatHistory,
                        toolManager.getToolsForLlm(AgentRole.PARENT),
                        systemPrompt
                );

                chatHistory.add(Map.of("role", "assistant", "content", response.getContent()));

                if ("tool_use".equals(response.getStopReason())) {
                    boolean usedTodo = responseDeclaresTodoTool(response);
                    List<Map<String, Object>> toolResults = new ArrayList<>();
                    boolean compactTriggered = false;

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
                            System.out.println("    输出: \n" + toolOutput);

                            // ── Layer 3：检测 compact 工具调用 ──
                            if (CompactTool.COMPACT_SIGNAL.equals(toolOutput)) {
                                compactTriggered = true;
                                // 不把 __COMPACT__ 加入 toolResults，直接触发压缩
                                System.out.println("🗜️ [Layer 3] 执行强制压缩...");
                                forceCompact(null);
                                // 压缩后跳出当前工具循环，重新开始本轮
                                break;
                            }

                            toolResults.add(Map.of(
                                    "type", "tool_result",
                                    "tool_use_id", block.getId(),
                                    "content", toolOutput
                            ));
                        }
                    }

                    if (!compactTriggered) {
                        chatHistory.add(Map.of("role", "user", "content", toolResults));
                        if (usedTodo) {
                            toolManager.resetTodoCounter();
                        } else {
                            toolManager.incrementRoundCounter();
                        }
                    }
                    // compactTriggered 时不追加 toolResults，下一轮重新调用 LLM
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

    /**
     * Layer 2：若 token 超阈值则执行自动压缩。
     * context 参数可为 null（AgentLoop 自身维护 chatHistory）。
     */
    void autoCompactIfNeeded(AgentContext context) {
        int threshold = AppConfig.getCompactTokenThreshold();
        List<Map<String, Object>> history = (context != null) ? context.getMessages() : chatHistory;

        if (compactor.estimateTokens(history) > threshold) {
            System.out.println("⚡ [Layer 2] token 超阈值，执行自动压缩...");
            try {
                List<Map<String, Object>> compacted = compactor.autoCompact(new ArrayList<>(history));
                history.clear();
                history.addAll(compacted);
                if (context != null) {
                    context.incrementCompactCount();
                }
                System.out.println("✅ [Layer 2] 压缩完成，历史已替换为摘要");
            } catch (Exception e) {
                System.err.println("⚠️ [Layer 2] 自动压缩失败: " + e.getMessage());
            }
        }
    }

    /**
     * Layer 3：强制执行压缩（由 compact 工具触发）。
     * context 参数可为 null（AgentLoop 自身维护 chatHistory）。
     */
    void forceCompact(AgentContext context) {
        List<Map<String, Object>> history = (context != null) ? context.getMessages() : chatHistory;
        try {
            List<Map<String, Object>> compacted = compactor.autoCompact(new ArrayList<>(history));
            history.clear();
            history.addAll(compacted);
            if (context != null) {
                context.incrementCompactCount();
            }
            System.out.println("✅ [Layer 3] 强制压缩完成，历史已替换为摘要");
        } catch (Exception e) {
            System.err.println("⚠️ [Layer 3] 强制压缩失败: " + e.getMessage());
        }
    }

    private static boolean responseDeclaresTodoTool(LlmResponse response) {
        if (response.getContent() == null) {
            return false;
        }
        for (ContentBlock block : response.getContent()) {
            if ("tool_use".equals(block.getType()) && "todo".equals(block.getName())) {
                return true;
            }
        }
        return false;
    }
}
