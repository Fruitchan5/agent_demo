package cn.edu.agent.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class MonitorLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** 打印会话统计到控制台 */
    public static void printStats() {
        SessionStats s = SessionStats.get();
        System.out.println("\n========== /stats ==========");
        System.out.printf("Session ID : %s%n", s.sessionId);
        System.out.printf("Duration   : %ds%n",
                Duration.between(s.sessionStart, Instant.now()).getSeconds());

        // LLM 统计
        List<LlmInvocationRecord> llmList = s.getLlmRecords();
        System.out.printf("%nLLM calls  : %d total, %d success%n",
                llmList.size(), s.llmSuccessCount());
        if (!llmList.isEmpty()) {
            System.out.printf("LLM avg ms : %dms%n",
                    s.totalLlmDurationMs() / llmList.size());
        }

        // 工具统计
        Map<String, long[]> summary = s.toolSummary();
        if (summary.isEmpty()) {
            System.out.println("\nNo tool calls yet.");
        } else {
            System.out.println("\n--- Tool Calls ---");
            System.out.printf("%-14s %6s %7s %8s %10s%n",
                    "Tool", "Calls", "Success", "FailRate", "AvgMs");
            System.out.println("-".repeat(50));
            for (Map.Entry<String, long[]> e : summary.entrySet()) {
                long[] v = e.getValue();
                long total = v[0], ok = v[1], totalMs = v[2];
                double failRate = total == 0 ? 0 : (double)(total - ok) / total * 100;
                System.out.printf("%-14s %6d %7d %7.1f%% %9dms%n",
                        e.getKey(), total, ok, failRate, totalMs / total);
            }
        }
        System.out.println("=============================\n");
    }

    /** 将完整记录序列化为 JSON 写入 .logs/ 目录 */
    public static void flushToFile() {
        SessionStats s = SessionStats.get();
        try {
            Path logsDir = Paths.get(".logs");
            Files.createDirectories(logsDir);
            Path file = logsDir.resolve("session_" + s.sessionId + ".json");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", s.sessionId);
            payload.put("sessionStart", s.sessionStart.toString());
            payload.put("durationSeconds",
                    Duration.between(s.sessionStart, Instant.now()).getSeconds());

            // LLM records
            List<Map<String, Object>> llmList = new ArrayList<>();
            for (LlmInvocationRecord r : s.getLlmRecords()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("startTime", r.startTime.toString());
                m.put("durationMs", r.durationMs);
                m.put("success", r.success);
                m.put("errorMsg", r.errorMsg);
                m.put("stopReason", r.stopReason);
                m.put("contentBlockCount", r.contentBlockCount);
                m.put("retryCount", r.retryCount);
                llmList.add(m);
            }
            payload.put("llmInvocations", llmList);

            // Tool records
            List<Map<String, Object>> toolList = new ArrayList<>();
            for (ToolInvocationRecord r : s.getToolRecords()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("toolName", r.toolName);
                m.put("startTime", r.startTime.toString());
                m.put("durationMs", r.durationMs);
                m.put("success", r.success);
                m.put("errorMsg", r.errorMsg);
                m.put("inputSummary", r.inputSummary);
                m.put("outputSummary", r.outputSummary);
                toolList.add(m);
            }
            payload.put("toolInvocations", toolList);

            MAPPER.writeValue(file.toFile(), payload);
            System.out.println("[MONITOR] 日志已写入: " + file.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[MONITOR] 写日志失败: " + e.getMessage());
        }
    }
}
