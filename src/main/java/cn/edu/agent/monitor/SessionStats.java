package cn.edu.agent.monitor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SessionStats {

    private static final SessionStats INSTANCE = new SessionStats();

    public static SessionStats get() { return INSTANCE; }

    public final String sessionId = String.valueOf(System.currentTimeMillis());
    public final Instant sessionStart = Instant.now();

    private final List<ToolInvocationRecord> toolRecords = new CopyOnWriteArrayList<>();
    private final List<LlmInvocationRecord> llmRecords = new CopyOnWriteArrayList<>();

    private SessionStats() {}

    public void recordTool(ToolInvocationRecord r) { toolRecords.add(r); }
    public void recordLlm(LlmInvocationRecord r)   { llmRecords.add(r); }

    public List<ToolInvocationRecord> getToolRecords() { return Collections.unmodifiableList(toolRecords); }
    public List<LlmInvocationRecord>  getLlmRecords()  { return Collections.unmodifiableList(llmRecords); }

    /** 按工具名聚合：调用次数、成功数、平均耗时 */
    public Map<String, long[]> toolSummary() {
        // long[0]=total, long[1]=success, long[2]=totalMs
        Map<String, long[]> map = new LinkedHashMap<>();
        for (ToolInvocationRecord r : toolRecords) {
            map.computeIfAbsent(r.toolName, k -> new long[3]);
            long[] s = map.get(r.toolName);
            s[0]++;
            if (r.success) s[1]++;
            s[2] += r.durationMs;
        }
        return map;
    }

    public long totalLlmDurationMs() {
        return llmRecords.stream().mapToLong(r -> r.durationMs).sum();
    }

    public long llmSuccessCount() {
        return llmRecords.stream().filter(r -> r.success).count();
    }
}
