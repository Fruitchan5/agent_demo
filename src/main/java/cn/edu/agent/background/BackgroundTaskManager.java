package cn.edu.agent.background;

import cn.edu.agent.config.AppConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * 后台任务管理器（线程安全）
 */
public class BackgroundTaskManager {
    private final ConcurrentHashMap<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    private final BlockingQueue<TaskNotification> notificationQueue = new LinkedBlockingQueue<>();
    private final TaskExecutor executor;
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public BackgroundTaskManager() {
        int maxTasks = AppConfig.getBackgroundMaxTasks();
        this.executor = AppConfig.useThreadPool()
            ? new PooledExecutor(maxTasks)
            : new DaemonThreadExecutor(maxTasks);

        // 每 5 分钟清理一次超过 1 小时的已完成任务
        cleaner.scheduleAtFixedRate(this::cleanupOldTasks, 5, 5, TimeUnit.MINUTES);
    }

    public String runInBackground(String command, String workDir, Integer timeoutSeconds) {
        // 检查并发任务数
        long running = tasks.values().stream()
            .filter(t -> t.getStatus() == TaskStatus.RUNNING)
            .count();
        int maxTasks = AppConfig.getBackgroundMaxTasks();
        if (running >= maxTasks) {
            return "Error: Too many background tasks (" + running + "/" + maxTasks + "), wait for some to finish";
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        TaskInfo taskInfo = new TaskInfo(taskId, command, workDir);
        tasks.put(taskId, taskInfo);

        int timeout = timeoutSeconds != null ? timeoutSeconds : AppConfig.getBackgroundTimeoutSeconds();

        try {
            executor.execute(() -> executeTask(taskInfo, timeout), taskId);
            return "Background task " + taskId + " started";
        } catch (RejectedExecutionException e) {
            taskInfo.transitionTo(TaskStatus.START_FAILED);
            taskInfo.setErrorMessage(e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public String checkTask(String taskId) {
        TaskInfo info = tasks.get(taskId);
        if (info == null) {
            return "Error: Task " + taskId + " not found";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(taskId).append("\n");
        sb.append("Command: ").append(info.getCommand()).append("\n");
        sb.append("Status: ").append(info.getStatus()).append("\n");
        sb.append("Start: ").append(info.getStartTime()).append("\n");

        if (info.getEndTime() != null) {
            long duration = ChronoUnit.MILLIS.between(info.getStartTime(), info.getEndTime());
            sb.append("Duration: ").append(duration / 1000.0).append("s\n");
            sb.append("Exit code: ").append(info.getExitCode()).append("\n");
        }

        if (info.getErrorMessage() != null) {
            sb.append("Error: ").append(info.getErrorMessage()).append("\n");
        }

        if (info.getOutput() != null) {
            sb.append("\nOutput:\n").append(info.getOutput());
        }

        return sb.toString();
    }

    public String listTasks() {
        if (tasks.isEmpty()) {
            return "No background tasks";
        }
        StringBuilder sb = new StringBuilder("Background tasks:\n");
        for (Map.Entry<String, TaskInfo> entry : tasks.entrySet()) {
            TaskInfo info = entry.getValue();
            sb.append("- ").append(entry.getKey())
              .append(" [").append(info.getStatus()).append("]")
              .append(" ").append(info.getCommand())
              .append("\n");
        }
        return sb.toString();
    }

    public String cancelTask(String taskId) {
        TaskInfo info = tasks.get(taskId);
        if (info == null) {
            return "Error: Task " + taskId + " not found";
        }

        if (info.getStatus() != TaskStatus.RUNNING) {
            return "Task " + taskId + " is already " + info.getStatus();
        }

        Process process = info.getProcess();
        if (process == null || !process.isAlive()) {
            return "Task " + taskId + " process not found or already finished";
        }

        // 优雅终止
        process.destroy();
        try {
            int gracefulSeconds = AppConfig.getBackgroundGracefulShutdownSeconds();
            boolean exited = process.waitFor(gracefulSeconds, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }

        info.transitionTo(TaskStatus.CANCELLED);
        info.setOutput("Task cancelled by user");
        return "Task " + taskId + " cancelled";
    }

    public List<TaskNotification> drainNotifications() {
        List<TaskNotification> result = new ArrayList<>();
        notificationQueue.drainTo(result);
        return result;
    }

    public void shutdown() {
        cleaner.shutdown();
        executor.shutdown();
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private void executeTask(TaskInfo taskInfo, int timeoutSeconds) {
        try {
            System.out.println("[DEBUG] executeTask started for " + taskInfo.getTaskId());
            taskInfo.transitionTo(TaskStatus.RUNNING);

            ProcessBuilder pb = createProcessBuilder(taskInfo.getCommand(), taskInfo.getWorkDir());
            Process process = pb.start();
            taskInfo.setProcess(process);
            System.out.println("[DEBUG] Process started, PID: " + process.pid());

            // 启动输出捕获线程（异步）
            OutputCapture capture = new OutputCapture(process, AppConfig.getBackgroundOutputMaxChars());
            capture.start();
            System.out.println("[DEBUG] Output capture started");

            // 等待进程完成或超时
            System.out.println("[DEBUG] Waiting for process, timeout: " + timeoutSeconds + "s");
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            System.out.println("[DEBUG] waitFor returned: " + finished);

            if (!finished) {
                // 超时：先强制终止进程
                process.destroyForcibly();
                // 等待输出捕获完成（最多2秒，因为进程已被终止）
                String output = capture.getOutput(2);
                taskInfo.transitionTo(TaskStatus.TIMEOUT);
                taskInfo.setOutput(output + "\n... (timeout after " + timeoutSeconds + "s)");
                taskInfo.setExitCode(-1);
            } else {
                // 正常完成：等待输出捕获完成（最多5秒）
                String output = capture.getOutput(5);
                int exitCode = process.exitValue();
                taskInfo.setExitCode(exitCode);
                taskInfo.setOutput(output);
                taskInfo.transitionTo(exitCode == 0 ? TaskStatus.COMPLETED : TaskStatus.FAILED);
            }

            // 入队通知
            enqueueNotification(taskInfo);

        } catch (IOException e) {
            System.out.println("[DEBUG] IOException: " + e.getMessage());
            taskInfo.transitionTo(TaskStatus.START_FAILED);
            taskInfo.setErrorMessage(e.getMessage());
            taskInfo.setOutput("Failed to start: " + e.getMessage());
            enqueueNotification(taskInfo);
        } catch (InterruptedException e) {
            System.out.println("[DEBUG] InterruptedException: " + e.getMessage());
            taskInfo.transitionTo(TaskStatus.CANCELLED);
            taskInfo.setOutput("Task interrupted");
            enqueueNotification(taskInfo);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 捕获所有其他异常（如 NullPointerException, IllegalArgumentException 等）
            System.out.println("[DEBUG] Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            taskInfo.transitionTo(TaskStatus.START_FAILED);
            taskInfo.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            taskInfo.setOutput("Failed to execute: " + e.getMessage());
            enqueueNotification(taskInfo);
        }
    }

    private ProcessBuilder createProcessBuilder(String command, String workDir) {
        ProcessBuilder pb;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }
        if (workDir != null) {
            pb.directory(new File(workDir));
        }
        pb.redirectErrorStream(false);
        return pb;
    }

    /**
     * 异步输出捕获器，避免阻塞主线程
     */
    private static class OutputCapture {
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private final Thread stdoutReader;
        private final Thread stderrReader;
        private final int maxChars;

        public OutputCapture(Process process, int maxChars) {
            this.maxChars = maxChars;

            this.stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && stdout.length() < maxChars) {
                        stdout.append(line).append("\n");
                    }
                } catch (IOException e) {
                    // ignore
                }
            });
            this.stdoutReader.setDaemon(true);  // 防止资源泄漏

            this.stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && stderr.length() < maxChars) {
                        stderr.append(line).append("\n");
                    }
                } catch (IOException e) {
                    // ignore
                }
            });
            this.stderrReader.setDaemon(true);  // 防止资源泄漏
        }

