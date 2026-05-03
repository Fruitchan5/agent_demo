package cn.edu.agent.task;

/**
 * 任务认领结果
 */
public class ClaimResult {
    private final boolean success;
    private final String message;
    private final Task task;

    public ClaimResult(boolean success, String message, Task task) {
        this.success = success;
        this.message = message;
        this.task = task;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Task getTask() {
        return task;
    }

    public static ClaimResult success(Task task) {
        return new ClaimResult(true, "Claimed task #" + task.getId(), task);
    }

    public static ClaimResult error(String message) {
        return new ClaimResult(false, "Error: " + message, null);
    }
}
