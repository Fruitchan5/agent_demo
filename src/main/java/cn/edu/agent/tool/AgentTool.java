package cn.edu.agent.tool;

import java.util.Map;

public interface AgentTool {
    String getName();        // 工具的名称，LLM 调用的凭证
    String getDescription(); // 告诉 LLM 这个工具是干嘛的
    Map<String, Object> getInputSchema(); // 告诉 LLM 需要传什么参数
    String execute(Map<String, Object> input) throws Exception; // 真正的执行逻辑
}