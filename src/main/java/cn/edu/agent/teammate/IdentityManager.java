package cn.edu.agent.teammate;

import java.util.List;
import java.util.Map;

/**
 * 身份重注入管理器
 * 用于在上下文压缩后恢复 agent 的身份信息
 */
public class IdentityManager {

    /**
     * 创建身份块
     *
     * @param name 队友名称
     * @param role 队友角色
     * @param teamName 团队名称
     * @return 身份消息块
     */
    public static Map<String, Object> makeIdentityBlock(String name, String role, String teamName) {
        String content = String.format(
            "<identity>You are '%s', role: %s, team: %s. Continue your work.</identity>",
            name, role, teamName
        );
        return Map.of("role", "user", "content", content);
    }

    /**
     * 判断是否需要重注入身份
     * 当消息列表过短时（<= 3），表明发生了上下文压缩
     *
     * @param messages 消息历史列表
     * @return 是否需要重注入
     */
    public static boolean needsReinjection(List<Map<String, Object>> messages) {
        return messages.size() <= 3;
    }

    /**
     * 重注入身份信息
     * 在消息列表开头插入身份块和确认消息
     *
     * @param messages 消息历史列表（会被修改）
     * @param name 队友名称
     * @param role 队友角色
     * @param teamName 团队名称
     */
    public static void reinjectIdentity(
        List<Map<String, Object>> messages,
        String name,
        String role,
        String teamName
    ) {
        if (needsReinjection(messages)) {
            // 在开头插入身份块
            messages.add(0, makeIdentityBlock(name, role, teamName));
            // 在第二位插入确认消息
            messages.add(1, Map.of(
                "role", "assistant",
                "content", "I am " + name + ". Continuing."
            ));
        }
    }
}
