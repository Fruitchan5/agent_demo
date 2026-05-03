package cn.edu.agent.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerTest {

    private Path tempDir;
    private TaskManager taskManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("task-test-");
        taskManager = new TaskManager(tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理临时目录
        if (Files.exists(tempDir)) {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
            }
        }
    }

    @Test
    void testScanUnclaimedTasks_emptyBoard() throws IOException {
        List<Task> unclaimed = taskManager.scanUnclaimedTasks();
        assertTrue(unclaimed.isEmpty());
    }

    @Test
    void testScanUnclaimedTasks_onlyUnclaimedTasks() throws IOException {
        // 创建 3 个未认领任务
        taskManager.create("Task 1", "desc1", List.of());
        taskManager.create("Task 2", "desc2", List.of());
        taskManager.create("Task 3", "desc3", List.of());

        List<Task> unclaimed = taskManager.scanUnclaimedTasks();
        assertEquals(3, unclaimed.size());
        assertEquals(1, unclaimed.get(0).getId());
        assertEquals(2, unclaimed.get(1).getId());
        assertEquals(3, unclaimed.get(2).getId());
    }

    @Test
    void testScanUnclaimedTasks_filtersClaimed() throws IOException {
        // 创建任务
        Task task1 = taskManager.create("Task 1", "desc1", List.of());
        taskManager.create("Task 2", "desc2", List.of());

        // 认领第一个任务
        taskManager.claimTask(task1.getId(), "alice");

        List<Task> unclaimed = taskManager.scanUnclaimedTasks();
        assertEquals(1, unclaimed.size());
        assertEquals(2, unclaimed.get(0).getId());
    }

    @Test
    void testScanUnclaimedTasks_filtersBlocked() throws IOException {
        // 创建任务
        Task task1 = taskManager.create("Task 1", "desc1", List.of());
        taskManager.create("Task 2", "desc2", List.of(task1.getId())); // 被 task1 阻塞

        List<Task> unclaimed = taskManager.scanUnclaimedTasks();
        assertEquals(1, unclaimed.size());
        assertEquals(1, unclaimed.get(0).getId());
    }

    @Test
    void testScanUnclaimedTasks_filtersNonPending() throws IOException {
        // 创建任务
        Task task1 = taskManager.create("Task 1", "desc1", List.of());
        taskManager.create("Task 2", "desc2", List.of());

        // 更新第一个任务状态为 completed
        taskManager.update(task1.getId(), "completed", null, null);

        List<Task> unclaimed = taskManager.scanUnclaimedTasks();
        assertEquals(1, unclaimed.size());
        assertEquals(2, unclaimed.get(0).getId());
    }

    @Test
    void testClaimTask_success() throws IOException {
        Task task = taskManager.create("Task 1", "desc1", List.of());

        ClaimResult result = taskManager.claimTask(task.getId(), "alice");

        assertTrue(result.isSuccess());
        assertEquals("Claimed task #" + task.getId(), result.getMessage());
        assertNotNull(result.getTask());
        assertEquals("alice", result.getTask().getOwner());
        assertEquals("in_progress", result.getTask().getStatus());
    }

    @Test
    void testClaimTask_alreadyClaimed() throws IOException {
        Task task = taskManager.create("Task 1", "desc1", List.of());
        taskManager.claimTask(task.getId(), "alice");

        ClaimResult result = taskManager.claimTask(task.getId(), "bob");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("already claimed"));
        assertNull(result.getTask());
    }

    @Test
    void testClaimTask_wrongStatus() throws IOException {
        Task task = taskManager.create("Task 1", "desc1", List.of());
        // 先认领任务（状态变为 in_progress）
        taskManager.claimTask(task.getId(), "alice");

        // 尝试再次认领已经是 in_progress 状态的任务
        ClaimResult result = taskManager.claimTask(task.getId(), "bob");

        assertFalse(result.isSuccess());
        // 这次会因为 owner 已存在而失败，而不是状态问题
        assertTrue(result.getMessage().contains("already claimed"));
        assertNull(result.getTask());
    }

    @Test
    void testClaimTask_blocked() throws IOException {
        Task task1 = taskManager.create("Task 1", "desc1", List.of());
        Task task2 = taskManager.create("Task 2", "desc2", List.of(task1.getId()));

        ClaimResult result = taskManager.claimTask(task2.getId(), "alice");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("blocked"));
        assertNull(result.getTask());
    }

    @Test
    void testClaimTask_notFound() throws IOException {
        ClaimResult result = taskManager.claimTask(999, "alice");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Error") || result.getMessage().contains("not found"));
        assertNull(result.getTask());
    }

    @Test
    void testConcurrentClaim_onlyOneSucceeds() throws Exception {
        // 创建一个任务
        Task task = taskManager.create("Task 1", "desc1", List.of());

        // 5 个线程同时尝试认领
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<ClaimResult>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            String owner = "agent" + i;
            futures.add(executor.submit(() -> taskManager.claimTask(task.getId(), owner)));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 收集结果
        List<ClaimResult> results = futures.stream()
            .map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();

        // 验证：只有一个成功
        long successCount = results.stream().filter(ClaimResult::isSuccess).count();
        assertEquals(1, successCount, "Only one agent should successfully claim the task");

        // 验证：失败的都是因为已被认领
        long alreadyClaimedCount = results.stream()
            .filter(r -> !r.isSuccess())
            .filter(r -> r.getMessage().contains("already claimed"))
            .count();
        assertEquals(4, alreadyClaimedCount);
    }

    @Test
    void testConcurrentClaim_multipleTasks() throws Exception {
        // 创建 3 个任务
        taskManager.create("Task 1", "desc1", List.of());
        taskManager.create("Task 2", "desc2", List.of());
        taskManager.create("Task 3", "desc3", List.of());

        // 3 个线程同时扫描并认领
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<ClaimResult>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 3; i++) {
            String owner = "agent" + i;
            futures.add(executor.submit(() -> {
                // 重试逻辑：如果第一个任务被抢先认领，尝试下一个
                for (int attempt = 0; attempt < 5; attempt++) {
                    List<Task> unclaimed = taskManager.scanUnclaimedTasks();
                    if (unclaimed.isEmpty()) {
                        break;
                    }
                    ClaimResult result = taskManager.claimTask(unclaimed.get(0).getId(), owner);
                    if (result.isSuccess()) {
                        return result;
                    }
                    // 认领失败，继续尝试
                }
                return ClaimResult.error("No tasks available");
            }));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 收集结果
        List<ClaimResult> results = futures.stream()
            .map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();

        // 验证：所有 3 个任务都被认领
        long successCount = results.stream().filter(ClaimResult::isSuccess).count();
        assertEquals(3, successCount, "All 3 tasks should be claimed");

        // 验证：没有剩余未认领任务
        List<Task> remaining = taskManager.scanUnclaimedTasks();
        assertTrue(remaining.isEmpty());
    }
}
