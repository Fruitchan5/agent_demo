package cn.edu.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TaskManager {

    private static final String TASK_FILE_PREFIX = "task_";
    private static final String TASK_FILE_SUFFIX = ".json";
    private static final String TASK_FILE_PATTERN = "task_\\d+\\.json";

    private final Path tasksDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public TaskManager(Path tasksDir) {
        this.tasksDir = tasksDir;
        try {
            Files.createDirectories(tasksDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create tasks directory: " + tasksDir, e);
        }
    }

    public synchronized Task create(String subject, String description, List<Integer> blockedBy) throws IOException {
        Task task = new Task();
        task.setId(nextId());
        task.setSubject(subject);
        task.setDescription(description != null ? description : "");
        task.setStatus("pending");
        task.setBlockedBy(blockedBy != null ? new ArrayList<>(blockedBy) : new ArrayList<>());
        task.setOwner("");
        save(task);
        return task;
    }

    public synchronized Task get(int taskId) throws IOException {
        Task task = load(taskId);
        task.setBlocks(computeBlocks(taskId));
        return task;
    }

    public synchronized Task update(int taskId, String status,
                                    List<Integer> addBlockedBy,
                                    List<Integer> removeBlockedBy) throws IOException {
        Task task = load(taskId);
        if (status != null) {
            task.setStatus(status);
            if ("completed".equals(status)) {
                save(task);
                boolean clusterDeleted = deleteCompletedCluster(taskId);
                if (!clusterDeleted) {
                    clearDependency(taskId);
                }
                return task;
            }
        }
        if (addBlockedBy != null) {
            for (int id : addBlockedBy) {
                if (!task.getBlockedBy().contains(id)) task.getBlockedBy().add(id);
            }
        }
        if (removeBlockedBy != null) {
            task.getBlockedBy().removeAll(removeBlockedBy);
        }
        save(task);
        return task;
    }

    public synchronized List<Task> listAll() throws IOException {
        List<Task> tasks = loadAll();
        fillBlocks(tasks);
        return tasks;
    }

    public String toJson(Object obj) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private Path getTaskFilePath(int taskId) {
        return tasksDir.resolve(TASK_FILE_PREFIX + taskId + TASK_FILE_SUFFIX);
    }

    private void clearDependency(int completedId) throws IOException {
        for (Path file : taskFiles()) {
            Task t;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                t = mapper.readValue(reader, Task.class);
            }
            if (t.getBlockedBy().remove(Integer.valueOf(completedId))) {
                save(t);
            }
        }
    }

    private List<Integer> computeBlocks(int taskId) throws IOException {
        List<Integer> blocks = new ArrayList<>();
        for (Path file : taskFiles()) {
            Task t;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                t = mapper.readValue(reader, Task.class);
            }
            if (t.getBlockedBy().contains(taskId)) blocks.add(t.getId());
        }
        return blocks;
    }

    private void fillBlocks(List<Task> tasks) {
        Map<Integer, Task> byId = new HashMap<>();
        for (Task t : tasks) {
            t.setBlocks(new ArrayList<>());
            byId.put(t.getId(), t);
        }
        for (Task t : tasks) {
            for (int predId : t.getBlockedBy()) {
                Task pred = byId.get(predId);
                if (pred != null) pred.getBlocks().add(t.getId());
            }
        }
    }

    private void save(Task task) throws IOException {
        Path file = getTaskFilePath(task.getId());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, task);
        }
    }

    private Task load(int taskId) throws IOException {
        Path file = getTaskFilePath(taskId);
        if (!Files.exists(file)) throw new IllegalArgumentException("Task " + taskId + " not found");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return mapper.readValue(reader, Task.class);
        }
    }

    private List<Task> loadAll() throws IOException {
        List<Task> tasks = new ArrayList<>();
        for (Path file : taskFiles()) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                tasks.add(mapper.readValue(reader, Task.class));
            }
        }
        tasks.sort(Comparator.comparingInt(Task::getId));
        return tasks;
    }

    private List<Path> taskFiles() throws IOException {
        try (Stream<Path> stream = Files.list(tasksDir)) {
            return stream
                .filter(p -> p.getFileName().toString().matches(TASK_FILE_PATTERN))
                .collect(Collectors.toList());
        }
    }

    private int nextId() throws IOException {
        return taskFiles().stream()
            .map(p -> p.getFileName().toString())
            .mapToInt(n -> Integer.parseInt(n.substring(5, n.length() - 5)))
            .max()
            .orElse(0) + 1;
    }

    private boolean deleteCompletedCluster(int completedId) throws IOException {
        // 一次性加载所有任务到内存
        List<Task> allTasks = loadAll();
        Map<Integer, Task> taskMap = allTasks.stream()
            .collect(Collectors.toMap(Task::getId, t -> t));
        
        // 构建反向依赖关系（谁依赖我）
        Map<Integer, List<Integer>> blocksMap = new HashMap<>();
        for (Task t : allTasks) {
            for (int depId : t.getBlockedBy()) {
                blocksMap.computeIfAbsent(depId, k -> new ArrayList<>()).add(t.getId());
            }
        }
        
        // 在内存中查找依赖集群
        Set<Integer> cluster = findClusterInMemory(completedId, taskMap, blocksMap);
        
        // 检查集群中所有任务是否都已完成
        boolean allCompleted = cluster.stream()
            .allMatch(id -> {
                Task task = taskMap.get(id);
                return task != null && "completed".equals(task.getStatus());
            });
        
        // 如果全部完成，删除整个集群
        if (allCompleted) {
            List<Integer> failedDeletes = new ArrayList<>();
            for (int id : cluster) {
                try {
                    Path file = getTaskFilePath(id);
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    failedDeletes.add(id);
                    System.err.println("Failed to delete task " + id + ": " + e.getMessage());
                }
            }
            
            // 如果有删除失败的，抛出异常告知调用者
            if (!failedDeletes.isEmpty()) {
                throw new IOException("Failed to delete tasks: " + failedDeletes);
            }
            return true;
        }
        return false;
    }

    private Set<Integer> findClusterInMemory(int taskId, 
                                             Map<Integer, Task> taskMap,
                                             Map<Integer, List<Integer>> blocksMap) {
        Set<Integer> cluster = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(taskId);
        cluster.add(taskId);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            Task task = taskMap.get(current);
            if (task == null) continue;

            // 向上遍历：我依赖的任务
            for (int depId : task.getBlockedBy()) {
                if (cluster.add(depId)) {
                    queue.add(depId);
                }
            }

            // 向下遍历：依赖我的任务
            List<Integer> blocks = blocksMap.getOrDefault(current, Collections.emptyList());
            for (int blockId : blocks) {
                if (cluster.add(blockId)) {
                    queue.add(blockId);
                }
            }
        }

        return cluster;
    }
}