        public void start() {
            stdoutReader.start();
            stderrReader.start();
        }

        public String getOutput(int timeoutSeconds) {
            try {
                stdoutReader.join(timeoutSeconds * 1000L);
                stderrReader.join(timeoutSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            String combined = stdout.toString() + stderr.toString();
            if (combined.length() > maxChars) {
                return combined.substring(0, maxChars) + "\n... (truncated)";
            }
            return combined;
        }
    }

    private void enqueueNotification(TaskInfo taskInfo) {
        int summaryChars = AppConfig.getBackgroundNotificationSummaryChars();
        String output = taskInfo.getOutput() != null ? taskInfo.getOutput() : "";
        String summary = output.length() > summaryChars
            ? output.substring(0, summaryChars) + "..."
            : output;

        long duration = taskInfo.getEndTime() != null
            ? ChronoUnit.MILLIS.between(taskInfo.getStartTime(), taskInfo.getEndTime())
            : 0;

        TaskNotification notification = new TaskNotification(
            taskInfo.getTaskId(),
            taskInfo.getStatus(),
            duration,
            summary,
            taskInfo.getExitCode(),
            taskInfo.getStartTime(),
            taskInfo.getEndTime(),
            taskInfo.getErrorMessage(),
            taskInfo.getCommand()
        );

        notificationQueue.offer(notification);
    }

    private void cleanupOldTasks() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        tasks.entrySet().removeIf(entry -> {
            TaskInfo info = entry.getValue();
            return info.getStatus().isTerminal()
                && info.getEndTime() != null
                && info.getEndTime().isBefore(cutoff);
        });
    }
}
