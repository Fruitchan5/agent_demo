package cn.edu.agent.core;

import cn.edu.agent.pojo.ContentBlock;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.ToolRegistry;

import java.util.*;

// AgentLoop 是整个 Agent 的核心循环，负责管理对话历史、调用 LLM、执行工具，并根据 LLM 的回复决定下一步行动。
public class AgentLoop {
    private final LlmClient llmClient = new LlmClient();
    private final ToolRegistry toolRegistry = new ToolRegistry();
    private final List<Map<String, Object>> chatHistory = new ArrayList<>();

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println(" [Kiro Agent Java Edition] 已启动!");

        while (true) {
            System.out.print("\n你 >> ");
            String userInput = scanner.nextLine();
            if ("exit".equalsIgnoreCase(userInput)) break;

            // 1. 记录用户输入
            chatHistory.add(Map.of("role", "user", "content", userInput));

            // 进入 Agent 的自主思考循环
            runAgentCycle();
        }
    }

    // Agent 的自主思考循环
    private void runAgentCycle() {
        boolean turnFinished = false;

        while (!turnFinished) {
            try {
                // 2. 调用大模型
                LlmResponse response = llmClient.call(chatHistory, toolRegistry.getToolsForLlm());

                // 将助手的原始回复加入历史记录 (非常重要，否则上下文会断)
                chatHistory.add(Map.of("role", "assistant", "content", response.getContent()));

                // 3. 判断是否需要使用工具
                if ("tool_use".equals(response.getStopReason())) {
                    List<Map<String, Object>> toolResults = new ArrayList<>();

                    // 遍历模型返回的 content，寻找 tool_use
                    for (ContentBlock block : response.getContent()) {
                        if ("text".equals(block.getType()) && block.getText() != null) {
                            System.out.println("🤔 思考: " + block.getText());
                        } else if ("tool_use".equals(block.getType())) {
                            // 拿到模型想调用的工具
                            AgentTool tool = toolRegistry.getTool(block.getName());
                            String toolOutput;
                            if (tool != null) {
                                toolOutput = tool.execute(block.getInput());
                            } else {
                                toolOutput = "Error: Tool " + block.getName() + " not found.";
                            }
                            System.out.println("    输出: \n" + toolOutput);

                            // 包装工具执行结果
                            toolResults.add(Map.of(
                                    "type", "tool_result",
                                    "tool_use_id", block.getId(),
                                    "content", toolOutput
                            ));
                        }
                    }
                    // 把工具执行结果作为 user 角色发回给模型，继续循环！
                    chatHistory.add(Map.of("role", "user", "content", toolResults));

                } else {
                    // 模型没有调用工具，正常回复文本
                    for (ContentBlock block : response.getContent()) {
                        if ("text".equals(block.getType())) {
                            System.out.println("🤖 Kiro >> " + block.getText());
                        }
                    }
                    turnFinished = true; // 结束这一轮的自主思考，等待用户下一句话
                }
            } catch (Exception e) {
                System.err.println("❌ 发生错误: " + e.getMessage());
                turnFinished = true; // 报错则强制中断循环
            }
        }
    }
}