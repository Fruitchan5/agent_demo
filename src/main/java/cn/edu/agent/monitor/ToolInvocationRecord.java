package cn.edu.agent.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ToolInvocationRecord {
    public final String toolName;
    public final Instant startTime;
    public final long durationMs;
    public final boolean success;
    public final String errorMsg;
    public final String inputSummary;
    public final String outputSummary;

}
