package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CompactTool — Layer 3 压缩工具
 * LLM 感觉上下文过长时可主动调用此工具。
 * execute() 本身不做压缩，只返回标记字符串 "__COMPACT__"，
 * AgentLoop 检测到该标记后调用 ContextCompactor.autoCompact()。
 */
public class CompactTool implements AgentTool {

    public static final String COMPACT_SIGNAL = "__COMPACT__";

    @Override
    public String getName() {
        return "compact";
    }

    @Override
    public String getDescription() {
        return "当你感觉对话历史过长、影响思考效率时，主动调用此工具压缩上下文。" +
                "压缩后历史将被摘要替换，但完整记录已落盘保存。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> reasonProp = new LinkedHashMap<>();
        reasonProp.put("type", "string");
        reasonProp.put("description", "触发压缩的原因（可选，用于日志）");
        properties.put("reason", reasonProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> input) {
        // 记录触发原因（如有）
        if (input != null && input.containsKey("reason")) {
            System.out.println("🗜️ LLM 主动触发压缩，原因: " + input.get("reason"));
        } else {
            System.out.println("🗜️ LLM 主动触发压缩");
        }
        // 返回标记，由 AgentLoop 检测并执行真正的压缩
        return COMPACT_SIGNAL;
    }
}
