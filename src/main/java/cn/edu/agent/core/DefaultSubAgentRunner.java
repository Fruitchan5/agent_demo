package cn.edu.agent.core;

import cn.edu.agent.pojo.AgentContext;
import cn.edu.agent.pojo.ContentBlock;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.pojo.SubAgentResult;
import cn.edu.agent.tool.AgentRole;
import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class DefaultSubAgentRunner implements SubAgentRunner {
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;

    @Override
    public String run(String prompt) {
        AgentContext context = new AgentContext(AgentRole.CHILD, maxIterations);
        context.appendMessage("user", prompt);

        try {
            while (!context.hasReachedLimit()) {
                LlmResponse response = llmClient.call(
                        context.getMessages(),
                        toolRegistry.getToolsForLlm(AgentRole.CHILD)
                );

                context.appendMessage("assistant", response.getContent());

                if (!"tool_use".equals(response.getStopReason())) {
                    String text = extractText(response);
                    int iterationsUsed = context.getCurrentIteration() + 1;
                    return SubAgentResult.success(text, iterationsUsed).getSummary();
                }

                List<Map<String, Object>> toolResults = new ArrayList<>();

                if (response.getContent() != null) {
                    for (ContentBlock block : response.getContent()) {
                        if ("text".equals(block.getType()) && block.getText() != null) {
                            System.out.println("[SubAgent] 🤔 思考: " + block.getText());
                        } else if ("tool_use".equals(block.getType())) {
                            String toolName = block.getName();
                            AgentTool tool = toolRegistry.getTool(toolName);

                            String toolOutput;
                            if (tool != null) {
                                System.out.println("[SubAgent] 🔧 调用工具: " + toolName);
                                toolOutput = tool.execute(block.getInput());
                            } else {
                                toolOutput = "Error: Tool " + toolName + " not found.";
                            }

                            System.out.println("[SubAgent]    输出: \n" + toolOutput);

                            toolResults.add(Map.of(
                                    "type", "tool_result",
                                    "tool_use_id", block.getId(),
                                    "content", toolOutput
                            ));
                        }
                    }
                }

                context.appendMessage("user", toolResults);
                context.incrementIteration();
            }

            return SubAgentResult.truncated("(subagent reached iteration limit)", maxIterations).getSummary();
        } catch (Exception e) {
            return SubAgentResult.error(e.getMessage(), context.getCurrentIteration()).getSummary();
        }
    }

    private static String extractText(LlmResponse response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.getContent()) {
            if ("text".equals(block.getType()) && block.getText() != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(block.getText());
            }
        }
        return sb.toString();
    }
}

