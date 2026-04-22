package cn.edu.agent.test;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.pojo.AgentContext;
import cn.edu.agent.tool.AgentRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ContextCompactorTest {

    @Test
    void configDefaults_shouldMatchExpectedValues() {
        assertEquals(50000, AppConfig.getCompactTokenThreshold());
        assertEquals(3, AppConfig.getCompactKeepRecent());
        assertEquals(".transcripts", AppConfig.getTranscriptDir());
    }

    @Test
    void compactCount_shouldIncrementCorrectly() {
        AgentContext context = new AgentContext(AgentRole.PARENT, 10);
        assertEquals(0, context.getCompactCount());

        context.incrementCompactCount();
        context.incrementCompactCount();
        assertEquals(2, context.getCompactCount());
    }

    @Test
    void layer1_microCompact_shouldReplaceOlderToolResult() throws Exception {
        Class<?> compactorClass = findClass(
                "cn.edu.agent.core.ContextCompactor",
                "cn.edu.agent.compact.ContextCompactor"
        );
        assumeTrue(compactorClass != null, "ContextCompactor not available yet");

        Method microCompact = findMethod(compactorClass, "microCompact");
        assumeTrue(microCompact != null, "microCompact method not available");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(new java.util.HashMap<>(Map.of("role", "user", "content", "q1")));

        // 模拟历史轮次 1（最老，应该被压缩）
        messages.add(new java.util.HashMap<>(Map.of("role", "user", "content", new ArrayList<>(List.of(
                new java.util.HashMap<>(Map.of("type", "tool_result", "tool_use_id", "1", "content", "old-a", "name", "read_file"))
        )))));

        // 模拟历史轮次 2（次老，应该被压缩）
        messages.add(new java.util.HashMap<>(Map.of("role", "user", "content", new ArrayList<>(List.of(
                new java.util.HashMap<>(Map.of("type", "tool_result", "tool_use_id", "2", "content", "old-b", "name", "bash"))
        )))));

        // 模拟历史轮次 3（保留）
        messages.add(new java.util.HashMap<>(Map.of("role", "user", "content", new ArrayList<>(List.of(
                new java.util.HashMap<>(Map.of("type", "tool_result", "tool_use_id", "3", "content", "new-c", "name", "todo"))
        )))));

        // 模拟历史轮次 4（保留）
        messages.add(new java.util.HashMap<>(Map.of("role", "user", "content", new ArrayList<>(List.of(
                new java.util.HashMap<>(Map.of("type", "tool_result", "tool_use_id", "4", "content", "new-d", "name", "compact"))
        )))));

        invokeMicroCompact(microCompact, compactorClass, messages, 2);

        String all = messages.toString();
        assertTrue(
                all.contains("[Previous: used") || all.contains("[Compressed]"),
                "Older tool_result should be compacted into placeholders/summaries"
        );
    }

    @Test
    void layer2_autoCompact_shouldCompactAndCount() throws Exception {
        Class<?> loopClass = findClass("cn.edu.agent.core.AgentLoop");
        assumeTrue(loopClass != null, "AgentLoop not available");

        Method autoCompact = findMethod(loopClass, "autoCompactIfNeeded");
        assumeTrue(autoCompact != null, "autoCompactIfNeeded method not available");

        Object loop = loopClass.getDeclaredConstructor().newInstance();
        AgentContext context = new AgentContext(AgentRole.PARENT, 10);
        for (int i = 0; i < 50; i++) {
            context.appendMessage("user", "very long message " + i + " ".repeat(10000));
        }

        invokeAutoCompact(autoCompact, loop, context);
        assertTrue(context.getCompactCount() >= 1, "Layer2 should increment compactCount");
    }

    @Test
    void layer3_compactTool_shouldTriggerSameFlow() throws Exception {
        Class<?> loopClass = findClass("cn.edu.agent.core.AgentLoop");
        assumeTrue(loopClass != null, "AgentLoop not available");

        Method forceCompact = findMethod(loopClass, "forceCompact");
        assumeTrue(forceCompact != null, "forceCompact method not available");

        Object loop = loopClass.getDeclaredConstructor().newInstance();
        AgentContext context = new AgentContext(AgentRole.PARENT, 10);
        context.appendMessage("user", "trigger compact");

        invokeForceCompact(forceCompact, loop, context);
        assertTrue(context.getCompactCount() >= 1, "Layer3 should increment compactCount");
    }

    @Test
    void compactTool_shouldBeRegisteredWhenAvailable() throws Exception {
        Class<?> compactToolClass = findClass("cn.edu.agent.tool.impl.CompactTool");
        assumeTrue(compactToolClass != null, "CompactTool not available yet");

        Class<?> registryClass = Class.forName("cn.edu.agent.tool.ToolRegistry");
        Object registry = registryClass.getDeclaredConstructor().newInstance();
        Method getTool = registryClass.getMethod("getTool", String.class);
        Object tool = getTool.invoke(registry, "compact");
        assertTrue(tool != null, "compact tool should be in base registry");
    }

    private static void invokeMicroCompact(Method method, Class<?> holder, List<Map<String, Object>> messages, int keepRecent)
            throws Exception {
        method.setAccessible(true);
        Object target = java.lang.reflect.Modifier.isStatic(method.getModifiers())
                ? null
                : holder.getDeclaredConstructor().newInstance();
        if (method.getParameterCount() == 2) {
            method.invoke(target, messages, keepRecent);
        } else if (method.getParameterCount() == 1) {
            method.invoke(target, messages);
        } else {
            assumeTrue(false, "Unsupported microCompact signature");
        }
    }

    private static void invokeAutoCompact(Method method, Object loop, AgentContext context) throws Exception {
        method.setAccessible(true);
        if (method.getParameterCount() == 1) {
            method.invoke(loop, context);
            return;
        }
        if (method.getParameterCount() == 2) {
            method.invoke(loop, context, AppConfig.getCompactTokenThreshold());
            return;
        }
        assumeTrue(false, "Unsupported autoCompactIfNeeded signature");
    }

    private static void invokeForceCompact(Method method, Object loop, AgentContext context) throws Exception {
        method.setAccessible(true);
        if (method.getParameterCount() == 1) {
            method.invoke(loop, context);
            return;
        }
        if (method.getParameterCount() == 0) {
            Field field = loop.getClass().getDeclaredField("agentContext");
            field.setAccessible(true);
            field.set(loop, context);
            method.invoke(loop);
            return;
        }
        assumeTrue(false, "Unsupported forceCompact signature");
    }

    private static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }
}
