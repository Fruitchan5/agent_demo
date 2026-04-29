package cn.edu.agent.task;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单的任务持久化测试
 */
class TaskPersistenceTest {

    private Path tempDir;
    private TaskManager taskManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("task-test-");
        taskManager = new TaskManager(tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理测试文件
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // ignore
                }
            });
    }

    @Test
    void testCreateAndGet() throws IOException {
        // 创建任务
        Task task = taskManager.create("测试任务", "这是一个测试", null);

        assertEquals(1, task.getId());
        assertEquals("测试任务", task.getSubject());
        assertEquals("这是一个测试", task.getDescription());
        assertEquals("pending", task.getStatus());

        // 重新读取
        Task loaded = taskManager.get(1);
        assertEquals(task.getId(), loaded.getId());
        assertEquals(task.getSubject(), loaded.getSubject());
    }

    @Test
    void testDependency() throws IOException {
        // 创建两个任务，task2 依赖 task1
        Task task1 = taskManager.create("任务1", "先做这个", null);
        Task task2 = taskManager.create("任务2", "后做这个", List.of(1));

        assertEquals(1, task2.getBlockedBy().size());
        assertTrue(task2.getBlockedBy().contains(1));

        // task1 应该 block task2
        Task reloaded1 = taskManager.get(1);
        assertEquals(1, reloaded1.getBlocks().size());
        assertTrue(reloaded1.getBlocks().contains(2));
    }

    @Test
    void testCompleteTask() throws IOException {
        // 创建依赖关系
        taskManager.create("任务1", "先做", null);
        taskManager.create("任务2", "后做", List.of(1));

        // 完成 task1
        taskManager.update(1, "completed", null, null);

        // task2 的 blockedBy 应该被自动清空
        Task task2 = taskManager.get(2);
        assertTrue(task2.getBlockedBy().isEmpty());
    }

    @Test
    void testListAll() throws IOException {
        taskManager.create("任务A", "描述A", null);
        taskManager.create("任务B", "描述B", null);
        taskManager.create("任务C", "描述C", List.of(1));

        List<Task> all = taskManager.listAll();
        assertEquals(3, all.size());

        // 验证 blocks 字段被正确计算
        Task task1 = all.stream().filter(t -> t.getId() == 1).findFirst().orElseThrow();
        assertEquals(1, task1.getBlocks().size());
        assertTrue(task1.getBlocks().contains(3));
    }

    @Test
    void testAutoDeleteIndependentTask() throws IOException {
        // 创建一个独立任务
        Task task = taskManager.create("独立任务", "没有依赖关系", null);
        assertEquals(1, task.getId());

        // 完成任务
        taskManager.update(1, "completed", null, null);

        // 任务应该被自动删除
        List<Task> all = taskManager.listAll();
        assertEquals(0, all.size());

        // 文件也应该被删除
        assertFalse(Files.exists(tempDir.resolve("task_1.json")));
    }

    @Test
    void testAutoDeleteCompletedCluster() throws IOException {
        // 创建依赖链：task1 <- task2 <- task3
        taskManager.create("任务1", "第一步", null);
        taskManager.create("任务2", "第二步", List.of(1));
        taskManager.create("任务3", "第三步", List.of(2));

        // 完成 task1，集群未全部完成，不删除
        taskManager.update(1, "completed", null, null);
        assertEquals(3, taskManager.listAll().size());

        // 完成 task2，集群未全部完成，不删除
        taskManager.update(2, "completed", null, null);
        assertEquals(3, taskManager.listAll().size());

        // 完成 task3，集群全部完成，自动删除整个集群
        taskManager.update(3, "completed", null, null);
        assertEquals(0, taskManager.listAll().size());

        // 所有文件都应该被删除
        assertFalse(Files.exists(tempDir.resolve("task_1.json")));
        assertFalse(Files.exists(tempDir.resolve("task_2.json")));
        assertFalse(Files.exists(tempDir.resolve("task_3.json")));
    }

    @Test
    void testNoDeletePartialCluster() throws IOException {
        // 创建依赖关系：task1 <- task2, task1 <- task3
        taskManager.create("任务1", "基础任务", null);
        taskManager.create("任务2", "依赖1", List.of(1));
        taskManager.create("任务3", "依赖2", List.of(1));

        // 完成 task1 和 task2
        taskManager.update(1, "completed", null, null);
        taskManager.update(2, "completed", null, null);

        // task3 还未完成，集群不应该被删除
        List<Task> all = taskManager.listAll();
        assertEquals(3, all.size());

        // 文件都还在
        assertTrue(Files.exists(tempDir.resolve("task_1.json")));
        assertTrue(Files.exists(tempDir.resolve("task_2.json")));
        assertTrue(Files.exists(tempDir.resolve("task_3.json")));
    }
}
