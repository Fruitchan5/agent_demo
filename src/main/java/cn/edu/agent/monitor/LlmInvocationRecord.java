package cn.edu.agent.monitor;

import java.time.Instant;

public class LlmInvocationRecord {
    public final Instant startTime;
    public final long durationMs;
    public final boolean success;
    public final String errorMsg;
    public final String stopReason;
    public final int contentBlockCount;
    public final int retryCount;

    public LlmInvocationRecord(Instant startTime, long durationMs, boolean success,
                                String errorMsg, String stopReason,
                                int contentBlockCount, int retryCount) {
        this.startTime = startTime;
        this.durationMs = durationMs;
        this.success = success;
        this.errorMsg = errorMsg;
        this.stopReason = stopReason;
        this.contentBlockCount = contentBlockCount;
        this.retryCount = retryCount;
    }
}
