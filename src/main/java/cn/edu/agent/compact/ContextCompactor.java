package cn.edu.agent.compact;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.core.LlmClient;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.pojo.ContentBlock;

import java.util.*;

/**
 * 三层上下文压缩器
 * Layer 1 — microCompact：静默替换旧 tool_result，每轮必执行
 * Layer 2 — autoCompact：token 超阈值时调用 LLM 生成摘要，整体替换
 * Layer 3 — 由 CompactTool 触发，逻辑同 Layer 2
 */
public class ContextCompactor {

    private final LlmClient llmClient;
    private final TranscriptManager transcriptManager;

    public ContextCompactor() {
        this.llmClient = new LlmClient();
        this.transcriptManager = new TranscriptManager();
    }

    /**
     * Layer 1 — Micro Compact（静默，每轮必执行）
     * 将超过 keepRecent 轮的旧 tool_result 替换为占位符，
     * 只保留最近 keepRecent 条 tool_result 的完整内容。
     *
     * @param messages   当前完整对话历史（原地修改）
     * @param keepRecent 保留最近几条 tool_result 不压缩（默认 3）
     * @return 压缩后的消息列表（同一引用）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> microCompact(List<Map<String, Object>> messages, int keepRecent) {
        // 收集所有包含 tool_result 的 user 消息（content 为 List 类型）
        List<Map<String, Object>> toolResultMessages = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            if ("user".equals(msg.get("role")) && msg.get("content") instanceof List) {
                List<Object> contentList = (List<Object>) msg.get("content");
                boolean hasToolResult = contentList.stream()
                        .anyMatch(item -> item instanceof Map
                                && "tool_result".equals(((Map<Object, Object>) item).get("type")));
                if (hasToolResult) {
                    toolResultMessages.add(msg);
                }
            }
        }

        // 只压缩超出 keepRecent 的旧消息
        int totalToolResultMsgs = toolResultMessages.size();
        int compressCount = Math.max(0, totalToolResultMsgs - keepRecent);

        for (int i = 0; i < compressCount; i++) {
            Map<String, Object> msg = toolResultMessages.get(i);
            List<Object> contentList = (List<Object>) msg.get("content");
            List<Object> compressedContent = new ArrayList<>();

            for (Object item : contentList) {
                if (item instanceof Map) {
                    Map<String, Object> block = (Map<String, Object>) item;
                    if ("tool_result".equals(block.get("type"))) {
                        // 替换为占位符，保留 tool_use_id
                        String toolName = (String) block.getOrDefault("name", "tool");
                        Map<String, Object> placeholder = new LinkedHashMap<>();
                        placeholder.put("type", "tool_result");
                        placeholder.put("tool_use_id", block.get("tool_use_id"));
                        placeholder.put("content", "[Previous: used " + toolName + "]");
                        compressedContent.add(placeholder);
                    } else {
                        compressedContent.add(item);
                    }
                } else {
                    compressedContent.add(item);
                }
            }

            // 原地替换 content（需要可变 Map）
            Map<String, Object> mutableMsg = new LinkedHashMap<>(msg);
            mutableMsg.put("content", compressedContent);
            int idx = messages.indexOf(msg);
            if (idx >= 0) {
                messages.set(idx, mutableMsg);
            }
        }

        return messages;
    }

    /**
     * Layer 2 — Auto Compact（token 超阈值时触发）
     * 1. 调用 TranscriptManager 将完整历史落盘
     * 2. 调用 LlmClient 对历史做摘要
     * 3. 返回只含一条 [Compressed] 摘要消息的新列表
     *
     * @param messages 当前完整对话历史
     * @return 压缩后的单条摘要消息列表
     */
    public List<Map<String, Object>> autoCompact(List<Map<String, Object>> messages) throws Exception {
        // Step 1：落盘完整历史
        String transcriptDir = AppConfig.getTranscriptDir();
        try {
            String savedPath = transcriptManager.save(messages, transcriptDir);
            System.out.println("📁 对话历史已落盘: " + savedPath);
        } catch (Exception e) {
            System.err.println("⚠️ 落盘失败（继续压缩）: " + e.getMessage());
        }

        // Step 2：构造摘要请求
        String summaryPrompt = buildSummaryPrompt(messages);
        List<Map<String, Object>> summaryMessages = new ArrayList<>();
        summaryMessages.add(Map.of("role", "user", "content", summaryPrompt));

        // Step 3：调用 LLM 生成摘要
        String summary;
        try {
            LlmResponse response = llmClient.call(summaryMessages, Collections.emptyList(),
                    "你是一个对话历史压缩助手。请将以下对话历史压缩为简洁的摘要，保留关键进度、决策和待办事项。");
            StringBuilder sb = new StringBuilder();
            if (response.getContent() != null) {
                for (ContentBlock block : response.getContent()) {
                    if ("text".equals(block.getType()) && block.getText() != null) {
                        sb.append(block.getText());
                    }
                }
            }
            summary = sb.toString().trim();
            if (summary.isEmpty()) {
                summary = "（对话历史已压缩，摘要生成失败）";
            }
        } catch (Exception e) {
            System.err.println("⚠️ LLM 摘要失败，使用简单摘要: " + e.getMessage());
            summary = "（对话历史已压缩，共 " + messages.size() + " 条消息）";
        }

        // Step 4：返回单条摘要消息
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Map.of("role", "user", "content", "[Compressed]\n\n" + summary));
        return result;
    }

    /**
     * 估算消息列表的 token 数（粗估：字符数 / 4）
     *
     * @param messages 消息列表
     * @return 估算 token 数
     */
    public int estimateTokens(List<Map<String, Object>> messages) {
        int charCount = 0;
        for (Map<String, Object> msg : messages) {
            charCount += toCharCount(msg);
        }
        return charCount / 4;
    }

    // ---- 私有辅助方法 ----

    private int toCharCount(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof String) return ((String) obj).length();
        if (obj instanceof Map) {
            int count = 0;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) obj).entrySet()) {
                count += toCharCount(entry.getKey());
                count += toCharCount(entry.getValue());
            }
            return count;
        }
        if (obj instanceof List) {
            int count = 0;
            for (Object item : (List<Object>) obj) {
                count += toCharCount(item);
            }
            return count;
        }
        return obj.toString().length();
    }

    private String buildSummaryPrompt(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下对话历史压缩为简洁摘要，保留关键进度、决策和待办事项：\n\n");
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.getOrDefault("role", "unknown");
            Object content = msg.get("content");
            sb.append("[").append(role).append("]: ");
            if (content instanceof String) {
                sb.append(content);
            } else if (content instanceof List) {
                for (Object item : (List<Object>) content) {
                    if (item instanceof Map) {
                        Map<Object,Object> block = (Map<Object,Object>) item;
                        String type = (String) block.getOrDefault("type", "");
                        if ("text".equals(type)) {
                            sb.append(block.getOrDefault("text", ""));
                        } else if ("tool_use".equals(type)) {
                            sb.append("[调用工具: ").append(block.getOrDefault("name", "")).append("]");
                        } else if ("tool_result".equals(type)) {
                            String c = String.valueOf(block.getOrDefault("content", ""));
                            // 截断过长的工具结果
                            if (c.length() > 200) c = c.substring(0, 200) + "...";
                            sb.append("[工具结果: ").append(c).append("]");
                        }
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
