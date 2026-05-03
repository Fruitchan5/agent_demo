package cn.edu.agent.worktree;

import cn.edu.agent.task.TaskManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * WorktreeManager实现类
 * 管理git worktree的生命周期和任务绑定
 */
public class WorktreeManagerImpl implements WorktreeManager {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private final Path projectRoot;
    private final Path worktreesDir;
    private final Path indexFile;
    private final Path eventsFile;
    private final TaskManager taskManager;
    private final boolean gitAvailable;

    public WorktreeManagerImpl(Path projectRoot, TaskManager taskManager) {
        this.projectRoot = projectRoot;
        this.worktreesDir = projectRoot.resolve(".worktrees");
        this.indexFile = worktreesDir.resolve("index.json");
        this.eventsFile = worktreesDir.resolve("events.jsonl");
        this.taskManager = taskManager;
        this.gitAvailable = checkGitAvailable();

        // 初始化目录和文件
        try {
            Files.createDirectories(worktreesDir);
            if (!Files.exists(indexFile)) {
                saveIndex(new WorktreeIndex());
            }
            if (!Files.exists(eventsFile)) {
                Files.createFile(eventsFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize worktree manager", e);
        }
    }

    private boolean checkGitAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree");
            pb.directory(projectRoot.toFile());
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public synchronized WorktreeInfo create(String name, Integer taskId, String baseRef) {
        if (!gitAvailable) {
            throw new RuntimeException("Not in a git repository. Worktree tools require git.");
        }

        validateName(name);

        // 检查是否已存在
        if (get(name) != null) {
            throw new IllegalArgumentException("Worktree '" + name + "' already exists");
        }

        // 检查任务是否存在
        if (taskId != null) {
            try {
                taskManager.get(taskId);
            } catch (Exception e) {
                throw new IllegalArgumentException("Task " + taskId + " not found");
            }
        }

        Path worktreePath = worktreesDir.resolve(name);
        String branch = "wt/" + name;
        String actualBaseRef = (baseRef == null || baseRef.isEmpty()) ? "HEAD" : baseRef;

        // 发出创建前事件
        emitEvent("worktree.create.before", Map.of(
            "task", taskId != null ? Map.of("id", taskId) : Map.of(),
            "worktree", Map.of("name", name, "base_ref", actualBaseRef)
        ));

        try {
            // 执行git worktree add
            runGit("worktree", "add", "-b", branch, worktreePath.toString(), actualBaseRef);

            // 创建worktree信息
            WorktreeInfo info = new WorktreeInfo(name, worktreePath, branch, taskId, WorktreeState.ACTIVE);

            // 更新索引
            WorktreeIndex index = loadIndex();
            index.getWorktrees().add(info);
            saveIndex(index);

            // 绑定任务
            if (taskId != null) {
                taskManager.bindWorktree(taskId, name);
            }

            // 发出创建后事件
            emitEvent("worktree.create.after", Map.of(
                "task", taskId != null ? Map.of("id", taskId) : Map.of(),
                "worktree", Map.of(
                    "name", name,
                    "path", worktreePath.toString(),
                    "branch", branch,
                    "status", "active"
                )
            ));

            return info;

        } catch (Exception e) {
            // 发出失败事件
            emitEvent("worktree.create.failed", Map.of(
                "task", taskId != null ? Map.of("id", taskId) : Map.of(),
                "worktree", Map.of("name", name, "base_ref", actualBaseRef),
                "error", e.getMessage()
            ));
            throw new RuntimeException("Failed to create worktree: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized WorktreeInfo get(String name) {
        WorktreeIndex index = loadIndex();
        return index.getWorktrees().stream()
            .filter(wt -> wt.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    @Override
    public synchronized List<WorktreeInfo> listAll() {
        return new ArrayList<>(loadIndex().getWorktrees());
    }

    @Override
    public synchronized void remove(String name, boolean force, boolean completeTask) {
        if (!gitAvailable) {
            throw new RuntimeException("Not in a git repository. Worktree tools require git.");
        }

        WorktreeInfo info = get(name);
        if (info == null) {
            throw new IllegalArgumentException("Worktree '" + name + "' not found");
        }

        // 发出删除前事件
        emitEvent("worktree.remove.before", Map.of(
            "task", info.getTaskId() != null ? Map.of("id", info.getTaskId()) : Map.of(),
            "worktree", Map.of("name", name, "path", info.getPath().toString())
        ));

        try {
            // 执行git worktree remove
            if (force) {
                runGit("worktree", "remove", "--force", info.getPath().toString());
            } else {
                runGit("worktree", "remove", info.getPath().toString());
            }

            // 完成任务
            if (completeTask && info.getTaskId() != null) {
                try {
                    taskManager.update(info.getTaskId(), "completed", null, null);
                    taskManager.unbindWorktree(info.getTaskId());

                    emitEvent("task.completed", Map.of(
                        "task", Map.of("id", info.getTaskId(), "status", "completed"),
                        "worktree", Map.of("name", name)
                    ));
                } catch (Exception e) {
                    // 任务更新失败不影响worktree删除
                    System.err.println("Failed to complete task " + info.getTaskId() + ": " + e.getMessage());
                }
            }

            // 更新索引
            WorktreeIndex index = loadIndex();
            index.getWorktrees().stream()
                .filter(wt -> wt.getName().equals(name))
                .findFirst()
                .ifPresent(wt -> wt.setStatus(WorktreeState.REMOVED));
            saveIndex(index);

            // 发出删除后事件
            emitEvent("worktree.remove.after", Map.of(
                "task", info.getTaskId() != null ? Map.of("id", info.getTaskId()) : Map.of(),
                "worktree", Map.of("name", name, "path", info.getPath().toString(), "status", "removed")
            ));

        } catch (Exception e) {
            // 发出失败事件
            emitEvent("worktree.remove.failed", Map.of(
                "task", info.getTaskId() != null ? Map.of("id", info.getTaskId()) : Map.of(),
                "worktree", Map.of("name", name, "path", info.getPath().toString()),
                "error", e.getMessage()
            ));
            throw new RuntimeException("Failed to remove worktree: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void keep(String name) {
        WorktreeInfo info = get(name);
        if (info == null) {
            throw new IllegalArgumentException("Worktree '" + name + "' not found");
        }

        // 更新状态
        WorktreeIndex index = loadIndex();
        index.getWorktrees().stream()
            .filter(wt -> wt.getName().equals(name))
            .findFirst()
            .ifPresent(wt -> wt.setStatus(WorktreeState.KEPT));
        saveIndex(index);

        // 发出保留事件
        emitEvent("worktree.keep", Map.of(
            "task", info.getTaskId() != null ? Map.of("id", info.getTaskId()) : Map.of(),
            "worktree", Map.of("name", name, "path", info.getPath().toString(), "status", "kept")
        ));
    }

    @Override
    public CommandResult execute(String name, String command) {
        WorktreeInfo info = get(name);
        if (info == null) {
            throw new IllegalArgumentException("Worktree '" + name + "' not found");
        }

        Path worktreePath = info.getPath();
        if (!Files.exists(worktreePath)) {
            throw new IllegalStateException("Worktree path does not exist: " + worktreePath);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(worktreePath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String result = output.toString().trim();

            // 记录命令执行事件
            emitEvent("command.executed", Map.of(
                "task", info.getTaskId() != null ? Map.of("id", info.getTaskId()) : Map.of(),
                "worktree", Map.of("name", name),
                "command", command,
                "exit_code", exitCode
            ));

            return new CommandResult(exitCode, result.isEmpty() ? "(no output)" : result);

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: " + e.getMessage(), e);
        }
    }

    @Override
    public String status(String name) {
        WorktreeInfo info = get(name);
        if (info == null) {
            throw new IllegalArgumentException("Worktree '" + name + "' not found");
        }

        Path worktreePath = info.getPath();
        if (!Files.exists(worktreePath)) {
            throw new IllegalStateException("Worktree path does not exist: " + worktreePath);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("git", "status", "--short", "--branch");
            pb.directory(worktreePath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();
            String result = output.toString().trim();
            return result.isEmpty() ? "Clean worktree" : result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get status: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void bindTask(String name, int taskId) {
        WorktreeInfo info = get(name);
        if (info == null) {
            throw new IllegalArgumentException("Worktree '" + name + "' not found");
        }

        // 检查任务是否存在
        try {
            taskManager.get(taskId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }

        // 更新worktree信息
        WorktreeIndex index = loadIndex();
        index.getWorktrees().stream()
            .filter(wt -> wt.getName().equals(name))
            .findFirst()
            .ifPresent(wt -> wt.setTaskId(taskId));
        saveIndex(index);

        // 更新任务
        taskManager.bindWorktree(taskId, name);

        // 发出绑定事件
        emitEvent("task.bound", Map.of(
            "task", Map.of("id", taskId),
            "worktree", Map.of("name", name)
        ));
    }

    @Override
    public synchronized void unbindTask(String name) {
        WorktreeInfo info = get(name);
        if (info == null) {
            throw new IllegalArgumentException("Worktree '" + name + "' not found");
        }

        Integer taskId = info.getTaskId();
        if (taskId == null) {
            return; // 没有绑定任务
        }

        // 更新worktree信息
        WorktreeIndex index = loadIndex();
        index.getWorktrees().stream()
            .filter(wt -> wt.getName().equals(name))
            .findFirst()
            .ifPresent(wt -> wt.setTaskId(null));
        saveIndex(index);

        // 更新任务
        try {
            taskManager.unbindWorktree(taskId);
        } catch (Exception e) {
            // 任务可能已删除，忽略错误
        }

        // 发出解绑事件
        emitEvent("task.unbound", Map.of(
            "task", Map.of("id", taskId),
            "worktree", Map.of("name", name)
        ));
    }

    @Override
    public String getRecentEvents(int limit) {
        try {
            List<String> lines = Files.readAllLines(eventsFile);
            int start = Math.max(0, lines.size() - limit);
            List<String> recent = lines.subList(start, lines.size());

            List<Map<String, Object>> events = new ArrayList<>();
            for (String line : recent) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = MAPPER.readValue(line, Map.class);
                    events.add(event);
                } catch (Exception e) {
                    events.add(Map.of("event", "parse_error", "raw", line));
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(events);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read events: " + e.getMessage(), e);
        }
    }

    // ========== 私有辅助方法 ==========

    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Invalid worktree name. Use 1-40 chars: letters, numbers, ., _, -");
        }
    }

    private void runGit(String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        pb.command(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Git command failed: " + output.toString().trim());
        }
    }

    private WorktreeIndex loadIndex() {
        try {
            return MAPPER.readValue(indexFile.toFile(), WorktreeIndex.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load worktree index", e);
        }
    }

    private void saveIndex(WorktreeIndex index) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(indexFile.toFile(), index);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save worktree index", e);
        }
    }

    private void emitEvent(String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", eventType);
            event.put("ts", Instant.now().toEpochMilli());
            event.putAll(data);

            try (FileWriter writer = new FileWriter(eventsFile.toFile(), true)) {
                writer.write(MAPPER.writeValueAsString(event) + "\n");
            }
        } catch (IOException e) {
            System.err.println("Failed to emit event: " + e.getMessage());
        }
    }

    // 内部索引类
    private static class WorktreeIndex {
        private List<WorktreeInfo> worktrees = new ArrayList<>();

        public List<WorktreeInfo> getWorktrees() {
            return worktrees;
        }

        public void setWorktrees(List<WorktreeInfo> worktrees) {
            this.worktrees = worktrees;
        }
    }
}
