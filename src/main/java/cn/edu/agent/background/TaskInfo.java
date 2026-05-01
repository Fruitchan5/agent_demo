package cn.edu.agent.background;

import java.time.Instant;

/**
 * 后台任务信息（带状态机验证）
 */
public class TaskInfo {
    private final String taskId;
    private final String command;
    private final String workDir;
    private TaskStatus status;
    private Process process;
    private Instant startTime;
    private Instant endTime;
    private int exitCode;
    private String output;
    private String errorMessage;

    public TaskInfo(String taskId, String command, String workDir) {
        this.taskId = taskId;
        this.command = command;
        this.workDir = workDir;
        this.status = TaskStatus.PENDING;
        this.startTime = Instant.now();
    }

    public synchronized void transitionTo(TaskStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new IllegalStateException(
                "Invalid transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
        if (newStatus.isTerminal() && this.endTime == null) {
            this.endTime = Instant.now();
        }
    }

    private boolean isValidTransition(TaskStatus from, TaskStatus to) {
        switch (from) {
            case PENDING:
                return to == TaskStatus.RUNNING || to == TaskStatus.START_FAILED;
            case RUNNING:
                return to == TaskStatus.COMPLETED || to == TaskStatus.FAILED
                    || to == TaskStatus.TIMEOUT || to == TaskStatus.CANCELLED;
            default:
                return false;  // 终态不能转换
        }
    }

    // Getters and setters
    public String getTaskId() { return taskId; }
    public String getCommand() { return command; }
    public String getWorkDir() { return workDir; }
    public synchronized TaskStatus getStatus() { return status; }
    public synchronized Process getProcess() { return process; }
    public synchronized void setProcess(Process process) { this.process = process; }
    public Instant getStartTime() { return startTime; }
    public synchronized Instant getEndTime() { return endTime; }
    public synchronized int getExitCode() { return exitCode; }
    public synchronized void setExitCode(int exitCode) { this.exitCode = exitCode; }
    public synchronized String getOutput() { return output; }
    public synchronized void setOutput(String output) { this.output = output; }
    public synchronized String getErrorMessage() { return errorMessage; }
    public synchronized void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
