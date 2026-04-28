package cn.edu.agent.monitor;

import cn.edu.agent.tool.AgentTool;

import java.time.Instant;
import java.util.Map;

public class MonitoredTool implements AgentTool {

    private final AgentTool delegate;
    private final SessionStats stats = SessionStats.get();

    public MonitoredTool(AgentTool delegate) {
        this.delegate = delegate;
    }

    @Override public String getName()                        { return delegate.getName(); }
    @Override public String getDescription()                 { return delegate.getDescription(); }
    @Override public Map<String, Object> getInputSchema()    { return delegate.getInputSchema(); }


    // 监控工具的核心：在 execute 前后记录时间、输入输出、成功失败等信息
    @Override
    public String execute(Map<String, Object> input) throws Exception {
        Instant start = Instant.now();
        String inputSummary = truncate(input.toString(), 50);
        boolean success = true;
        String errorMsg = null;
        String outputSummary;
        String result;

        try {
            result = delegate.execute(input);
            outputSummary = truncate(result, 100);
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            outputSummary = "ERROR: " + errorMsg;
            long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
            stats.recordTool(new ToolInvocationRecord(
                    getName(), start, ms, false, errorMsg, inputSummary, outputSummary));
            System.out.printf("[MONITOR] %-12s | %4dms | FAIL%n", getName(), ms);
            throw e;
        }

        long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
        stats.recordTool(new ToolInvocationRecord(
                getName(), start, ms, true, null, inputSummary, outputSummary));
        System.out.printf("[MONITOR] %-12s | %4dms | OK%n", getName(), ms);
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
