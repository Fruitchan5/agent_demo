package cn.edu.agent.background;

import java.time.Instant;

/**
 * 后台任务完成通知
 */
public class TaskNotification {
    private final String taskId;
    private final TaskStatus status;
    private final long durationMs;
    private final String summary;
    private final int exitCode;
    private final Instant startTime;
    private final Instant endTime;
    private final String errorMessage;
    private final String command;

    public TaskNotification(String taskId, TaskStatus status, long durationMs,
                           String summary, int exitCode, Instant startTime,
                           Instant endTime, String errorMessage, String command) {
        this.taskId = taskId;
        this.status = status;
        this.durationMs = durationMs;
        this.summary = summary;
        this.exitCode = exitCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorMessage = errorMessage;
        this.command = command;
    }

    public String getTaskId() { return taskId; }
    public TaskStatus getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public String getSummary() { return summary; }
    public int getExitCode() { return exitCode; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getErrorMessage() { return errorMessage; }
    public String getCommand() { return command; }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("Task ").append(taskId).append(" ");
        if ("completed".equals(status)) {
            sb.append("✓ completed");
        } else if ("failed".equals(status)) {
            sb.append("✗ failed");
        } else if ("timeout".equals(status)) {
            sb.append("⏱ timeout");
        } else {
            sb.append(status);
        }
        if (summary != null && !summary.isEmpty()) {
            sb.append("\n").append(summary);
        }
        return sb.toString();
    }
}
