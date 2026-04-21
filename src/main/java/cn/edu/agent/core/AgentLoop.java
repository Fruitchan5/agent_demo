package cn.edu.agent.core;

import cn.edu.agent.pojo.ContentBlock;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.AgentRole;
import cn.edu.agent.tool.ToolManager;

import java.util.*;

// AgentLoop 是整个 Agent 的核心循环，负责管理对话历史、调用 LLM、执行工具，并根据 LLM 的回复决定下一步行动。
public class AgentLoop {
    private final LlmClient llmClient;
    private final ToolManager toolManager;
    private final String systemPrompt;
    private final List<Map<String, Object>> chatHistory = new ArrayList<>();

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
        System.out.println(" [Claude Agent Java Edition] 已启动! (s03: todo + 问责提醒)");

        while (true) {
            System.out.print("\n你 >> ");
            String userInput = scanner.nextLine();
            if ("exit".equalsIgnoreCase(userInput)) break;

            String contentForHistory = toolManager.prefixUserMessageIfNeeded(userInput);
            chatHistory.add(Map.of("role", "user", "content", contentForHistory));

            runAgentCycle();
        }
    }

    // Agent 的自主思考循环
    private void runAgentCycle() {
        boolean turnFinished = false;

        while (!turnFinished) {
            try {
                LlmResponse response = llmClient.call(
                        chatHistory,
                        toolManager.getToolsForLlm(AgentRole.PARENT),
                        systemPrompt
                );

                chatHistory.add(Map.of("role", "assistant", "content", response.getContent()));

                if ("tool_use".equals(response.getStopReason())) {
                    boolean usedTodo = responseDeclaresTodoTool(response);
                    List<Map<String, Object>> toolResults = new ArrayList<>();

                    for (ContentBlock block : response.getContent()) {
                        if ("text".equals(block.getType()) && block.getText() != null) {
                            System.out.println("🤔 思考: " + block.getText());
                        } else if ("tool_use".equals(block.getType())) {
                            AgentTool tool = toolManager.getTool(block.getName());
                            String toolName = block.getName();
                            if ("todo".equals(toolName)) {
                                toolManager.resetTodoCounter(); // 只有真正调了，才原谅它
                            }
                            String toolOutput;
                            if (tool != null) {
                                toolOutput = tool.execute(block.getInput());
                            } else {
                                toolOutput = "Error: Tool " + block.getName() + " not found.";
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

                    if (usedTodo) {
                        toolManager.resetTodoCounter();
                    } else {
                        toolManager.incrementRoundCounter();
                    }
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
